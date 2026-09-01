package com.example.englishcantoneselearning.ui.material

import com.example.englishcantoneselearning.data.network.GeneratedBatch
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.preferences.SpeechSpeedPreferences
import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxConfigStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxVoiceCatalogStore
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MiniMaxVoiceCatalog
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceReference
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import com.example.englishcantoneselearning.speech.SpeechAudioCache
import com.example.englishcantoneselearning.speech.MiniMaxVoiceService
import java.io.File
import com.example.englishcantoneselearning.ui.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MaterialViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeMaterialRepository
    private lateinit var speech: FakeMaterialSpeechController
    private lateinit var configStore: FakeServiceConfigStore
    private lateinit var preferences: FakeLearnerPreferences
    private lateinit var viewModel: MaterialViewModel

    @Before
    fun setUp() {
        repository = FakeMaterialRepository(mutableListOf(material()))
        speech = FakeMaterialSpeechController()
        configStore = FakeServiceConfigStore()
        preferences = FakeLearnerPreferences()
        viewModel = MaterialViewModel(
            repository = repository,
            generator = FakeGenerator(),
            providerStore = configStore,
            miniMaxConfigStore = configStore,
            voiceCatalogStore = configStore,
            voiceService = FakeVoiceService(),
            userPreferences = preferences,
            speechController = speech,
            audioCache = FakeAudioCache(),
        )
    }

    @Test
    fun continuousPlaybackAlternatesTargetMandarinThenNextTarget() = runTest {
        viewModel.openMaterial("material-1")
        viewModel.selectAndPlaySentence(0)

        assertEquals(SpeechLanguage.ENGLISH_US, speech.spoken[0].language)
        assertEquals("First English sentence.", speech.spoken[0].text)

        speech.emit(SpeechEvent.Done(speech.spoken[0].requestId))
        assertEquals(SpeechLanguage.MANDARIN_CN, speech.spoken[1].language)
        assertEquals("第一句中文。", speech.spoken[1].text)

        speech.emit(SpeechEvent.Done(speech.spoken[1].requestId))
        assertEquals(SpeechLanguage.ENGLISH_US, speech.spoken[2].language)
        assertEquals("Second English sentence.", speech.spoken[2].text)
        assertEquals(1, viewModel.uiState.value.selectedSentenceIndex)
    }

    @Test
    fun singleModeStopsAfterOneBilingualPair() = runTest {
        viewModel.openMaterial("material-1")
        viewModel.setPlaybackMode(PlaybackMode.SINGLE)
        viewModel.selectAndPlaySentence(0)

        speech.emit(SpeechEvent.Done(speech.spoken[0].requestId))
        speech.emit(SpeechEvent.Done(speech.spoken[1].requestId))

        assertEquals(2, speech.spoken.size)
        assertEquals(PlaybackStatus.IDLE, viewModel.uiState.value.playbackStatus)
        assertEquals(0, viewModel.uiState.value.selectedSentenceIndex)
    }

    @Test
    fun staleCompletionAfterJumpIsIgnored() = runTest {
        viewModel.openMaterial("material-1")
        viewModel.selectAndPlaySentence(0)
        val stale = speech.spoken.single().requestId

        viewModel.nextSentence()
        val countAfterJump = speech.spoken.size
        speech.emit(SpeechEvent.Done(stale))

        assertEquals(countAfterJump, speech.spoken.size)
        assertEquals(1, viewModel.uiState.value.selectedSentenceIndex)
    }

    @Test
    fun eitherMissingVoiceDisablesBilingualPlayback() {
        speech.availability[SpeechLanguage.MANDARIN_CN] = TtsAvailability.MISSING_DATA
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(0)

        assertTrue(speech.spoken.isEmpty())
        assertEquals(PlaybackStatus.IDLE, viewModel.uiState.value.playbackStatus)
        assertTrue(viewModel.uiState.value.userMessage.orEmpty().contains("MiniMax"))
    }

    @Test
    fun generationSavesExactlyOneReturnedBatchAndReloadsHistory() {
        repository.generated = listOf(material("generated-1"))

        viewModel.generateNewBatch()

        assertEquals(1, repository.generateCalls)
        assertEquals(1, viewModel.uiState.value.materials.size)
        assertEquals("generated-1", viewModel.uiState.value.selectedMaterial?.id)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun clearMiniMaxKeyRemovesCredentialWithoutExposingItsValue() {
        viewModel.clearMiniMaxKey()

        assertEquals("", configStore.miniMax.apiKey)
        assertTrue(viewModel.uiState.value.miniMaxConfig.apiKey.isBlank())
    }

    @Test
    fun threeLanguagesPersistIndependentVoiceSelections() {
        viewModel.selectVoice(SpeechLanguage.ENGLISH_US, "English_Graceful_Lady")
        viewModel.selectVoice(SpeechLanguage.CANTONESE_HK, "Cantonese_KindWoman")
        viewModel.selectVoice(SpeechLanguage.MANDARIN_CN, "Chinese (Mandarin)_News_Anchor")

        assertEquals("English_Graceful_Lady", configStore.miniMax.englishVoice)
        assertEquals("Cantonese_KindWoman", configStore.miniMax.cantoneseVoice)
        assertEquals("Chinese (Mandarin)_News_Anchor", configStore.miniMax.mandarinVoice)
    }

    @Test
    fun customVoiceFavoritesRemainStoredButCannotBecomeCurrentVoice() {
        val favorite = CustomVoiceFavorite(
            id = "favorite-1",
            displayName = "My voice",
            voiceId = "custom-voice-001",
            languages = setOf(SpeechLanguage.ENGLISH_US, SpeechLanguage.CANTONESE_HK),
        )
        assertTrue(viewModel.saveCustomVoice(favorite))
        assertEquals(1, configStore.voiceFavorites.size)
        assertFalse(viewModel.saveCustomVoice(favorite.copy(id = "favorite-2")))

        viewModel.selectVoice(SpeechLanguage.ENGLISH_US, favorite.voiceId)

        assertEquals(1, configStore.voiceFavorites.size)
        assertEquals("Serene_Woman", configStore.miniMax.englishVoice)
        assertTrue(viewModel.uiState.value.userMessage.orEmpty().contains("官方系统音色"))
    }

    @Test
    fun excludedEnglishAccentCannotBecomeCurrentVoice() {
        viewModel.selectVoice(SpeechLanguage.ENGLISH_US, "English_Aussie_Bloke")

        assertEquals("Serene_Woman", configStore.miniMax.englishVoice)
        assertTrue(viewModel.uiState.value.userMessage.orEmpty().contains("官方系统音色"))
    }

    @Test
    fun previewUsesExplicitVoiceAndLanguageSpecificSample() {
        viewModel.previewVoice("Cantonese_KindWoman", SpeechLanguage.CANTONESE_HK)

        val preview = speech.previewed.single()
        assertEquals("Cantonese_KindWoman", preview.voiceId)
        assertEquals(SpeechLanguage.CANTONESE_HK, preview.language)
        assertTrue(preview.text.contains("廣東話"))
    }

    @Test
    fun threeSpeechSpeedsAreClampedToSupportedRange() {
        viewModel.setSpeechSpeed(SpeechLanguage.ENGLISH_US, 9f)
        viewModel.setSpeechSpeed(SpeechLanguage.CANTONESE_HK, 0.1f)
        viewModel.setSpeechSpeed(SpeechLanguage.MANDARIN_CN, 1.3f)

        assertEquals(2.0f, viewModel.uiState.value.englishSpeed)
        assertEquals(0.5f, viewModel.uiState.value.cantoneseSpeed)
        assertEquals(1.3f, viewModel.uiState.value.mandarinSpeed)
    }

    @Test
    fun observedGlobalSpeechSpeedUpdatesMaterialState() {
        preferences.setSpeechSpeed(SpeechLanguage.ENGLISH_US, 1.4f)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.4f, viewModel.uiState.value.englishSpeed)
        assertEquals(0.8f, viewModel.uiState.value.cantoneseSpeed)
        assertEquals(0.8f, viewModel.uiState.value.mandarinSpeed)
    }

    @Test
    fun finishingTargetSpeedChangeRestartsActiveSegmentFromBeginning() = runTest {
        viewModel.openMaterial("material-1")
        viewModel.selectAndPlaySentence(0)
        speech.emit(SpeechEvent.Started(speech.spoken.single().requestId))

        viewModel.setSpeechSpeed(SpeechLanguage.ENGLISH_US, 1.2f)
        viewModel.onSpeechSpeedChangeFinished(SpeechLanguage.ENGLISH_US)

        assertEquals(2, speech.spoken.size)
        assertEquals(1.2f, speech.spoken.last().speed)
        assertEquals(0, speech.spoken.last().startOffset)
    }

    @Test
    fun listeningBandIsRoundedClampedAndPersisted() {
        viewModel.setListeningBand(6.26f)
        assertEquals(6.5f, viewModel.uiState.value.listeningBand)
        assertEquals(6.5f, preferences.learnerProfile().listeningBand)

        viewModel.setListeningBand(12f)
        assertEquals(9f, viewModel.uiState.value.listeningBand)
        assertEquals(9f, preferences.learnerProfile().listeningBand)
    }

    @Test
    fun libraryLanguageIsIndependentAndPersisted() {
        assertEquals(MaterialLanguage.ENGLISH, viewModel.uiState.value.libraryLanguage)

        viewModel.setLibraryLanguage(MaterialLanguage.CANTONESE)

        assertEquals(MaterialLanguage.CANTONESE, viewModel.uiState.value.libraryLanguage)
        assertEquals(MaterialLanguage.CANTONESE, preferences.libraryLanguage)
        assertEquals(MaterialLanguage.ENGLISH, viewModel.uiState.value.language)
    }

    @Test
    fun playbackPreloadsCurrentTranslationAndNextTwoBilingualSentences() {
        val longer = material().copy(
            sentences = (0..4).map { index ->
                BilingualSentence("material-1:$index", "English $index.", null, "中文$index。")
            },
        )
        repository.replaceWith(longer)
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(0)

        assertEquals(
            listOf("中文0。", "English 1.", "中文1。", "English 2.", "中文2。"),
            speech.preloaded.map { it.text },
        )
        assertEquals(
            listOf(
                SpeechLanguage.MANDARIN_CN,
                SpeechLanguage.ENGLISH_US,
                SpeechLanguage.MANDARIN_CN,
                SpeechLanguage.ENGLISH_US,
                SpeechLanguage.MANDARIN_CN,
            ),
            speech.preloaded.map { it.language },
        )
    }

    @Test
    fun preloadWindowIsClippedAtArticleEnd() {
        val longer = material().copy(
            sentences = (0..4).map { index ->
                BilingualSentence("material-1:$index", "English $index.", null, "中文$index。")
            },
        )
        repository.replaceWith(longer)
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(4)

        assertEquals(listOf("中文4。"), speech.preloaded.map { it.text })
    }

    @Test
    fun preloadWindowSkipsBlankTranslationsAndManualArticleTranslations() {
        val aiMaterial = material().copy(
            sentences = listOf(
                BilingualSentence("material-1:0", "English 0.", null, " "),
                BilingualSentence("material-1:1", "English 1.", null, null),
                BilingualSentence("material-1:2", "English 2.", null, "中文2。"),
            ),
        )
        repository.replaceWith(aiMaterial)
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(0)

        assertEquals(
            listOf("English 1.", "English 2.", "中文2。"),
            speech.preloaded.map { it.text },
        )

        speech.preloaded.clear()
        val manual = aiMaterial.copy(origin = ArticleOrigin.MANUAL_PASTE)
        repository.replaceWith(manual)
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(0)

        assertEquals(listOf("English 1.", "English 2."), speech.preloaded.map { it.text })
    }

    @Test
    fun onePreloadFailureDoesNotCancelTheRestOrShowPlaybackError() {
        val longer = material().copy(
            sentences = (0..2).map { index ->
                BilingualSentence("material-1:$index", "English $index.", null, "中文$index。")
            },
        )
        repository.replaceWith(longer)
        speech.preloadReturnsFalse += "English 1."
        speech.preloadThrows += "中文1。"
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(0)

        assertEquals(
            listOf("中文0。", "English 1.", "中文1。", "English 2.", "中文2。"),
            speech.preloaded.map { it.text },
        )
        assertEquals(PlaybackStatus.PREPARING, viewModel.uiState.value.playbackStatus)
        assertEquals(null, viewModel.uiState.value.userMessage)
    }

    @Test
    fun overlappingPreloadWindowsShareFourRequestConcurrencyLimit() {
        val longer = material().copy(
            sentences = (0..4).map { index ->
                BilingualSentence("material-1:$index", "English $index.", null, "中文$index。")
            },
        )
        repository.replaceWith(longer)
        speech.holdPreloads = true
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        viewModel.selectAndPlaySentence(0)
        viewModel.nextSentence()

        assertEquals(4, speech.activePreloads)
        assertEquals(4, speech.maxActivePreloads)
        assertEquals(4, speech.preloaded.size)

        speech.releasePreloads()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, speech.maxActivePreloads)
        assertTrue(speech.preloaded.size > 4)
    }

    @Test
    fun selectedArticlesCanBeCachedAndDeletedTogether() {
        viewModel.toggleLibraryArticleSelection("material-1")
        viewModel.cacheSelectedLibraryArticles()

        assertEquals(4, speech.preloaded.size)
        assertFalse(viewModel.uiState.value.isAudioCaching)
        assertTrue(viewModel.uiState.value.userMessage.orEmpty().contains("已缓存"))

        viewModel.deleteSelectedLibraryArticles()

        assertTrue(viewModel.uiState.value.materials.isEmpty())
        assertTrue(viewModel.uiState.value.librarySelectedArticleIds.isEmpty())
    }

    @Test
    fun cancelGenerationLeavesIdleStateWithoutAutomaticRetry() {
        repository.waitForCancellation = true

        viewModel.generateNewBatch()
        assertTrue(viewModel.uiState.value.isGenerating)
        viewModel.cancelGeneration()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals(1, repository.generateCalls)
        assertTrue(viewModel.uiState.value.userMessage.orEmpty().contains("已取消"))
    }

    @Test
    fun bilingualSentenceCountsOnlyAfterTargetAndMandarinBothFinish() = runTest {
        viewModel.openMaterial("material-1")
        viewModel.setPlaybackMode(PlaybackMode.SINGLE)
        viewModel.selectAndPlaySentence(0)

        speech.emit(SpeechEvent.Done(speech.spoken[0].requestId))
        assertTrue(viewModel.uiState.value.playbackProgress["material-1"]
            ?.completedSentenceIndices.orEmpty().isEmpty())

        speech.emit(SpeechEvent.Done(speech.spoken[1].requestId))
        val progress = viewModel.uiState.value.playbackProgress.getValue("material-1")
        assertEquals(setOf(0), progress.completedSentenceIndices)
        assertEquals(50, progress.percent(2))
    }

    @Test
    fun jumpingForwardSavesResumeButDoesNotClaimSkippedSentence() {
        viewModel.openMaterial("material-1")
        viewModel.nextSentence()

        val progress = viewModel.uiState.value.playbackProgress.getValue("material-1")
        assertEquals(1, progress.resumeSentenceIndex)
        assertTrue(progress.completedSentenceIndices.isEmpty())
    }

    @Test
    fun persistedResumePositionIsUsedWhenArticleReopens() {
        repository.progress["material-1"] = MaterialPlaybackProgress("material-1", resumeSentenceIndex = 1)
        viewModel.reloadMaterials()
        viewModel.openMaterial("material-1")

        assertEquals(1, viewModel.uiState.value.selectedSentenceIndex)
    }

    @Test
    fun manualArticleCountsAfterTargetAudioOnly() = runTest {
        val manual = material("manual").copy(
            origin = ArticleOrigin.MANUAL_PASTE,
            sentences = listOf(BilingualSentence("manual:0", "Manual sentence.", null, null)),
        )
        repository.replaceWith(manual)
        viewModel.reloadMaterials()
        viewModel.openMaterial("manual")
        viewModel.selectAndPlaySentence(0)

        speech.emit(SpeechEvent.Done(speech.spoken.last().requestId))

        assertTrue(viewModel.uiState.value.playbackProgress.getValue("manual").completed)
        assertEquals(1, speech.spoken.count { it.text == "Manual sentence." })
    }

    private fun material(id: String = "material-1") = PracticeMaterial(
        id = id,
        batchId = "batch-1",
        batchPosition = 0,
        language = MaterialLanguage.ENGLISH,
        difficulty = Difficulty.TARGET,
        topic = "日常",
        title = "Practice material",
        targetText = "First English sentence. Second English sentence.",
        sentences = listOf(
            BilingualSentence("$id:0", "First English sentence.", null, "第一句中文。"),
            BilingualSentence("$id:1", "Second English sentence.", null, "第二句中文。"),
        ),
        sources = listOf(SourceReference("Source", "Publisher", "https://example.com", null, "English")),
        createdAt = 1L,
        promptVersion = "listening-material-v1",
        providerName = "Wawa",
        model = "gpt-5.6-sol",
        responseId = "resp",
        inputTokens = 1,
        outputTokens = 2,
        requestFingerprint = "fingerprint-$id",
    )
}

