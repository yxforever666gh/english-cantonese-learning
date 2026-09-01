package com.example.englishcantoneselearning.ui.news

import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.TtsAvailability

data class NewsSection(
    val title: String,
    val startSentenceIndex: Int,
)

data class NewsUiState(
    val language: MaterialLanguage = MaterialLanguage.ENGLISH,
    val feeds: Map<MaterialLanguage, List<NewsItem>> = emptyMap(),
    val refreshingLanguages: Set<MaterialLanguage> = emptySet(),
    val feedErrors: Map<MaterialLanguage, String> = emptyMap(),
    val lastUpdatedAt: Map<MaterialLanguage, Long> = emptyMap(),
    val selectedTags: Set<NewsTag> = emptySet(),
    val selectedItem: NewsItem? = null,
    val article: SourceArticleSnapshot? = null,
    val sentences: List<SentenceItem> = emptyList(),
    val sections: List<NewsSection> = emptyList(),
    val isArticleLoading: Boolean = false,
    val articleError: String? = null,
    val isSaving: Boolean = false,
    val savedMaterialId: String? = null,
    val selectedSentenceIndex: Int = -1,
    val characterOffset: Int = 0,
    val playbackMode: PlaybackMode = PlaybackMode.CONTINUOUS,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val speed: Float = 0.8f,
    val ttsAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
    val userMessage: String? = null,
) {
    val items: List<NewsItem>
        get() = feeds[language].orEmpty()

    val visibleItems: List<NewsItem>
        get() = if (selectedTags.isEmpty()) items else items.filter { item ->
            item.tags.any(selectedTags::contains)
        }

    val tagCounts: Map<NewsTag, Int>
        get() = NewsTag.entries.associateWith { tag -> items.count { tag in it.tags } }

    val isRefreshing: Boolean
        get() = language in refreshingLanguages

    val feedError: String?
        get() = feedErrors[language]

    val updatedAt: Long?
        get() = lastUpdatedAt[language]
}
