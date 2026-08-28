package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.network.GeneratedBatch
import com.example.englishcantoneselearning.data.network.GeneratedMaterial
import com.example.englishcantoneselearning.data.network.GeneratedSentence
import com.example.englishcantoneselearning.data.network.GatewayFormatException
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.SourceReference
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialValidatorTest {
    @Test
    fun acceptsOneCompleteLongEnglishMaterialWithShortSentences() {
        MaterialValidator.validate(request(), validBatch())
    }

    @Test(expected = GatewayFormatException::class)
    fun rejectsIncorrectSentenceCount() {
        val original = validBatch()
        val invalid = original.materials.first().copy(sentences = original.materials.first().sentences.dropLast(1))
        MaterialValidator.validate(request(), original.copy(materials = listOf(invalid)))
    }

    @Test(expected = GatewayFormatException::class)
    fun rejectsOneSentenceOutsidePerSentenceRange() {
        val original = validBatch()
        val first = original.materials.first()
        val tooLong = first.sentences.first().copy(
            targetText = (1..14).joinToString(" ") { "word$it" },
        )
        val invalid = first.copy(
            sentences = listOf(tooLong) + first.sentences.drop(1),
            targetText = (listOf(tooLong) + first.sentences.drop(1)).joinToString(" ") { it.targetText },
        )
        MaterialValidator.validate(request(), original.copy(materials = listOf(invalid)))
    }

    @Test
    fun acceptsOneUnitBelowTargetButKeepsUpperBoundStrict() {
        val original = validBatch()
        val first = original.materials.single()
        val slightlyShort = first.sentences.first().copy(
            targetText = (1..10).joinToString(" ") { "word$it" },
        )
        MaterialValidator.validate(
            request(),
            original.copy(materials = listOf(first.copy(
                sentences = listOf(slightlyShort) + first.sentences.drop(1),
            ))),
        )
    }

    @Test(expected = GatewayFormatException::class)
    fun rejectsSourceNotReturnedByWebSearch() {
        val original = validBatch()
        val forged = original.materials.first().copy(
            sources = listOf(source("https://forged.example/story")),
        )
        MaterialValidator.validate(request(), original.copy(materials = listOf(forged)))
    }

    @Test(expected = GatewayFormatException::class)
    fun rejectsCantoneseSentenceWithoutJyutping() {
        val cantoneseRequest = request().copy(language = MaterialLanguage.CANTONESE)
        val sentences = List(12) { "今日天氣幾好" }
            .map { GeneratedSentence(it, "", "简体翻译") }
        val material = validBatch().materials.first().copy(targetText = sentences.joinToString("") { it.targetText }, sentences = sentences)
        MaterialValidator.validate(
            cantoneseRequest,
            validBatch().copy(materials = listOf(material)),
        )
    }

    @Test
    fun canonicalizationRemovesTrackingQueryAndTrailingSlash() {
        assertEquals(
            "https://example.com/news/item",
            MaterialValidator.canonicalize("https://EXAMPLE.com/news/item/?utm_source=test#section"),
        )
    }

    @Test
    fun canonicalizationKeepsContentIdentifyingQuery() {
        assertEquals(
            "https://example.com/article?id=123&lang=en",
            MaterialValidator.canonicalize("https://example.com/article?lang=en&utm_medium=app&id=123"),
        )
    }

    @Test
    fun fixedSourceModeAcceptsExactUrlAndParagraphCoverageWithoutWebSearchResults() {
        val original = validBatch()
        val source = source("https://example.com/fixed-story")
        val snapshot = SourceArticleSnapshot(
            "fixed", "Publisher", "Source", source.url, null, "English",
            listOf(SourceParagraph("p001", null, "A locally cleaned source paragraph.")),
            "hash", 1L, "cleaner-v1",
        )
        val fixedRequest = request().copy(
            sourceSnapshot = snapshot,
            chapterParagraphs = snapshot.paragraphs,
            expectedParagraphIds = listOf("p001"),
        )
        val material = original.materials.single().copy(
            sources = listOf(source),
            coveredParagraphIds = listOf("p001"),
        )

        MaterialValidator.validate(fixedRequest, original.copy(materials = listOf(material), webSourceUrls = emptySet()))
    }

    @Test(expected = GatewayFormatException::class)
    fun fixedSourceModeRejectsMissingParagraphCoverage() {
        val original = validBatch()
        val source = source("https://example.com/fixed-story")
        val snapshot = SourceArticleSnapshot(
            "fixed", "Publisher", "Source", source.url, null, "English",
            listOf(SourceParagraph("p001", null, "A locally cleaned source paragraph.")),
            "hash", 1L, "cleaner-v1",
        )
        MaterialValidator.validate(
            request().copy(sourceSnapshot = snapshot, expectedParagraphIds = listOf("p001")),
            original.copy(materials = listOf(original.materials.single().copy(sources = listOf(source))), webSourceUrls = emptySet()),
        )
    }

    private fun request() = MaterialGenerationRequest(
        language = MaterialLanguage.ENGLISH,
        difficulty = Difficulty.EASY,
        topic = MaterialTopic.DAILY,
        currentDate = "2026-08-22",
    )

    private fun validBatch(): GeneratedBatch {
        val urls = listOf("https://example.com/story-1")
        val materials = urls.mapIndexed { index, url ->
            val sentences = (1..20).map { sentenceIndex ->
                GeneratedSentence(
                    targetText = (1..11).joinToString(" ") { "word${index + 1}_${sentenceIndex}_$it" },
                    jyutping = "",
                    simplifiedChinese = "这是第 $sentenceIndex 句译文。",
                )
            }
            GeneratedMaterial(
                title = "Material ${index + 1}",
                topic = "日常",
                difficulty = "EASY",
                targetText = sentences.joinToString(" ") { it.targetText },
                sentences = sentences,
                sources = listOf(source(url)),
            )
        }
        return GeneratedBatch("resp", 10, 20, materials, urls.toSet())
    }

    private fun source(url: String) = SourceReference(
        title = "Source",
        publisher = "Publisher",
        url = url,
        publishedAt = "2026-08-20",
        sourceLanguage = "English",
    )
}
