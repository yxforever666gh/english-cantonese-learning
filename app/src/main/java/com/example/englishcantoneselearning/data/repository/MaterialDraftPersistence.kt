package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialDao
import com.example.englishcantoneselearning.data.local.MaterialDraftEntity
import com.example.englishcantoneselearning.data.network.GeneratedSentence
import com.example.englishcantoneselearning.data.network.MaterialPromptBuilder
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialSection
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import com.example.englishcantoneselearning.model.SourceReference
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal class MaterialDraftStore(private val dao: MaterialDao) {
    fun save(
        accumulator: DraftAccumulator,
        request: MaterialGenerationRequest,
        status: String,
        resumeFailureCount: Int,
    ) {
        dao.saveDraft(MaterialDraftEntity().also { draft ->
            draft.id = accumulator.id
            draft.requestJson = MaterialDraftCodec.encodeRequest(request)
            draft.stateJson = MaterialDraftCodec.encodeDraft(accumulator)
            draft.status = status
            draft.resumeFailureCount = resumeFailureCount
            draft.updatedAt = System.currentTimeMillis()
        })
    }

    fun decodeRequest(json: String): MaterialGenerationRequest = MaterialDraftCodec.decodeRequest(json)

    fun decode(entity: MaterialDraftEntity): DraftAccumulator = MaterialDraftCodec.decodeDraft(entity)
}

internal data class DraftAccumulator(
    val id: String,
    val fingerprint: String,
    var chapterIndex: Int = 0,
    var nextParagraphIndex: Int = 0,
    var title: String = "",
    var topic: String = "",
    var source: SourceReference? = null,
    val outlineSections: MutableList<String> = mutableListOf(),
    val coveredSectionIds: MutableList<String> = mutableListOf(),
    val sentences: MutableList<GeneratedSentence> = mutableListOf(),
    val sections: MutableList<MaterialSection> = mutableListOf(),
    var providerName: String = "",
    var model: String = "",
    val responseIds: MutableList<String> = mutableListOf(),
    var inputTokens: Int = 0,
    var outputTokens: Int = 0,
)

internal fun DraftAccumulator.toMaterial(request: MaterialGenerationRequest): PracticeMaterial {
    val materialId = UUID.randomUUID().toString()
    val uniqueSections = sections
        .distinctBy { "${it.startSentenceIndex}:${it.title}" }
        .sortedBy(MaterialSection::startSentenceIndex)
    return PracticeMaterial(
        id = materialId,
        batchId = materialId,
        batchPosition = 0,
        language = request.language,
        difficulty = request.difficulty,
        topic = topic.ifBlank { request.topic.displayName },
        title = title.ifBlank { sentences.first().targetText.take(30) },
        targetText = sentences.joinToString(if (request.language == MaterialLanguage.ENGLISH) " " else "") {
            it.targetText
        },
        sentences = sentences.mapIndexed { index, sentence ->
            BilingualSentence(
                id = "$materialId:$index",
                targetText = sentence.targetText,
                jyutping = sentence.jyutping.takeIf(String::isNotBlank),
                simplifiedChinese = sentence.simplifiedChinese,
            )
        },
        sources = listOfNotNull(source),
        createdAt = System.currentTimeMillis(),
        promptVersion = MaterialPromptBuilder.PROMPT_VERSION,
        providerName = providerName,
        model = model,
        responseId = responseIds.joinToString(","),
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        requestFingerprint = fingerprint,
        origin = ArticleOrigin.AI_GENERATED,
        sections = uniqueSections,
    )
}

internal object MaterialDraftCodec {
    fun encodeRequest(request: MaterialGenerationRequest): String = JSONObject()
        .put("language", request.language.name)
        .put("difficulty", request.difficulty.name)
        .put("topic", request.topic.name)
        .put("englishListening", request.profile.englishListening.toDouble())
        .put("cantoneseLevel", request.profile.cantoneseLevel)
        .put("excludedSourceUrls", JSONArray(request.excludedSourceUrls))
        .put("currentDate", request.currentDate)
        .put("sourceSnapshot", request.sourceSnapshot?.let(::encodeSnapshot) ?: JSONObject.NULL)
        .toString()

    fun decodeRequest(json: String): MaterialGenerationRequest {
        val value = JSONObject(json)
        return MaterialGenerationRequest(
            language = enumValueOf(value.getString("language")),
            difficulty = enumValueOf(value.getString("difficulty")),
            topic = enumValueOf(value.getString("topic")),
            profile = LearnerProfile(
                englishListening = value.optDouble("englishListening", 6.0).toFloat(),
                cantoneseLevel = value.optString("cantoneseLevel", "A0/A1 零基础"),
            ),
            excludedSourceUrls = value.optJSONArray("excludedSourceUrls").toStringList(),
            currentDate = value.optString("currentDate"),
            sourceSnapshot = value.optJSONObject("sourceSnapshot")?.let(::decodeSnapshot),
        )
    }

