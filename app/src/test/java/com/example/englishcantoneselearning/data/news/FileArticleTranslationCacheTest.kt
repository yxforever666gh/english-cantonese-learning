package com.example.englishcantoneselearning.data.news

import com.example.englishcantoneselearning.model.MaterialLanguage
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileArticleTranslationCacheTest {
    private lateinit var directory: File
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("article-translation-cache").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun exactKeyHitsAndReplacementIsAtomic() {
        val cache = FileArticleTranslationCache(directory) { now }
        val key = key(contentHash = "content-a")

        cache.save(key, mapOf("s1" to " 第一段 "))
        assertEquals(mapOf("s1" to "第一段"), cache.load(key))
        assertNull(cache.load(key.copy(sentenceHash = "different-segmentation")))

        cache.save(key, mapOf("s1" to "更新", "s2" to "第二段"))

        assertEquals(mapOf("s1" to "更新", "s2" to "第二段"), cache.load(key))
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        assertEquals(1, jsonFiles().size)
    }

    @Test
    fun expiredEntryIsIgnoredAndRemoved() {
        val cache = FileArticleTranslationCache(directory) { now }
        val key = key()
        cache.save(key, mapOf("s1" to "译文"))

        now += 14L * 24 * 60 * 60 * 1_000 + 1

        assertNull(cache.load(key))
        assertTrue(jsonFiles().isEmpty())
    }

    @Test
    fun damagedEntryFallsBackToMissAndIsRemoved() {
        val cache = FileArticleTranslationCache(directory) { now }
        val key = key()
        cache.save(key, mapOf("s1" to "译文"))
        jsonFiles().single().writeText("not-json")

        assertNull(cache.load(key))
        assertTrue(jsonFiles().isEmpty())
    }

    @Test
    fun keepsOnlyThirtyNewestArticles() {
        val cache = FileArticleTranslationCache(directory) { now }

        repeat(31) { index ->
            cache.save(key(contentHash = "content-$index"), mapOf("s1" to "译文-$index"))
            now += 1
        }

        assertEquals(30, jsonFiles().size)
        assertNull(cache.load(key(contentHash = "content-0")))
        assertEquals(mapOf("s1" to "译文-30"), cache.load(key(contentHash = "content-30")))
    }

    @Test
    fun invalidOrEmptyTranslationsAreNotPersisted() {
        val cache = FileArticleTranslationCache(directory) { now }

        cache.save(key(), mapOf("" to "x", "s1" to "  "))

        assertTrue(jsonFiles().isEmpty())
    }

    private fun key(contentHash: String = "content") = ArticleTranslationCacheKey(
        promptVersion = "news-translation-v1",
        language = MaterialLanguage.ENGLISH,
        contentHash = contentHash,
        sentenceHash = "sentences",
    )

    private fun jsonFiles(): List<File> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" }
}
