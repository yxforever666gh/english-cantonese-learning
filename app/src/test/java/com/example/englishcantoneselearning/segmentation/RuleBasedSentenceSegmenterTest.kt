package com.example.englishcantoneselearning.segmentation

import com.example.englishcantoneselearning.model.LearningLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedSentenceSegmenterTest {
    private val segmenter = RuleBasedSentenceSegmenter()

    @Test
    fun english_preservesAbbreviationsDecimalsQuotesAndConsecutivePunctuation() {
        val result = segmenter.segment(
            text = "Dr. Smith paid 3.50 dollars. \"Really?!\" Yes!",
            language = LearningLanguage.ENGLISH,
        )

        assertEquals(
            listOf("Dr. Smith paid 3.50 dollars.", "\"Really?!\"", "Yes!"),
            result.map { it.text },
        )
    }

    @Test
    fun english_doesNotSplitInsideMultiPartAbbreviations() {
        val result = segmenter.segment(
            text = "Use fruit, e.g. apples and pears. Next sentence.",
            language = LearningLanguage.ENGLISH,
        )

        assertEquals(
            listOf("Use fruit, e.g. apples and pears.", "Next sentence."),
            result.map { it.text },
        )
    }

    @Test
    fun explicitNewlinesAlwaysCreateSentenceBoundaries() {
        val result = segmenter.segment(
            text = "First line without punctuation\n\nSecond line.",
            language = LearningLanguage.ENGLISH,
        )

        assertEquals(
            listOf("First line without punctuation", "Second line."),
            result.map { it.text },
        )
    }

    @Test
    fun cantonese_splitsChinesePunctuationEllipsisAndClosingQuotes() {
        val result = segmenter.segment(
            text = "你好！「食咗飯未？」我食咗；多謝……再見。",
            language = LearningLanguage.CANTONESE,
        )

        assertEquals(
            listOf("你好！", "「食咗飯未？」", "我食咗；", "多謝……", "再見。"),
            result.map { it.text },
        )
    }

    @Test
    fun blankInputProducesNoSentencesAndIdsAreUnique() {
        assertTrue(segmenter.segment(" \n\t ", LearningLanguage.ENGLISH).isEmpty())

        val ids = segmenter.segment("One. Two. Three.", LearningLanguage.ENGLISH).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun textWithoutPunctuationRemainsOneSentence() {
        val result = segmenter.segment(
            text = "This is one long sentence without a terminator",
            language = LearningLanguage.ENGLISH,
        )

        assertEquals(1, result.size)
        assertEquals("This is one long sentence without a terminator", result.single().text)
    }
}
