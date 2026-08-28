package com.example.englishcantoneselearning.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.example.englishcantoneselearning.data.network.GeneratedBatch
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxConfigStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxVoiceCatalogStore
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MiniMaxVoiceCatalog
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceReference
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.speech.SpeechAudioCache
import com.example.englishcantoneselearning.speech.MiniMaxVoiceService
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import com.example.englishcantoneselearning.ui.material.MaterialScreen
import com.example.englishcantoneselearning.ui.material.MaterialUiState
import com.example.englishcantoneselearning.ui.material.MaterialViewModel
import com.example.englishcantoneselearning.ui.material.MaterialScreenMode
import com.example.englishcantoneselearning.ui.settings.SettingsScreen
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MaterialScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationShowsEditorialSelectedState() {
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                AppNavigationBar(AppDestination.ARTICLE_LIST, onSelect = {})
            }
        }

        composeRule.onNodeWithTag("nav_article_list").assertIsSelected()
    }

    @Test
    fun creationShowsConfigurationAndGenerationStates() {
        val dependencies = TestMaterialDependencies()
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                MaterialScreen(
                    state = MaterialUiState(),
                    viewModel = viewModel,
                    onNavigate = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("尚未配置 API 密钥").assertExists()
        composeRule.onNodeWithTag("generate_materials_button").assertIsNotEnabled()
    }

    @Test
    fun creationShowsActiveGenerationState() {
        val dependencies = TestMaterialDependencies()
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                MaterialScreen(
                    state = MaterialUiState(
                        isGenerating = true,
                        materialProviders = dependencies.configStore.values,
                    ),
                    viewModel = viewModel,
                    onNavigate = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onNodeWithText("正在生成").assertExists()
    }

    @Test
    fun settingsAcceptsAndMasksMiniMaxKey() {
        val dependencies = TestMaterialDependencies()
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                SettingsScreen(MaterialUiState(), viewModel, onNavigate = {})
            }
        }

        composeRule.onNodeWithTag("minimax_key_input").performTextInput("new-device-test-key")
        composeRule.onNodeWithTag("save_minimax_button").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals("new-device-test-key", dependencies.configStore.miniMax.apiKey)
        }
        composeRule.onNodeWithTag("settings_list").performScrollToIndex(6)
        composeRule.onNodeWithTag("ielts_listening_slider").assertExists()
    }

    @Test
    fun settingsUsesFullScreenSystemVoicePickerAndDoesNotAutoCloseAfterSelection() {
        val dependencies = TestMaterialDependencies()
        dependencies.configStore.miniMax = MiniMaxTtsConfig(apiKey = "mini-key")
        dependencies.configStore.voiceFavorites = listOf(
            CustomVoiceFavorite(
                "favorite-1",
                "Hidden custom voice",
                "custom-voice-001",
                setOf(SpeechLanguage.ENGLISH_US),
            ),
        )
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                SettingsScreen(state, viewModel, onNavigate = {})
            }
        }

        composeRule.onNodeWithTag("settings_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("choose_voice_ENGLISH_US").performClick()
        composeRule.onNodeWithTag("voice_selection_screen_ENGLISH_US").assertExists()
        composeRule.onNodeWithTag("select_voice_English_Aussie_Bloke_ENGLISH_US").assertDoesNotExist()
        composeRule.onNodeWithTag("select_voice_custom-voice-001_ENGLISH_US").assertDoesNotExist()
        composeRule.onNodeWithTag("select_voice_Attractive_Girl_ENGLISH_US").performClick()
        composeRule.runOnIdle {
            assertEquals("Attractive_Girl", dependencies.configStore.miniMax.englishVoice)
        }
        composeRule.onNodeWithTag("voice_selection_screen_ENGLISH_US").assertExists()
        composeRule.onNodeWithTag("voice_selection_back").performClick()
        composeRule.onNodeWithTag("minimax_key_input").assertExists()
        composeRule.onNodeWithTag("add_custom_voice_button").assertDoesNotExist()
    }

    @Test
    fun creationShowsLevelAndDispatchesFilters() {
        val material = englishMaterial()
        val dependencies = TestMaterialDependencies(listOf(material))
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                MaterialScreen(
                    state = MaterialUiState(
                        materials = listOf(material),
                        materialProviders = dependencies.configStore.values,
                        miniMaxConfig = dependencies.configStore.miniMax,
                        targetAvailability = TtsAvailability.READY,
                        mandarinAvailability = TtsAvailability.READY,
                    ),
                    viewModel = viewModel,
                    onNavigate = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("IELTS 听力 6.0：轻松 5.5 · 适合 6.0 · 挑战 6.5").assertExists()
        composeRule.onNodeWithTag("material_language_cantonese").performClick()
        composeRule.onNodeWithTag("difficulty_easy").performClick()
        composeRule.onNodeWithText("文化").performClick()

        composeRule.runOnIdle {
            assertEquals(MaterialLanguage.CANTONESE, viewModel.uiState.value.language)
            assertEquals(Difficulty.EASY, viewModel.uiState.value.difficulty)
            assertEquals(MaterialTopic.CULTURE, viewModel.uiState.value.topic)
        }
    }

    @Test
    fun articleListFiltersLanguagesAndLongPressEntersBatchEdit() {
        val ai = englishMaterial()
        val manual = ai.copy(id = "manual", title = "Manual saved article",
            origin = com.example.englishcantoneselearning.model.ArticleOrigin.MANUAL_PASTE)
        val cantonese = cantoneseMaterial()
        val dependencies = TestMaterialDependencies(listOf(ai, manual, cantonese))
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                val state by viewModel.uiState.collectAsState()
                MaterialScreen(
                    state = state,
                    viewModel = viewModel,
                    onNavigate = {},
                    onOpenSettings = {},
                    mode = MaterialScreenMode.LIBRARY,
                )
            }
        }

        composeRule.waitUntil { !viewModel.uiState.value.isLoading }
        composeRule.onNodeWithText("English（2）").assertExists()
        composeRule.onNodeWithText("粤语（1）").assertExists()
        composeRule.onNodeWithText("已保存英语文章（2）").assertExists()
        composeRule.onNodeWithText("Saved English material").assertExists()
        composeRule.onNodeWithText("Manual saved article").assertExists()
        composeRule.onNodeWithText("手动粘贴", substring = true).assertExists()
        composeRule.onNodeWithText("粤语问候").assertDoesNotExist()

        composeRule.onNodeWithTag("library_language_cantonese").performClick()
        composeRule.onNodeWithText("已保存粤语文章（1）").assertExists()
        composeRule.onNodeWithText("粤语问候").assertExists()
        composeRule.onNodeWithText("Saved English material").assertDoesNotExist()

        composeRule.onNodeWithTag("library_article_cantonese").performTouchInput { longClick() }
        composeRule.onNodeWithTag("cache_selected_articles").assertExists()
        composeRule.onNodeWithTag("delete_selected_articles").assertExists()
    }

    @Test
    fun articleListShowsLanguageSpecificEmptyState() {
        val dependencies = TestMaterialDependencies(listOf(englishMaterial()))
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                val state by viewModel.uiState.collectAsState()
                MaterialScreen(
                    state = state,
                    viewModel = viewModel,
                    onNavigate = {},
                    onOpenSettings = {},
                    mode = MaterialScreenMode.LIBRARY,
                )
            }
        }
        composeRule.waitUntil { !viewModel.uiState.value.isLoading }
        composeRule.onNodeWithTag("library_language_cantonese").performClick()
        composeRule.onNodeWithText("还没有保存的粤语文章，请先到“智能材料”生成或粘贴保存。").assertExists()
    }

    @Test
    fun detailShowsJyutpingSourceAndPreparingState() {
        val material = cantoneseMaterial()
        val dependencies = TestMaterialDependencies(listOf(material))
        val viewModel = dependencies.viewModel()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                MaterialScreen(
                    state = MaterialUiState(
                        materials = listOf(material),
                        selectedMaterial = material,
                        selectedSentenceIndex = 0,
                        bilingualPhase = BilingualPhase.TARGET,
                        playbackStatus = PlaybackStatus.PREPARING,
                        materialProviders = dependencies.configStore.values,
                        miniMaxConfig = dependencies.configStore.miniMax,
                        targetAvailability = TtsAvailability.READY,
                        mandarinAvailability = TtsAvailability.READY,
                    ),
                    viewModel = viewModel,
                    onNavigate = {},
                    onOpenSettings = {},
                    mode = MaterialScreenMode.LIBRARY,
                )
            }
        }

        composeRule.onNodeWithText("nei5 hou2 aa3").assertExists()
        composeRule.onNodeWithText("正在生成 MiniMax 语音…").assertExists()
        composeRule.onNodeWithTag("material_detail_list").performScrollToIndex(2)
        composeRule.onNodeWithText("Source article").assertExists()
    }
}

