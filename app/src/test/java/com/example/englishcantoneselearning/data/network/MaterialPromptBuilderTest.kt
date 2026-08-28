package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialPromptBuilderTest {
    @Test
    fun promptContainsVersionProfileLengthAndExclusions() {
        val prompt = MaterialPromptBuilder.input(request())

        assertTrue(prompt.contains("listening-material-v6-source-adaptation"))
        assertTrue(prompt.contains("IELTS listening 6.0"))
        assertTrue(prompt.contains("Effective level for this TARGET request: IELTS listening 6.0"))
        assertTrue(prompt.contains("B1+"))
        assertTrue(prompt.contains("12-30 sentence pairs"))
        assertTrue(prompt.contains("EACH target sentence must contain 13-15 English words"))
        assertTrue(prompt.contains("no total word-duration target"))
        assertTrue(!prompt.contains("speaking", ignoreCase = true))
        assertTrue(!prompt.contains("writing", ignoreCase = true))
        assertTrue(!prompt.contains("reading", ignoreCase = true))
        assertTrue(prompt.contains("https://example.com/source"))
        assertTrue(prompt.contains("[p001] Source paragraph"))
        assertTrue(MaterialPromptBuilder.instructions.contains("Never browse or search the web"))
    }

    @Test
    fun cantoneseRulesUseJyutpingAndBeginnerLengths() {
        val request = request(language = MaterialLanguage.CANTONESE, difficulty = Difficulty.EASY)

        assertEquals(
            "12-30 sentence pairs in this chapter; EACH target sentence must contain 6-8 Chinese Han characters. The finished article must contain at least 20 sentence pairs, but has no total word-duration target. Split longer clauses at commas or natural semantic boundaries and give every part its own Simplified Chinese translation.",
            MaterialPromptBuilder.lengthRule(request.language, request.difficulty),
        )
        assertTrue(MaterialPromptBuilder.input(request).contains("jyutping must be non-empty"))
    }

    @Test
    fun fingerprintIsStableButChangesWithRequestInputs() {
        val first = request()
        val sameWithDifferentExclusionOrder = first.copy(
            excludedSourceUrls = listOf("https://example.com/another", "https://example.com/used"),
        )
        val sameOrderA = first.copy(excludedSourceUrls = sameWithDifferentExclusionOrder.excludedSourceUrls.reversed())

        assertEquals(
            MaterialPromptBuilder.fingerprint(sameWithDifferentExclusionOrder),
            MaterialPromptBuilder.fingerprint(sameOrderA),
        )
        assertNotEquals(
            MaterialPromptBuilder.fingerprint(first),
            MaterialPromptBuilder.fingerprint(first.copy(topic = MaterialTopic.CULTURE)),
        )
        assertNotEquals(
            MaterialPromptBuilder.fingerprint(first),
            MaterialPromptBuilder.fingerprint(first.copy(profile = LearnerProfile(englishListening = 6.5f))),
        )
    }

    private fun request(
        language: MaterialLanguage = MaterialLanguage.ENGLISH,
        difficulty: Difficulty = Difficulty.TARGET,
    ) = MaterialGenerationRequest(
        language = language,
        difficulty = difficulty,
        topic = MaterialTopic.TECHNOLOGY,
        excludedSourceUrls = listOf("https://example.com/used"),
        currentDate = "2026-08-22",
        sourceSnapshot = SourceArticleSnapshot(
            "test", "Publisher", "Source", "https://example.com/source", null, "English",
            listOf(SourceParagraph("p001", null, "Source paragraph with enough useful facts.")),
            "hash", 1L, "cleaner",
        ),
        chapterParagraphs = listOf(SourceParagraph("p001", null, "Source paragraph with enough useful facts.")),
        expectedParagraphIds = listOf("p001"),
    )
}