private class FakeMaterialRepository(
    private val stored: MutableList<PracticeMaterial>,
) : MaterialRepository {
    var generated: List<PracticeMaterial> = emptyList()
    var generateCalls = 0
    var waitForCancellation = false
    val progress = mutableMapOf<String, MaterialPlaybackProgress>()

    fun replaceWith(material: PracticeMaterial) {
        stored.clear()
        stored += material
    }

    override suspend fun listMaterials(): List<PracticeMaterial> = stored.toList()
    override suspend fun getMaterial(id: String): PracticeMaterial? = stored.firstOrNull { it.id == id }
    override suspend fun recentSourceUrls(limit: Int): List<String> = emptyList()
    override suspend fun generate(request: MaterialGenerationRequest): List<PracticeMaterial> {
        generateCalls++
        if (waitForCancellation) awaitCancellation()
        stored.clear()
        stored += generated
        return generated
    }
    override suspend fun deleteMaterial(id: String) {
        stored.removeAll { it.id == id }
    }
    override suspend fun deleteBatch(batchId: String) {
        stored.removeAll { it.batchId == batchId }
    }
    override suspend fun playbackProgress(): Map<String, MaterialPlaybackProgress> = progress.toMap()
    override suspend fun savePlaybackProgress(progress: MaterialPlaybackProgress) {
        this.progress[progress.materialId] = progress
    }
    override suspend fun clearPlaybackProgress(materialId: String) {
        progress.remove(materialId)
    }
}

