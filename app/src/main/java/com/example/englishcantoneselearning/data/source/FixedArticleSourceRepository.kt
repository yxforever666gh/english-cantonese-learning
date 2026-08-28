package com.example.englishcantoneselearning.data.source

import com.example.englishcantoneselearning.model.ArticleSourceDefinition
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceCandidate
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

interface FixedSourceRepository {
    suspend fun discover(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit = {},
    ): SourceArticleSnapshot
}

class FixedArticleSourceRepository(
    private val client: OkHttpClient,
    private val cleaner: ArticleContentCleaner = ArticleContentCleaner(),
    private val definitions: List<ArticleSourceDefinition> = defaultDefinitions(),
    private val now: () -> Long = System::currentTimeMillis,
) : FixedSourceRepository {
    private val feedCache = ConcurrentHashMap<String, CachedFeed>()

    override suspend fun discover(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): SourceArticleSnapshot = coroutineScope {
        onActivity(activity(request, GenerationStage.DISCOVERING_SOURCE, "source.feeds.start"))
        val applicable = definitions.filter { definition ->
            definition.language == request.language &&
                (request.topic == MaterialTopic.RANDOM || request.topic in definition.topics)
        }
        val feedResults = applicable.map { definition ->
            async {
                runCatching { definition to loadFeed(definition) }
            }
        }.awaitAll()
        val errors = feedResults.mapNotNull { it.exceptionOrNull()?.message }.distinct()
        val excluded = request.excludedSourceUrls.mapNotNull(::canonicalUrl).toSet()
        val candidates = feedResults.mapNotNull(Result<Pair<ArticleSourceDefinition, List<SourceCandidate>>>::getOrNull)
            .flatMap { (definition, values) ->
                values.mapIndexed { index, candidate ->
                    definition to candidate.copy(score = score(candidate, request.topic, index, excluded))
                }
            }
            .filter { (_, candidate) -> canonicalUrl(candidate.url) !in excluded }
            .sortedWith(compareByDescending<Pair<ArticleSourceDefinition, SourceCandidate>> { it.second.score }
                .thenBy { deterministicRank(request, it.second.url) })
            .take(20)
        if (candidates.isEmpty()) {
            throw SourceDiscoveryException(
                if (errors.isEmpty()) "固定来源没有可用的新文章" else "固定来源读取失败：${errors.joinToString("；").take(500)}",
            )
        }

        val attempts = diversify(candidates).take(MAX_ARTICLE_ATTEMPTS)
        val failures = mutableListOf<String>()
        for ((definition, candidate) in attempts) {
            try {
                onActivity(activity(request, GenerationStage.FETCHING_SOURCE, "source.article.fetch", definition.publisher))
                val response = execute(Request.Builder().url(candidate.url).get().build())
                val finalHost = response.finalUrl.host.lowercase()
                if (finalHost !in definition.allowedHosts) {
                    throw SourceDiscoveryException("来源重定向到了白名单外站点 $finalHost")
                }
                onActivity(activity(request, GenerationStage.CLEANING_SOURCE, "source.article.clean", definition.publisher))
                return@coroutineScope cleaner.clean(definition, candidate.copy(url = response.finalUrl.toString()), response.body)
            } catch (error: Throwable) {
                failures += "${definition.publisher}：${error.message ?: error.javaClass.simpleName}"
            }
        }
        throw SourceDiscoveryException("候选文章均无法提取正文：${failures.joinToString("；").take(600)}")
    }

    private suspend fun loadFeed(definition: ArticleSourceDefinition): List<SourceCandidate> {
        feedCache[definition.feedUrl]?.takeIf { now() - it.savedAt < FEED_CACHE_MS }?.let { return it.items }
        val response = execute(Request.Builder().url(definition.feedUrl).get().build())
        if (response.finalUrl.host.lowercase() !in definition.allowedHosts) {
            throw SourceDiscoveryException("${definition.publisher} RSS重定向到白名单外站点")
        }
        val parsed = parseFeed(response.body, definition)
        if (parsed.isEmpty()) throw SourceDiscoveryException("${definition.publisher} RSS没有文章")
        feedCache[definition.feedUrl] = CachedFeed(now(), parsed)
        return parsed
    }

    private fun parseFeed(xml: String, definition: ArticleSourceDefinition): List<SourceCandidate> {
        val itemBlocks = Regex("<item\\b[\\s\\S]*?</item>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }.toList()
        val blocks = if (itemBlocks.isNotEmpty()) itemBlocks else
            Regex("<entry\\b[\\s\\S]*?</entry>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }.toList()
        return blocks.take(80).mapNotNull { block ->
            val title = tagValue(block, "title")
            val link = tagValue(block, "link").ifBlank {
                Regex("<link[^>]+href=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.get(1).orEmpty()
            }
            if (title.isBlank() || link.isBlank() || !isAllowedUrl(link, definition.allowedHosts)) return@mapNotNull null
            SourceCandidate(
                sourceId = definition.id,
                publisher = definition.publisher,
                title = decodeText(title),
                url = decodeText(link),
                publishedAt = tagValue(block, "pubDate").ifBlank { tagValue(block, "updated") }.takeIf(String::isNotBlank),
                summary = decodeText(tagValue(block, "description").ifBlank { tagValue(block, "summary") }),
            )
        }.distinctBy { canonicalUrl(it.url) }
    }

    private fun tagValue(block: String, tag: String): String = Regex(
        "<$tag(?:\\s[^>]*)?>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE,
    ).find(block)?.groupValues?.get(1)?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim().orEmpty()

    private fun decodeText(value: String): String = org.jsoup.Jsoup.parse(value).text().trim()

    private fun score(
        candidate: SourceCandidate,
        topic: MaterialTopic,
        feedIndex: Int,
        excluded: Set<String>,
    ): Int {
        val haystack = "${candidate.title} ${candidate.summary}".lowercase()
        val topicScore = TOPIC_KEYWORDS[topic].orEmpty().count { it in haystack } * 20
        val unseenScore = if (canonicalUrl(candidate.url) !in excluded) 80 else -200
        return 200 - feedIndex.coerceAtMost(80) + topicScore + unseenScore + candidate.summary.length.coerceAtMost(300) / 20
    }

    private fun diversify(
        candidates: List<Pair<ArticleSourceDefinition, SourceCandidate>>,
    ): List<Pair<ArticleSourceDefinition, SourceCandidate>> {
        val result = mutableListOf<Pair<ArticleSourceDefinition, SourceCandidate>>()
        val queues = candidates.groupBy { it.first.id }.values.map { it.toMutableList() }.toMutableList()
        while (queues.any { it.isNotEmpty() }) {
            queues.forEach { queue -> if (queue.isNotEmpty()) result += queue.removeAt(0) }
        }
        return result
    }

    private fun deterministicRank(request: MaterialGenerationRequest, url: String): String {
        val seed = "${request.language}|${request.topic}|${request.currentDate}|${request.excludedSourceUrls.sorted()}|$url"
        return MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun execute(request: Request): SourceHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(SourceDiscoveryException(e.message ?: "网络错误", e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                runCatching {
                    response.use {
                        if (!it.isSuccessful) throw SourceDiscoveryException("HTTP ${it.code}")
                        SourceHttpResponse(it.request.url, it.body?.string().orEmpty())
                    }
                }.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
            }
        })
    }

    private fun activity(
        request: MaterialGenerationRequest,
        stage: GenerationStage,
        eventType: String,
        provider: String = "固定来源",
    ) = GenerationActivity(provider, request.chapterIndex + 1, stage, eventType)

    private data class CachedFeed(val savedAt: Long, val items: List<SourceCandidate>)
    private data class SourceHttpResponse(val finalUrl: okhttp3.HttpUrl, val body: String)

    companion object {
        private const val FEED_CACHE_MS = 30 * 60 * 1_000L
        private const val MAX_ARTICLE_ATTEMPTS = 5

        private val TOPIC_KEYWORDS = mapOf(
            MaterialTopic.DAILY to listOf("life", "daily", "family", "health", "生活", "社區", "健康"),
            MaterialTopic.TECHNOLOGY to listOf("technology", "science", "space", "digital", "科技", "創新"),
            MaterialTopic.CULTURE to listOf("culture", "art", "music", "film", "history", "文化", "藝術", "康樂"),
            MaterialTopic.WORK to listOf("business", "work", "job", "economy", "career", "就業", "工作", "商業"),
            MaterialTopic.CURRENT_EVENTS to listOf("world", "news", "government", "policy", "國際", "新聞", "政府"),
            MaterialTopic.RANDOM to emptyList(),
        )

        fun defaultDefinitions(): List<ArticleSourceDefinition> {
            val all = MaterialTopic.entries.toSet()
            fun source(
                id: String,
                publisher: String,
                feed: String,
                language: MaterialLanguage,
                topics: Set<MaterialTopic>,
                hosts: Set<String>,
                selectors: List<String>,
            ) = ArticleSourceDefinition(id, publisher, feed, language, topics + MaterialTopic.RANDOM, hosts, selectors)
            return listOf(
                source("bbc-world", "BBC", "https://feeds.bbci.co.uk/news/world/rss.xml", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.CURRENT_EVENTS, MaterialTopic.DAILY), setOf("feeds.bbci.co.uk", "www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk"), listOf("article", "main")),
                source("bbc-business", "BBC", "https://feeds.bbci.co.uk/news/business/rss.xml", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.WORK, MaterialTopic.CURRENT_EVENTS), setOf("feeds.bbci.co.uk", "www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk"), listOf("article", "main")),
                source("bbc-tech", "BBC", "https://feeds.bbci.co.uk/news/technology/rss.xml", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.TECHNOLOGY), setOf("feeds.bbci.co.uk", "www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk"), listOf("article", "main")),
                source("bbc-science", "BBC", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.TECHNOLOGY, MaterialTopic.DAILY), setOf("feeds.bbci.co.uk", "www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk"), listOf("article", "main")),
                source("bbc-culture", "BBC", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.CULTURE), setOf("feeds.bbci.co.uk", "www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk"), listOf("article", "main")),
                source("nasa", "NASA", "https://www.nasa.gov/feed/", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.TECHNOLOGY, MaterialTopic.CULTURE), setOf("www.nasa.gov", "nasa.gov", "science.nasa.gov"), listOf("article", "main")),
                source("un-news", "UN News", "https://news.un.org/feed/subscribe/en/news/all/rss.xml", MaterialLanguage.ENGLISH,
                    setOf(MaterialTopic.CURRENT_EVENTS, MaterialTopic.WORK, MaterialTopic.DAILY), setOf("news.un.org"), listOf("article", "main", ".field--name-body")),
                source("hk-top", "香港政府新聞網", "https://www.news.gov.hk/tc/common/html/topstories.rss.xml", MaterialLanguage.CANTONESE,
                    all, setOf("www.news.gov.hk", "news.gov.hk"), listOf("article", "main", "#content")),
                source("hk-city", "香港政府新聞網", "https://www.news.gov.hk/tc/city_life/html/articlelist.rss.xml", MaterialLanguage.CANTONESE,
                    setOf(MaterialTopic.DAILY, MaterialTopic.CULTURE), setOf("www.news.gov.hk", "news.gov.hk"), listOf("article", "main", "#content")),
                source("hk-school-work", "香港政府新聞網", "https://www.news.gov.hk/tc/categories/school_work/html/articlelist.rss.xml", MaterialLanguage.CANTONESE,
                    setOf(MaterialTopic.WORK, MaterialTopic.DAILY), setOf("www.news.gov.hk", "news.gov.hk"), listOf("article", "main", "#content")),
                source("hk-health", "香港政府新聞網", "https://www.news.gov.hk/tc/categories/health/html/articlelist.rss.xml", MaterialLanguage.CANTONESE,
                    setOf(MaterialTopic.DAILY, MaterialTopic.CURRENT_EVENTS), setOf("www.news.gov.hk", "news.gov.hk"), listOf("article", "main", "#content")),
                source("rthk-local", "香港電台", "https://rthk.hk/rthk/news/rss/c_expressnews_clocal.xml", MaterialLanguage.CANTONESE,
                    all, setOf("rthk.hk", "news.rthk.hk", "www.rthk.hk"), listOf("article", "main", ".itemFullText")),
                source("rthk-finance", "香港電台", "https://rthk.hk/rthk/news/rss/c_expressnews_cfinance.xml", MaterialLanguage.CANTONESE,
                    setOf(MaterialTopic.WORK, MaterialTopic.CURRENT_EVENTS), setOf("rthk.hk", "news.rthk.hk", "www.rthk.hk"), listOf("article", "main", ".itemFullText")),
                source("hk-press", "香港政府新聞公報", "https://www.info.gov.hk/gia/rss/general_zh.xml", MaterialLanguage.CANTONESE,
                    all, setOf("www.info.gov.hk", "info.gov.hk"), listOf("article", "main", "#pressrelease", ".pressrelease")),
            )
        }

        private fun canonicalUrl(value: String): String? = runCatching {
            val uri = URI(value.trim())
            val localTest = uri.host?.lowercase() in LOCAL_HTTP_HOSTS
            if ((uri.scheme != "https" && !localTest) || uri.host.isNullOrBlank()) return null
            "${uri.scheme.lowercase()}://${uri.host.lowercase()}${uri.path.trimEnd('/')}"
        }.getOrNull()

        private fun isAllowedUrl(value: String, hosts: Set<String>): Boolean = runCatching {
            val uri = URI(decodeXmlUrl(value))
            val host = uri.host?.lowercase()
            (uri.scheme == "https" || host in LOCAL_HTTP_HOSTS) && host in hosts
        }.getOrDefault(false)

        private fun decodeXmlUrl(value: String): String = value.replace("&amp;", "&")

        // MockWebServer may expose its loopback endpoint through the container bridge on Windows.
        // These names are accepted only when the same host is also explicitly present in a source whitelist.
        private val LOCAL_HTTP_HOSTS = setOf(
            "localhost",
            "127.0.0.1",
            "host.docker.internal",
            "kubernetes.docker.internal",
        )
    }
}

class SourceDiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)