private class TestMaterialDependencies(materials: List<PracticeMaterial> = emptyList()) {
    val configStore = TestServiceConfigStore()
    private val repository = TestRepository(materials)
    private val preferences = TestPreferences()
    private val speech = TestSpeechController()

    fun viewModel() = MaterialViewModel(
        repository = repository,
        generator = TestGenerator(),
        providerStore = configStore,
        miniMaxConfigStore = configStore,
        voiceCatalogStore = configStore,
        voiceService = TestVoiceService(),
        userPreferences = preferences,
        speechController = speech,
        audioCache = TestAudioCache(),
    )
}

private class TestServiceConfigStore : MaterialProviderStore, MiniMaxConfigStore, MiniMaxVoiceCatalogStore {
    var values = listOf(MaterialProviderConfig("wawa", "Wawa", "https://wawazz.xyz", "gpt-5.6-sol", "key"))
    var miniMax = MiniMaxTtsConfig(apiKey = "")
    override fun providers() = values
    override fun save(providers: List<MaterialProviderConfig>) { values = providers }
    override fun config() = miniMax
    override fun save(config: MiniMaxTtsConfig) { miniMax = config }
    var voiceCatalog: MiniMaxVoiceCatalog? = null
    var voiceFavorites = emptyList<CustomVoiceFavorite>()
    override fun catalog() = voiceCatalog
    override fun saveCatalog(catalog: MiniMaxVoiceCatalog) { voiceCatalog = catalog }
    override fun favorites() = voiceFavorites
    override fun saveFavorites(favorites: List<CustomVoiceFavorite>) { voiceFavorites = favorites }
}

