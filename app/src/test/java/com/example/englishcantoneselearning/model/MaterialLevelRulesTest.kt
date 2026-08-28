package com.example.englishcantoneselearning.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialLevelRulesTest {
    @Test
    fun allLanguageDifficultyRulesMatchProductLimits() {
        assertRule(MaterialLanguage.ENGLISH, Difficulty.EASY, 5, 11..13, 55..65)
        assertRule(MaterialLanguage.ENGLISH, Difficulty.TARGET, 6, 13..15, 78..90)
        assertRule(MaterialLanguage.ENGLISH, Difficulty.CHALLENGE, 7, 15..17, 105..119)
        assertRule(MaterialLanguage.CANTONESE, Difficulty.EASY, 5, 6..8, 30..40)
        assertRule(MaterialLanguage.CANTONESE, Difficulty.TARGET, 6, 8..10, 48..60)
        assertRule(MaterialLanguage.CANTONESE, Difficulty.CHALLENGE, 7, 10..12, 70..84)
    }

    @Test
    fun listeningBandRoundsToHalfAndClampsAtIeltsBoundaries() {
        assertEquals(1f, MaterialLevelRules.normalizeListeningBand(-2f))
        assertEquals(6f, MaterialLevelRules.normalizeListeningBand(6.24f))
        assertEquals(6.5f, MaterialLevelRules.normalizeListeningBand(6.26f))
        assertEquals(9f, MaterialLevelRules.normalizeListeningBand(12f))
    }

    @Test
    fun effectiveDifficultyBandsClampAtOneAndNine() {
        assertEquals(1f, MaterialLevelRules.effectiveListeningBand(1f, Difficulty.EASY))
        assertEquals(1f, MaterialLevelRules.effectiveListeningBand(1f, Difficulty.TARGET))
        assertEquals(1.5f, MaterialLevelRules.effectiveListeningBand(1f, Difficulty.CHALLENGE))
        assertEquals(8.5f, MaterialLevelRules.effectiveListeningBand(9f, Difficulty.EASY))
        assertEquals(9f, MaterialLevelRules.effectiveListeningBand(9f, Difficulty.TARGET))
        assertEquals(9f, MaterialLevelRules.effectiveListeningBand(9f, Difficulty.CHALLENGE))
    }

    private fun assertRule(
        language: MaterialLanguage,
        difficulty: Difficulty,
        sentenceCount: Int,
        perSentence: IntRange,
        total: IntRange,
    ) {
        val rule = MaterialLevelRules.length(language, difficulty)
        assertEquals(sentenceCount, rule.sentenceCount)
        assertEquals(perSentence, rule.perSentenceRange)
        assertEquals(total, rule.totalRange)
    }
}
