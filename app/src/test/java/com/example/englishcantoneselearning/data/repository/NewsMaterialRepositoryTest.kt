package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialDao
import com.example.englishcantoneselearning.data.local.MaterialDraftEntity
import com.example.englishcantoneselearning.data.local.MaterialEntity
import com.example.englishcantoneselearning.data.local.MaterialPlaybackProgressEntity
import com.example.englishcantoneselearning.data.network.GeneratedBatch
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsMaterialRepositoryTest {
    @Test
    fun saveNewsArticlePersistsCompleteSingleLanguageMaterial() = runTest {
        val storage = InMemoryMaterialDao()
        val repository = DefaultMaterialRepository(storage.dao, UnsupportedGenerator)
        val snapshot = snapshot()

        val saved = repository.saveNewsArticle(
            snapshot = snapshot,
            language = MaterialLanguage.ENGLISH,
            tags = linkedSetOf(NewsTag.TECHNOLOGY, NewsTag.AI_DIGITAL),
            sentenceTexts = listOf(
                "First sentence.",
                "Second sentence.",
                "Third sentence.",
            ),
        )

        assertEquals(ArticleOrigin.NEWS_FEED, saved.origin)
        assertEquals("news-import-v1", saved.promptVersion)
        assertEquals("科技、AI数码", saved.topic)
        assertEquals("First sentence. Second sentence.\n\nThird sentence.", saved.targetText)
        assertTrue(saved.sentences.all { it.jyutping == null && it.simplifiedChinese == null })
        assertEquals(listOf(0, 2), saved.sections.map { it.startSentenceIndex })
        assertEquals(listOf("Opening", "Update"), saved.sections.map { it.title })
        assertEquals(snapshot.contentHash, saved.sources.single().contentHash)
        assertEquals(snapshot.paragraphs.joinToString("\n\n") { it.text }, saved.targetText)
        assertEquals("news-import-v1", storage.materials.single().promptVersion)
        assertNull(saved.listeningBand)
    }

    @Test
    fun saveNewsArticleDeduplicatesCanonicalUrl() = runTest {
        val storage = InMemoryMaterialDao()
        val repository = DefaultMaterialRepository(storage.dao, UnsupportedGenerator)
        val original = repository.saveNewsArticle(
            snapshot(),
            MaterialLanguage.ENGLISH,
            setOf(NewsTag.TECHNOLOGY),
            listOf("First sentence.", "Second sentence.", "Third sentence."),
        )

        val duplicate = repository.saveNewsArticle(
            snapshot().copy(
                url = "https://EXAMPLE.com/news/story/?utm_source=reader#top",
                contentHash = "changed-hash",
            ),
            MaterialLanguage.ENGLISH,
            setOf(NewsTag.INTERNATIONAL),
            listOf("Replacement sentence."),
        )

        assertEquals(original, duplicate)
        assertEquals(1, storage.materials.size)
    }

    @Test
    fun saveNewsArticleDeduplicatesContentHashAcrossUrls() = runTest {
        val storage = InMemoryMaterialDao()
        val repository = DefaultMaterialRepository(storage.dao, UnsupportedGenerator)
        val original = repository.saveNewsArticle(
            snapshot(),
            MaterialLanguage.ENGLISH,
            emptySet(),
            listOf("First sentence.", "Second sentence.", "Third sentence."),
        )

        val duplicate = repository.saveNewsArticle(
            snapshot().copy(url = "https://example.org/reprinted-story"),
            MaterialLanguage.ENGLISH,
            emptySet(),
            listOf("First sentence.", "Second sentence.", "Third sentence."),
        )

        assertEquals(original.id, duplicate.id)
        assertEquals(1, storage.materials.size)
    }

    private fun snapshot() = SourceArticleSnapshot(
        sourceId = "source-1",
        publisher = "Publisher",
        title = "News title",
        url = "https://example.com/news/story",
        publishedAt = "2026-09-01T08:00:00Z",
        sourceLanguage = "English",
        paragraphs = listOf(
            SourceParagraph("paragraph-1", "Opening", "First sentence. Second sentence."),
            SourceParagraph("paragraph-2", "Update", "Third sentence."),
        ),
        contentHash = "content-hash",
        fetchedAt = 1234L,
        cleanerVersion = "cleaner-v1",
    )
}

private class InMemoryMaterialDao : InvocationHandler {
    val materials = mutableListOf<MaterialEntity>()
    private val playback = mutableListOf<MaterialPlaybackProgressEntity>()
    private var draft: MaterialDraftEntity? = null

    val dao: MaterialDao = Proxy.newProxyInstance(
        MaterialDao::class.java.classLoader,
        arrayOf(MaterialDao::class.java),
        this,
    ) as MaterialDao

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
        "getAll" -> materials.toList()
        "getById" -> materials.firstOrNull { it.id == args?.get(0) }
        "getByFingerprint" -> materials.filter { it.requestFingerprint == args?.get(0) }
        "insertAll" -> {
            @Suppress("UNCHECKED_CAST")
            (args?.get(0) as List<MaterialEntity>).forEach(::upsert)
        }
        "insert" -> upsert(args?.get(0) as MaterialEntity)
        "delete" -> materials.removeIf { it.id == (args?.get(0) as MaterialEntity).id }
        "deleteBatch" -> materials.removeIf { it.batchId == args?.get(0) }
        "getRecentSourcesJson" -> materials.take(args?.get(0) as Int).map { it.sourcesJson }
        "getAllPlaybackProgress" -> playback.toList()
        "getPlaybackProgress" -> playback.firstOrNull { it.materialId == args?.get(0) }
        "savePlaybackProgress" -> {
            val value = args?.get(0) as MaterialPlaybackProgressEntity
            playback.removeIf { it.materialId == value.materialId }
            playback += value
        }
        "deletePlaybackProgress" -> playback.removeIf { it.materialId == args?.get(0) }
        "getActiveDraft" -> draft
        "saveDraft" -> Unit.also { draft = args?.get(0) as MaterialDraftEntity }
        "deleteDraft" -> Unit.also { if (draft?.id == args?.get(0)) draft = null }
        "finalizeDraft" -> {
            upsert(args?.get(0) as MaterialEntity)
            if (draft?.id == args?.get(1)) draft = null
            Unit
        }
        "toString" -> "InMemoryMaterialDao"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.get(0)
        else -> error("Unexpected MaterialDao call: ${method.name}")
    }

    private fun upsert(entity: MaterialEntity) {
        materials.removeIf { it.id == entity.id }
        materials += entity
    }
}

private object UnsupportedGenerator : MaterialGenerator {
    override suspend fun test(provider: MaterialProviderConfig): Boolean = false

    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch =
        error("Generation is not used by news persistence tests")
}
