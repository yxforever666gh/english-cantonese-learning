package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialDao
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.data.source.FixedSourceRepository
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.MaterialSection
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceReference
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    suspend fun saveNewsArticle(
        snapshot: SourceArticleSnapshot,
        language: MaterialLanguage,
        tags: Set<NewsTag>,
        sentenceTexts: List<String>,
    ): PracticeMaterial = error("当前仓库不支持保存新闻文章")
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
    private val draftStore = MaterialDraftStore(dao)
    private val chapterCoordinator = MaterialChapterGenerationCoordinator(dao, generator, draftStore)
    private val newsSaveMutex = Mutex()

    override suspend fun generate(request: MaterialGenerationRequest): List<PracticeMaterial> =
        generate(request) {}

    override suspend fun listMaterials(): List<PracticeMaterial> = withContext(Dispatchers.IO) {
        val progress = dao.allPlaybackProgress.associateBy { it.materialId }
        dao.all.map { it.toPracticeMaterial() }.sortedByDescending { material ->
            progress[material.id]?.updatedAt?.coerceAtLeast(material.createdAt) ?: material.createdAt
        }
    }

    override suspend fun getMaterial(id: String): PracticeMaterial? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toPracticeMaterial()
    }

    override suspend fun recentSourceUrls(limit: Int): List<String> = withContext(Dispatchers.IO) {
        dao.getRecentSourcesJson(limit)
            .flatMap(MaterialJsonCodec::decodeSources)
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
        chapterCoordinator.generate(effectiveRequest, onActivity)
    }

    override suspend fun resumePendingGeneration(
        automatic: Boolean,
        onActivity: (GenerationActivity) -> Unit,
    ): List<PracticeMaterial>? = withContext(Dispatchers.IO) {
        val draft = dao.activeDraft ?: return@withContext null
        val decodedRequest = draftStore.decodeRequest(draft.requestJson)
        val decodedDraft = draftStore.decode(draft)
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
        dao.insert(material.toMaterialEntity())
        material
    }

    override suspend fun saveNewsArticle(
        snapshot: SourceArticleSnapshot,
        language: MaterialLanguage,
        tags: Set<NewsTag>,
        sentenceTexts: List<String>,
    ): PracticeMaterial = withContext(Dispatchers.IO) {
        require(snapshot.paragraphs.isNotEmpty()) { "新闻正文为空" }
        val originalText = snapshot.paragraphs.joinToString("\n\n") { it.text.trim() }
        require(originalText.isNotBlank()) { "新闻正文为空" }
        val cleanSentences = sentenceTexts.map(String::trim).filter(String::isNotEmpty)
        require(cleanSentences.isNotEmpty()) { "请先完成断句" }

        newsSaveMutex.withLock {
            findExistingNewsMaterial(snapshot)?.let { return@withLock it }

            val materialId = UUID.randomUUID().toString()
            val material = PracticeMaterial(
                id = materialId,
                batchId = materialId,
                batchPosition = 0,
                language = language,
                difficulty = com.example.englishcantoneselearning.model.Difficulty.TARGET,
                topic = tags.sortedBy(NewsTag::ordinal).joinToString("、", transform = NewsTag::displayName)
                    .ifBlank { "新闻收藏" },
                title = snapshot.title.trim().ifBlank { cleanSentences.first().take(30) },
                targetText = originalText,
                sentences = cleanSentences.mapIndexed { index, text ->
                    BilingualSentence("$materialId:$index", text, null, null)
                },
                sources = listOf(snapshot.toSourceReference()),
                createdAt = System.currentTimeMillis(),
                promptVersion = NEWS_IMPORT_VERSION,
                providerName = "新闻导入",
                model = "",
                responseId = "",
                inputTokens = 0,
                outputTokens = 0,
                requestFingerprint = newsFingerprint(snapshot),
                origin = ArticleOrigin.NEWS_FEED,
                sections = buildNewsSections(snapshot, cleanSentences),
                listeningBand = null,
            )
            dao.insert(material.toMaterialEntity())
            material
        }
    }

    private fun findExistingNewsMaterial(snapshot: SourceArticleSnapshot): PracticeMaterial? {
        val canonicalUrl = MaterialValidator.canonicalize(snapshot.url)
        return dao.all.asSequence()
            .map { it.toPracticeMaterial() }
            .firstOrNull { material ->
                material.sources.any { source ->
                    (snapshot.contentHash.isNotBlank() && source.contentHash == snapshot.contentHash) ||
                        (canonicalUrl != null && MaterialValidator.canonicalize(source.url) == canonicalUrl)
                }
            }
    }

    private fun SourceArticleSnapshot.toSourceReference() = SourceReference(
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

    private fun buildNewsSections(
        snapshot: SourceArticleSnapshot,
        sentenceTexts: List<String>,
    ): List<MaterialSection> {
        var sentenceIndex = 0
        return buildList {
            snapshot.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                paragraph.heading?.trim()?.takeIf(String::isNotEmpty)?.let { heading ->
                    add(MaterialSection(paragraph.id.ifBlank { "news-section-$paragraphIndex" }, heading, sentenceIndex))
                }
                val paragraphText = normalizeForSentenceMatch(paragraph.text)
                while (sentenceIndex < sentenceTexts.size &&
                    paragraphText.contains(normalizeForSentenceMatch(sentenceTexts[sentenceIndex]))
                ) {
                    sentenceIndex++
                }
            }
        }.distinctBy(MaterialSection::startSentenceIndex)
    }

    private fun newsFingerprint(snapshot: SourceArticleSnapshot): String {
        val stableIdentity = snapshot.contentHash.ifBlank {
            MaterialValidator.canonicalize(snapshot.url).orEmpty()
        }
        return "news-${stableIdentity.ifBlank { UUID.randomUUID().toString() }}"
    }

    private fun normalizeForSentenceMatch(text: String): String = text
        .replace(Regex("\\s+"), "")
        .trim()

    override suspend fun playbackProgress(): Map<String, MaterialPlaybackProgress> = withContext(Dispatchers.IO) {
        dao.allPlaybackProgress.associate { entity -> entity.materialId to entity.toPlaybackProgress() }
    }

    override suspend fun savePlaybackProgress(progress: MaterialPlaybackProgress) = withContext(Dispatchers.IO) {
        dao.savePlaybackProgress(progress.toPlaybackProgressEntity())
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

    private companion object {
        const val NEWS_IMPORT_VERSION = "news-import-v1"
    }

}
