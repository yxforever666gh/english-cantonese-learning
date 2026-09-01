package com.example.englishcantoneselearning.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishcantoneselearning.migration3To4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialDatabaseMigrationDeviceTest {
    private lateinit var context: Context
    private var roomDatabase: MaterialDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        roomDatabase?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migration3To4BackfillsAiBandsAndPreservesRelatedRows() {
        createVersion3Database()

        val migrated = Room.databaseBuilder(context, MaterialDatabase::class.java, DATABASE_NAME)
            .addMigrations(migration3To4(6.26f))
            .allowMainThreadQueries()
            .build()
            .also { roomDatabase = it }
        val dao = migrated.materialDao()

        assertEquals(6f, dao.getById("english-easy").listeningBand)
        assertEquals(6.5f, dao.getById("english-target").listeningBand)
        assertEquals(7f, dao.getById("cantonese-challenge").listeningBand)
        assertNull(dao.getById("manual").listeningBand)
        assertEquals("[{\"url\":\"preserved\"}]", dao.getById("english-easy").sourcesJson)
        assertEquals(3, dao.getPlaybackProgress("english-easy").resumeSentenceIndex)
        assertEquals(LEGACY_REQUEST_JSON, dao.activeDraft.requestJson)
    }

    private fun createVersion3Database() {
        val databaseFile = context.getDatabasePath(DATABASE_NAME).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                """CREATE TABLE practice_materials (
                    id TEXT NOT NULL PRIMARY KEY,
                    batchId TEXT NOT NULL,
                    batchPosition INTEGER NOT NULL,
                    language TEXT NOT NULL,
                    difficulty TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    title TEXT NOT NULL,
                    targetText TEXT NOT NULL,
                    sentencesJson TEXT NOT NULL,
                    sourcesJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    promptVersion TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    model TEXT NOT NULL,
                    responseId TEXT NOT NULL,
                    inputTokens INTEGER NOT NULL,
                    outputTokens INTEGER NOT NULL,
                    requestFingerprint TEXT NOT NULL,
                    origin TEXT NOT NULL,
                    sectionsJson TEXT NOT NULL
                )""".trimIndent(),
            )
            database.execSQL(
                """CREATE TABLE material_playback_progress (
                    materialId TEXT NOT NULL PRIMARY KEY,
                    resumeSentenceIndex INTEGER NOT NULL,
                    completedSentenceIndicesJson TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(materialId) REFERENCES practice_materials(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent(),
            )
            database.execSQL(
                """CREATE TABLE material_generation_drafts (
                    id TEXT NOT NULL PRIMARY KEY,
                    requestJson TEXT NOT NULL,
                    stateJson TEXT NOT NULL,
                    status TEXT NOT NULL,
                    resumeFailureCount INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""".trimIndent(),
            )
            insertMaterial(database, "english-easy", "ENGLISH", "EASY", "AI_GENERATED")
            insertMaterial(database, "english-target", "ENGLISH", "TARGET", "AI_GENERATED")
            insertMaterial(database, "cantonese-challenge", "CANTONESE", "CHALLENGE", "AI_GENERATED")
            insertMaterial(database, "manual", "ENGLISH", "TARGET", "MANUAL_PASTE")
            database.execSQL(
                "INSERT INTO material_playback_progress VALUES (?, ?, ?, ?, ?)",
                arrayOf("english-easy", 3, "[0,1]", 0, 1234L),
            )
            database.execSQL(
                "INSERT INTO material_generation_drafts VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf("draft", LEGACY_REQUEST_JSON, "{}", "PAUSED", 1, 2345L),
            )
            database.execSQL("PRAGMA user_version = 3")
        }
    }

    private fun insertMaterial(
        database: SQLiteDatabase,
        id: String,
        language: String,
        difficulty: String,
        origin: String,
    ) {
        database.insertOrThrow(
            "practice_materials",
            null,
            ContentValues().apply {
                put("id", id)
                put("batchId", id)
                put("batchPosition", 0)
                put("language", language)
                put("difficulty", difficulty)
                put("topic", "topic")
                put("title", id)
                put("targetText", "target")
                put("sentencesJson", "[]")
                put("sourcesJson", "[{\"url\":\"preserved\"}]")
                put("createdAt", 100L)
                put("promptVersion", "v6")
                put("providerName", "provider")
                put("model", "model")
                put("responseId", "response")
                put("inputTokens", 1)
                put("outputTokens", 2)
                put("requestFingerprint", "fingerprint-$id")
                put("origin", origin)
                put("sectionsJson", "[]")
            },
        )
    }

    private companion object {
        const val DATABASE_NAME = "material-database-migration-device-test.db"
        const val LEGACY_REQUEST_JSON =
            "{\"language\":\"ENGLISH\",\"difficulty\":\"EASY\",\"englishListening\":6.5}"
    }
}
