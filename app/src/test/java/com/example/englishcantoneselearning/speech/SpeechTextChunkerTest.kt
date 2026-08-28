package com.example.englishcantoneselearning.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextChunkerTest {
    @Test
    fun chunksNeverExceedLimitAndReconstructOriginalText() {
        val text = "alpha beta gamma delta, epsilon zeta eta theta"
        val chunks = SpeechTextChunker.chunk(text, maxLength = 12, baseOffset = 7)

        assertTrue(chunks.all { it.text.length <= 12 })
        assertEquals(text, chunks.joinToString(separator = "") { it.text })
        assertEquals(7, chunks.first().startOffset)
        chunks.zipWithNext().forEach { (first, second) ->
            assertEquals(first.startOffset + first.text.length, second.startOffset)
        }
    }

    @Test
    fun textWithoutSafeBoundaryUsesHardLimitWithoutLosingCharacters() {
        val text = "abcdefghijklmnop"
        val chunks = SpeechTextChunker.chunk(text, maxLength = 5)

        assertEquals(listOf("abcde", "fghij", "klmno", "p"), chunks.map { it.text })
        assertEquals(text, chunks.joinToString(separator = "") { it.text })
    }

    @Test
    fun emptyTextProducesNoChunks() {
        assertTrue(SpeechTextChunker.chunk("", maxLength = 5).isEmpty())
    }
}
