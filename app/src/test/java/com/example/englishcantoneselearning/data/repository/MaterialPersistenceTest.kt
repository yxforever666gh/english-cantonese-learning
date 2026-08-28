package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialDraftEntity
import com.example.englishcantoneselearning.data.network.GeneratedSentence
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.MaterialSection
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import com.example.englishcantoneselearning.model.SourceReference
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialPersistenceTest {
    @Test
    fun materialEntityRoundTripPreservesPersistedFields() {
        val material = PracticeMaterial(
            id = "material-1",
            batchId = "batch-1",
            batchPosition = 2,
            language = MaterialLanguage.CANTONESE,
            difficulty = Difficulty.CHALLENGE,
            topic = "文化",
            title = "測試文章",
            targetText = "第一句。第二句。",
            sentences = listOf(
                BilingualSentence("sentence-1", "第一句。", "dai6 jat1 geoi3", "第一句。"),
                BilingualSentence("sentence-2", "第二句。", null, null),
            ),
            sources = listOf(sourceReference()),
            createdAt = 1234L,
            promptVersion = "prompt-v1",
            providerName = "provider",
            model = "model",
            responseId = "response",
            inputTokens = 12,
            outputTokens = 34,
            requestFingerprint = "fingerprint",
            origin = ArticleOrigin.MANUAL_PASTE,
            sections = listOf(MaterialSection("section-1", "第一節", 0)),
        )

        assertEquals(material, material.toMaterialEntity().toPracticeMaterial())
    }

    @Test
    fun playbackProgressRoundTripPreservesIndicesAndStatus() {
        val progress = MaterialPlaybackProgress(
            materialId = "material-1",
            resumeSentenceIndex = 3,
            completedSentenceIndices = setOf(5, 1, 3),
            completed = true,
            updatedAt = 5678L,
        )

        assertEquals(progress, progress.toPlaybackProgressEntity().toPlaybackProgress())
    }

    @Test
    fun generationRequestRoundTripPreservesLegacyDraftShape() {
        val snapshot = SourceArticleSnapshot(
            sourceId = "source-1",
            publisher = "Publisher",
            title = "Source title",
            url = "https://example.com/article",
            publishedAt = null,
            sourceLanguage = "en",
            paragraphs = listOf(SourceParagraph("paragraph-1", null, "Paragraph text")),
            contentHash = "hash",
            fetchedAt = 100L,
            cleanerVersion = "cleaner-v1",
        )
        val request = MaterialGenerationRequest(
            language = MaterialLanguage.ENGLISH,
            difficulty = Difficulty.TARGET,
            topic = MaterialTopic.TECHNOLOGY,
            profile = LearnerProfile(7.5f, "B1 中級"),
            excludedSourceUrls = listOf("https://example.com/old"),
            currentDate = "2026-08-29",
            sourceSnapshot = snapshot,
        )

        assertEquals(request, MaterialDraftCodec.decodeRequest(MaterialDraftCodec.encodeRequest(request)))
    }

    @Test
    fun draftStateRoundTripPreservesResumeFields() {
        val accumulator = DraftAccumulator(
            id = "draft-1",
            fingerprint = "fingerprint",
            chapterIndex = 2,
            nextParagraphIndex = 4,
            title = "Draft title",
            topic = "科技",
            source = sourceReference(),
            outlineSections = mutableListOf("Section A"),
            coveredSectionIds = mutableListOf("paragraph-1"),
            sentences = mutableListOf(GeneratedSentence("Target", "jyutping", "翻译")),
            sections = mutableListOf(MaterialSection("section-1", "Section A", 0)),
            providerName = "provider",
            model = "model",
            responseIds = mutableListOf("response-1"),
            inputTokens = 10,
            outputTokens = 20,
        )
        val entity = MaterialDraftEntity().also {
            it.id = accumulator.id
            it.stateJson = MaterialDraftCodec.encodeDraft(accumulator)
        }

        assertEquals(accumulator, MaterialDraftCodec.decodeDraft(entity))
    }

    private fun sourceReference() = SourceReference(
        title = "Source title",
        publisher = "Publisher",
        url = "https://example.com/article",
        publishedAt = null,
        sourceLanguage = "yue",
        sourceId = "source-1",
        contentHash = "hash",
        fetchedAt = 100L,
        cleanerVersion = "cleaner-v1",
    )
}
