package com.example.englishcantoneselearning.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.englishcantoneselearning.data.preferences.DEFAULT_SPEECH_SPEED
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.data.source.FixedSourceRepository
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.toSpeechLanguage
import com.example.englishcantoneselearning.segmentation.RuleBasedSentenceSegmenter
import com.example.englishcantoneselearning.segmentation.SentenceSegmenter
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class NewsViewModel(
    private val sourceRepository: FixedSourceRepository,
    private val materialRepository: MaterialRepository,
    private val userPreferences: LearnerPreferences,
    private val speechController: SpeechController,
    private val sentenceSegmenter: SentenceSegmenter = RuleBasedSentenceSegmenter(),
    private val now: () -> Long = System::currentTimeMillis,
    private val shutdownSpeechControllerOnClear: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        NewsUiState(speed = userPreferences.speechSpeed(SpeechLanguage.ENGLISH_US)),
    )
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val feedJobs = mutableMapOf<MaterialLanguage, Job>()
    private var articleJob: Job? = null
    private var preloadJob: Job? = null
    private var activeRequestId: Long? = null

    init {
        viewModelScope.launch { speechController.events.collect(::handleSpeechEvent) }
        viewModelScope.launch {
            userPreferences.speechSpeeds.collect { speeds ->
                _uiState.update { state ->
                    state.copy(speed = speeds.forLanguage(state.language.toSpeechLanguage()))
                }
            }
        }
        refreshTtsAvailability()
    }

    /** Called whenever the news destination becomes visible. */
    fun onEnter() {
        refresh(forceRefresh = true)
    }

    fun refresh(forceRefresh: Boolean = true) {
        val language = _uiState.value.language
        feedJobs.remove(language)?.cancel()
        _uiState.update { state ->
            state.copy(
                refreshingLanguages = state.refreshingLanguages + language,
                feedErrors = state.feedErrors - language,
            )
        }
        feedJobs[language] = viewModelScope.launch {
            try {
                val items = sourceRepository.refreshFeed(language, forceRefresh)
                _uiState.update { state ->
                    state.copy(
                        feeds = state.feeds + (language to items),
                        refreshingLanguages = state.refreshingLanguages - language,
                        feedErrors = state.feedErrors - language,
                        lastUpdatedAt = state.lastUpdatedAt + (language to now()),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        refreshingLanguages = state.refreshingLanguages - language,
                        feedErrors = state.feedErrors +
                            (language to (error.message ?: "新闻刷新失败，请稍后重试")),
                    )
                }
            }
        }
    }

    fun setLanguage(language: MaterialLanguage) {
        if (language == _uiState.value.language) return
        closeArticle()
        _uiState.update {
            it.copy(
                language = language,
                selectedTags = emptySet(),
                speed = userPreferences.speechSpeed(language.toSpeechLanguage()),
                userMessage = null,
            )
        }
        refresh(forceRefresh = true)
        refreshTtsAvailability()
    }

    fun toggleTag(tag: NewsTag) {
        _uiState.update { state ->
            state.copy(
                selectedTags = if (tag in state.selectedTags) {
                    state.selectedTags - tag
                } else {
                    state.selectedTags + tag
                },
            )
        }
    }

    fun showLatest() {
        _uiState.update { it.copy(selectedTags = emptySet()) }
    }

    fun openArticle(item: NewsItem) {
        articleJob?.cancel()
        stopPlayback()
        _uiState.update {
            it.copy(
                language = item.language,
                selectedItem = item,
                article = null,
                sentences = emptyList(),
                sections = emptyList(),
                isArticleLoading = true,
                articleError = null,
                isSaving = false,
                savedMaterialId = null,
                selectedSentenceIndex = -1,
                characterOffset = 0,
                speed = userPreferences.speechSpeed(item.language.toSpeechLanguage()),
                userMessage = null,
            )
        }
        articleJob = viewModelScope.launch {
            try {
                val snapshot = sourceRepository.loadArticle(item)
                val segmented = segment(snapshot, item.language)
                if (segmented.sentences.isEmpty()) error("正文中没有可朗读的句子")
                if (_uiState.value.selectedItem?.url != item.url) return@launch
                _uiState.update {
                    it.copy(
                        article = snapshot,
                        sentences = segmented.sentences,
                        sections = segmented.sections,
                        isArticleLoading = false,
                        articleError = null,
                        selectedSentenceIndex = 0,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_uiState.value.selectedItem?.url == item.url) {
                    _uiState.update {
                        it.copy(
                            isArticleLoading = false,
                            articleError = error.message ?: "新闻正文加载失败",
                        )
                    }
                }
            }
        }
    }

    fun retryArticle() {
        _uiState.value.selectedItem?.let(::openArticle)
    }

    fun closeArticle() {
        articleJob?.cancel()
        articleJob = null
        stopPlayback()
        _uiState.update {
            it.copy(
                selectedItem = null,
                article = null,
                sentences = emptyList(),
                sections = emptyList(),
                isArticleLoading = false,
                articleError = null,
                isSaving = false,
                savedMaterialId = null,
                selectedSentenceIndex = -1,
                characterOffset = 0,
            )
        }
    }

    fun onLeaveScreen() {
        closeArticle()
    }

    fun saveArticle() {
        val state = _uiState.value
        val item = state.selectedItem ?: return
        val snapshot = state.article ?: return
        if (state.sentences.isEmpty() || state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val saved = materialRepository.saveNewsArticle(
                    snapshot = snapshot,
                    language = item.language,
                    tags = item.tags,
                    sentenceTexts = state.sentences.map(SentenceItem::text),
                )
                if (_uiState.value.selectedItem?.url == item.url) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedMaterialId = saved.id,
                            userMessage = "已保存到材料库",
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(isSaving = false, userMessage = error.message ?: "保存新闻失败")
                }
            }
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _uiState.update { it.copy(playbackMode = mode) }
    }

    fun setSpeechSpeed(speed: Float) {
        val normalized = (speed.coerceIn(0.5f, 2.0f) * 10).roundToInt() / 10f
        val language = _uiState.value.language.toSpeechLanguage()
        _uiState.update { it.copy(speed = normalized) }
        userPreferences.setSpeechSpeed(language, normalized)
    }

    fun onSpeechSpeedChangeFinished() {
        val state = _uiState.value
        if (state.selectedSentenceIndex !in state.sentences.indices) return
        if (state.playbackStatus == PlaybackStatus.PLAYING ||
            state.playbackStatus == PlaybackStatus.PREPARING
        ) {
            startSpeaking(state.selectedSentenceIndex, 0)
        } else {
            preloadWindow(state.selectedSentenceIndex, includeCurrent = true)
        }
    }

    fun playOrPause() {
        when (_uiState.value.playbackStatus) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PREPARING -> stopPlayback()
            PlaybackStatus.PAUSED -> {
                if (speechController.resume()) {
                    _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
                } else {
                    startSpeaking(_uiState.value.selectedSentenceIndex, 0)
                }
            }
            PlaybackStatus.IDLE -> {
                val index = _uiState.value.selectedSentenceIndex.coerceAtLeast(0)
                startSpeaking(index, 0)
            }
        }
    }

    fun previousSentence() = navigateBy(-1)

    fun nextSentence() = navigateBy(1)

    fun selectAndPlay(index: Int) {
        if (index !in _uiState.value.sentences.indices) return
        stopPlayback()
        startSpeaking(index, 0)
    }

    fun refreshTtsAvailability() {
        val availability = speechController.checkAvailability(_uiState.value.language.toSpeechLanguage())
        if (availability != TtsAvailability.READY &&
            _uiState.value.playbackStatus != PlaybackStatus.IDLE
        ) {
            stopPlayback()
        }
        _uiState.update { it.copy(ttsAvailability = availability) }
    }

    fun onAppBackgrounded() {
        stopPlayback()
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        articleJob?.cancel()
        preloadJob?.cancel()
        activeRequestId = null
        speechController.stop()
        if (shutdownSpeechControllerOnClear) speechController.shutdown()
        super.onCleared()
    }

    private fun segment(snapshot: SourceArticleSnapshot, language: MaterialLanguage): SegmentedArticle {
        val learningLanguage = if (language == MaterialLanguage.ENGLISH) {
            LearningLanguage.ENGLISH
        } else {
            LearningLanguage.CANTONESE
        }
        val sentences = mutableListOf<SentenceItem>()
        val sections = mutableListOf<NewsSection>()
        snapshot.paragraphs.forEach { paragraph ->
            val start = sentences.size
            val paragraphSentences = sentenceSegmenter.segment(paragraph.text, learningLanguage)
            if (paragraphSentences.isNotEmpty()) {
                paragraph.heading?.trim()?.takeIf(String::isNotEmpty)?.let { heading ->
                    sections += NewsSection(heading, start)
                }
                sentences += paragraphSentences
            }
        }
        return SegmentedArticle(sentences, sections)
    }

    private fun pause() {
        if (speechController.pause()) {
            _uiState.update { it.copy(playbackStatus = PlaybackStatus.PAUSED) }
        } else {
            stopPlayback()
        }
    }

    private fun navigateBy(delta: Int) {
        val state = _uiState.value
        if (state.sentences.isEmpty()) return
        val current = state.selectedSentenceIndex.coerceAtLeast(0)
        val next = (current + delta).coerceIn(state.sentences.indices)
        val continuePlaying = state.playbackStatus in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)
        val remainPaused = state.playbackStatus == PlaybackStatus.PAUSED
        activeRequestId = null
        preloadJob?.cancel()
        speechController.stop()
        _uiState.update {
            it.copy(
                selectedSentenceIndex = next,
                characterOffset = 0,
                playbackStatus = if (remainPaused) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
            )
        }
        if (continuePlaying) startSpeaking(next, 0)
    }

    private fun startSpeaking(index: Int, startOffset: Int) {
        val state = _uiState.value
        if (index !in state.sentences.indices) return
        val speechLanguage = state.language.toSpeechLanguage()
        val availability = speechController.checkAvailability(speechLanguage)
        if (availability != TtsAvailability.READY) {
            activeRequestId = null
            _uiState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.IDLE,
                    ttsAvailability = availability,
                    userMessage = availabilityMessage(availability),
                )
            }
            return
        }

        activeRequestId = null
        preloadJob?.cancel()
        speechController.stop()
        val sentence = state.sentences[index]
        val safeOffset = startOffset.coerceIn(0, (sentence.text.length - 1).coerceAtLeast(0))
        // Other screens use positive counters with the shared controller. Reserve negative IDs
        // for news so a late event from a hidden player can never match this request.
        val requestId = requestIds.getAndDecrement()
        activeRequestId = requestId
        _uiState.update {
            it.copy(
                selectedSentenceIndex = index,
                characterOffset = safeOffset,
                playbackStatus = PlaybackStatus.PREPARING,
                ttsAvailability = TtsAvailability.READY,
                userMessage = null,
            )
        }
        val accepted = speechController.speak(
            requestId = requestId,
            text = sentence.text,
            language = speechLanguage,
            speed = state.speed,
            startOffset = safeOffset,
        )
        if (!accepted) {
            activeRequestId = null
            _uiState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.IDLE,
                    userMessage = "无法开始播放，请先配置MiniMax语音",
                )
            }
        } else {
            preloadWindow(index, includeCurrent = false)
        }
    }

    private fun preloadWindow(index: Int, includeCurrent: Boolean) {
        preloadJob?.cancel()
        val state = _uiState.value
        val start = if (includeCurrent) index else index + 1
        val count = if (includeCurrent) PRELOAD_FUTURE_SENTENCES + 1 else PRELOAD_FUTURE_SENTENCES
        val texts = state.sentences.drop(start).take(count).map(SentenceItem::text)
        if (texts.isEmpty()) return
        val language = state.language.toSpeechLanguage()
        val speed = state.speed
        preloadJob = viewModelScope.launch {
            supervisorScope {
                texts.forEach { text ->
                    launch {
                        try {
                            speechController.preload(text, language, speed)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            // Preloading is best-effort. Playback retries the same audio normally.
                        }
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        activeRequestId = null
        preloadJob?.cancel()
        preloadJob = null
        speechController.stop()
        _uiState.update {
            it.copy(playbackStatus = PlaybackStatus.IDLE, characterOffset = 0)
        }
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Initialized -> if (event.successful) {
                refreshTtsAvailability()
            } else {
                _uiState.update {
                    it.copy(ttsAvailability = TtsAvailability.ERROR, userMessage = "MiniMax语音初始化失败")
                }
            }
            is SpeechEvent.Started -> if (event.requestId == activeRequestId) {
                _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
            }
            is SpeechEvent.Range -> if (event.requestId == activeRequestId) {
                _uiState.update { it.copy(characterOffset = event.sentenceOffset) }
            }
            is SpeechEvent.Done -> if (event.requestId == activeRequestId) {
                activeRequestId = null
                val state = _uiState.value
                val next = state.selectedSentenceIndex + 1
                if (state.playbackMode == PlaybackMode.CONTINUOUS && next in state.sentences.indices) {
                    startSpeaking(next, 0)
                } else {
                    _uiState.update { it.copy(playbackStatus = PlaybackStatus.IDLE, characterOffset = 0) }
                }
            }
            is SpeechEvent.Error -> if (activeRequestId != null &&
                (event.requestId == null || event.requestId == activeRequestId)
            ) {
                activeRequestId = null
                _uiState.update {
                    it.copy(
                        playbackStatus = PlaybackStatus.IDLE,
                        characterOffset = 0,
                        userMessage = event.message,
                    )
                }
            }
        }
    }

    private fun availabilityMessage(availability: TtsAvailability): String = when (availability) {
        TtsAvailability.INITIALIZING -> "MiniMax语音正在初始化，请稍候"
        TtsAvailability.MISSING_DATA -> "请先在设置中填写MiniMax API Key"
        TtsAvailability.UNSUPPORTED -> "MiniMax语音配置不受支持"
        TtsAvailability.ERROR -> "MiniMax语音不可用"
        TtsAvailability.READY -> ""
    }

    class Factory(
        private val sourceRepository: FixedSourceRepository,
        private val materialRepository: MaterialRepository,
        private val userPreferences: LearnerPreferences,
        private val speechController: SpeechController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NewsViewModel::class.java))
            return NewsViewModel(
                sourceRepository = sourceRepository,
                materialRepository = materialRepository,
                userPreferences = userPreferences,
                speechController = speechController,
            ) as T
        }
    }

    private data class SegmentedArticle(
        val sentences: List<SentenceItem>,
        val sections: List<NewsSection>,
    )

    private companion object {
        const val PRELOAD_FUTURE_SENTENCES = 2
        val requestIds = AtomicLong(-1L)
    }
}
