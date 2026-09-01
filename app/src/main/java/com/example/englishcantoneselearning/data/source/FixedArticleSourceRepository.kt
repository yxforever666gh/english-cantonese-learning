package com.example.englishcantoneselearning.data.source

import com.example.englishcantoneselearning.model.ArticleSourceDefinition
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceCandidate
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    suspend fun refreshFeed(language: MaterialLanguage, forceRefresh: Boolean = false): List<NewsItem> =
        error("当前来源仓库不支持新闻列表")
    suspend fun loadArticle(item: NewsItem): SourceArticleSnapshot =
        error("当前来源仓库不支持新闻正文")
}

class FixedArticleSourceRepository(
    private val client: OkHttpClient,
    private val cleaner: ArticleContentCleaner = ArticleContentCleaner(),
    private val definitions: List<ArticleSourceDefinition> = defaultDefinitions(),
    private val cacheStore: NewsFeedCacheStore = NoOpNewsFeedCacheStore,
    private val now: () -> Long = System::currentTimeMillis,
) : FixedSourceRepository {
    private val feedCache = ConcurrentHashMap<String, CachedFeed>()

    override suspend fun refreshFeed(language: MaterialLanguage, forceRefresh: Boolean): List<NewsItem> = coroutineScope {
        val applicable = definitions.filter { it.language == language }
        val results = applicable.map { definition ->
            async { runCatching { definition to loadFeed(definition, forceRefresh) } }
        }.awaitAll()
        val successes = results.mapNotNull(Result<Pair<ArticleSourceDefinition, List<SourceCandidate>>>::getOrNull)
            .filter { it.second.isNotEmpty() }
        if (successes.isEmpty()) {
            val fallback = cachedFallback(language)
            if (fallback.isNotEmpty()) return@coroutineScope fallback
            val errors = results.mapNotNull { it.exceptionOrNull()?.message }.distinct()
            throw SourceDiscoveryException(
                if (errors.isEmpty()) "固定来源没有可用的新闻"
                else "固定来源读取失败：${errors.joinToString("；").take(500)}",
            )
        }
        val items = buildFeed(successes)
        if (items.isEmpty()) {
            val fallback = cachedFallback(language)
            if (fallback.isNotEmpty()) return@coroutineScope fallback
            throw SourceDiscoveryException("没有符合时间和质量条件的新闻")
        }
        cacheStore.save(language, CachedNewsFeed(now(), items))
        items
    }

    override suspend fun loadArticle(item: NewsItem): SourceArticleSnapshot {
        val definition = definitionFor(item)
        val response = fetchArticle(definition, item.url)
        return cleaner.cleanForNews(
            definition,
            item.toCandidate().copy(url = response.finalUrl.toString()),
            response.body,
            now(),
        )
    }

    override suspend fun discover(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): SourceArticleSnapshot {
        onActivity(activity(request, GenerationStage.DISCOVERING_SOURCE, "source.feeds.start"))
        val applicableIds = definitions.filter {
            it.language == request.language && (request.topic == MaterialTopic.RANDOM || request.topic in it.topics)
        }.mapTo(mutableSetOf()) { it.id }
        val excluded = request.excludedSourceUrls.mapNotNull(::canonicalUrl).toSet()
        val candidates = refreshFeed(request.language, false)
            .filter { it.sourceId in applicableIds && canonicalUrl(it.url) !in excluded }
            .mapIndexed { index, item ->
                definitionFor(item) to item.toCandidate().copy(score = score(item.toCandidate(), request.topic, index))
            }
            .sortedWith(compareByDescending<Pair<ArticleSourceDefinition, SourceCandidate>> { it.second.score }
                .thenBy { deterministicRank(request, it.second.url) })
        if (candidates.isEmpty()) throw SourceDiscoveryException("固定来源没有可用的新文章")
        val failures = mutableListOf<String>()
        for ((definition, candidate) in diversify(candidates).take(MAX_ARTICLE_ATTEMPTS)) {
            try {
                onActivity(activity(request, GenerationStage.FETCHING_SOURCE, "source.article.fetch", definition.publisher))
                val response = fetchArticle(definition, candidate.url)
                onActivity(activity(request, GenerationStage.CLEANING_SOURCE, "source.article.clean", definition.publisher))
                return cleaner.clean(
                    definition,
                    candidate.copy(url = response.finalUrl.toString()),
                    response.body,
                    now(),
                )
            } catch (error: Throwable) {
                failures += "${definition.publisher}：${error.message ?: error.javaClass.simpleName}"
            }
        }
        throw SourceDiscoveryException("候选文章均无法提取正文：${failures.joinToString("；").take(600)}")
    }

    private suspend fun loadFeed(definition: ArticleSourceDefinition, forceRefresh: Boolean): List<SourceCandidate> {
        if (!forceRefresh) {
            feedCache[definition.feedUrl]?.takeIf { now() - it.savedAt in 0 until FEED_CACHE_MS }?.let { return it.items }
        }
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
        val rss = Regex("<item\\b[\\s\\S]*?</item>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }.toList()
        val blocks = rss.ifEmpty {
            Regex("<entry\\b[\\s\\S]*?</entry>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }.toList()
        }
        return blocks.take(200).mapNotNull { block ->
            val title = decodeText(tagValue(block, "title"))
            val link = tagValue(block, "link").ifBlank {
                Regex("<link[^>]+href=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.get(1).orEmpty()
            }.let(::decodeXmlUrl)
            val summary = decodeText(tagValue(block, "description").ifBlank {
                tagValue(block, "summary").ifBlank { tagValue(block, "content") }
            })
            val published = listOf("pubDate", "published", "updated", "dc:date")
                .firstNotNullOfOrNull { tagValue(block, it).takeIf(String::isNotBlank) }
            if (title.isBlank() || link.isBlank() || isNoise(title) || !isAllowedUrl(link, definition.allowedHosts)) null
            else SourceCandidate(definition.id, definition.publisher, title, link, published, summary)
        }.distinctBy { canonicalUrl(it.url) }
    }

    private fun buildFeed(sources: List<Pair<ArticleSourceDefinition, List<SourceCandidate>>>): List<NewsItem> {
        val cutoff = now() - RETENTION_MS
        val queues = sources.mapNotNull { (definition, candidates) ->
            candidates.mapNotNull { candidate ->
                val epoch = parseDate(candidate.publishedAt)
                if (epoch != null && epoch < cutoff) null else NewsItem(
                    candidate.sourceId, candidate.publisher, candidate.title, candidate.url,
                    candidate.publishedAt, epoch, candidate.summary, definition.language,
                    tags(definition, candidate),
                )
            }.sortedByDescending { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                .take(MAX_PER_SOURCE).toMutableList().takeIf { it.isNotEmpty() }
        }.toMutableList()
        val result = mutableListOf<NewsItem>()
        val urls = mutableSetOf<String>()
        val titles = mutableSetOf<String>()
        while (queues.any { it.isNotEmpty() } && result.size < MAX_ITEMS) {
            queues.sortedByDescending { it.firstOrNull()?.publishedAtEpochMillis ?: Long.MIN_VALUE }.forEach { queue ->
                while (queue.isNotEmpty()) {
                    val item = queue.removeAt(0)
                    val url = canonicalUrl(item.url) ?: continue
                    val title = item.title.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")
                    if (urls.add(url) && titles.add(title)) {
                        result += item
                        break
                    }
                }
            }
        }
        return result.take(MAX_ITEMS)
    }

    private fun tags(definition: ArticleSourceDefinition, candidate: SourceCandidate): Set<NewsTag> {
        val scores = mutableMapOf<NewsTag, Int>()
        definition.defaultNewsTags.forEach { scores[it] = 1 }
        val title = candidate.title.lowercase(Locale.ROOT)
        val summary = candidate.summary.lowercase(Locale.ROOT)
        TAG_WORDS.forEach { (tag, words) ->
            val score = words.count(title::contains) * 3 + words.count(summary::contains)
            if (score > 0) scores[tag] = (scores[tag] ?: 0) + score
        }
        return scores.entries.sortedWith(compareByDescending<Map.Entry<NewsTag, Int>> { it.value }.thenBy { it.key.ordinal })
            .take(5).mapTo(linkedSetOf()) { it.key }
    }

    private fun cachedFallback(language: MaterialLanguage): List<NewsItem> {
        val cached = cacheStore.load(language) ?: return emptyList()
        if (now() - cached.savedAt !in 0..RETENTION_MS) return emptyList()
        val cutoff = now() - RETENTION_MS
        return cached.items.filter {
            it.language == language && (it.publishedAtEpochMillis == null || it.publishedAtEpochMillis >= cutoff)
        }.take(MAX_ITEMS)
    }

    private suspend fun fetchArticle(definition: ArticleSourceDefinition, url: String): HttpResponse {
        if (!isAllowedUrl(url, definition.allowedHosts)) throw SourceDiscoveryException("文章链接不在来源白名单中")
        val response = execute(Request.Builder().url(url).get().build())
        val host = response.finalUrl.host.lowercase()
        if (host !in definition.allowedHosts) throw SourceDiscoveryException("来源重定向到了白名单外站点 $host")
        return response
    }

    private fun definitionFor(item: NewsItem) = definitions.firstOrNull {
        it.id == item.sourceId && it.language == item.language
    } ?: throw SourceDiscoveryException("新闻来源已失效或不在白名单中")

    private fun NewsItem.toCandidate() = SourceCandidate(sourceId, publisher, title, url, publishedAt, summary)

    private fun tagValue(block: String, tag: String) = Regex(
        "<$tag(?:\\s[^>]*)?>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE,
    ).find(block)?.groupValues?.get(1)?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim().orEmpty()

    private fun decodeText(value: String) = org.jsoup.Jsoup.parse(value).text().trim()

    private fun score(candidate: SourceCandidate, topic: MaterialTopic, index: Int): Int {
        val text = "${candidate.title} ${candidate.summary}".lowercase()
        return 280 - index.coerceAtMost(80) + TOPIC_WORDS[topic].orEmpty().count(text::contains) * 20
    }

    private fun diversify(values: List<Pair<ArticleSourceDefinition, SourceCandidate>>): List<Pair<ArticleSourceDefinition, SourceCandidate>> {
        val result = mutableListOf<Pair<ArticleSourceDefinition, SourceCandidate>>()
        val queues = values.groupBy { it.first.id }.values.map { it.toMutableList() }
        while (queues.any { it.isNotEmpty() }) queues.forEach { if (it.isNotEmpty()) result += it.removeAt(0) }
        return result
    }

    private fun deterministicRank(request: MaterialGenerationRequest, url: String): String {
        val seed = "${request.language}|${request.topic}|${request.currentDate}|${request.excludedSourceUrls.sorted()}|$url"
        return MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun execute(request: Request): HttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(SourceDiscoveryException(e.message ?: "网络错误", e))
            }
            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) { response.close(); return }
                runCatching {
                    response.use {
                        if (!it.isSuccessful) throw SourceDiscoveryException("HTTP ${it.code}")
                        HttpResponse(it.request.url, it.body?.string().orEmpty())
                    }
                }.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
            }
        })
    }

    private fun activity(request: MaterialGenerationRequest, stage: GenerationStage, type: String, provider: String = "固定来源") =
        GenerationActivity(provider, request.chapterIndex + 1, stage, type)

    private data class CachedFeed(val savedAt: Long, val items: List<SourceCandidate>)
    private data class HttpResponse(val finalUrl: okhttp3.HttpUrl, val body: String)

    companion object {
        private const val FEED_CACHE_MS = 30 * 60 * 1_000L
        private const val RETENTION_MS = 7 * 24 * 60 * 60 * 1_000L
        private const val MAX_ARTICLE_ATTEMPTS = 5
        private const val MAX_PER_SOURCE = 8
        private const val MAX_ITEMS = 60

        private val NOISE = listOf("advertisement", "advertorial", "sponsored content", "subscribe", "newsletter", "job vacancy", "广告", "廣告", "订阅", "訂閱")
        private val TAG_WORDS = mapOf(
            NewsTag.INTERNATIONAL to listOf("world", "international", "global", "war", "国际", "國際", "全球"),
            NewsTag.HONG_KONG to listOf("hong kong", "香港", "本港"),
            NewsTag.LOCAL_LIFE to listOf("community", "housing", "transport", "weather", "社区", "社區", "交通", "民生"),
            NewsTag.GOVERNMENT_POLICY to listOf("government", "policy", "minister", "政府", "政策", "立法会", "立法會"),
            NewsTag.SAFETY_LAW to listOf("law", "court", "police", "security", "法律", "法院", "警方", "安全"),
            NewsTag.FINANCE to listOf("business", "economy", "finance", "market", "经济", "經濟", "财经", "財經", "金融"),
            NewsTag.CAREERS to listOf("employment", "workplace", "career", "labour", "就业", "就業", "职场", "劳工", "勞工"),
            NewsTag.TECHNOLOGY to listOf("technology", "tech", "innovation", "robot", "科技", "创新", "創新", "机器人", "機器人"),
            NewsTag.AI_DIGITAL to listOf("artificial intelligence", " ai ", "digital", "cyber", "software", "人工智能", "数码", "數碼", "网络", "網絡"),
            NewsTag.SCIENCE_SPACE to listOf("science", "space", "nasa", "moon", "mars", "科学", "太空", "宇宙", "月球", "火星"),
            NewsTag.ENVIRONMENT_CLIMATE to listOf("climate", "environment", "carbon", "pollution", "气候", "氣候", "环境", "環境", "污染"),
            NewsTag.HEALTH to listOf("health", "hospital", "medical", "disease", "健康", "医院", "醫院", "医疗", "醫療"),
            NewsTag.EDUCATION to listOf("education", "school", "student", "university", "教育", "学校", "學校", "学生", "學生", "大学", "大學"),
            NewsTag.CULTURE_ARTS to listOf("culture", "art", "museum", "heritage", "文化", "艺术", "藝術", "博物馆"),
            NewsTag.ENTERTAINMENT_MUSIC to listOf("film", "movie", "music", "television", "entertainment", "电影", "電影", "音乐", "音樂", "影视", "影視"),
        )
        private val TOPIC_WORDS = mapOf(
            MaterialTopic.DAILY to listOf("life", "family", "health", "生活", "社区", "社區", "健康"),
            MaterialTopic.TECHNOLOGY to listOf("technology", "science", "space", "digital", "科技"),
            MaterialTopic.CULTURE to listOf("culture", "art", "music", "film", "文化", "艺术", "藝術"),
            MaterialTopic.WORK to listOf("business", "work", "job", "economy", "就业", "就業", "工作"),
            MaterialTopic.CURRENT_EVENTS to listOf("world", "news", "government", "policy", "国际", "國際", "新闻", "新聞"),
            MaterialTopic.RANDOM to emptyList(),
        )

        fun defaultDefinitions(): List<ArticleSourceDefinition> {
            val all = MaterialTopic.entries.toSet()
            fun source(id: String, publisher: String, feed: String, language: MaterialLanguage, topics: Set<MaterialTopic>, hosts: Set<String>, selectors: List<String>, tags: Set<NewsTag>) =
                ArticleSourceDefinition(id, publisher, feed, language, topics + MaterialTopic.RANDOM, hosts, selectors, tags)
            val bbcHosts = setOf("feeds.bbci.co.uk", "www.bbc.com", "bbc.com", "www.bbc.co.uk", "bbc.co.uk")
            val hkHosts = setOf("www.news.gov.hk", "news.gov.hk")
            val rthkHosts = setOf("rthk.hk", "news.rthk.hk", "www.rthk.hk")
            return listOf(
                source("bbc-world", "BBC", "https://feeds.bbci.co.uk/news/world/rss.xml", MaterialLanguage.ENGLISH, setOf(MaterialTopic.CURRENT_EVENTS, MaterialTopic.DAILY), bbcHosts, listOf("article", "main"), setOf(NewsTag.INTERNATIONAL)),
                source("bbc-business", "BBC", "https://feeds.bbci.co.uk/news/business/rss.xml", MaterialLanguage.ENGLISH, setOf(MaterialTopic.WORK, MaterialTopic.CURRENT_EVENTS), bbcHosts, listOf("article", "main"), setOf(NewsTag.FINANCE, NewsTag.CAREERS)),
                source("bbc-tech", "BBC", "https://feeds.bbci.co.uk/news/technology/rss.xml", MaterialLanguage.ENGLISH, setOf(MaterialTopic.TECHNOLOGY), bbcHosts, listOf("article", "main"), setOf(NewsTag.TECHNOLOGY, NewsTag.AI_DIGITAL)),
                source("bbc-science", "BBC", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", MaterialLanguage.ENGLISH, setOf(MaterialTopic.TECHNOLOGY, MaterialTopic.DAILY), bbcHosts, listOf("article", "main"), setOf(NewsTag.SCIENCE_SPACE, NewsTag.ENVIRONMENT_CLIMATE)),
                source("bbc-culture", "BBC", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", MaterialLanguage.ENGLISH, setOf(MaterialTopic.CULTURE), bbcHosts, listOf("article", "main"), setOf(NewsTag.CULTURE_ARTS, NewsTag.ENTERTAINMENT_MUSIC)),
                source("nasa", "NASA", "https://www.nasa.gov/feed/", MaterialLanguage.ENGLISH, setOf(MaterialTopic.TECHNOLOGY, MaterialTopic.CULTURE), setOf("www.nasa.gov", "nasa.gov", "science.nasa.gov"), listOf("article", "main"), setOf(NewsTag.SCIENCE_SPACE, NewsTag.TECHNOLOGY)),
                source("un-news", "UN News", "https://news.un.org/feed/subscribe/en/news/all/rss.xml", MaterialLanguage.ENGLISH, setOf(MaterialTopic.CURRENT_EVENTS, MaterialTopic.WORK, MaterialTopic.DAILY), setOf("news.un.org"), listOf("article", "main", ".field--name-body"), setOf(NewsTag.INTERNATIONAL, NewsTag.GOVERNMENT_POLICY)),
                source("hk-top", "香港政府新闻网", "https://www.news.gov.hk/tc/common/html/topstories.rss.xml", MaterialLanguage.CANTONESE, all, hkHosts, listOf("article", "main", "#content"), setOf(NewsTag.HONG_KONG, NewsTag.GOVERNMENT_POLICY)),
                source("hk-city", "香港政府新闻网", "https://www.news.gov.hk/tc/city_life/html/articlelist.rss.xml", MaterialLanguage.CANTONESE, setOf(MaterialTopic.DAILY, MaterialTopic.CULTURE), hkHosts, listOf("article", "main", "#content"), setOf(NewsTag.HONG_KONG, NewsTag.LOCAL_LIFE, NewsTag.CULTURE_ARTS)),
                source("hk-school-work", "香港政府新闻网", "https://www.news.gov.hk/tc/categories/school_work/html/articlelist.rss.xml", MaterialLanguage.CANTONESE, setOf(MaterialTopic.WORK, MaterialTopic.DAILY), hkHosts, listOf("article", "main", "#content"), setOf(NewsTag.HONG_KONG, NewsTag.EDUCATION, NewsTag.CAREERS)),
                source("hk-health", "香港政府新闻网", "https://www.news.gov.hk/tc/categories/health/html/articlelist.rss.xml", MaterialLanguage.CANTONESE, setOf(MaterialTopic.DAILY, MaterialTopic.CURRENT_EVENTS), hkHosts, listOf("article", "main", "#content"), setOf(NewsTag.HONG_KONG, NewsTag.HEALTH)),
                source("rthk-local", "香港电台", "https://rthk.hk/rthk/news/rss/c_expressnews_clocal.xml", MaterialLanguage.CANTONESE, all, rthkHosts, listOf("article", "main", ".itemFullText"), setOf(NewsTag.HONG_KONG, NewsTag.LOCAL_LIFE)),
                source("rthk-finance", "香港电台", "https://rthk.hk/rthk/news/rss/c_expressnews_cfinance.xml", MaterialLanguage.CANTONESE, setOf(MaterialTopic.WORK, MaterialTopic.CURRENT_EVENTS), rthkHosts, listOf("article", "main", ".itemFullText"), setOf(NewsTag.HONG_KONG, NewsTag.FINANCE)),
                source("hk-press", "香港政府新闻公报", "https://www.info.gov.hk/gia/rss/general_zh.xml", MaterialLanguage.CANTONESE, all, setOf("www.info.gov.hk", "info.gov.hk"), listOf("article", "main", "#pressrelease", ".pressrelease"), setOf(NewsTag.HONG_KONG, NewsTag.GOVERNMENT_POLICY)),
            )
        }

        internal fun parseDate(value: String?): Long? {
            val text = value?.trim().orEmpty()
            if (text.isBlank()) return null
            return listOf<() -> Instant>(
                { Instant.parse(text) },
                { OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() },
                { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
                { LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC) },
            ).firstNotNullOfOrNull { runCatching(it).getOrNull() }?.toEpochMilli()
        }

        private fun isNoise(title: String): Boolean {
            val text = title.lowercase(Locale.ROOT)
            return text.length < 5 || NOISE.any { text == it || text.startsWith("$it:") || text.startsWith("$it -") }
        }

        internal fun canonicalUrl(value: String): String? = runCatching {
            val uri = URI(decodeXmlUrl(value.trim()))
            val host = uri.host?.lowercase() ?: return null
            if (uri.scheme?.lowercase() != "https" && host !in LOCAL_HOSTS) return null
            val path = uri.path.orEmpty().ifBlank { "/" }.trimEnd('/').ifBlank { "/" }
            "${uri.scheme.lowercase()}://$host$path"
        }.getOrNull()

        private fun isAllowedUrl(value: String, hosts: Set<String>) = runCatching {
            val uri = URI(decodeXmlUrl(value))
            val host = uri.host?.lowercase()
            (uri.scheme?.lowercase() == "https" || host in LOCAL_HOSTS) && host in hosts
        }.getOrDefault(false)

        private fun decodeXmlUrl(value: String) = value.replace("&amp;", "&")
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "host.docker.internal", "kubernetes.docker.internal")
    }
}

class SourceDiscoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)
