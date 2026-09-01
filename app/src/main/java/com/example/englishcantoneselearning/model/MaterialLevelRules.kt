package com.example.englishcantoneselearning.model

import kotlin.math.roundToInt

data class MaterialLengthRule(
    val sentenceCount: Int,
    val perSentenceRange: IntRange,
    val totalRange: IntRange,
    val unitLabel: String,
) {
    fun promptText(): String =
        "12-30 sentence pairs in this chapter; EACH target sentence must contain " +
            "${perSentenceRange.first}-${perSentenceRange.last} $unitLabel. " +
            "The finished article must contain at least 20 sentence pairs, but has no total word-duration target. " +
            "Split longer clauses at commas or natural semantic boundaries and give every part its own Simplified Chinese translation."
}

object MaterialLevelRules {
    fun length(language: MaterialLanguage, difficulty: Difficulty): MaterialLengthRule = when (language) {
        MaterialLanguage.ENGLISH -> when (difficulty) {
            Difficulty.EASY -> MaterialLengthRule(5, 11..13, 55..65, "English words")
            Difficulty.TARGET -> MaterialLengthRule(6, 13..15, 78..90, "English words")
            Difficulty.CHALLENGE -> MaterialLengthRule(7, 15..17, 105..119, "English words")
        }
        MaterialLanguage.CANTONESE -> when (difficulty) {
            Difficulty.EASY -> MaterialLengthRule(5, 6..8, 30..40, "Chinese Han characters")
            Difficulty.TARGET -> MaterialLengthRule(6, 8..10, 48..60, "Chinese Han characters")
            Difficulty.CHALLENGE -> MaterialLengthRule(7, 10..12, 70..84, "Chinese Han characters")
        }
    }

    fun normalizeListeningBand(value: Float): Float =
        ((value.coerceIn(1f, 9f) * 2f).roundToInt() / 2f)

    fun effectiveListeningBand(baseBand: Float, difficulty: Difficulty): Float {
        val adjustment = when (difficulty) {
            Difficulty.EASY -> -0.5f
            Difficulty.TARGET -> 0f
            Difficulty.CHALLENGE -> 0.5f
        }
        return normalizeListeningBand(baseBand + adjustment)
    }

    fun englishGuidance(band: Float): String = when (normalizeListeningBand(band)) {
        in 1f..3.5f -> "A1/A2 foundation: use only very common daily words, direct subject-verb-object clauses, and one fact per sentence."
        in 4f..5f -> "B1: use common vocabulary and familiar situations, with at most one simple subordinate clause per sentence."
        in 5.5f..6f -> "B1+: use mostly high-frequency words, no nested clauses, and at most two necessary topic-specific terms per material, made clear by context."
        in 6.5f..7f -> "B2: allow broader natural vocabulary and up to three contextualized topic terms, while keeping the required short sentences."
        in 7.5f..8f -> "B2+/C1: use precise natural language and moderately complex ideas, but never exceed the short-sentence limit."
        else -> "C1+: use advanced natural vocabulary and nuanced ideas, but never exceed the short-sentence limit."
    }

    fun cantoneseGuidance(band: Float): String = when (normalizeListeningBand(band)) {
        in 1f..3.5f -> "Foundation: use very common Hong Kong daily expressions, direct clauses, and one concrete fact per sentence."
        in 4f..5f -> "Developing: use familiar colloquial vocabulary and at most one simple subordinate clause per sentence."
        in 5.5f..6f -> "Intermediate: use mostly high-frequency colloquial vocabulary, no nested clauses, and explain necessary topic terms through context."
        in 6.5f..7f -> "Upper-intermediate: allow broader natural Hong Kong vocabulary and moderately detailed ideas while keeping sentences short."
        in 7.5f..8f -> "Advanced: use precise idiomatic Cantonese and moderately complex ideas, but keep every sentence within the short-sentence limit."
        else -> "Highly advanced: use nuanced, idiomatic Hong Kong Cantonese without Mandarin-style wording, while respecting the short-sentence limit."
    }
}
