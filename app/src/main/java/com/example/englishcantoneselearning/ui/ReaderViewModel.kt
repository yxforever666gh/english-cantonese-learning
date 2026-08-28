package com.example.englishcantoneselearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.englishcantoneselearning.AppContainer
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.toSpeechLanguage
import com.example.englishcantoneselearning.segmentation.RuleBasedSentenceSegmenter
import com.example.englishcantoneselearning.segmentation.SentenceSegmenter
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val speechController: SpeechController,
    private val sentenceSegmenter: SentenceSegmenter = RuleBasedSentenceSegmenter(),
    private val shutdownSpeechControllerOnClear: Boolean = true,
    private val materialRepository: MaterialRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val requestIds = AtomicLong(1)
    private var nextManualSentenceId = -1L
    private var activeRequestId: Long? = null

    init {
        viewModelScope.launch {
            speechController.events.collect(::handleSpeechEvent)
        }
        refreshTtsAvailability()
    }

    fun onArticleTextChange(text: String) {
        if (text == _uiState.value.articleText) return
        if (_uiState.value.sentences.isNotEmpty()) stopPlayback()
        _uiState.update {
            it.copy(
                articleText = text,
                articleTitle = "",
                sentences = emptyList(),
                selectedIndex = -1,
                characterOffset = 0,
                userMessage = null,
            )
        }
    }

    fun onArticleTitleChange(title: String) {
        _uiState.update { it.copy(articleTitle = title.take(120), userMessage = null) }
    }

    fun onLanguageChange(language: LearningLanguage) {
        if (language == _uiState.value.language) return
        stopPlayback()
        _uiState.update {
            it.copy(
                language = language,
                sentences = emptyList(),
                selectedIndex = -1,
                characterOffset = 0,
                userMessage = null,
            )
        }
        refreshTtsAvailability()
    }

    fun segmentArticle() {
        val state = _uiState.value
        if (state.articleText.isBlank()) {
            showMessage("请先输入或粘贴文章")
            return
        }

        stopPlayback()
        val sentences = sentenceSegmenter.segment(state.articleText, state.language)
        _uiState.update {
            it.copy(
                sentences = sentences,
                articleTitle = it.articleTitle.ifBlank {
                    state.articleText.lineSequence().firstOrNull { line -> line.isNotBlank() }
                        ?.trim()?.take(30) ?: sentences.firstOrNull()?.text?.take(30).orEmpty()
                },
                selectedIndex = if (sentences.isEmpty()) -1 else 0,
                characterOffset = 0,
                userMessage = if (sentences.isEmpty()) "没有找到可朗读的句子" else null,
            )
        }
    }

    fun saveToArticleList() {
        val repository = materialRepository ?: run {
            showMessage("文章存储尚未初始化")
            return
        }
        val state = _uiState.value
        if (state.sentences.isEmpty()) {
            showMessage("请先自动断句")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveManualArticle(
                    title = state.articleTitle,
                    language = if (state.language == LearningLanguage.ENGLISH) MaterialLanguage.ENGLISH else MaterialLanguage.CANTONESE,
                    sentenceTexts = state.sentences.map { it.text },
                )
            }.onSuccess {
                showMessage("已保存到文章列表")
            }.onFailure { showMessage(it.message ?: "保存文章失败") }
        }
    }

    fun onPlaybackModeChange(mode: PlaybackMode) {
        _uiState.update { it.copy(playbackMode = mode) }
    }

    fun onSpeedChange(speed: Float) {
        val normalized = ((speed.coerceIn(0.5f, 2.0f) * 10).roundToInt() / 10f)
        _uiState.update { it.copy(speed = normalized) }
    }

    fun onSpeedChangeFinished() {
        val state = _uiState.value
        if ((state.playbackStatus == PlaybackStatus.PLAYING || state.playbackStatus == PlaybackStatus.PREPARING) &&
            state.selectedIndex >= 0
        ) {
            startSpeaking(state.selectedIndex, 0)
        }
    }

    fun playOrPause() {
        when (_uiState.value.playbackStatus) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PREPARING -> stopPlayback()
            PlaybackStatus.PAUSED -> {
                val state = _uiState.value
                if (speechController.resume()) {
                    _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
                } else startSpeaking(state.selectedIndex, 0)
            }
            PlaybackStatus.IDLE -> {
                val state = _uiState.value
                val index = if (state.selectedIndex >= 0) state.selectedIndex else 0
                startSpeaking(index, startOffset = 0)
            }
        }
    }

    fun previousSentence() = navigateBy(-1)

    fun nextSentence() = navigateBy(1)

    fun selectAndPlay(index: Int) {
        if (index !in _uiState.value.sentences.indices) return
        stopPlayback()
        _uiState.update { it.copy(selectedIndex = index, characterOffset = 0) }
        startSpeaking(index, startOffset = 0)
    }

    fun updateSentence(id: Long, editedText: String): Boolean {
        val normalized = editedText.trim()
        if (normalized.isEmpty()) {
            showMessage("句子不能为空")
            return false
        }

        val index = _uiState.value.sentences.indexOfFirst { it.id == id }
        if (index < 0) return false
        stopPlayback()
        _uiState.update { state ->
            state.copy(
                sentences = state.sentences.toMutableList().apply {
                    this[index] = this[index].copy(text = normalized)
                },
                selectedIndex = index,
                characterOffset = 0,
            )
        }
        return true
    }

    fun splitSentence(id: Long, editedText: String, cursorPosition: Int): Boolean {
        val index = _uiState.value.sentences.indexOfFirst { it.id == id }
        if (index < 0) return false

        val safeCursor = cursorPosition.coerceIn(0, editedText.length)
        val first = editedText.substring(0, safeCursor).trim()
        val second = editedText.substring(safeCursor).trim()
        if (first.isEmpty() || second.isEmpty()) {
            showMessage("请把光标放在句子中间，再进行拆分")
            return false
        }

        stopPlayback()
        _uiState.update { state ->
            val original = state.sentences[index]
            val updated = state.sentences.toMutableList().apply {
                this[index] = original.copy(text = first)
                add(index + 1, SentenceItem(nextManualSentenceId--, second))
            }
            state.copy(
                sentences = updated,
                selectedIndex = index,
                characterOffset = 0,
            )
        }
        return true
    }

    fun mergeWithNext(id: Long): Boolean {
        val state = _uiState.value
        val index = state.sentences.indexOfFirst { it.id == id }
        if (index < 0 || index >= state.sentences.lastIndex) return false

        val separator = if (state.language == LearningLanguage.ENGLISH) " " else ""
        val mergedText = state.sentences[index].text.trimEnd() + separator +
            state.sentences[index + 1].text.trimStart()
        val selectedIndex = when {
            state.selectedIndex == index + 1 -> index
            state.selectedIndex > index + 1 -> state.selectedIndex - 1
            else -> state.selectedIndex
        }

        stopPlayback()
        _uiState.update { current ->
            current.copy(
                sentences = current.sentences.toMutableList().apply {
                    this[index] = this[index].copy(text = mergedText)
                    removeAt(index + 1)
                },
                selectedIndex = selectedIndex,
                characterOffset = 0,
            )
        }
        return true
    }

    fun refreshTtsAvailability() {
        val availability = speechController.checkAvailability(_uiState.value.language.toSpeechLanguage())
        if (availability != TtsAvailability.READY &&
            _uiState.value.playbackStatus in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)
        ) {
            stopPlayback()
        }
        _uiState.update { it.copy(ttsAvailability = availability) }
    }

    fun onAppBackgrounded() {
        if (_uiState.value.playbackStatus != PlaybackStatus.IDLE) stopPlayback()
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        if (shutdownSpeechControllerOnClear) speechController.shutdown()
        super.onCleared()
    }

    private fun pause() {
        if (speechController.pause()) {
            _uiState.update { it.copy(playbackStatus = PlaybackStatus.PAUSED) }
        } else stopPlayback()
    }

    private fun navigateBy(delta: Int) {
        val state = _uiState.value
        if (state.sentences.isEmpty()) return
        val currentIndex = state.selectedIndex.coerceAtLeast(0)
        val newIndex = (currentIndex + delta).coerceIn(state.sentences.indices)
        val wasPlaying = state.playbackStatus == PlaybackStatus.PLAYING ||
            state.playbackStatus == PlaybackStatus.PREPARING
        val wasPaused = state.playbackStatus == PlaybackStatus.PAUSED

        activeRequestId = null
        speechController.stop()
        _uiState.update {
            it.copy(
                selectedIndex = newIndex,
                characterOffset = 0,
                playbackStatus = if (wasPaused) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
            )
        }
        if (wasPlaying) startSpeaking(newIndex, startOffset = 0)
    }

    private fun startSpeaking(index: Int, startOffset: Int) {
        val state = _uiState.value
        if (state.sentences.isEmpty() || index !in state.sentences.indices) {
            showMessage("请先完成断句")
            return
        }

        val availability = speechController.checkAvailability(state.language.toSpeechLanguage())
        if (availability != TtsAvailability.READY) {
            _uiState.update {
                it.copy(
                    ttsAvailability = availability,
                    playbackStatus = PlaybackStatus.IDLE,
                    userMessage = availabilityMessage(availability),
                )
            }
            return
        }

        val sentence = state.sentences[index]
        val safeOffset = startOffset.coerceIn(0, (sentence.text.length - 1).coerceAtLeast(0))
        val requestId = requestIds.getAndIncrement()
        activeRequestId = requestId
        _uiState.update {
            it.copy(
                selectedIndex = index,
                characterOffset = safeOffset,
                playbackStatus = PlaybackStatus.PREPARING,
                ttsAvailability = TtsAvailability.READY,
                userMessage = null,
            )
        }

        val accepted = speechController.speak(
            requestId = requestId,
            text = sentence.text,
            language = state.language.toSpeechLanguage(),
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
            val upcoming = state.sentences.drop(index + 1).take(DEFAULT_PRELOAD_SENTENCES)
            if (upcoming.isNotEmpty()) {
                viewModelScope.launch {
                    upcoming.forEach {
                        speechController.preload(it.text, state.language.toSpeechLanguage(), state.speed)
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        activeRequestId = null
        speechController.stop()
        _uiState.update {
            it.copy(
                playbackStatus = PlaybackStatus.IDLE,
                characterOffset = 0,
            )
        }
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Initialized -> {
                if (event.successful) {
                    refreshTtsAvailability()
                } else {
                    _uiState.update {
                        it.copy(
                            ttsAvailability = TtsAvailability.ERROR,
                            userMessage = "MiniMax语音初始化失败",
                        )
                    }
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
                val nextIndex = state.selectedIndex + 1
                if (state.playbackMode == PlaybackMode.CONTINUOUS &&
                    nextIndex in state.sentences.indices
                ) {
                    startSpeaking(nextIndex, startOffset = 0)
                } else {
                    _uiState.update {
                        it.copy(playbackStatus = PlaybackStatus.IDLE, characterOffset = 0)
                    }
                }
            }
            is SpeechEvent.Error -> if (event.requestId == null || event.requestId == activeRequestId) {
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

    private fun showMessage(message: String) {
        _uiState.update { it.copy(userMessage = message) }
    }

    private fun availabilityMessage(availability: TtsAvailability): String = when (availability) {
        TtsAvailability.INITIALIZING -> "MiniMax语音正在初始化，请稍候"
        TtsAvailability.MISSING_DATA -> "请先在设置中填写MiniMax API Key"
        TtsAvailability.UNSUPPORTED -> "MiniMax语音配置不受支持"
        TtsAvailability.ERROR -> "MiniMax语音不可用"
        TtsAvailability.READY -> ""
    }

    class Factory private constructor(
        private val controller: SpeechController,
        private val sharedController: Boolean,
        private val repository: MaterialRepository?,
    ) : ViewModelProvider.Factory {
        constructor(container: AppContainer) : this(container.speechController, true, container.materialRepository)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReaderViewModel::class.java))
            return ReaderViewModel(
                speechController = controller,
                shutdownSpeechControllerOnClear = !sharedController,
                materialRepository = repository,
            ) as T
        }
    }

    private companion object {
        const val DEFAULT_PRELOAD_SENTENCES = 3
    }
}
