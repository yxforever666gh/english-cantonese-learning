package com.example.englishcantoneselearning.model

enum class LearningLanguage {
    ENGLISH,
    CANTONESE,
}

enum class PlaybackMode {
    SINGLE,
    CONTINUOUS,
}

enum class PlaybackStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
}

enum class TtsAvailability {
    INITIALIZING,
    READY,
    MISSING_DATA,
    UNSUPPORTED,
    ERROR,
}

data class SentenceItem(
    val id: Long,
    val text: String,
)

data class ReaderUiState(
    val articleText: String = "",
    val articleTitle: String = "",
    val language: LearningLanguage = LearningLanguage.ENGLISH,
    val sentences: List<SentenceItem> = emptyList(),
    val selectedIndex: Int = -1,
    val characterOffset: Int = 0,
    val playbackMode: PlaybackMode = PlaybackMode.CONTINUOUS,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val speed: Float = 0.8f,
    val ttsAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
    val userMessage: String? = null,
)
