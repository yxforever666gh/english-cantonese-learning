package com.example.englishcantoneselearning.ui

import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var speechController: FakeSpeechController
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        speechController = FakeSpeechController()
        viewModel = ReaderViewModel(speechController)
    }

    @Test
    fun continuousPlaybackAdvancesUntilLastSentence() = runTest {
        prepare("One. Two.")
        viewModel.playOrPause()

        val first = speechController.spoken.single()
        speechController.emit(SpeechEvent.Done(first.requestId))

        assertEquals(2, speechController.spoken.size)
        assertEquals("Two.", speechController.spoken.last().text)
        assertEquals(1, viewModel.uiState.value.selectedIndex)
        assertEquals(PlaybackStatus.PREPARING, viewModel.uiState.value.playbackStatus)
        speechController.emit(SpeechEvent.Started(speechController.spoken.last().requestId))
        assertEquals(PlaybackStatus.PLAYING, viewModel.uiState.value.playbackStatus)

        speechController.emit(SpeechEvent.Done(speechController.spoken.last().requestId))
        assertEquals(PlaybackStatus.IDLE, viewModel.uiState.value.playbackStatus)
    }

    @Test
    fun playbackPreloadsExactlyNextThreeSentences() {
        prepare("One. Two. Three. Four. Five.")

        viewModel.playOrPause()

        assertEquals(listOf("Two.", "Three.", "Four."), speechController.preloaded)
    }

    @Test
    fun singleModeStopsAfterCurrentSentence() = runTest {
        prepare("One. Two.")
        viewModel.onPlaybackModeChange(PlaybackMode.SINGLE)
        viewModel.playOrPause()

        speechController.emit(SpeechEvent.Done(speechController.spoken.single().requestId))

        assertEquals(1, speechController.spoken.size)
        assertEquals(0, viewModel.uiState.value.selectedIndex)
        assertEquals(PlaybackStatus.IDLE, viewModel.uiState.value.playbackStatus)
    }

    @Test
    fun pauseAndResumeKeepThePreparedCloudAudio() = runTest {
        prepare("Read this sentence.")
        viewModel.playOrPause()
        val firstRequest = speechController.spoken.single().requestId
        speechController.emit(SpeechEvent.Started(firstRequest))

        viewModel.playOrPause()
        assertEquals(PlaybackStatus.PAUSED, viewModel.uiState.value.playbackStatus)
        assertEquals(1, speechController.spoken.size)

        viewModel.playOrPause()
        assertEquals(1, speechController.spoken.size)
        assertEquals(PlaybackStatus.PLAYING, viewModel.uiState.value.playbackStatus)
    }

    @Test
    fun staleCompletionAfterNavigationIsIgnored() = runTest {
        prepare("One. Two. Three.")
        viewModel.playOrPause()
        val staleRequest = speechController.spoken.single().requestId

        viewModel.nextSentence()
        assertEquals(2, speechController.spoken.size)
        assertEquals(1, viewModel.uiState.value.selectedIndex)

        speechController.emit(SpeechEvent.Done(staleRequest))
        assertEquals(2, speechController.spoken.size)
        assertEquals(1, viewModel.uiState.value.selectedIndex)
    }

    @Test
    fun editSplitAndMergeUpdateSentenceListAndStopPlayback() {
        prepare("One. Two.")
        viewModel.playOrPause()
        val firstId = viewModel.uiState.value.sentences.first().id

        assertTrue(viewModel.updateSentence(firstId, "One edited."))
        assertEquals(PlaybackStatus.IDLE, viewModel.uiState.value.playbackStatus)
        assertEquals("One edited.", viewModel.uiState.value.sentences.first().text)

        assertTrue(viewModel.splitSentence(firstId, "One. Extra.", cursorPosition = 5))
        assertEquals(listOf("One.", "Extra.", "Two."), viewModel.uiState.value.sentences.map { it.text })

        assertTrue(viewModel.mergeWithNext(firstId))
        assertEquals(listOf("One. Extra.", "Two."), viewModel.uiState.value.sentences.map { it.text })
    }

    @Test
    fun missingVoicePreventsPlaybackAndKeepsLanguageSpecificError() {
        speechController.availability = TtsAvailability.MISSING_DATA
        viewModel.onLanguageChange(LearningLanguage.CANTONESE)
        prepare("你好。")

        viewModel.playOrPause()

        assertTrue(speechController.spoken.isEmpty())
        assertEquals(TtsAvailability.MISSING_DATA, viewModel.uiState.value.ttsAvailability)
        assertFalse(viewModel.uiState.value.userMessage.isNullOrBlank())
    }

    private fun prepare(text: String) {
        viewModel.onArticleTextChange(text)
        viewModel.segmentArticle()
    }
}

private class FakeSpeechController : SpeechController {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SpeechEvent> = mutableEvents
    var availability: TtsAvailability = TtsAvailability.READY
    val spoken = mutableListOf<SpokenRequest>()
    val preloaded = mutableListOf<String>()
    var stopCount = 0
    var shutdownCalled = false
    var paused = false

    override fun checkAvailability(language: SpeechLanguage): TtsAvailability = availability

    override fun speak(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        speed: Float,
        startOffset: Int,
    ): Boolean {
        spoken += SpokenRequest(requestId, text, language, speed, startOffset)
        return true
    }

    override suspend fun preload(text: String, language: SpeechLanguage, speed: Float): Boolean {
        preloaded += text
        return true
    }

    override fun stop() {
        stopCount++
        paused = false
    }

    override fun pause(): Boolean {
        paused = true
        return true
    }

    override fun resume(): Boolean {
        val result = paused
        paused = false
        return result
    }

    override fun shutdown() {
        shutdownCalled = true
    }

    suspend fun emit(event: SpeechEvent) {
        mutableEvents.emit(event)
    }
}

private data class SpokenRequest(
    val requestId: Long,
    val text: String,
    val language: SpeechLanguage,
    val speed: Float,
    val startOffset: Int,
)
