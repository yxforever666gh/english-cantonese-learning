package com.example.englishcantoneselearning.ui.material

import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.TtsAvailability

enum class ConnectionState {
    IDLE,
    CHECKING,
    READY,
    ERROR,
}

data class MaterialUiState(
    val materials: List<PracticeMaterial> = emptyList(),
    val selectedMaterial: PracticeMaterial? = null,
    val language: MaterialLanguage = MaterialLanguage.ENGLISH,
    val libraryLanguage: MaterialLanguage = MaterialLanguage.ENGLISH,
    val librarySelectedArticleIds: Set<String> = emptySet(),
    val isAudioCaching: Boolean = false,
    val audioCachingProgress: String? = null,
    val difficulty: Difficulty = Difficulty.TARGET,
    val topic: MaterialTopic = MaterialTopic.RANDOM,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val generationActivity: GenerationActivity? = null,
    val hasPendingDraft: Boolean = false,
    val materialProviders: List<MaterialProviderConfig> = emptyList(),
    val providerConnectionStates: Map<String, ConnectionState> = emptyMap(),
    val miniMaxConfig: MiniMaxTtsConfig = MiniMaxTtsConfig(),
    val miniMaxConnectionState: ConnectionState = ConnectionState.IDLE,
    val voiceCatalog: List<MiniMaxVoice> = emptyList(),
    val voiceCatalogFetchedAt: Long = 0L,
    val voiceCatalogState: ConnectionState = ConnectionState.IDLE,
    val customVoiceFavorites: List<CustomVoiceFavorite> = emptyList(),
    val previewingVoiceId: String? = null,
    val audioCacheBytes: Long = 0,
    val englishListeningBand: Float = 6.0f,
    val selectedSentenceIndex: Int = -1,
    val bilingualPhase: BilingualPhase = BilingualPhase.TARGET,
    val characterOffset: Int = 0,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val playbackMode: PlaybackMode = PlaybackMode.CONTINUOUS,
    val targetAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
    val mandarinAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
    val englishSpeed: Float = 0.9f,
    val cantoneseSpeed: Float = 0.8f,
    val mandarinSpeed: Float = 1.0f,
    val generationError: String? = null,
    val userMessage: String? = null,
    val playbackProgress: Map<String, MaterialPlaybackProgress> = emptyMap(),
)
