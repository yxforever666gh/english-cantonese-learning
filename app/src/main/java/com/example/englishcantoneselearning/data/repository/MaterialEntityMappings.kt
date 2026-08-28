package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialEntity
import com.example.englishcantoneselearning.data.local.MaterialPlaybackProgressEntity
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.MaterialSection
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceReference
import org.json.JSONArray
import org.json.JSONObject

internal fun PracticeMaterial.toMaterialEntity(): MaterialEntity = MaterialEntity().apply {
    id = this@toMaterialEntity.id
    batchId = this@toMaterialEntity.batchId
    batchPosition = this@toMaterialEntity.batchPosition
    language = this@toMaterialEntity.language.name
    difficulty = this@toMaterialEntity.difficulty.name
    topic = this@toMaterialEntity.topic
    title = this@toMaterialEntity.title
    targetText = this@toMaterialEntity.targetText
    sentencesJson = MaterialJsonCodec.encodeSentences(sentences)
    sourcesJson = MaterialJsonCodec.encodeSources(sources)
    createdAt = this@toMaterialEntity.createdAt
    promptVersion = this@toMaterialEntity.promptVersion
    providerName = this@toMaterialEntity.providerName
    model = this@toMaterialEntity.model
    responseId = this@toMaterialEntity.responseId
    inputTokens = this@toMaterialEntity.inputTokens
    outputTokens = this@toMaterialEntity.outputTokens
    requestFingerprint = this@toMaterialEntity.requestFingerprint
    origin = this@toMaterialEntity.origin.name
    sectionsJson = MaterialJsonCodec.encodeSections(sections)
}

internal fun MaterialEntity.toPracticeMaterial(): PracticeMaterial = PracticeMaterial(
    id = id,
    batchId = batchId,
    batchPosition = batchPosition,
    language = enumValueOf(language),
    difficulty = enumValueOf(difficulty),
    topic = topic,
    title = title,
    targetText = targetText,
    sentences = MaterialJsonCodec.decodeSentences(sentencesJson),
    sources = MaterialJsonCodec.decodeSources(sourcesJson),
    createdAt = createdAt,
    promptVersion = promptVersion,
    providerName = providerName,
    model = model,
    responseId = responseId,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    requestFingerprint = requestFingerprint,
    origin = runCatching { enumValueOf<ArticleOrigin>(origin) }.getOrDefault(ArticleOrigin.AI_GENERATED),
    sections = MaterialJsonCodec.decodeSections(sectionsJson),
)

internal fun MaterialPlaybackProgress.toPlaybackProgressEntity() = MaterialPlaybackProgressEntity().also { entity ->
    entity.materialId = materialId
    entity.resumeSentenceIndex = resumeSentenceIndex
    entity.completedSentenceIndicesJson = JSONArray(completedSentenceIndices.sorted()).toString()
    entity.completed = completed
    entity.updatedAt = updatedAt
}

internal fun MaterialPlaybackProgressEntity.toPlaybackProgress(): MaterialPlaybackProgress {
    val array = runCatching { JSONArray(completedSentenceIndicesJson) }.getOrElse { JSONArray() }
    val indices = buildSet { for (index in 0 until array.length()) add(array.optInt(index)) }
    return MaterialPlaybackProgress(materialId, resumeSentenceIndex, indices, completed, updatedAt)
}

internal object MaterialJsonCodec {
    fun encodeSentences(sentences: List<BilingualSentence>): String = JSONArray().apply {
        sentences.forEach { sentence ->
            put(
                JSONObject()
                    .put("id", sentence.id)
                    .put("targetText", sentence.targetText)
                    .put("jyutping", sentence.jyutping ?: JSONObject.NULL)
                    .put("simplifiedChinese", sentence.simplifiedChinese),
            )
        }
    }.toString()

    fun decodeSentences(json: String): List<BilingualSentence> = buildList {
        val array = JSONArray(json)
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                BilingualSentence(
                    id = item.getString("id"),
                    targetText = item.getString("targetText"),
                    jyutping = item.optString("jyutping").takeIf { it.isNotBlank() && it != "null" },
                    simplifiedChinese = item.optString("simplifiedChinese")
                        .takeIf { it.isNotBlank() && it != "null" },
                ),
            )
        }
    }

    fun encodeSources(sources: List<SourceReference>): String = JSONArray().apply {
        sources.forEach { source ->
            put(
                JSONObject()
                    .put("title", source.title)
                    .put("publisher", source.publisher)
                    .put("url", source.url)
                    .put("publishedAt", source.publishedAt ?: JSONObject.NULL)
                    .put("sourceLanguage", source.sourceLanguage)
                    .put("sourceId", source.sourceId)
                    .put("contentHash", source.contentHash)
                    .put("fetchedAt", source.fetchedAt)
                    .put("cleanerVersion", source.cleanerVersion),
            )
        }
    }.toString()

    fun decodeSources(json: String): List<SourceReference> = buildList {
        val array = JSONArray(json)
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                SourceReference(
                    title = item.getString("title"),
                    publisher = item.getString("publisher"),
                    url = item.getString("url"),
                    publishedAt = item.optString("publishedAt").takeIf { it.isNotBlank() && it != "null" },
                    sourceLanguage = item.getString("sourceLanguage"),
                    sourceId = item.optString("sourceId"),
                    contentHash = item.optString("contentHash"),
                    fetchedAt = item.optLong("fetchedAt"),
                    cleanerVersion = item.optString("cleanerVersion"),
                ),
            )
        }
    }

    fun encodeSections(sections: List<MaterialSection>): String = JSONArray().apply {
        sections.forEach { section ->
            put(
                JSONObject()
                    .put("id", section.id)
                    .put("title", section.title)
                    .put("startSentenceIndex", section.startSentenceIndex),
            )
        }
    }.toString()

    fun decodeSections(json: String): List<MaterialSection> = buildList {
        val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                MaterialSection(
                    item.optString("id", "section-$index"),
                    item.optString("title"),
                    item.optInt("startSentenceIndex"),
                ),
            )
        }
    }
}