private class TestVoiceService : MiniMaxVoiceService {
    override suspend fun fetchVoices(config: MiniMaxTtsConfig) = MiniMaxVoiceCatalog(emptyList(), 1L)
}

private class TestPreferences : LearnerPreferences {
    private var profile = LearnerProfile()
    private var libraryLanguage = MaterialLanguage.ENGLISH
    private val speeds = SpeechLanguage.entries.associateWith { 1f }.toMutableMap()
    override fun learnerProfile() = profile
    override fun setEnglishListening(band: Float) { profile = profile.copy(englishListening = band) }
    override fun articleLibraryLanguage() = libraryLanguage
    override fun setArticleLibraryLanguage(language: MaterialLanguage) { libraryLanguage = language }
    override fun speechSpeed(language: SpeechLanguage) = speeds.getValue(language)
    override fun setSpeechSpeed(language: SpeechLanguage, speed: Float) { speeds[language] = speed }
}

private class TestRepository(materials: List<PracticeMaterial>) : MaterialRepository {
    private val stored = materials.toMutableList()
    override suspend fun listMaterials() = stored.toList()
    override suspend fun getMaterial(id: String) = stored.firstOrNull { it.id == id }
    override suspend fun recentSourceUrls(limit: Int) = emptyList<String>()
    override suspend fun generate(request: MaterialGenerationRequest) = emptyList<PracticeMaterial>()
    override suspend fun deleteMaterial(id: String) { stored.removeAll { it.id == id } }
    override suspend fun deleteBatch(batchId: String) { stored.removeAll { it.batchId == batchId } }
}

private class TestGenerator : MaterialGenerator {
    override suspend fun test(provider: MaterialProviderConfig) = true
    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch = error("not used")
}

private class TestAudioCache : SpeechAudioCache {
    override fun get(cacheIdentity: String): File? = null
    override fun put(cacheIdentity: String, bytes: ByteArray): File = error("not used")
    override fun sizeBytes() = 0L
    override fun clear() = Unit
}

private class TestSpeechController : SpeechController {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<SpeechEvent> = mutableEvents
    override fun checkAvailability(language: SpeechLanguage) = TtsAvailability.READY
    override fun speak(requestId: Long, text: String, language: SpeechLanguage, speed: Float, startOffset: Int) = true
    override fun stop() = Unit
    override fun shutdown() = Unit
}

private fun englishMaterial() = PracticeMaterial(
    id = "english", batchId = "batch-en", batchPosition = 0,
    language = MaterialLanguage.ENGLISH, difficulty = Difficulty.TARGET, topic = "文化",
    title = "Saved English material", targetText = "A short practice sentence.",
    sentences = listOf(BilingualSentence("en:0", "A short practice sentence.", null, "一条简短练习句。")),
    sources = listOf(SourceReference("Source article", "Publisher", "https://example.com/en", null, "English")),
    createdAt = 1L, promptVersion = "listening-material-v1", providerName = "Wawa", model = "gpt-5.6-sol",
    responseId = "resp-en", inputTokens = 1, outputTokens = 2, requestFingerprint = "fp-en",
)

private fun cantoneseMaterial() = PracticeMaterial(
    id = "cantonese", batchId = "batch-yue", batchPosition = 0,
    language = MaterialLanguage.CANTONESE, difficulty = Difficulty.EASY, topic = "日常",
    title = "粤语问候", targetText = "你好啊。",
    sentences = listOf(BilingualSentence("yue:0", "你好啊。", "nei5 hou2 aa3", "你好呀。")),
    sources = listOf(SourceReference("Source article", "Publisher", "https://example.com/yue", null, "繁体中文")),
    createdAt = 1L, promptVersion = "listening-material-v1", providerName = "Wawa", model = "gpt-5.6-sol",
    responseId = "resp-yue", inputTokens = 1, outputTokens = 2, requestFingerprint = "fp-yue",
)
