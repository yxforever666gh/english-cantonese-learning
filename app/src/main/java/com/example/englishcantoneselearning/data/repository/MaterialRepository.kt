package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialDao
import com.example.englishcantoneselearning.data.local.MaterialEntity
import com.example.englishcantoneselearning.data.local.MaterialPlaybackProgressEntity
import com.example.englishcantoneselearning.data.local.MaterialDraftEntity
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.data.network.GeneratedSentence
import com.example.englishcantoneselearning.data.network.GatewayFormatException
import com.example.englishcantoneselearning.data.source.FixedSourceRepository
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.network.MaterialPromptBuilder
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.MaterialSection
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceReference
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

interface MaterialRepository {
    suspend fun listMaterials(): List<PracticeMaterial>
    suspend fun getMaterial(id: String): PracticeMaterial?
    suspend fun recentSourceUrls(limit: Int = 20): List<String>
    suspend fun generate(request: MaterialGenerationRequest): List<PracticeMaterial> =
        error("当前仓库不支持生成材料")
    suspend fun generate(
        request: MaterialGenerationRequest,
        onActivity: (com.example.englishcantoneselearning.model.GenerationActivity) -> Unit = {},
    ): List<PracticeMaterial> = generate(request)
    suspend fun saveManualArticle(
        title: String,
        language: MaterialLanguage,
        sentenceTexts: List<String>,
    ): PracticeMaterial = error("当前仓库不支持保存手动文章")
    suspend fun playbackProgress(): Map<String, MaterialPlaybackProgress> = emptyMap()
    suspend fun savePlaybackProgress(progress: MaterialPlaybackProgress) = Unit
    suspend fun clearPlaybackProgress(materialId: String) = Unit
    suspend fun resumePendingGeneration(
        automatic: Boolean = false,
        onActivity: (GenerationActivity) -> Unit = {},
    ): List<PracticeMaterial>? = null
    suspend fun discardPendingGeneration() = Unit
    suspend fun hasPendingGeneration(): Boolean = false
    suspend fun deleteMaterial(id: String)
    suspend fun deleteBatch(batchId: String)
}

