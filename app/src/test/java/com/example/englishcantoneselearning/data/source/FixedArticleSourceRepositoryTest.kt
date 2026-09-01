package com.example.englishcantoneselearning.data.source

import com.example.englishcantoneselearning.model.ArticleSourceDefinition
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FixedArticleSourceRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun readsRssFetchesArticleCleansItAndEmitsStages() = runBlocking {
        val articleUrl = server.url("/article").toString()
        server.enqueue(MockResponse().setBody(rss(articleUrl)))
        server.enqueue(MockResponse().setBody(longArticleHtml()))
        val stages = mutableListOf<GenerationStage>()
        val repository = repository(listOf(definition(server.url("/feed").toString())))

        val snapshot = repository.discover(request()) { stages += it.stage }

        assertEquals("A useful technology story", snapshot.title)
        assertEquals(articleUrl.trimEnd('/'), snapshot.url.trimEnd('/'))
        assertTrue(snapshot.paragraphs.size >= 8)
        assertTrue(GenerationStage.DISCOVERING_SOURCE in stages)
        assertTrue(GenerationStage.FETCHING_SOURCE in stages)
        assertTrue(GenerationStage.CLEANING_SOURCE in stages)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun recentlyUsedCandidateIsExcludedBeforeArticleDownload() {
        val articleUrl = server.url("/article").toString()
        server.enqueue(MockResponse().setBody(rss(articleUrl)))
        val repository = repository(listOf(definition(server.url("/feed").toString())))

        assertThrows(SourceDiscoveryException::class.java) {
            runBlocking { repository.discover(request().copy(excludedSourceUrls = listOf(articleUrl))) }
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun malformedOrShortCandidateDoesNotInvokeAnythingOutsideWhitelist() {
        server.enqueue(MockResponse().setBody(rss("https://outside.example/story")))
        val repository = repository(listOf(definition(server.url("/feed").toString())))

        val error = assertThrows(SourceDiscoveryException::class.java) {
            runBlocking { repository.discover(request()) }
        }

        assertTrue(error.message.orEmpty().contains("没有可用") || error.message.orEmpty().contains("读取失败"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun newsRefreshFiltersOldItemsAssignsMultipleTagsAndForceBypassesCache() = runBlocking {
        val now = Instant.parse("2026-08-28T12:00:00Z").toEpochMilli()
        val feedUrl = server.url("/feed").toString()
        val firstUrl = server.url("/technology").toString()
        val oldUrl = server.url("/old").toString()
        val definition = definition(feedUrl).copy(
            defaultNewsTags = setOf(NewsTag.INTERNATIONAL),
        )
        server.enqueue(MockResponse().setBody(newsRss(
            item("AI technology reaches hospitals", firstUrl, "Fri, 28 Aug 2026 08:00:00 GMT",
                "Artificial intelligence technology improves medical health systems."),
            item("Old story", oldUrl, "Sat, 01 Aug 2026 08:00:00 GMT", "Technology archive."),
        )))
        val repository = repository(listOf(definition), now = now)

        val first = repository.refreshFeed(MaterialLanguage.ENGLISH, forceRefresh = false)

        assertEquals(listOf("AI technology reaches hospitals"), first.map { it.title })
        assertTrue(NewsTag.INTERNATIONAL in first.single().tags)
        assertTrue(NewsTag.TECHNOLOGY in first.single().tags)
        assertTrue(NewsTag.AI_DIGITAL in first.single().tags)
        assertTrue(NewsTag.HEALTH in first.single().tags)
        assertEquals(1, server.requestCount)

        repository.refreshFeed(MaterialLanguage.ENGLISH, forceRefresh = false)
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody(newsRss(
            item("Fresh finance market report", server.url("/finance").toString(),
                "Fri, 28 Aug 2026 10:00:00 GMT", "Business economy and finance market update."),
        )))

        val refreshed = repository.refreshFeed(MaterialLanguage.ENGLISH, forceRefresh = true)

        assertEquals(listOf("Fresh finance market report"), refreshed.map { it.title })
        assertTrue(NewsTag.FINANCE in refreshed.single().tags)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun newsArticleUsesRelaxedCodeOnlyBodyThresholds() = runBlocking {
        val articleUrl = server.url("/news-detail").toString()
        server.enqueue(MockResponse().setBody(buildString {
            append("<html><article>")
            repeat(4) { index ->
                append("<p>Paragraph $index contains verified local news context and enough words for direct reading. ")
                append("It is cleaned entirely by deterministic application code without any model request.</p>")
            }
            append("</article></html>")
        }))
        val repository = repository(listOf(definition(server.url("/feed").toString())))
        val item = NewsItem(
            sourceId = "test-source",
            publisher = "Test Publisher",
            title = "Direct reading story",
            url = articleUrl,
            publishedAt = null,
            publishedAtEpochMillis = null,
            summary = "",
            language = MaterialLanguage.ENGLISH,
            tags = setOf(NewsTag.LOCAL_LIFE),
        )

        val snapshot = repository.loadArticle(item)

        assertEquals(4, snapshot.paragraphs.size)
        assertEquals("article-cleaner-v2", snapshot.cleanerVersion)
        assertEquals(1, server.requestCount)
    }

    private fun repository(definitions: List<ArticleSourceDefinition>, now: Long = 100L) = FixedArticleSourceRepository(
        client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build(),
        definitions = definitions,
        now = { now },
    )

    private fun definition(feed: String) = ArticleSourceDefinition(
        id = "test-source",
        publisher = "Test Publisher",
        feedUrl = feed,
        language = MaterialLanguage.ENGLISH,
        topics = MaterialTopic.entries.toSet(),
        allowedHosts = setOf(server.hostName),
        contentSelectors = listOf("article"),
    )

    private fun request() = MaterialGenerationRequest(
        MaterialLanguage.ENGLISH, Difficulty.TARGET, MaterialTopic.TECHNOLOGY,
        currentDate = "2026-08-28",
    )

    private fun rss(articleUrl: String) = """
        <rss><channel><item><title>A useful technology story</title>
        <link>${articleUrl.replace("&", "&amp;")}</link><pubDate>Fri, 28 Aug 2026 08:00:00 GMT</pubDate>
        <description>Technology science digital innovation with a detailed summary.</description>
        </item></channel></rss>
    """.trimIndent()

    private fun newsRss(vararg items: String) = "<rss><channel>${items.joinToString("")}</channel></rss>"

    private fun item(title: String, url: String, date: String, summary: String) = """
        <item><title>$title</title><link>${url.replace("&", "&amp;")}</link>
        <pubDate>$date</pubDate><description>$summary</description></item>
    """.trimIndent()

    private fun longArticleHtml() = buildString {
        append("<html><article><h2>Technology</h2>")
        repeat(10) { index ->
            append("<p>This is informative paragraph $index about technology and practical science. ")
            append("It contains enough factual context to survive the local quality filter and support listening practice.</p>")
        }
        append("</article></html>")
    }
}
