package com.example.englishcantoneselearning.data.repository

import com.example.englishcantoneselearning.data.local.MaterialDao
import com.example.englishcantoneselearning.data.network.GatewayFormatException
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.network.MaterialPromptBuilder
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialSection
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import com.example.englishcantoneselearning.model.SourceReference
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal class MaterialChapterGenerationCoordinator(
    private val dao: MaterialDao,
    private val generator: MaterialGenerator,
    private val draftStore: MaterialDraftStore,
) {
    suspend fun generate(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): List<PracticeMaterial> {
        val fingerprint = MaterialPromptBuilder.fingerprint(request)
        dao.getByFingerprint(fingerprint).takeIf { it.size == 1 }?.let { cached ->
            return cached.map { it.toPracticeMaterial() }
        }
        val existing = dao.activeDraft
        val accumulator = if (existing != null) {
            val restored = draftStore.decode(existing)
            if (restored.fingerprint != fingerprint) {
                throw GatewayFormatException("已有未完成长文草稿，请先继续或删除草稿")
            }
            restored
        } else {
            DraftAccumulator(id = UUID.randomUUID().toString(), fingerprint = fingerprint).also { created ->
                request.sourceSnapshot?.let { snapshot ->
                    created.title = snapshot.title
                    created.topic = request.topic.displayName
                    created.source = snapshot.toReference()
                    created.outlineSections += snapshot.paragraphs.mapNotNull { it.heading }.distinct()
                }
            }
        }
        draftStore.save(accumulator, request, "ACTIVE", existing?.resumeFailureCount ?: 0)

        try {
            while (accumulator.chapterIndex < MAX_CHAPTERS) {
                val chapterParagraphs = request.sourceSnapshot?.let { snapshot ->
                    nextParagraphChunk(snapshot.paragraphs, accumulator.nextParagraphIndex)
                }.orEmpty()
                if (request.sourceSnapshot != null && chapterParagraphs.isEmpty()) {
                    throw GatewayFormatException("来源草稿没有剩余段落，但尚未完成合并")
                }
                val chapterRequest = request.copy(
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
                    if (MaterialValidator.canonicalize(existingSource.url) !=
                        MaterialValidator.canonicalize(chapterSource.url)
                    ) {
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
                draftStore.save(accumulator, request, "ACTIVE", 0)
                onActivity(
                    GenerationActivity(
                        accumulator.providerName,
                        accumulator.chapterIndex,
                        GenerationStage.SAVING,
                        "chapter.saved",
                        completedPairs = accumulator.sentences.size,
                    ),
                )
                val fixedSourceComplete = request.sourceSnapshot?.let {
                    accumulator.nextParagraphIndex >= it.paragraphs.size
                }
                if (fixedSourceComplete == true || fixedSourceComplete == null && !chapter.hasMore) {
                    if (accumulator.sentences.size < 20) {
                        throw GatewayFormatException("完整文章至少需要20个句对")
                    }
                    val material = accumulator.toMaterial(request)
                    dao.finalizeDraft(material.toMaterialEntity(), accumulator.id)
                    onActivity(
                        GenerationActivity(
                            accumulator.providerName,
                            accumulator.chapterIndex,
                            GenerationStage.COMPLETED,
                            "article.merged",
                            completedPairs = accumulator.sentences.size,
                        ),
                    )
                    return listOf(material)
                }
            }
            draftStore.save(accumulator, request, "PAUSED", 0)
            throw GatewayFormatException("来源超过10章处理上限，已保留草稿")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val failures = (dao.activeDraft?.resumeFailureCount ?: 0) + 1
            draftStore.save(accumulator, request, if (failures >= 3) "PAUSED" else "ERROR", failures)
            throw error
        }
    }

    private fun normalizeSentence(text: String): String = text.lowercase()
        .replace(Regex("[\\s\\p{Punct}，。！？；：、“”‘’]+"), "")

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

    private companion object {
        const val MAX_CHAPTERS = 10
        const val MIN_CHAPTER_SOURCE_CHARS = 3_000
        const val MAX_CHAPTER_SOURCE_CHARS = 6_000
    }
}
