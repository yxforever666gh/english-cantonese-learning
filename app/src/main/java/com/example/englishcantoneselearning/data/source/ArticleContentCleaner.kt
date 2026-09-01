package com.example.englishcantoneselearning.data.source

import com.example.englishcantoneselearning.model.ArticleSourceDefinition
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceCandidate
import com.example.englishcantoneselearning.model.SourceParagraph
import java.security.MessageDigest
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ArticleContentCleaner {
    fun clean(
        definition: ArticleSourceDefinition,
        candidate: SourceCandidate,
        html: String,
        fetchedAt: Long = System.currentTimeMillis(),
    ): SourceArticleSnapshot = cleanWithThresholds(
        definition = definition,
        candidate = candidate,
        html = html,
        fetchedAt = fetchedAt,
        minimumCharacters = if (definition.language == MaterialLanguage.ENGLISH) 900 else 450,
        minimumParagraphs = 1,
    )

    fun cleanForNews(
        definition: ArticleSourceDefinition,
        candidate: SourceCandidate,
        html: String,
        fetchedAt: Long = System.currentTimeMillis(),
    ): SourceArticleSnapshot = cleanWithThresholds(
        definition = definition,
        candidate = candidate,
        html = html,
        fetchedAt = fetchedAt,
        minimumCharacters = if (definition.language == MaterialLanguage.ENGLISH) 350 else 180,
        minimumParagraphs = 3,
    )

    private fun cleanWithThresholds(
        definition: ArticleSourceDefinition,
        candidate: SourceCandidate,
        html: String,
        fetchedAt: Long,
        minimumCharacters: Int,
        minimumParagraphs: Int,
    ): SourceArticleSnapshot {
        val document = Jsoup.parse(html, candidate.url)
        document.select(REMOVE_SELECTORS).remove()
        val root = definition.contentSelectors.asSequence()
            .mapNotNull { selector -> document.selectFirst(selector) }
            .firstOrNull { it.select("p").size >= 3 }
            ?: document.selectFirst("article")
            ?: document.selectFirst("main")
            ?: document.body()
        val paragraphs = extractParagraphs(root, definition.language)
        require(paragraphs.size >= minimumParagraphs && paragraphs.sumOf { it.text.length } >= minimumCharacters) {
            "正文过短或主要内容无法提取"
        }
        val hash = sha256(paragraphs.joinToString("\n") { "${it.heading.orEmpty()}\n${it.text}" })
        return SourceArticleSnapshot(
            sourceId = definition.id,
            publisher = definition.publisher,
            title = candidate.title.ifBlank { document.title() },
            url = candidate.url,
            publishedAt = candidate.publishedAt,
            sourceLanguage = if (definition.language == MaterialLanguage.ENGLISH) "English" else "Traditional Chinese",
            paragraphs = paragraphs,
            contentHash = hash,
            fetchedAt = fetchedAt,
            cleanerVersion = CLEANER_VERSION,
        )
    }

    private fun extractParagraphs(root: Element, language: MaterialLanguage): List<SourceParagraph> {
        val results = mutableListOf<Pair<String?, String>>()
        val seen = mutableSetOf<String>()
        var heading: String? = null
        root.select("h2, h3, p").forEach { element ->
            val text = element.text().replace(Regex("\\s+"), " ").trim()
            if (element.tagName() in setOf("h2", "h3")) {
                if (text.length in 3..160 && !isNoise(text)) heading = text
                return@forEach
            }
            val minimum = if (language == MaterialLanguage.ENGLISH) 45 else 18
            if (text.length < minimum || isNoise(text) || linkRatio(element) > 0.45) return@forEach
            splitOversized(text).forEach { part ->
                val normalized = part.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
                if (normalized.length >= minimum / 2 && seen.add(normalized)) {
                    results += heading to part
                    heading = null
                }
            }
        }
        return results.mapIndexed { index, (section, text) ->
            SourceParagraph("p${(index + 1).toString().padStart(3, '0')}", section, text)
        }
    }

    private fun splitOversized(text: String): List<String> {
        if (text.length <= 1_800) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?。！？])\\s+|(?<=[。！？])"))
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        sentences.filter(String::isNotBlank).forEach { sentence ->
            if (current.isNotEmpty() && current.length + sentence.length > 1_500) {
                chunks += current.toString().trim()
                current = StringBuilder()
            }
            current.append(sentence.trim()).append(' ')
        }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks.ifEmpty { listOf(text.take(1_800)) }
    }

    private fun linkRatio(element: Element): Double {
        val total = element.text().length.coerceAtLeast(1)
        return element.select("a").sumOf { it.text().length }.toDouble() / total
    }

    private fun isNoise(text: String): Boolean {
        val normalized = text.lowercase()
        return NOISE_TERMS.any(normalized::contains) ||
            normalized.matches(Regex("^(share|follow|subscribe|related|read more)\\b.*"))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val CLEANER_VERSION = "article-cleaner-v2"
        private const val REMOVE_SELECTORS =
            "script,style,noscript,nav,footer,aside,form,button,figure,figcaption," +
                ".advertisement,.advert,.ad,.promo,.related,.recommended,.share,.social,.cookie,.newsletter"
        private val NOISE_TERMS = listOf(
            "all rights reserved", "copyright", "privacy policy", "terms of use",
            "sign up for", "subscribe to", "follow us", "download our app", "advertisement",
            "related stories", "recommended for you", "更多新闻", "延伸阅读", "按此订阅",
            "版权所有", "私隐政策", "免责声明", "分享至", "责任编辑：",
        )
    }
}
