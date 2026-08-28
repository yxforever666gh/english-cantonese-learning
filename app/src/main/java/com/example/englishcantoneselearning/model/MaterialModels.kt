package com.example.englishcantoneselearning.model

enum class SpeechLanguage {
    ENGLISH_US,
    CANTONESE_HK,
    MANDARIN_CN,
}

enum class MaterialLanguage {
    ENGLISH,
    CANTONESE,
}

enum class Difficulty {
    EASY,
    TARGET,
    CHALLENGE,
}

enum class MaterialTopic(val displayName: String) {
    DAILY("日常"),
    TECHNOLOGY("科技"),
    CULTURE("文化"),
    WORK("职场"),
    CURRENT_EVENTS("时事"),
    RANDOM("随机混合"),
}

data class LearnerProfile(
    val englishListening: Float = 6.0f,
    val cantoneseLevel: String = "A0/A1 零基础",
)

data class BilingualSentence(
    val id: String,
    val targetText: String,
    val jyutping: String?,
    val simplifiedChinese: String?,
)

enum class ArticleOrigin {
    AI_GENERATED,
    MANUAL_PASTE,
}

data class MaterialSection(
    val id: String,
    val title: String,
    val startSentenceIndex: Int,
)

data class MaterialPlaybackProgress(
    val materialId: String,
    val resumeSentenceIndex: Int = 0,
    val completedSentenceIndices: Set<Int> = emptySet(),
    val completed: Boolean = false,
    val updatedAt: Long = 0L,
) {
    fun percent(totalSentences: Int): Int = if (totalSentences <= 0) 0 else
        ((completedSentenceIndices.size.coerceAtMost(totalSentences) * 100f) / totalSentences).toInt()
}

data class SourceReference(
    val title: String,
    val publisher: String,
    val url: String,
    val publishedAt: String?,
    val sourceLanguage: String,
    val sourceId: String = "",
    val contentHash: String = "",
    val fetchedAt: Long = 0L,
    val cleanerVersion: String = "",
)

data class PracticeMaterial(
    val id: String,
    val batchId: String,
    val batchPosition: Int,
    val language: MaterialLanguage,
    val difficulty: Difficulty,
    val topic: String,
    val title: String,
    val targetText: String,
    val sentences: List<BilingualSentence>,
    val sources: List<SourceReference>,
    val createdAt: Long,
    val promptVersion: String,
    val providerName: String,
    val model: String,
    val responseId: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val requestFingerprint: String,
    val origin: ArticleOrigin = ArticleOrigin.AI_GENERATED,
    val sections: List<MaterialSection> = emptyList(),
)

data class MaterialProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val enabled: Boolean = true,
)

data class MiniMaxTtsConfig(
    val baseUrl: String = "https://api.minimaxi.com",
    val apiKey: String = "",
    val model: String = "speech-2.8-turbo",
    val englishVoice: String = "Serene_Woman",
    val cantoneseVoice: String = "Cantonese_GentleLady",
    val mandarinVoice: String = "female-tianmei",
)

data class MaterialGenerationRequest(
    val language: MaterialLanguage,
    val difficulty: Difficulty,
    val topic: MaterialTopic,
    val profile: LearnerProfile = LearnerProfile(),
    val excludedSourceUrls: List<String> = emptyList(),
    val currentDate: String,
    val chapterIndex: Int = 0,
    val primarySourceUrl: String? = null,
    val outlineSections: List<String> = emptyList(),
    val completedSectionIds: List<String> = emptyList(),
    val completedSentenceCount: Int = 0,
    val previousSentenceTail: List<String> = emptyList(),
    val sourceSnapshot: SourceArticleSnapshot? = null,
    val chapterParagraphs: List<SourceParagraph> = emptyList(),
    val expectedParagraphIds: List<String> = emptyList(),
)

enum class GenerationStage {
    DISCOVERING_SOURCE,
    FETCHING_SOURCE,
    CLEANING_SOURCE,
    CONNECTING,
    SEARCHING,
    REASONING,
    WRITING,
    VALIDATING,
    SAVING,
    FAILOVER,
    COMPLETED,
}

data class GenerationActivity(
    val provider: String,
    val chapter: Int,
    val stage: GenerationStage,
    val eventType: String,
    val receivedChars: Int = 0,
    val completedPairs: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val lastActivityAt: Long = System.currentTimeMillis(),
)

enum class BilingualPhase {
    TARGET,
    TRANSLATION,
}

data class PlaybackEntry(
    val text: String,
    val speechLanguage: SpeechLanguage,
    val sentenceId: String,
    val phase: BilingualPhase,
)

fun LearningLanguage.toSpeechLanguage(): SpeechLanguage = when (this) {
    LearningLanguage.ENGLISH -> SpeechLanguage.ENGLISH_US
    LearningLanguage.CANTONESE -> SpeechLanguage.CANTONESE_HK
}

fun MaterialLanguage.toSpeechLanguage(): SpeechLanguage = when (this) {
    MaterialLanguage.ENGLISH -> SpeechLanguage.ENGLISH_US
    MaterialLanguage.CANTONESE -> SpeechLanguage.CANTONESE_HK
}