private class FakeLearnerPreferences : LearnerPreferences {
    private var profile = LearnerProfile()
    var libraryLanguage = MaterialLanguage.ENGLISH
    private val mutableSpeeds = MutableStateFlow(SpeechSpeedPreferences())
    override val speechSpeeds: StateFlow<SpeechSpeedPreferences> = mutableSpeeds
    override fun learnerProfile(): LearnerProfile = profile
    override fun setListeningBand(band: Float) {
        profile = profile.copy(listeningBand = band)
    }
    override fun articleLibraryLanguage(): MaterialLanguage = libraryLanguage
    override fun setArticleLibraryLanguage(language: MaterialLanguage) {
        libraryLanguage = language
    }
    override fun speechSpeed(language: SpeechLanguage): Float = mutableSpeeds.value.forLanguage(language)
    override fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        mutableSpeeds.value = mutableSpeeds.value.withSpeed(language, speed.coerceIn(0.5f, 2.0f))
    }
}

private class FakeGenerator : MaterialGenerator {
    override suspend fun test(provider: MaterialProviderConfig): Boolean = true
    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch =
        error("Repository fake handles generation")
}

private class FakeServiceConfigStore : MaterialProviderStore, MiniMaxConfigStore, MiniMaxVoiceCatalogStore {
    var values = listOf(
        MaterialProviderConfig("wawa", "Wawa", "https://wawazz.xyz", "gpt-5.6-sol", "test-key"),
    )
    var miniMax = MiniMaxTtsConfig(apiKey = "mini-key")
    override fun providers(): List<MaterialProviderConfig> = values
    override fun save(providers: List<MaterialProviderConfig>) { values = providers }
    override fun config(): MiniMaxTtsConfig = miniMax
    override fun save(config: MiniMaxTtsConfig) { miniMax = config }
    var voiceCatalog: MiniMaxVoiceCatalog? = null
    var voiceFavorites = emptyList<CustomVoiceFavorite>()
    override fun catalog() = voiceCatalog
    override fun saveCatalog(catalog: MiniMaxVoiceCatalog) { voiceCatalog = catalog }
    override fun favorites() = voiceFavorites
    override fun saveFavorites(favorites: List<CustomVoiceFavorite>) { voiceFavorites = favorites }
}