class DefaultMaterialRepository(
    private val dao: MaterialDao,
    private val generator: MaterialGenerator,
    private val sourceRepository: FixedSourceRepository? = null,
) : MaterialRepository {
    override suspend fun generate(request: MaterialGenerationRequest): List<PracticeMaterial> =
        generate(request) {}

    override suspend fun listMaterials(): List<PracticeMaterial> = withContext(Dispatchers.IO) {
        val progress = dao.allPlaybackProgress.associateBy { it.materialId }
        dao.all.map(::toModel).sortedByDescending { material ->
            progress[material.id]?.updatedAt?.coerceAtLeast(material.createdAt) ?: material.createdAt
        }
    }

    override suspend fun getMaterial(id: String): PracticeMaterial? = withContext(Dispatchers.IO) {
        dao.getById(id)?.let(::toModel)
    }

    override suspend fun recentSourceUrls(limit: Int): List<String> = withContext(Dispatchers.IO) {
        dao.getRecentSourcesJson(limit)
            .flatMap(::decodeSources)
            .map { it.url }
            .distinct()
            .take(limit)
    }

    override suspend fun generate(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): List<PracticeMaterial> = withContext(Dispatchers.IO) {
        val effectiveRequest = if (request.sourceSnapshot == null && sourceRepository != null) {
            val snapshot = sourceRepository.discover(request, onActivity)
            request.copy(
                sourceSnapshot = snapshot,
                primarySourceUrl = snapshot.url,
                outlineSections = snapshot.paragraphs.mapNotNull { it.heading }.distinct(),
            )
        } else request
        val fingerprint = MaterialPromptBuilder.fingerprint(effectiveRequest)
        dao.getByFingerprint(fingerprint).takeIf { it.size == 1 }?.let { cached ->
            return@withContext cached.map(::toModel)
        }
        val existing = dao.activeDraft
        val accumulator = if (existing != null) {
            val restored = decodeDraft(existing)
            if (restored.fingerprint != fingerprint) {
                throw GatewayFormatException("已有未完成长文草稿，请先继续或删除草稿")
            }
            restored
        } else {
            DraftAccumulator(id = UUID.randomUUID().toString(), fingerprint = fingerprint).also { created ->
                effectiveRequest.sourceSnapshot?.let { snapshot ->
                    created.title = snapshot.title
                    created.topic = effectiveRequest.topic.displayName
                    created.source = snapshot.toReference()
                    created.outlineSections += snapshot.paragraphs.mapNotNull { it.heading }.distinct()
                }
            }
        }
        saveDraft(accumulator, effectiveRequest, "ACTIVE", existing?.resumeFailureCount ?: 0)

        try {
            while (accumulator.chapterIndex < MAX_CHAPTERS) {
                val chapterParagraphs = effectiveRequest.sourceSnapshot?.let { snapshot ->
                    nextParagraphChunk(snapshot.paragraphs, accumulator.nextParagraphIndex)
                }.orEmpty()
                if (effectiveRequest.sourceSnapshot != null && chapterParagraphs.isEmpty()) {
                    throw GatewayFormatException("来源草稿没有剩余段落，但尚未完成合并")
                }
                val chapterRequest = effectiveRequest.copy(
                    chapterIndex = accumulator.chapterIndex,
                    primarySourceUrl = accumulator.source?.url,
                    outlineSections = accumulator.outlineSections,
                    completedSectionIds = accumulator.coveredSectionIds,
                    completedSentenceCount = accumulator.sentences.size,
                    previousSentenceTail = accumulator.sentences.takeLast(2).map { it.targetText },
                    chapterParagraphs = chapterParagraphs,
                    expectedParagraphIds = chapterParagraphs.map { it.id },
                )
                val generated = generator.generate(chapterRequest, onActivity)
                val chapter = generated.materials.singleOrNull()
                    ?: throw GatewayFormatException("当前章节没有返回唯一材料")
                val chapterSource = chapter.sources.single()
                accumulator.source?.let { existingSource ->
                    if (MaterialValidator.canonicalize(existingSource.url) != MaterialValidator.canonicalize(chapterSource.url)) {
                        throw GatewayFormatException("续章更换了主来源，本章未保存")
                    }
                }
                val seen = accumulator.sentences.map { normalizeSentence(it.targetText) }.toMutableSet()
                val originalToGlobal = mutableMapOf<Int, Int>()
                val unique = buildList {
                    chapter.sentences.forEachIndexed { index, sentence ->
                        if (seen.add(normalizeSentence(sentence.targetText))) {
                            originalToGlobal[index] = accumulator.sentences.size + size
                            add(sentence)
                        }
                    }
                }
                if (unique.isEmpty()) throw GatewayFormatException("当前章节与已有内容完全重复")
                accumulator.sentences += unique
                accumulator.sections += chapter.sections.mapIndexed { sectionIndex, section ->
                    val globalStart = originalToGlobal.entries
                        .firstOrNull { it.key >= section.startSentenceIndex }?.value
                        ?: originalToGlobal.values.last()
                    MaterialSection(
                        id = section.id.ifBlank { "section-${accumulator.chapterIndex}-$sectionIndex" },
                        title = section.title,
                        startSentenceIndex = globalStart,
                    )
                }.filter { it.title.isNotBlank() }
                accumulator.title = accumulator.title.ifBlank { chapter.title }
                accumulator.topic = accumulator.topic.ifBlank { chapter.topic }
                accumulator.source = accumulator.source ?: chapterSource
                if (accumulator.outlineSections.isEmpty()) accumulator.outlineSections += chapter.outlineSections
                accumulator.coveredSectionIds += chapter.coveredSectionIds.filterNot(accumulator.coveredSectionIds::contains)
                accumulator.nextParagraphIndex += chapterParagraphs.size
                accumulator.chapterIndex += 1
                accumulator.providerName = generated.providerName
                accumulator.model = generated.model
                accumulator.responseIds += generated.responseId
                accumulator.inputTokens += generated.inputTokens
                accumulator.outputTokens += generated.outputTokens
                saveDraft(accumulator, effectiveRequest, "ACTIVE", 0)
                onActivity(
                    GenerationActivity(
                        accumulator.providerName,
                        accumulator.chapterIndex,
                        GenerationStage.SAVING,
                        "chapter.saved",
                        completedPairs = accumulator.sentences.size,
                    ),
                )
                val fixedSourceComplete = effectiveRequest.sourceSnapshot?.let {
                    accumulator.nextParagraphIndex >= it.paragraphs.size
                }
                if (fixedSourceComplete == true || fixedSourceComplete == null && !chapter.hasMore) {
                    if (accumulator.sentences.size < 20) throw GatewayFormatException("完整文章至少需要20个句对")
                    val material = accumulator.toMaterial(effectiveRequest)
                    dao.finalizeDraft(toEntity(material), accumulator.id)
                    onActivity(
                        GenerationActivity(
                            accumulator.providerName,
                            accumulator.chapterIndex,
                            GenerationStage.COMPLETED,
                            "article.merged",
                            completedPairs = accumulator.sentences.size,
                        ),
                    )
                    return@withContext listOf(material)
                }
            }
            saveDraft(accumulator, effectiveRequest, "PAUSED", 0)
            throw GatewayFormatException("来源超过10章处理上限，已保留草稿")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val failures = (dao.activeDraft?.resumeFailureCount ?: 0) + 1
            saveDraft(accumulator, effectiveRequest, if (failures >= 3) "PAUSED" else "ERROR", failures)
            throw error
        }
    }

    override suspend fun resumePendingGeneration(
        automatic: Boolean,
        onActivity: (GenerationActivity) -> Unit,
    ): List<PracticeMaterial>? = withContext(Dispatchers.IO) {
        val draft = dao.activeDraft ?: return@withContext null
        val decodedRequest = decodeRequest(draft.requestJson)
        val decodedDraft = decodeDraft(draft)
        if (decodedRequest.sourceSnapshot == null && decodedDraft.chapterIndex == 0 && decodedDraft.sentences.isEmpty() &&
            sourceRepository != null
        ) {
            // Pre-3.1 web-search drafts with no completed work are incompatible with fixed-source
            // adaptation and contain nothing worth retaining.
            dao.deleteDraft(draft.id)
            return@withContext null
        }
        if (automatic && (draft.status == "PAUSED" || draft.resumeFailureCount >= 3)) return@withContext null
        generate(decodedRequest, onActivity)
    }

    override suspend fun discardPendingGeneration(): Unit = withContext(Dispatchers.IO) {
        dao.activeDraft?.let { dao.deleteDraft(it.id) }
        Unit
    }

    override suspend fun hasPendingGeneration(): Boolean = withContext(Dispatchers.IO) {
        dao.activeDraft != null
    }

    override suspend fun saveManualArticle(
        title: String,
        language: MaterialLanguage,
        sentenceTexts: List<String>,
    ): PracticeMaterial = withContext(Dispatchers.IO) {
        require(sentenceTexts.isNotEmpty()) { "请先完成断句" }
        val materialId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val material = PracticeMaterial(
            id = materialId,
            batchId = materialId,
            batchPosition = 0,
            language = language,
            difficulty = com.example.englishcantoneselearning.model.Difficulty.TARGET,
            topic = "粘贴文章",
            title = title.trim().ifBlank { sentenceTexts.first().take(30) },
            targetText = sentenceTexts.joinToString(if (language == MaterialLanguage.ENGLISH) " " else ""),
            sentences = sentenceTexts.mapIndexed { index, text ->
                BilingualSentence("$materialId:$index", text.trim(), null, null)
            },
            sources = emptyList(),
            createdAt = now,
            promptVersion = "manual-paste-v1",
            providerName = "手动粘贴",
            model = "",
            responseId = "",
            inputTokens = 0,
            outputTokens = 0,
            requestFingerprint = "manual-$materialId",
            origin = ArticleOrigin.MANUAL_PASTE,
        )
        dao.insert(toEntity(material))
        material
    }

    override suspend fun playbackProgress(): Map<String, MaterialPlaybackProgress> = withContext(Dispatchers.IO) {
        dao.allPlaybackProgress.associate { entity -> entity.materialId to entity.toModel() }
    }

    override suspend fun savePlaybackProgress(progress: MaterialPlaybackProgress) = withContext(Dispatchers.IO) {
        dao.savePlaybackProgress(progress.toEntity())
    }

    override suspend fun clearPlaybackProgress(materialId: String) = withContext(Dispatchers.IO) {
        dao.deletePlaybackProgress(materialId)
    }

    override suspend fun deleteMaterial(id: String) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let(dao::delete)
        Unit
    }

    override suspend fun deleteBatch(batchId: String) = withContext(Dispatchers.IO) {
        dao.deleteBatch(batchId)
    }

    private fun toEntity(material: PracticeMaterial): MaterialEntity = MaterialEntity().apply {
        id = material.id
        batchId = material.batchId
        batchPosition = material.batchPosition
        language = material.language.name
        difficulty = material.difficulty.name
        topic = material.topic
        title = material.title
        targetText = material.targetText
        sentencesJson = encodeSentences(material.sentences)
        sourcesJson = encodeSources(material.sources)
        createdAt = material.createdAt
        promptVersion = material.promptVersion
        providerName = material.providerName
        model = material.model
        responseId = material.responseId
        inputTokens = material.inputTokens
        outputTokens = material.outputTokens
        requestFingerprint = material.requestFingerprint
        origin = material.origin.name
        sectionsJson = encodeSections(material.sections)
    }

    private fun toModel(entity: MaterialEntity): PracticeMaterial = PracticeMaterial(
        id = entity.id,
        batchId = entity.batchId,
        batchPosition = entity.batchPosition,
        language = enumValueOf(entity.language),
        difficulty = enumValueOf(entity.difficulty),
        topic = entity.topic,
        title = entity.title,
        targetText = entity.targetText,
        sentences = decodeSentences(entity.sentencesJson),
        sources = decodeSources(entity.sourcesJson),
        createdAt = entity.createdAt,
        promptVersion = entity.promptVersion,
        providerName = entity.providerName,
        model = entity.model,
        responseId = entity.responseId,
        inputTokens = entity.inputTokens,
        outputTokens = entity.outputTokens,
        requestFingerprint = entity.requestFingerprint,
        origin = runCatching { enumValueOf<ArticleOrigin>(entity.origin) }.getOrDefault(ArticleOrigin.AI_GENERATED),
        sections = decodeSections(entity.sectionsJson),
    )

    private fun encodeSentences(sentences: List<BilingualSentence>): String = JSONArray().apply {
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

    private fun decodeSentences(json: String): List<BilingualSentence> = buildList {
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

    private fun encodeSources(sources: List<SourceReference>): String = JSONArray().apply {
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

    private fun decodeSources(json: String): List<SourceReference> = buildList {
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

    private fun encodeSections(sections: List<MaterialSection>): String = JSONArray().apply {
        sections.forEach { section ->
            put(JSONObject().put("id", section.id).put("title", section.title)
                .put("startSentenceIndex", section.startSentenceIndex))
        }
    }.toString()

    private fun decodeSections(json: String): List<MaterialSection> = buildList {
        val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(MaterialSection(item.optString("id", "section-$index"), item.optString("title"), item.optInt("startSentenceIndex")))
        }
    }

    private fun MaterialPlaybackProgress.toEntity() = MaterialPlaybackProgressEntity().also { entity ->
        entity.materialId = materialId
        entity.resumeSentenceIndex = resumeSentenceIndex
        entity.completedSentenceIndicesJson = JSONArray(completedSentenceIndices.sorted()).toString()
        entity.completed = completed
        entity.updatedAt = updatedAt
    }

    private fun MaterialPlaybackProgressEntity.toModel(): MaterialPlaybackProgress {
        val array = runCatching { JSONArray(completedSentenceIndicesJson) }.getOrElse { JSONArray() }
        val indices = buildSet { for (index in 0 until array.length()) add(array.optInt(index)) }
        return MaterialPlaybackProgress(materialId, resumeSentenceIndex, indices, completed, updatedAt)
    }

    private fun saveDraft(
        accumulator: DraftAccumulator,
        request: MaterialGenerationRequest,
        status: String,
        resumeFailureCount: Int,
    ) {
        dao.saveDraft(MaterialDraftEntity().also { draft ->
            draft.id = accumulator.id
            draft.requestJson = encodeRequest(request)
            draft.stateJson = encodeDraft(accumulator)
            draft.status = status
            draft.resumeFailureCount = resumeFailureCount
            draft.updatedAt = System.currentTimeMillis()
        })
    }

    private fun encodeRequest(request: MaterialGenerationRequest): String = JSONObject()
        .put("language", request.language.name)
        .put("difficulty", request.difficulty.name)
        .put("topic", request.topic.name)
        .put("englishListening", request.profile.englishListening.toDouble())
        .put("cantoneseLevel", request.profile.cantoneseLevel)
        .put("excludedSourceUrls", JSONArray(request.excludedSourceUrls))
        .put("currentDate", request.currentDate)
        .put("sourceSnapshot", request.sourceSnapshot?.let(::encodeSnapshot) ?: JSONObject.NULL)
        .toString()

    private fun decodeRequest(json: String): MaterialGenerationRequest {
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

    private fun encodeDraft(accumulator: DraftAccumulator): String = JSONObject()
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
                put(JSONObject()
                    .put("targetText", sentence.targetText)
                    .put("jyutping", sentence.jyutping)
                    .put("simplifiedChinese", sentence.simplifiedChinese))
            }
        })
        .put("sections", JSONArray().apply {
            accumulator.sections.forEach { section ->
                put(JSONObject().put("id", section.id).put("title", section.title)
                    .put("startSentenceIndex", section.startSentenceIndex))
            }
        })
        .put("providerName", accumulator.providerName)
        .put("model", accumulator.model)
        .put("responseIds", JSONArray(accumulator.responseIds))
        .put("inputTokens", accumulator.inputTokens)
        .put("outputTokens", accumulator.outputTokens)
        .toString()

    private fun decodeDraft(entity: MaterialDraftEntity): DraftAccumulator {
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
                    add(GeneratedSentence(
                        sentence.getString("targetText"),
                        sentence.optString("jyutping"),
                        sentence.optString("simplifiedChinese"),
                    ))
                }
            }.toMutableList(),
            sections = buildList {
                if (sections != null) for (index in 0 until sections.length()) {
                    val section = sections.getJSONObject(index)
                    add(MaterialSection(
                        section.optString("id", "section-$index"),
                        section.optString("title"),
                        section.optInt("startSentenceIndex"),
                    ))
                }
            }.toMutableList(),
            providerName = value.optString("providerName"),
            model = value.optString("model"),
            responseIds = value.optJSONArray("responseIds").toStringList().toMutableList(),
            inputTokens = value.optInt("inputTokens"),
            outputTokens = value.optInt("outputTokens"),
        )
    }

    private fun DraftAccumulator.toMaterial(request: MaterialGenerationRequest): PracticeMaterial {
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
            targetText = sentences.joinToString(if (request.language == MaterialLanguage.ENGLISH) " " else "") { it.targetText },
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
                put(JSONObject().put("id", paragraph.id).put("heading", paragraph.heading)
                    .put("text", paragraph.text))
            }
        })

    private fun decodeSnapshot(value: JSONObject): SourceArticleSnapshot {
        val array = value.optJSONArray("paragraphs") ?: JSONArray()
        val paragraphs = buildList {
            for (index in 0 until array.length()) {
                val paragraph = array.getJSONObject(index)
                add(SourceParagraph(
                    paragraph.getString("id"),
                    paragraph.optString("heading").takeIf { it.isNotBlank() && it != "null" },
                    paragraph.getString("text"),
                ))
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

    private fun normalizeSentence(text: String): String = text.lowercase()
        .replace(Regex("[\\s\\p{Punct}，。！？；：、“”‘’]+"), "")

    private data class DraftAccumulator(
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

    private companion object {
        const val MAX_CHAPTERS = 10
        const val MIN_CHAPTER_SOURCE_CHARS = 3_000
        const val MAX_CHAPTER_SOURCE_CHARS = 6_000
    }

    private fun SourceArticleSnapshot.toReference() = SourceReference(
        title = title,
        publisher = publisher,
        url = url,
        publishedAt = publishedAt,
        sourceLanguage = sourceLanguage,
        sourceId = sourceId,
        contentHash = contentHash,
        fetchedAt = fetchedAt,
        cleanerVersion = cleanerVersion,
    )

    private fun nextParagraphChunk(paragraphs: List<SourceParagraph>, start: Int): List<SourceParagraph> {
        if (start !in paragraphs.indices) return emptyList()
        val result = mutableListOf<SourceParagraph>()
        var characters = 0
        for (index in start until paragraphs.size) {
            val paragraph = paragraphs[index]
            if (result.isNotEmpty() && characters >= MIN_CHAPTER_SOURCE_CHARS &&
                characters + paragraph.text.length > MAX_CHAPTER_SOURCE_CHARS
            ) break
            result += paragraph
            characters += paragraph.text.length
            if (characters >= MAX_CHAPTER_SOURCE_CHARS) break
        }
        return result
    }
}
