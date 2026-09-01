package com.example.englishcantoneselearning.ui

import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.preferences.SpeechSpeedPreferences
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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

    @Test
    fun languageSpeedsAreIndependentAndDefaultToPointEight() {
        assertEquals(0.8f, viewModel.uiState.value.speed)

        viewModel.onSpeedChange(1.2f)
        viewModel.onLanguageChange(LearningLanguage.CANTONESE)
        assertEquals(0.8f, viewModel.uiState.value.speed)

        viewModel.onSpeedChange(0.6f)
        viewModel.onLanguageChange(LearningLanguage.ENGLISH)
        assertEquals(1.2f, viewModel.uiState.value.speed)
    }

    @Test
    fun sharedPreferencesSynchronizeSpeedAcrossReaderInstances() = runTest {
        val preferences = FakeLearnerPreferences()
        val first = ReaderViewModel(FakeSpeechController(), userPreferences = preferences)
        val second = ReaderViewModel(FakeSpeechController(), userPreferences = preferences)

        first.onSpeedChange(1.4f)

        assertEquals(1.4f, second.uiState.value.speed)
        assertEquals(1.4f, preferences.speechSpeed(SpeechLanguage.ENGLISH_US))
    }

    @Test
    fun finishingSpeedChangeRestartsPlayingSentenceFromBeginning() {
        prepare("Read this sentence.")
        viewModel.playOrPause()

        viewModel.onSpeedChange(1.3f)
        viewModel.onSpeedChangeFinished()

        assertEquals(2, speechController.spoken.size)
        assertEquals(1.3f, speechController.spoken.last().speed)
        assertEquals(0, speechController.spoken.last().startOffset)
    }

    @Test
    fun readingFontSizeIsClampedAndSynchronizedAcrossReaderInstances() = runTest {
        val preferences = FakeLearnerPreferences()
        val first = ReaderViewModel(FakeSpeechController(), userPreferences = preferences)
        val second = ReaderViewModel(FakeSpeechController(), userPreferences = preferences)

        first.onReadingFontSizeChange(40)

        assertEquals(32, first.uiState.value.readingFontSizeSp)
        assertEquals(32, second.uiState.value.readingFontSizeSp)
        assertEquals(32, preferences.readingFontSizeSp.value)

        second.onReadingFontSizeChange(8)
        assertEquals(12, first.uiState.value.readingFontSizeSp)
        assertEquals(12, second.uiState.value.readingFontSizeSp)
    }

    private fun prepare(text: String) {
        viewModel.onArticleTextChange(text)
        viewModel.segmentArticle()
    }
}

private class FakeLearnerPreferences : LearnerPreferences {
    private val mutableSpeeds = MutableStateFlow(SpeechSpeedPreferences())
    private val mutableReadingFontSize = MutableStateFlow(16)
    override val speechSpeeds: StateFlow<SpeechSpeedPreferences> = mutableSpeeds
    override val readingFontSizeSp: StateFlow<Int> = mutableReadingFontSize
    private var band = 6.0f
    private var libraryLanguage = MaterialLanguage.ENGLISH

    override fun learnerProfile() = LearnerProfile(band)

    override fun setListeningBand(band: Float) {
        this.band = band
    }

    override fun articleLibraryLanguage(): MaterialLanguage = libraryLanguage

    override fun setArticleLibraryLanguage(language: MaterialLanguage) {
        libraryLanguage = language
    }

    override fun speechSpeed(language: SpeechLanguage): Float = mutableSpeeds.value.forLanguage(language)

    override fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        mutableSpeeds.value = mutableSpeeds.value.withSpeed(language, speed)
    }

    override fun setReadingFontSizeSp(sizeSp: Int) {
        mutableReadingFontSize.value = sizeSp.coerceIn(12, 32)
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