private class FakeVoiceService : MiniMaxVoiceService {
    override suspend fun fetchVoices(config: MiniMaxTtsConfig) = MiniMaxVoiceCatalog(emptyList(), 1L)
}

private class FakeAudioCache : SpeechAudioCache {
    override fun get(cacheIdentity: String): File? = null
    override fun put(cacheIdentity: String, bytes: ByteArray): File = error("unused")
    override fun sizeBytes(): Long = 0
    override fun clear() = Unit
}

private class FakeMaterialSpeechController : SpeechController {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SpeechEvent> = mutableEvents
    val spoken = mutableListOf<MaterialSpokenRequest>()
    val previewed = mutableListOf<MaterialPreviewRequest>()
    val preloaded = mutableListOf<MaterialPreloadRequest>()
    val preloadReturnsFalse = mutableSetOf<String>()
    val preloadThrows = mutableSetOf<String>()
    var holdPreloads = false
    var activePreloads = 0
    var maxActivePreloads = 0
    private val preloadRelease = CompletableDeferred<Unit>()
    val availability = SpeechLanguage.entries.associateWith { TtsAvailability.READY }.toMutableMap()

    override fun checkAvailability(language: SpeechLanguage): TtsAvailability = availability.getValue(language)
    override fun speak(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        speed: Float,
        startOffset: Int,
    ): Boolean {
        spoken += MaterialSpokenRequest(requestId, text, language, speed, startOffset)
        return true
    }
    override fun preview(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        voiceId: String,
        speed: Float,
    ): Boolean {
        previewed += MaterialPreviewRequest(requestId, text, language, voiceId, speed)
        return true
    }
    override suspend fun preload(text: String, language: SpeechLanguage, speed: Float): Boolean {
        preloaded += MaterialPreloadRequest(text, language, speed)
        activePreloads++
        maxActivePreloads = maxOf(maxActivePreloads, activePreloads)
        return try {
            if (holdPreloads) preloadRelease.await()
            if (text in preloadThrows) error("Synthetic preload failure for $text")
            text !in preloadReturnsFalse
        } finally {
            activePreloads--
        }
    }
    override fun stop() = Unit
    override fun shutdown() = Unit
    suspend fun emit(event: SpeechEvent) {
        mutableEvents.emit(event)
    }
    fun releasePreloads() {
        preloadRelease.complete(Unit)
    }
}

private data class MaterialPreloadRequest(
    val text: String,
    val language: SpeechLanguage,
    val speed: Float,
)

private data class MaterialPreviewRequest(
    val requestId: Long,
    val text: String,
    val language: SpeechLanguage,
    val voiceId: String,
    val speed: Float,
)

private data class MaterialSpokenRequest(
    val requestId: Long,
    val text: String,
    val language: SpeechLanguage,
    val speed: Float,
    val startOffset: Int,
)
