package com.example.englishcantoneselearning

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.englishcantoneselearning.data.local.MaterialDatabase
import com.example.englishcantoneselearning.data.network.FailoverMaterialGenerator
import com.example.englishcantoneselearning.data.network.OpenAiResponsesMaterialGateway
import com.example.englishcantoneselearning.data.network.OpenAiResponsesNewsTranslationGateway
import com.example.englishcantoneselearning.data.network.FailoverNewsTranslator
import com.example.englishcantoneselearning.data.news.FileArticleTranslationCache
import com.example.englishcantoneselearning.data.news.SharedPreferencesTitleTranslationCache
import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.data.preferences.UserPreferences
import com.example.englishcantoneselearning.data.preferences.EmbeddedAppSeedInstaller
import com.example.englishcantoneselearning.data.repository.DefaultMaterialRepository
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.data.source.FixedArticleSourceRepository
import com.example.englishcantoneselearning.data.source.SharedPreferencesNewsFeedCacheStore
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.data.security.SecureApiKeyStore
import com.example.englishcantoneselearning.speech.AudioCache
import com.example.englishcantoneselearning.speech.CloudSpeechController
import com.example.englishcantoneselearning.speech.MiniMaxSpeechGateway
import com.example.englishcantoneselearning.speech.MiniMaxVoiceGateway
import com.example.englishcantoneselearning.speech.SpeechController
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val embeddedSeedInstaller = EmbeddedAppSeedInstaller(appContext)
    private val useEmbeddedDatabaseSeed = embeddedSeedInstaller.run {
        installPreferencesIfEligible()
        prepareDatabaseSeed()
    }
    val userPreferences = UserPreferences(appContext)
    private val database = Room.databaseBuilder(
        appContext,
        MaterialDatabase::class.java,
        "listening-materials.db",
    )
        .let { builder ->
            if (useEmbeddedDatabaseSeed) builder.createFromAsset(EmbeddedAppSeedInstaller.DATABASE_ASSET)
            else builder
        }
        // Never add fallbackToDestructiveMigration here: a missing migration must fail safely
        // instead of silently deleting the learner's saved materials.
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            migration3To4(userPreferences.learnerProfile().listeningBand),
        )
        .build()

    private val legacyApiKeyStore = SecureApiKeyStore(appContext)
    val serviceConfigStore = ServiceConfigStore(appContext, legacyApiKeyStore)
    val audioCache = AudioCache(appContext)
    val miniMaxSpeechGateway = MiniMaxSpeechGateway()
    val miniMaxVoiceGateway = MiniMaxVoiceGateway()
    val speechController: SpeechController = CloudSpeechController(
        serviceConfigStore,
        miniMaxSpeechGateway,
        audioCache,
    )
    val materialGateway = OpenAiResponsesMaterialGateway()
    val materialGenerator = FailoverMaterialGenerator(serviceConfigStore, materialGateway)
    val newsTranslationGateway = OpenAiResponsesNewsTranslationGateway()
    val newsTranslationService = FailoverNewsTranslator(serviceConfigStore, newsTranslationGateway)
    val titleTranslationCache = SharedPreferencesTitleTranslationCache(appContext)
    val articleTranslationCache = FileArticleTranslationCache(appContext)
    val fixedSourceRepository = FixedArticleSourceRepository(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "EnglishCantoneseLearning/${BuildConfig.VERSION_NAME} (Android; personal reader)",
                        )
                        .header("Accept", "application/rss+xml, application/atom+xml, text/html;q=0.9, */*;q=0.5")
                        .build(),
                )
            }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build(),
        cacheStore = SharedPreferencesNewsFeedCacheStore(appContext),
    )
    val materialRepository: MaterialRepository = DefaultMaterialRepository(
        dao = database.materialDao(),
        generator = materialGenerator,
        sourceRepository = fixedSourceRepository,
    )

    fun close() {
        speechController.shutdown()
        database.close()
    }

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE practice_materials ADD COLUMN providerName TEXT NOT NULL DEFAULT 'Wawa'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE practice_materials ADD COLUMN origin TEXT NOT NULL DEFAULT 'AI_GENERATED'")
                db.execSQL("ALTER TABLE practice_materials ADD COLUMN sectionsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS material_playback_progress (
                        materialId TEXT NOT NULL PRIMARY KEY,
                        resumeSentenceIndex INTEGER NOT NULL,
                        completedSentenceIndicesJson TEXT NOT NULL,
                        completed INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(materialId) REFERENCES practice_materials(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS material_generation_drafts (
                        id TEXT NOT NULL PRIMARY KEY,
                        requestJson TEXT NOT NULL,
                        stateJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        resumeFailureCount INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )""".trimIndent(),
                )
            }
        }
    }
}

internal fun migration3To4(currentListeningBand: Float): Migration {
    val targetBand = MaterialLevelRules.normalizeListeningBand(currentListeningBand)
    val easyBand = MaterialLevelRules.effectiveListeningBand(targetBand, Difficulty.EASY)
    val challengeBand = MaterialLevelRules.effectiveListeningBand(targetBand, Difficulty.CHALLENGE)
    return object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE practice_materials ADD COLUMN listeningBand REAL")
            db.execSQL(
                """UPDATE practice_materials
                    SET listeningBand = CASE difficulty
                        WHEN 'EASY' THEN ?
                        WHEN 'CHALLENGE' THEN ?
                        ELSE ?
                    END
                    WHERE origin = 'AI_GENERATED'
                      AND language IN ('ENGLISH', 'CANTONESE')
                """.trimIndent(),
                arrayOf(easyBand, challengeBand, targetBand),
            )
        }
    }
}
