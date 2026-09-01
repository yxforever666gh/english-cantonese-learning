package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import java.security.MessageDigest

object MaterialPromptBuilder {
    const val PROMPT_VERSION = "listening-material-v7-global-ielts"

    val instructions: String = """
        You adapt one supplied, locally cleaned source article into a listening exercise. Never browse or search the web.
        Use only the supplied source paragraphs. Preserve their facts and order, but never copy more than 20 consecutive source words.
        Never invent a source, URL, publisher, date, quotation, Jyutping, paragraph ID, or fact.
        For English, write natural American English appropriate to the requested IELTS listening level.
        For Cantonese, write natural colloquial Hong Kong Cantonese in Traditional Chinese. Supply accurate Jyutping with tone numbers for every sentence; do not replace Cantonese with Mandarin wording.
        Each target sentence must have one concise, faithful Simplified Chinese translation. The translation must not add explanations or facts. Output only data matching the supplied schema.
        Cover every supplied paragraph ID exactly once in covered_paragraph_ids. Preserve important facts and order. Do not copy the source verbatim.
        Count the words or Han characters in each target sentence before responding. Keep sentences short even when the complete article is long.
    """.trimIndent()

    fun input(request: MaterialGenerationRequest): String {
        val compatibleDifficulty = Difficulty.TARGET
        val length = lengthRule(request.language, compatibleDifficulty)
        val listeningBand = MaterialLevelRules.normalizeListeningBand(request.profile.listeningBand)
        val learnerRule = when (request.language) {
            MaterialLanguage.ENGLISH ->
                "Learner selected IELTS listening $listeningBand. " +
                    MaterialLevelRules.englishGuidance(listeningBand)
            MaterialLanguage.CANTONESE ->
                "Learner selected IELTS listening $listeningBand as the Cantonese complexity scale. " +
                    MaterialLevelRules.cantoneseGuidance(listeningBand)
        }
        val targetLanguage = if (request.language == MaterialLanguage.ENGLISH) "English (en-US)" else
            "Hong Kong Cantonese (yue-HK)"
        val snapshot = request.sourceSnapshot
        val paragraphs = request.chapterParagraphs.joinToString("\n") { paragraph ->
            buildString {
                append('[').append(paragraph.id).append("] ")
                paragraph.heading?.takeIf(String::isNotBlank)?.let { append("SECTION: ").append(it).append("\n") }
                append(paragraph.text)
            }
        }
        val finalChapter = snapshot != null && request.expectedParagraphIds.lastOrNull() == snapshot.paragraphs.lastOrNull()?.id
        val sourceBlock = if (snapshot == null) {
            "SOURCE SNAPSHOT MISSING. Refuse to invent content."
        } else {
            """Source title: ${snapshot.title}
                Source publisher: ${snapshot.publisher}
                Source URL: ${snapshot.url}
                Source publication date: ${snapshot.publishedAt.orEmpty()}
                Original language: ${snapshot.sourceLanguage}
                Source content hash: ${snapshot.contentHash}
                Required paragraph IDs for this chapter: ${request.expectedParagraphIds.joinToString(",")}
                Cleaned source paragraphs:
                $paragraphs
            """.trimIndent()
        }

        return """
            Prompt version: $PROMPT_VERSION
            Date: ${request.currentDate}
            Target language: $targetLanguage
            Difficulty: ${compatibleDifficulty.name}
            Topic: ${request.topic.displayName}
            $learnerRule
            Length rule for this chapter of the single article: $length
            This is adaptation chapter ${request.chapterIndex + 1}. Already completed sentence pairs: ${request.completedSentenceCount}.
            Previous target-language tail for continuity only: ${request.previousSentenceTail.joinToString(" / ")}
            $sourceBlock
            Rewrite difficult source facts into the learner's level. Source complexity must never determine language complexity.
            Return exactly one material object. Its difficulty must be exactly "${compatibleDifficulty.name}".
            Return the supplied source metadata unchanged in sources and exactly these IDs in covered_paragraph_ids: ${request.expectedParagraphIds.joinToString(",")}.
            Include outline_sections, covered_section_ids, covered_paragraph_ids, sections, has_more, and next_section_index.
            Set has_more=${!finalChapter}. Do not repeat earlier sentence pairs.
            Every section requires id, title, and start_sentence_index relative to this chapter.
            For English, set jyutping to an empty string. For Cantonese, jyutping must be non-empty.
        """.trimIndent()
    }

    fun fingerprint(request: MaterialGenerationRequest): String {
        val raw = buildString {
            append(PROMPT_VERSION)
            append('|').append(request.language)
            append('|').append(request.topic)
            append('|').append(request.currentDate)
            append('|').append(MaterialLevelRules.normalizeListeningBand(request.profile.listeningBand))
            append('|').append(request.sourceSnapshot?.contentHash.orEmpty())
            append('|').append(request.sourceSnapshot?.url.orEmpty())
            request.excludedSourceUrls.sorted().take(20).forEach { append('|').append(it) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun lengthRule(language: MaterialLanguage, difficulty: Difficulty): String =
        MaterialLevelRules.length(language, Difficulty.TARGET).promptText()
}
