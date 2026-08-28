package com.example.englishcantoneselearning.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.network.GeneratedBatch
import com.example.englishcantoneselearning.data.network.GeneratedMaterial
import com.example.englishcantoneselearning.data.network.GeneratedSentence
import com.example.englishcantoneselearning.data.repository.DefaultMaterialRepository
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.SourceReference
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import com.example.englishcantoneselearning.data.source.FixedSourceRepository
import com.example.englishcantoneselearning.data.source.SourceDiscoveryException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialDatabaseDeviceTest {
    private lateinit var context: Context
    private var database: MaterialDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun generatedBatchIsDeduplicatedAndSurvivesDatabaseReopen() = runBlocking {
        val gateway = PersistingFakeGateway()
        val firstDatabase = openDatabase()
        val firstRepository = DefaultMaterialRepository(firstDatabase.materialDao(), gateway)
        val request = MaterialGenerationRequest(
            language = MaterialLanguage.ENGLISH,
            difficulty = Difficulty.EASY,
            topic = MaterialTopic.DAILY,
            currentDate = "2026-08-22",
        )

        val generated = firstRepository.generate(request)
        val cached = firstRepository.generate(request)

        assertEquals(1, generated.size)
        assertEquals(generated.map { it.id }, cached.map { it.id })
        assertEquals(1, gateway.calls)
        firstDatabase.close()
        database = null

        val reopened = openDatabase()
        val reopenedRepository = DefaultMaterialRepository(reopened.materialDao(), gateway)
        val restored = reopenedRepository.listMaterials()

        assertEquals(1, restored.size)
        assertEquals(generated.map { it.title }, restored.map { it.title })
        assertEquals("https://example.com/story-1", restored.first().sources.single().url)

        val progress = MaterialPlaybackProgress(
            materialId = restored.first().id,
            resumeSentenceIndex = 7,
            completedSentenceIndices = setOf(0, 2, 5),
            updatedAt = 1234L,
        )
        reopenedRepository.savePlaybackProgress(progress)
        assertEquals(progress, reopenedRepository.playbackProgress().getValue(restored.first().id))
    }

    @Test
    fun completedChapterDraftSurvivesReopenAndMergesIntoOneArticle() = runBlocking {
        val request = MaterialGenerationRequest(
            language = MaterialLanguage.ENGLISH,
            difficulty = Difficulty.EASY,
            topic = MaterialTopic.DAILY,
            currentDate = "2026-08-28",
        )
        val firstDatabase = openDatabase()
        val interrupted = DefaultMaterialRepository(firstDatabase.materialDao(), InterruptedChapterGenerator())

        assertThrows(Exception::class.java) { runBlocking { interrupted.generate(request) } }
        assertTrue(interrupted.hasPendingGeneration())
        firstDatabase.close()
        database = null

        val reopened = openDatabase()
        val resumed = DefaultMaterialRepository(reopened.materialDao(), ResumeChapterGenerator())
        val result = resumed.resumePendingGeneration()

        assertEquals(1, result?.size)
        assertEquals(23, result?.single()?.sentences?.size)
        assertEquals(listOf(0, 12), result?.single()?.sections?.map { it.startSentenceIndex })
        assertTrue(!resumed.hasPendingGeneration())
        assertEquals(1, resumed.listMaterials().size)
    }

    @Test
    fun fixedSourceSnapshotIsChunkedLocallyAndMergedWithoutRefetching() = runBlocking {
        val snapshot = SourceArticleSnapshot(
            sourceId = "fixed",
            publisher = "Fixed Publisher",
            title = "Fixed long source",
            url = "https://example.com/fixed-long-source",
            publishedAt = null,
            sourceLanguage = "English",
            paragraphs = (1..8).map { index ->
                SourceParagraph("p${index.toString().padStart(3, '0')}", if (index in setOf(1, 7)) "Section $index" else null,
                    "Paragraph $index " + "source fact ".repeat(85))
            },
            contentHash = "fixed-hash",
            fetchedAt = 10L,
            cleanerVersion = "cleaner-v1",
        )
        val sourceRepository = CountingStaticSourceRepository(snapshot)
        val generator = FixedSnapshotGenerator(snapshot)
        val repository = DefaultMaterialRepository(openDatabase().materialDao(), generator, sourceRepository)

        val result = repository.generate(MaterialGenerationRequest(
            MaterialLanguage.ENGLISH, Difficulty.EASY, MaterialTopic.TECHNOLOGY,
            currentDate = "2026-08-28",
        ))

        assertEquals(1, sourceRepository.calls)
        assertEquals(2, generator.calls)
        assertEquals(24, result.single().sentences.size)
        assertEquals("fixed-hash", result.single().sources.single().contentHash)
        assertEquals(1, repository.listMaterials().size)
        assertTrue(!repository.hasPendingGeneration())
    }

    @Test
    fun sourceDiscoveryFailureNeverCallsMaterialModelOrCreatesDraft() = runBlocking {
        val generator = CountingUnusedGenerator()
        val db = openDatabase()
        val repository = DefaultMaterialRepository(db.materialDao(), generator, object : FixedSourceRepository {
            override suspend fun discover(
                request: MaterialGenerationRequest,
                onActivity: (com.example.englishcantoneselearning.model.GenerationActivity) -> Unit,
            ): SourceArticleSnapshot = throw SourceDiscoveryException("all fixed sources failed")
        })

        assertThrows(SourceDiscoveryException::class.java) {
            runBlocking {
                repository.generate(MaterialGenerationRequest(
                    MaterialLanguage.ENGLISH, Difficulty.EASY, MaterialTopic.DAILY,
                    currentDate = "2026-08-28",
                ))
            }
        }

        assertEquals(0, generator.calls)
        assertEquals(0, repository.listMaterials().size)
        assertTrue(!repository.hasPendingGeneration())
    }

    private fun openDatabase(): MaterialDatabase = Room.databaseBuilder(
        context,
        MaterialDatabase::class.java,
        DATABASE_NAME,
    ).build().also { database = it }

    private companion object {
        const val DATABASE_NAME = "material-database-device-test.db"
    }
}