    fun encodeDraft(accumulator: DraftAccumulator): String = JSONObject()
        .put("fingerprint", accumulator.fingerprint)
        .put("chapterIndex", accumulator.chapterIndex)
        .put("nextParagraphIndex", accumulator.nextParagraphIndex)
        .put("title", accumulator.title)
        .put("topic", accumulator.topic)
        .put("source", accumulator.source?.let(::encodeSource) ?: JSONObject.NULL)
        .put("outlineSections", JSONArray(accumulator.outlineSections))
        .put("coveredSectionIds", JSONArray(accumulator.coveredSectionIds))
        .put("sentences", JSONArray().apply {
            accumulator.sentences.forEach { sentence ->
                put(
                    JSONObject()
                        .put("targetText", sentence.targetText)
                        .put("jyutping", sentence.jyutping)
                        .put("simplifiedChinese", sentence.simplifiedChinese),
                )
            }
        })
        .put("sections", JSONArray().apply {
            accumulator.sections.forEach { section ->
                put(
                    JSONObject()
                        .put("id", section.id)
                        .put("title", section.title)
                        .put("startSentenceIndex", section.startSentenceIndex),
                )
            }
        })
        .put("providerName", accumulator.providerName)
        .put("model", accumulator.model)
        .put("responseIds", JSONArray(accumulator.responseIds))
        .put("inputTokens", accumulator.inputTokens)
        .put("outputTokens", accumulator.outputTokens)
        .toString()

    fun decodeDraft(entity: MaterialDraftEntity): DraftAccumulator {
        val value = JSONObject(entity.stateJson)
        val sentences = value.optJSONArray("sentences")
        val sections = value.optJSONArray("sections")
        return DraftAccumulator(
            id = entity.id,
            fingerprint = value.getString("fingerprint"),
            chapterIndex = value.optInt("chapterIndex"),
            nextParagraphIndex = value.optInt("nextParagraphIndex"),
            title = value.optString("title"),
            topic = value.optString("topic"),
            source = value.optJSONObject("source")?.let(::decodeSource),
            outlineSections = value.optJSONArray("outlineSections").toStringList().toMutableList(),
            coveredSectionIds = value.optJSONArray("coveredSectionIds").toStringList().toMutableList(),
            sentences = buildList {
                if (sentences != null) for (index in 0 until sentences.length()) {
                    val sentence = sentences.getJSONObject(index)
                    add(
                        GeneratedSentence(
                            sentence.getString("targetText"),
                            sentence.optString("jyutping"),
                            sentence.optString("simplifiedChinese"),
                        ),
                    )
                }
            }.toMutableList(),
            sections = buildList {
                if (sections != null) for (index in 0 until sections.length()) {
                    val section = sections.getJSONObject(index)
                    add(
                        MaterialSection(
                            section.optString("id", "section-$index"),
                            section.optString("title"),
                            section.optInt("startSentenceIndex"),
                        ),
                    )
                }
            }.toMutableList(),
            providerName = value.optString("providerName"),
            model = value.optString("model"),
            responseIds = value.optJSONArray("responseIds").toStringList().toMutableList(),
            inputTokens = value.optInt("inputTokens"),
            outputTokens = value.optInt("outputTokens"),
        )
    }

    private fun encodeSource(source: SourceReference): JSONObject = JSONObject()
        .put("title", source.title)
        .put("publisher", source.publisher)
        .put("url", source.url)
        .put("publishedAt", source.publishedAt)
        .put("sourceLanguage", source.sourceLanguage)
        .put("sourceId", source.sourceId)
        .put("contentHash", source.contentHash)
        .put("fetchedAt", source.fetchedAt)
        .put("cleanerVersion", source.cleanerVersion)

    private fun decodeSource(value: JSONObject): SourceReference = SourceReference(
        title = value.optString("title"),
        publisher = value.optString("publisher"),
        url = value.optString("url"),
        publishedAt = value.optString("publishedAt").takeIf(String::isNotBlank),
        sourceLanguage = value.optString("sourceLanguage"),
        sourceId = value.optString("sourceId"),
        contentHash = value.optString("contentHash"),
        fetchedAt = value.optLong("fetchedAt"),
        cleanerVersion = value.optString("cleanerVersion"),
    )

    private fun encodeSnapshot(snapshot: SourceArticleSnapshot): JSONObject = JSONObject()
        .put("sourceId", snapshot.sourceId)
        .put("publisher", snapshot.publisher)
        .put("title", snapshot.title)
        .put("url", snapshot.url)
        .put("publishedAt", snapshot.publishedAt)
        .put("sourceLanguage", snapshot.sourceLanguage)
        .put("contentHash", snapshot.contentHash)
        .put("fetchedAt", snapshot.fetchedAt)
        .put("cleanerVersion", snapshot.cleanerVersion)
        .put("paragraphs", JSONArray().apply {
            snapshot.paragraphs.forEach { paragraph ->
                put(
                    JSONObject()
                        .put("id", paragraph.id)
                        .put("heading", paragraph.heading)
                        .put("text", paragraph.text),
                )
            }
        })

    private fun decodeSnapshot(value: JSONObject): SourceArticleSnapshot {
        val array = value.optJSONArray("paragraphs") ?: JSONArray()
        val paragraphs = buildList {
            for (index in 0 until array.length()) {
                val paragraph = array.getJSONObject(index)
                add(
                    SourceParagraph(
                        paragraph.getString("id"),
                        paragraph.optString("heading").takeIf { it.isNotBlank() && it != "null" },
                        paragraph.getString("text"),
                    ),
                )
            }
        }
        return SourceArticleSnapshot(
            sourceId = value.optString("sourceId"),
            publisher = value.optString("publisher"),
            title = value.optString("title"),
            url = value.optString("url"),
            publishedAt = value.optString("publishedAt").takeIf { it.isNotBlank() && it != "null" },
            sourceLanguage = value.optString("sourceLanguage"),
            paragraphs = paragraphs,
            contentHash = value.optString("contentHash"),
            fetchedAt = value.optLong("fetchedAt"),
            cleanerVersion = value.optString("cleanerVersion"),
        )
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        if (this@toStringList != null) {
            for (index in 0 until length()) add(optString(index))
        }
    }
}
