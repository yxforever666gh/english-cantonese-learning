package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.network.GeneratedBatch
import com.example.englishcantoneselearning.data.network.GatewayFormatException
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import java.net.URI

object MaterialValidator {
    fun validate(request: MaterialGenerationRequest, batch: GeneratedBatch) {
        if (batch.materials.size != 1) throw GatewayFormatException("模型每章必须返回 1 篇材料")
        val snapshot = request.sourceSnapshot
        if (snapshot == null && batch.webSourceUrls.isEmpty()) {
            throw GatewayFormatException("网关没有返回网页搜索来源，为防止虚构内容，本批材料未保存")
        }
        val searchedSources = batch.webSourceUrls.mapNotNull(::canonicalize).toSet()

        batch.materials.forEachIndexed { index, material ->
            val number = index + 1
            if (material.title.isBlank() || material.targetText.isBlank()) {
                throw GatewayFormatException("第 $number 篇材料缺少标题或正文")
            }
            if (material.difficulty != request.difficulty.name) {
                throw GatewayFormatException("第 $number 篇材料难度字段不符合请求")
            }
            val rule = MaterialLevelRules.length(request.language, request.difficulty)
            val sentenceCount = material.sentences.size
            if (sentenceCount !in 12..30) {
                throw GatewayFormatException("第 $number 篇当前章节必须包含 12–30 句")
            }
            val finalFixedChapter = snapshot != null &&
                request.expectedParagraphIds.lastOrNull() == snapshot.paragraphs.lastOrNull()?.id
            if ((snapshot == null && !material.hasMore || finalFixedChapter) &&
                request.completedSentenceCount + sentenceCount < 20
            ) {
                throw GatewayFormatException("完整文章至少需要 20 个句对")
            }
            if (snapshot != null && material.coveredParagraphIds.toSet() != request.expectedParagraphIds.toSet()) {
                throw GatewayFormatException("模型没有完整覆盖本章来源段落，当前章节未保存")
            }
            material.sentences.forEachIndexed { sentenceIndex, sentence ->
                if (sentence.targetText.isBlank() || sentence.simplifiedChinese.isBlank()) {
                    throw GatewayFormatException("第 $number 篇材料存在空句子或空译文")
                }
                if (request.language == MaterialLanguage.CANTONESE && sentence.jyutping.isBlank()) {
                    throw GatewayFormatException("第 $number 篇粤语材料缺少粤拼")
                }
                val sentenceLength = countUnits(request.language, sentence.targetText)
                // Models occasionally count a contraction or hyphenated term differently. One unit
                // below the learning target remains a short, useful sentence; the upper bound stays
                // strict because preventing long listening chunks is the learner's core requirement.
                val acceptedRange = (rule.perSentenceRange.first - 1).coerceAtLeast(1)..rule.perSentenceRange.last
                if (sentenceLength !in acceptedRange) {
                    throw GatewayFormatException(
                        "第 $number 篇第 ${sentenceIndex + 1} 句长度为 $sentenceLength，" +
                            "必须在 ${acceptedRange.first}–${acceptedRange.last} 之间",
                    )
                }
            }
            if (material.sources.size != 1) {
                throw GatewayFormatException("每篇长文必须使用且只使用 1 个主来源")
            }
            material.sources.forEach { source ->
                val canonical = canonicalize(source.url)
                val verified = if (snapshot == null) canonical in searchedSources else
                    canonical == canonicalize(snapshot.url)
                if (source.title.isBlank() || source.publisher.isBlank() || !verified) {
                    throw GatewayFormatException("第 $number 篇材料的来源与固定来源快照不匹配")
                }
            }
        }
    }

    fun sentenceRange(difficulty: Difficulty, language: MaterialLanguage = MaterialLanguage.ENGLISH): IntRange =
        12..30

    fun lengthRange(language: MaterialLanguage, difficulty: Difficulty): IntRange =
        MaterialLevelRules.length(language, difficulty).totalRange

    private fun countUnits(language: MaterialLanguage, text: String): Int = when (language) {
        MaterialLanguage.ENGLISH -> Regex("[A-Za-z]+(?:['’-][A-Za-z]+)?").findAll(text).count()
        MaterialLanguage.CANTONESE -> text.count { it.code in 0x3400..0x9FFF }
    }

    fun canonicalize(url: String): String? = runCatching {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val port = when {
            uri.port == -1 -> ""
            scheme == "http" && uri.port == 80 -> ""
            scheme == "https" && uri.port == 443 -> ""
            else -> ":${uri.port}"
        }
        val path = (uri.path.ifBlank { "/" }).replace(Regex("/{2,}"), "/").trimEnd('/').ifEmpty { "/" }
        val query = uri.rawQuery
            ?.split('&')
            ?.filter(String::isNotBlank)
            ?.filterNot { part ->
                val name = part.substringBefore('=').lowercase()
                name.startsWith("utm_") || name in TRACKING_QUERY_NAMES
            }
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()
        "$scheme://${uri.host.lowercase()}$port$path${if (query.isEmpty()) "" else "?$query"}"
    }.getOrNull()

    private val TRACKING_QUERY_NAMES = setOf(
        "fbclid",
        "gclid",
        "mc_cid",
        "mc_eid",
        "ref",
        "referrer",
    )
}