private fun generatedChapter(chapter: Int, hasMore: Boolean): GeneratedBatch {
    val url = "https://example.com/long-story"
    val sentences = (0 until 12).map { sentenceIndex ->
        val effectiveIndex = if (chapter == 1 && sentenceIndex == 0) 11 else chapter * 12 + sentenceIndex
        GeneratedSentence(
            targetText = (1..11).joinToString(" ") { "chapter${effectiveIndex}word$it" },
            jyutping = "",
            simplifiedChinese = "第 $effectiveIndex 句译文。",
        )
    }
    return GeneratedBatch(
        responseId = "resp-$chapter",
        inputTokens = 10,
        outputTokens = 20,
        materials = listOf(
            GeneratedMaterial(
                title = "Long story",
                topic = "日常",
                difficulty = "EASY",
                targetText = sentences.joinToString(" ") { it.targetText },
                sentences = sentences,
                sources = listOf(SourceReference("Long source", "Publisher", url, null, "English")),
                sections = listOf(com.example.englishcantoneselearning.data.network.GeneratedSection(
                    "section-$chapter", "Section ${chapter + 1}", 0,
                )),
                outlineSections = listOf("Section 1", "Section 2"),
                coveredSectionIds = listOf("section-$chapter"),
                hasMore = hasMore,
                nextSectionIndex = chapter + 1,
            ),
        ),
        webSourceUrls = setOf(url),
        providerName = "Test provider",
        model = "test-model",
    )
}

private class InterruptedChapterGenerator : MaterialGenerator {
    override suspend fun test(provider: com.example.englishcantoneselearning.model.MaterialProviderConfig) = true
    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch =
        if (request.chapterIndex == 0) generatedChapter(0, true) else error("simulated interruption")
}

private class ResumeChapterGenerator : MaterialGenerator {
    override suspend fun test(provider: com.example.englishcantoneselearning.model.MaterialProviderConfig) = true
    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch {
        assertEquals(1, request.chapterIndex)
        assertEquals(12, request.completedSentenceCount)
        return generatedChapter(1, false)
    }
}

private class CountingStaticSourceRepository(
    private val snapshot: SourceArticleSnapshot,
) : FixedSourceRepository {
    var calls = 0
    override suspend fun discover(
        request: MaterialGenerationRequest,
        onActivity: (com.example.englishcantoneselearning.model.GenerationActivity) -> Unit,
    ): SourceArticleSnapshot {
        calls++
        return snapshot
    }
}

private class FixedSnapshotGenerator(private val snapshot: SourceArticleSnapshot) : MaterialGenerator {
    var calls = 0
    override suspend fun test(provider: com.example.englishcantoneselearning.model.MaterialProviderConfig) = true
    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch {
        calls++
        assertTrue(request.chapterParagraphs.isNotEmpty())
        val sentences = (1..12).map { index ->
            GeneratedSentence((1..11).joinToString(" ") { "chapter${request.chapterIndex}sentence${index}word$it" }, "", "译文 $index")
        }
        return GeneratedBatch(
            "fixed-${request.chapterIndex}", 1, 2,
            listOf(GeneratedMaterial(
                snapshot.title, "科技", "EASY", sentences.joinToString(" ") { it.targetText }, sentences,
                listOf(SourceReference(snapshot.title, snapshot.publisher, snapshot.url, null, snapshot.sourceLanguage)),
                coveredParagraphIds = request.expectedParagraphIds,
                hasMore = request.expectedParagraphIds.last() != snapshot.paragraphs.last().id,
            )),
            emptySet(), providerName = "Fixed model", model = "model",
        )
    }
}

private class CountingUnusedGenerator : MaterialGenerator {
    var calls = 0
    override suspend fun test(provider: com.example.englishcantoneselearning.model.MaterialProviderConfig) = true
    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch {
        calls++
        error("must not be called")
    }
}

private class PersistingFakeGateway : MaterialGenerator {
    var calls = 0

    override suspend fun test(provider: com.example.englishcantoneselearning.model.MaterialProviderConfig): Boolean = true

    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch {
        calls++
        val urls = listOf("https://example.com/story-1")
        val materials = urls.mapIndexed { materialIndex, url ->
            val sentences = (1..20).map { sentenceIndex ->
                GeneratedSentence(
                    targetText = (1..11).joinToString(" ") { "word${materialIndex}_${sentenceIndex}_$it" },
                    jyutping = "",
                    simplifiedChinese = "这是第 $sentenceIndex 句译文。",
                )
            }
            GeneratedMaterial(
                title = "Saved material ${materialIndex + 1}",
                topic = "日常",
                difficulty = "EASY",
                targetText = sentences.joinToString(" ") { it.targetText },
                sentences = sentences,
                sources = listOf(
                    SourceReference(
                        title = "Source ${materialIndex + 1}",
                        publisher = "Publisher",
                        url = url,
                        publishedAt = "2026-08-20",
                        sourceLanguage = "English",
                    ),
                ),
                outlineSections = listOf("main"),
                coveredSectionIds = listOf("main"),
                hasMore = false,
            )
        }
        return GeneratedBatch(
            responseId = "resp-device",
            inputTokens = 12,
            outputTokens = 34,
            materials = materials,
            webSourceUrls = urls.toSet(),
            providerId = "test",
            providerName = "Device test",
            model = "test-model",
        )
    }
}
