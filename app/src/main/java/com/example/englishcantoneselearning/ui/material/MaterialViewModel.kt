package com.example.englishcantoneselearning.ui.material

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.englishcantoneselearning.AppContainer
import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxConfigStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxVoiceCatalogStore
import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.BuiltInMiniMaxVoices
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.MiniMaxVoiceSelectionPolicy
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.toSpeechLanguage
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import com.example.englishcantoneselearning.speech.SpeechAudioCache
import com.example.englishcantoneselearning.speech.MiniMaxVoiceGateway
import com.example.englishcantoneselearning.speech.MiniMaxVoiceService
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MaterialViewModel(
    private val repository: MaterialRepository,
    private val generator: MaterialGenerator,
    private val providerStore: MaterialProviderStore,
    private val miniMaxConfigStore: MiniMaxConfigStore,
    private val voiceCatalogStore: MiniMaxVoiceCatalogStore,
    private val voiceService: MiniMaxVoiceService,
    private val userPreferences: LearnerPreferences,
    private val speechController: SpeechController,
    private val audioCache: SpeechAudioCache,
) : ViewModel() {
    private val initialVoiceCatalog = voiceCatalogStore.catalog()
    private val initialVoiceFavorites = voiceCatalogStore.favorites()
    private val initialMiniMaxConfig = miniMaxConfigStore.config()
    private val _uiState = MutableStateFlow(
        MaterialUiState(
            materialProviders = providerStore.providers(),
            miniMaxConfig = initialMiniMaxConfig,
            voiceCatalog = mergeVoiceCatalog(initialVoiceCatalog?.voices.orEmpty(), initialVoiceFavorites, initialMiniMaxConfig),
            voiceCatalogFetchedAt = initialVoiceCatalog?.fetchedAt ?: 0L,
            customVoiceFavorites = initialVoiceFavorites,
            audioCacheBytes = audioCache.sizeBytes(),
            englishListeningBand = userPreferences.learnerProfile().englishListening,
            libraryLanguage = userPreferences.articleLibraryLanguage(),
            englishSpeed = userPreferences.speechSpeed(SpeechLanguage.ENGLISH_US),
            cantoneseSpeed = userPreferences.speechSpeed(SpeechLanguage.CANTONESE_HK),
            mandarinSpeed = userPreferences.speechSpeed(SpeechLanguage.MANDARIN_CN),
        ),
    )
    val uiState: StateFlow<MaterialUiState> = _uiState.asStateFlow()

    private val speechRequestIds = AtomicLong(1_000_000_000L)
    private var activeSpeechRequestId: Long? = null
    private var miniMaxTestRequestId: Long? = null
    private var miniMaxPreviewVoiceId: String? = null
    private var generationJob: Job? = null
    private var audioCachingJob: Job? = null

    init {
        viewModelScope.launch { speechController.events.collect(::handleSpeechEvent) }
        reloadMaterials()
        refreshTtsAvailability()
        viewModelScope.launch {
            val pending = runCatching { repository.hasPendingGeneration() }.getOrDefault(false)
            _uiState.update { it.copy(hasPendingDraft = pending) }
            if (pending && _uiState.value.materialProviders.any { it.enabled && it.apiKey.isNotBlank() }) {
                resumePendingDraft(automatic = true)
            }
        }
    }

    fun setLanguage(language: MaterialLanguage) {
        if (language == _uiState.value.language) return
        stopPlayback()
        _uiState.update { it.copy(language = language, userMessage = null) }
        refreshTtsAvailability()
    }

    fun setLibraryLanguage(language: MaterialLanguage) {
        if (language == _uiState.value.libraryLanguage) return
        userPreferences.setArticleLibraryLanguage(language)
        _uiState.update {
            it.copy(
                libraryLanguage = language,
                librarySelectedArticleIds = emptySet(),
                userMessage = null,
            )
        }
    }

    fun toggleLibraryArticleSelection(id: String) {
        val state = _uiState.value
        if (state.materials.none { it.id == id && it.language == state.libraryLanguage }) return
        _uiState.update {
            val selected = it.librarySelectedArticleIds
            it.copy(
                librarySelectedArticleIds = if (id in selected) selected - id else selected + id,
            )
        }
    }

    fun clearLibrarySelection() {
        _uiState.update { it.copy(librarySelectedArticleIds = emptySet()) }
    }

    fun deleteSelectedLibraryArticles() {
        val ids = _uiState.value.librarySelectedArticleIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.deleteMaterial(it) }
            val materials = repository.listMaterials()
            _uiState.update {
                it.copy(
                    materials = materials,
                    playbackProgress = it.playbackProgress - ids,
                    librarySelectedArticleIds = emptySet(),
                    userMessage = "已删除 ${ids.size} 篇文章",
                )
            }
        }
    }

    fun cacheSelectedLibraryArticles() {
        if (audioCachingJob?.isActive == true) return
        val state = _uiState.value
        val materials = state.materials.filter { it.id in state.librarySelectedArticleIds }
        val entries = materials.flatMap(::speechCacheEntries)
        if (entries.isEmpty()) {
            showMessage("所选文章没有可缓存的句子")
            return
        }
        audioCachingJob = viewModelScope.launch {
            var failed = 0
            _uiState.update { it.copy(isAudioCaching = true, audioCachingProgress = "准备缓存 0/${entries.size}") }
            try {
                entries.forEachIndexed { index, entry ->
                    _uiState.update {
                        it.copy(audioCachingProgress = "正在缓存 ${index + 1}/${entries.size}")
                    }
                    if (!speechController.preload(entry.text, entry.language, entry.speed)) failed++
                }
                _uiState.update {
                    it.copy(
                        isAudioCaching = false,
                        audioCachingProgress = null,
                        audioCacheBytes = audioCache.sizeBytes(),
                        userMessage = if (failed == 0) "所选文章语音已缓存" else
                            "缓存完成：成功 ${entries.size - failed} 段，失败 $failed 段",
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isAudioCaching = false,
                        audioCachingProgress = null,
                        audioCacheBytes = audioCache.sizeBytes(),
                        userMessage = "已取消提前缓存",
                    )
                }
            } finally {
                audioCachingJob = null
            }
        }
    }

    fun cancelAudioCaching() {
        audioCachingJob?.cancel()
    }

    fun setDifficulty(difficulty: Difficulty) {
        _uiState.update { it.copy(difficulty = difficulty, userMessage = null) }
    }

    fun setEnglishListeningBand(band: Float) {
        val normalized = MaterialLevelRules.normalizeListeningBand(band)
        userPreferences.setEnglishListening(normalized)
        _uiState.update { it.copy(englishListeningBand = normalized, userMessage = null) }
    }

    fun setTopic(topic: MaterialTopic) {
        _uiState.update { it.copy(topic = topic, userMessage = null) }
    }

    fun generateNewBatch() {
        if (_uiState.value.isGenerating) return
        if (_uiState.value.materialProviders.none { it.enabled && it.apiKey.isNotBlank() }) {
            showMessage("请先到设置中添加并启用材料模型")
            return
        }
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generationError = null, userMessage = null) }
            runCatching {
                val current = _uiState.value
                val exclusions = repository.recentSourceUrls(20)
                val request = MaterialGenerationRequest(
                    language = current.language,
                    difficulty = current.difficulty,
                    topic = current.topic,
                    profile = userPreferences.learnerProfile(),
                    excludedSourceUrls = exclusions,
                    currentDate = LocalDate.now().toString(),
                )
                repository.generate(request) { activity ->
                    _uiState.update { it.copy(generationActivity = activity) }
                }
            }.onSuccess { generated ->
                val all = repository.listMaterials()
                _uiState.update {
                    it.copy(
                        materials = all,
                        selectedMaterial = generated.firstOrNull(),
                        selectedSentenceIndex = if (generated.firstOrNull()?.sentences.isNullOrEmpty()) -1 else 0,
                        isGenerating = false,
                        generationError = null,
                        generationActivity = null,
                        hasPendingDraft = false,
                        userMessage = "已生成并保存 1 篇完整材料",
                    )
                }
                refreshTtsAvailability()
            }.onFailure { error ->
                if (error !is CancellationException) {
                    val message = error.message ?: "生成材料失败"
                    runCatching {
                        Log.i(
                            "MaterialGeneration",
                            "failed type=${error.javaClass.simpleName} message=" +
                                message.replace('\r', ' ').replace('\n', ' ').take(600),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            generationError = message,
                            generationActivity = null,
                            hasPendingDraft = true,
                            userMessage = message,
                        )
                    }
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(isGenerating = false, generationActivity = null, hasPendingDraft = true,
                userMessage = "已取消生成请求，已完成章节已保留")
        }
    }

    fun resumePendingDraft(automatic: Boolean = false) {
        if (_uiState.value.isGenerating) return
        generationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isGenerating = true, generationError = null,
                    userMessage = if (automatic) "正在自动继续未完成草稿…" else "正在继续未完成草稿…")
            }
            runCatching {
                repository.resumePendingGeneration(automatic) { activity ->
                    _uiState.update { it.copy(generationActivity = activity) }
                }
            }.onSuccess { generated ->
                if (generated == null) {
                    val stillPending = runCatching { repository.hasPendingGeneration() }.getOrDefault(false)
                    _uiState.update {
                        it.copy(isGenerating = false, generationActivity = null, hasPendingDraft = stillPending,
                            userMessage = if (stillPending) "草稿已暂停，请点击继续生成或删除草稿"
                            else "旧版空草稿已安全清理")
                    }
                } else {
                    val materials = repository.listMaterials()
                    _uiState.update {
                        it.copy(materials = materials, isGenerating = false, generationActivity = null,
                            hasPendingDraft = false, userMessage = "长文已完成并合并到文章列表")
                    }
                }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    val pending = runCatching { repository.hasPendingGeneration() }.getOrDefault(true)
                    _uiState.update {
                        it.copy(isGenerating = false, generationActivity = null, hasPendingDraft = pending,
                            generationError = error.message, userMessage = error.message ?: "继续生成失败")
                    }
                }
            }
        }
    }

    fun discardPendingDraft() {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            repository.discardPendingGeneration()
            _uiState.update { it.copy(hasPendingDraft = false, generationError = null, userMessage = "草稿已删除") }
        }
    }

    fun saveProvider(provider: MaterialProviderConfig): Boolean = runCatching {
        val normalizedProvider = provider.copy(apiKey = ServiceConfigStore.sanitizeApiKey(provider.apiKey))
        ServiceConfigStore.validateProvider(normalizedProvider)
        val current = providerStore.providers().toMutableList()
        val index = current.indexOfFirst { it.id == normalizedProvider.id }
        if (index >= 0) current[index] = normalizedProvider else current += normalizedProvider
        providerStore.save(current)
        reloadProviderConfigs("材料模型已保存")
    }.fold(onSuccess = { true }, onFailure = {
        showMessage(it.message ?: "无法保存材料模型")
        false
    })

    fun deleteProvider(id: String) {
        providerStore.save(providerStore.providers().filterNot { it.id == id })
        reloadProviderConfigs("材料模型已删除")
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        providerStore.save(providerStore.providers().map { if (it.id == id) it.copy(enabled = enabled) else it })
        reloadProviderConfigs(null)
    }

    fun moveProvider(fromIndex: Int, toIndex: Int) {
        val providers = providerStore.providers().toMutableList()
        if (fromIndex !in providers.indices || toIndex !in providers.indices || fromIndex == toIndex) return
        val moved = providers.removeAt(fromIndex)
        providers.add(toIndex, moved)
        providerStore.save(providers)
        reloadProviderConfigs(null)
    }

    fun testProvider(provider: MaterialProviderConfig) {
        if (_uiState.value.providerConnectionStates[provider.id] == ConnectionState.CHECKING) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(providerConnectionStates = it.providerConnectionStates + (provider.id to ConnectionState.CHECKING))
            }
            runCatching { generator.test(provider) }
                .onSuccess { supported ->
                    _uiState.update {
                        it.copy(
                            providerConnectionStates = it.providerConnectionStates +
                                (provider.id to if (supported) ConnectionState.READY else ConnectionState.ERROR),
                            userMessage = if (supported) "${provider.name} 连接成功" else
                                "${provider.name} 的模型列表中没有 ${provider.model}",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            providerConnectionStates = it.providerConnectionStates + (provider.id to ConnectionState.ERROR),
                            userMessage = "${provider.name}：${error.message ?: "连接测试失败"}",
                        )
                    }
                }
        }
    }

    fun saveMiniMax(baseUrl: String, apiKey: String): Boolean = runCatching {
        val current = miniMaxConfigStore.config()
        val saved = current.copy(
            baseUrl = ServiceConfigStore.normalizeBaseUrl(baseUrl),
            apiKey = ServiceConfigStore.sanitizeApiKey(apiKey).ifBlank { current.apiKey },
        )
        require(saved.apiKey.isNotBlank()) { "MiniMax API Key 不能为空" }
        miniMaxConfigStore.save(saved)
        _uiState.update {
            it.copy(miniMaxConfig = saved, miniMaxConnectionState = ConnectionState.IDLE,
                targetAvailability = TtsAvailability.READY, mandarinAvailability = TtsAvailability.READY,
                userMessage = "MiniMax语音配置已保存")
        }
    }.fold(onSuccess = { true }, onFailure = {
        showMessage(it.message ?: "无法保存MiniMax配置")
        false
    })

    fun refreshVoiceCatalogIfStale() {
        val state = _uiState.value
        if (state.miniMaxConfig.apiKey.isBlank() || state.voiceCatalogState == ConnectionState.CHECKING) return
        if (state.voiceCatalogFetchedAt <= 0L ||
            System.currentTimeMillis() - state.voiceCatalogFetchedAt >= VOICE_CATALOG_MAX_AGE_MS
        ) {
            refreshVoiceCatalog(showSuccessMessage = false)
        }
    }

    fun refreshVoiceCatalog(showSuccessMessage: Boolean = true) {
        if (_uiState.value.voiceCatalogState == ConnectionState.CHECKING) return
        val config = miniMaxConfigStore.config()
        if (config.apiKey.isBlank()) {
            showMessage("请先保存MiniMax API Key")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(voiceCatalogState = ConnectionState.CHECKING, userMessage = null) }
            runCatching { voiceService.fetchVoices(config) }
                .onSuccess { catalog ->
                    voiceCatalogStore.saveCatalog(catalog)
                    val favorites = voiceCatalogStore.favorites()
                    val latestConfig = miniMaxConfigStore.config()
                    _uiState.update {
                        it.copy(
                            miniMaxConfig = latestConfig,
                            voiceCatalog = mergeVoiceCatalog(catalog.voices, favorites, latestConfig),
                            voiceCatalogFetchedAt = catalog.fetchedAt,
                            voiceCatalogState = ConnectionState.READY,
                            customVoiceFavorites = favorites,
                            userMessage = if (showSuccessMessage) "MiniMax音色列表已更新" else null,
                        )
                    }
                }
                .onFailure { error ->
                    val cached = voiceCatalogStore.catalog()
                    val favorites = voiceCatalogStore.favorites()
                    val latestConfig = miniMaxConfigStore.config()
                    _uiState.update {
                        it.copy(
                            voiceCatalog = mergeVoiceCatalog(cached?.voices.orEmpty(), favorites, latestConfig),
                            voiceCatalogState = ConnectionState.ERROR,
                            customVoiceFavorites = favorites,
                            userMessage = "音色列表刷新失败，继续使用本地列表：${error.message ?: "网络错误"}",
                        )
                    }
                }
        }
    }

    fun selectVoice(language: SpeechLanguage, voiceId: String) {
        val cleanId = ServiceConfigStore.sanitizeVoiceId(voiceId)
        if (cleanId.isBlank()) {
            showMessage("Voice ID不能为空")
            return
        }
        val candidate = _uiState.value.voiceCatalog.firstOrNull { it.id == cleanId }
            ?: BuiltInMiniMaxVoices.find(cleanId)
        if (candidate == null || !MiniMaxVoiceSelectionPolicy.isSelectable(language, candidate)) {
            showMessage("当前版本只允许选择该语言的MiniMax官方系统音色")
            return
        }
        val current = miniMaxConfigStore.config()
        val updated = when (language) {
            SpeechLanguage.ENGLISH_US -> current.copy(englishVoice = cleanId)
            SpeechLanguage.CANTONESE_HK -> current.copy(cantoneseVoice = cleanId)
            SpeechLanguage.MANDARIN_CN -> current.copy(mandarinVoice = cleanId)
        }
        miniMaxConfigStore.save(updated)
        _uiState.update {
            it.copy(
                miniMaxConfig = updated,
                voiceCatalog = mergeVoiceCatalog(
                    voiceCatalogStore.catalog()?.voices.orEmpty(),
                    voiceCatalogStore.favorites(),
                    updated,
                ),
                userMessage = "${speechLanguageName(language)}音色已设为 $cleanId",
            )
        }
    }

    fun saveCustomVoice(favorite: CustomVoiceFavorite): Boolean = runCatching {
        val voiceId = ServiceConfigStore.sanitizeVoiceId(favorite.voiceId)
        require(voiceId.isNotBlank()) { "Voice ID不能为空" }
        require(favorite.displayName.isNotBlank()) { "音色名称不能为空" }
        require(favorite.languages.isNotEmpty()) { "至少选择一种适用语言" }
        val current = voiceCatalogStore.favorites().toMutableList()
        val index = current.indexOfFirst { it.id == favorite.id }
        require(current.none { it.id != favorite.id && it.voiceId == voiceId }) { "该Voice ID已收藏" }
        val knownOnline = voiceCatalogStore.catalog()?.voices.orEmpty().any { it.id == voiceId }
        val knownBuiltIn = BuiltInMiniMaxVoices.voices.any { it.id == voiceId }
        require(index >= 0 || (!knownOnline && !knownBuiltIn)) { "该音色已在可用列表中，无需重复收藏" }
        val normalized = favorite.copy(displayName = favorite.displayName.trim(), voiceId = voiceId)
        if (index >= 0) current[index] = normalized else current += normalized
        voiceCatalogStore.saveFavorites(current)
        reloadVoiceCatalog("自定义音色已保存")
    }.fold(onSuccess = { true }, onFailure = {
        showMessage(it.message ?: "无法保存自定义音色")
        false
    })

    fun deleteCustomVoice(id: String) {
        val favorite = voiceCatalogStore.favorites().firstOrNull { it.id == id } ?: return
        val config = miniMaxConfigStore.config()
        if (favorite.voiceId in setOf(config.englishVoice, config.cantoneseVoice, config.mandarinVoice)) {
            showMessage("该音色正在使用，请先为对应语言选择其他音色")
            return
        }
        voiceCatalogStore.saveFavorites(voiceCatalogStore.favorites().filterNot { it.id == id })
        reloadVoiceCatalog("自定义音色已删除")
    }

    fun previewVoice(voiceId: String, language: SpeechLanguage) {
        val cleanId = ServiceConfigStore.sanitizeVoiceId(voiceId)
        if (miniMaxConfigStore.config().apiKey.isBlank()) {
            showMessage("请先保存MiniMax API Key")
            return
        }
        if (cleanId.isBlank()) {
            showMessage("Voice ID不能为空")
            return
        }
        stopPlayback()
        val requestId = speechRequestIds.getAndIncrement()
        miniMaxTestRequestId = requestId
        miniMaxPreviewVoiceId = cleanId
        activeSpeechRequestId = requestId
        _uiState.update {
            it.copy(previewingVoiceId = cleanId, miniMaxConnectionState = ConnectionState.CHECKING,
                userMessage = "正在准备 $cleanId 的试听…")
        }
        if (!speechController.preview(
                requestId,
                previewText(language),
                language,
                cleanId,
                speedFor(language),
            )
        ) {
            miniMaxTestRequestId = null
            miniMaxPreviewVoiceId = null
            activeSpeechRequestId = null
            _uiState.update {
                it.copy(previewingVoiceId = null, miniMaxConnectionState = ConnectionState.ERROR,
                    userMessage = "无法启动 $cleanId 的试听")
            }
        }
    }

    fun stopVoicePreview() {
        if (miniMaxTestRequestId != null || _uiState.value.previewingVoiceId != null) stopPlayback()
    }

    fun clearMiniMaxKey() {
        val cleared = miniMaxConfigStore.config().copy(apiKey = "")
        miniMaxConfigStore.save(cleared)
        _uiState.update {
            it.copy(miniMaxConfig = cleared, miniMaxConnectionState = ConnectionState.IDLE,
                targetAvailability = TtsAvailability.MISSING_DATA, mandarinAvailability = TtsAvailability.MISSING_DATA,
                userMessage = "MiniMax API Key已清除")
        }
    }

    fun resetMiniMaxUrl() {
        val current = miniMaxConfigStore.config().copy(baseUrl = ServiceConfigStore.DEFAULT_MINIMAX_URL)
        miniMaxConfigStore.save(current)
        _uiState.update { it.copy(miniMaxConfig = current, userMessage = "已恢复MiniMax官方地址") }
    }

    fun testMiniMax() {
        previewVoice(miniMaxConfigStore.config().mandarinVoice, SpeechLanguage.MANDARIN_CN)
    }

    fun clearAudioCache() {
        stopPlayback()
        audioCache.clear()
        _uiState.update { it.copy(audioCacheBytes = 0, userMessage = "语音缓存已清除") }
    }

    fun openMaterial(id: String) {
        val material = _uiState.value.materials.firstOrNull { it.id == id } ?: return
        val progress = _uiState.value.playbackProgress[id]
        stopPlayback()
        _uiState.update {
            it.copy(
                selectedMaterial = material,
                selectedSentenceIndex = if (material.sentences.isEmpty()) -1 else
                    progress?.resumeSentenceIndex?.coerceIn(material.sentences.indices) ?: 0,
                bilingualPhase = BilingualPhase.TARGET,
                characterOffset = 0,
            )
        }
        refreshTtsAvailability()
    }

    fun closeMaterial() {
        stopPlayback()
        _uiState.update { it.copy(selectedMaterial = null, selectedSentenceIndex = -1) }
        refreshTtsAvailability()
    }

    fun deleteSelectedMaterial() {
        val material = _uiState.value.selectedMaterial ?: return
        viewModelScope.launch {
            stopPlayback()
            repository.deleteMaterial(material.id)
            _uiState.update {
                it.copy(
                    selectedMaterial = null,
                    selectedSentenceIndex = -1,
                    materials = repository.listMaterials(),
                    playbackProgress = it.playbackProgress - material.id,
                )
            }
        }
    }

    fun deleteSelectedBatch() {
        val material = _uiState.value.selectedMaterial ?: return
        viewModelScope.launch {
            stopPlayback()
            repository.deleteBatch(material.batchId)
            _uiState.update {
                it.copy(selectedMaterial = null, selectedSentenceIndex = -1, materials = repository.listMaterials())
            }
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _uiState.update { it.copy(playbackMode = mode) }
    }

    fun selectAndPlaySentence(index: Int) {
        val material = _uiState.value.selectedMaterial ?: return
        if (index !in material.sentences.indices) return
        stopPlayback()
        _uiState.update {
            it.copy(selectedSentenceIndex = index, bilingualPhase = BilingualPhase.TARGET, characterOffset = 0)
        }
        saveResumePosition(material, index)
        startSpeaking(index, BilingualPhase.TARGET, 0)
    }

    fun playOrPause() {
        val state = _uiState.value
        when (state.playbackStatus) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PREPARING -> stopPlayback()
            PlaybackStatus.PAUSED -> if (speechController.resume()) {
                _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
            } else startSpeaking(state.selectedSentenceIndex, state.bilingualPhase, 0)
            PlaybackStatus.IDLE -> startSpeaking(
                state.selectedSentenceIndex.coerceAtLeast(0),
                BilingualPhase.TARGET,
                0,
            )
        }
    }

    fun previousSentence() = navigateBy(-1)
    fun nextSentence() = navigateBy(1)

    fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        userPreferences.setSpeechSpeed(language, speed)
        _uiState.update {
            when (language) {
                SpeechLanguage.ENGLISH_US -> it.copy(englishSpeed = userPreferences.speechSpeed(language))
                SpeechLanguage.CANTONESE_HK -> it.copy(cantoneseSpeed = userPreferences.speechSpeed(language))
                SpeechLanguage.MANDARIN_CN -> it.copy(mandarinSpeed = userPreferences.speechSpeed(language))
            }
        }
    }

    fun refreshTtsAvailability() {
        val targetLanguage = (_uiState.value.selectedMaterial?.language ?: _uiState.value.language).toSpeechLanguage()
        _uiState.update {
            it.copy(
                targetAvailability = speechController.checkAvailability(targetLanguage),
                mandarinAvailability = speechController.checkAvailability(SpeechLanguage.MANDARIN_CN),
            )
        }
    }

    fun onAppBackgrounded() {
        _uiState.value.selectedMaterial?.let { material ->
            val index = _uiState.value.selectedSentenceIndex
            if (index in material.sentences.indices) saveResumePosition(material, index)
        }
        stopPlayback()
    }
    fun clearMessage() = _uiState.update { it.copy(userMessage = null) }

    override fun onCleared() {
        generationJob?.cancel()
        audioCachingJob?.cancel()
        stopPlayback()
        super.onCleared()
    }

    fun reloadMaterials() {
        viewModelScope.launch {
            val materials = runCatching { repository.listMaterials() }.getOrDefault(emptyList())
            val progress = runCatching { repository.playbackProgress() }.getOrDefault(emptyMap())
            _uiState.update { it.copy(materials = materials, playbackProgress = progress, isLoading = false) }
        }
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
        val sentences = state.selectedMaterial?.sentences ?: return
        if (sentences.isEmpty()) return
        val newIndex = (state.selectedSentenceIndex.coerceAtLeast(0) + delta).coerceIn(sentences.indices)
        val wasPlaying = state.playbackStatus == PlaybackStatus.PLAYING ||
            state.playbackStatus == PlaybackStatus.PREPARING
        val wasPaused = state.playbackStatus == PlaybackStatus.PAUSED
        activeSpeechRequestId = null
        speechController.stop()
        _uiState.update {
            it.copy(
                selectedSentenceIndex = newIndex,
                bilingualPhase = BilingualPhase.TARGET,
                characterOffset = 0,
                playbackStatus = if (wasPaused) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
            )
        }
        state.selectedMaterial?.let { saveResumePosition(it, newIndex) }
        if (wasPlaying) startSpeaking(newIndex, BilingualPhase.TARGET, 0)
    }

    private fun startSpeaking(index: Int, phase: BilingualPhase, startOffset: Int) {
        val state = _uiState.value
        val material = state.selectedMaterial ?: run {
            showMessage("请先打开一篇材料")
            return
        }
        if (index !in material.sentences.indices) return
        val targetLanguage = material.language.toSpeechLanguage()
        val targetAvailability = speechController.checkAvailability(targetLanguage)
        val mandarinAvailability = speechController.checkAvailability(SpeechLanguage.MANDARIN_CN)
        val needsTranslation = material.origin == ArticleOrigin.AI_GENERATED &&
            material.sentences[index].simplifiedChinese?.isNotBlank() == true
        if (targetAvailability != TtsAvailability.READY ||
            (needsTranslation && mandarinAvailability != TtsAvailability.READY)
        ) {
            _uiState.update {
                it.copy(
                    targetAvailability = targetAvailability,
                    mandarinAvailability = mandarinAvailability,
                    playbackStatus = PlaybackStatus.IDLE,
                    userMessage = missingVoiceMessage(targetAvailability, mandarinAvailability),
                )
            }
            return
        }

        val sentence = material.sentences[index]
        val text = if (phase == BilingualPhase.TARGET) sentence.targetText else sentence.simplifiedChinese.orEmpty()
        val language = if (phase == BilingualPhase.TARGET) targetLanguage else SpeechLanguage.MANDARIN_CN
        val speed = speedFor(language)
        val safeOffset = startOffset.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        val requestId = speechRequestIds.getAndIncrement()
        activeSpeechRequestId = requestId
        _uiState.update {
            it.copy(
                selectedSentenceIndex = index,
                bilingualPhase = phase,
                characterOffset = safeOffset,
                playbackStatus = PlaybackStatus.PREPARING,
                userMessage = null,
            )
        }
        saveResumePosition(material, index)
        if (!speechController.speak(requestId, text, language, speed, safeOffset)) {
            activeSpeechRequestId = null
            _uiState.update { it.copy(playbackStatus = PlaybackStatus.IDLE, userMessage = "无法开始播放，请先配置MiniMax语音") }
        } else if (phase == BilingualPhase.TARGET) {
            preloadUpcomingSentences(material, index)
        }
    }

    private fun preloadUpcomingSentences(
        material: com.example.englishcantoneselearning.model.PracticeMaterial,
        currentIndex: Int,
    ) {
        val nextIndices = ((currentIndex + 1)..(currentIndex + DEFAULT_PRELOAD_SENTENCES))
            .filter { it in material.sentences.indices }
        val entries = speechCacheEntries(material, nextIndices)
        if (entries.isEmpty()) return
        viewModelScope.launch {
            entries.forEach { speechController.preload(it.text, it.language, it.speed) }
            _uiState.update { it.copy(audioCacheBytes = audioCache.sizeBytes()) }
        }
    }

    private data class SpeechCacheEntry(
        val text: String,
        val language: SpeechLanguage,
        val speed: Float,
    )

    private fun speechCacheEntries(
        material: com.example.englishcantoneselearning.model.PracticeMaterial,
        indices: Iterable<Int> = material.sentences.indices.asIterable(),
    ): List<SpeechCacheEntry> = buildList {
        indices.forEach { index ->
            val sentence = material.sentences.getOrNull(index) ?: return@forEach
            val targetLanguage = material.language.toSpeechLanguage()
            if (sentence.targetText.isNotBlank()) {
                add(SpeechCacheEntry(sentence.targetText, targetLanguage, speedFor(targetLanguage)))
            }
            if (material.origin == ArticleOrigin.AI_GENERATED && sentence.simplifiedChinese?.isNotBlank() == true) {
                add(SpeechCacheEntry(sentence.simplifiedChinese, SpeechLanguage.MANDARIN_CN, speedFor(SpeechLanguage.MANDARIN_CN)))
            }
        }
    }

    private fun speedFor(language: SpeechLanguage): Float = when (language) {
        SpeechLanguage.ENGLISH_US -> _uiState.value.englishSpeed
        SpeechLanguage.CANTONESE_HK -> _uiState.value.cantoneseSpeed
        SpeechLanguage.MANDARIN_CN -> _uiState.value.mandarinSpeed
    }

    private fun stopPlayback() {
        activeSpeechRequestId = null
        miniMaxTestRequestId = null
        miniMaxPreviewVoiceId = null
        speechController.stop()
        _uiState.update {
            it.copy(
                playbackStatus = PlaybackStatus.IDLE,
                characterOffset = 0,
                bilingualPhase = BilingualPhase.TARGET,
                previewingVoiceId = null,
            )
        }
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Range -> if (event.requestId == activeSpeechRequestId) {
                _uiState.update { it.copy(characterOffset = event.sentenceOffset) }
            }
            is SpeechEvent.Done -> if (event.requestId == miniMaxTestRequestId) {
                val voiceId = miniMaxPreviewVoiceId
                miniMaxTestRequestId = null
                miniMaxPreviewVoiceId = null
                activeSpeechRequestId = null
                _uiState.update {
                    it.copy(
                        previewingVoiceId = null,
                        miniMaxConnectionState = ConnectionState.READY,
                        userMessage = "${voiceId ?: "MiniMax音色"} 试听完成",
                    )
                }
            } else if (event.requestId == activeSpeechRequestId) {
                activeSpeechRequestId = null
                val state = _uiState.value
                val material = state.selectedMaterial
                val sentence = material?.sentences?.getOrNull(state.selectedSentenceIndex)
                val hasTranslation = material?.origin == ArticleOrigin.AI_GENERATED &&
                    sentence?.simplifiedChinese?.isNotBlank() == true
                if (state.bilingualPhase == BilingualPhase.TARGET && hasTranslation) {
                    startSpeaking(state.selectedSentenceIndex, BilingualPhase.TRANSLATION, 0)
                } else {
                    if (material != null) markSentenceCompleted(material, state.selectedSentenceIndex)
                    val next = state.selectedSentenceIndex + 1
                    val sentenceCount = state.selectedMaterial?.sentences?.size ?: 0
                    if (state.playbackMode == PlaybackMode.CONTINUOUS && next < sentenceCount) {
                        startSpeaking(next, BilingualPhase.TARGET, 0)
                    } else {
                        _uiState.update {
                            it.copy(playbackStatus = PlaybackStatus.IDLE, characterOffset = 0, bilingualPhase = BilingualPhase.TARGET)
                        }
                    }
                }
            }
            is SpeechEvent.Error -> if (event.requestId == miniMaxTestRequestId) {
                val voiceId = miniMaxPreviewVoiceId
                miniMaxTestRequestId = null
                miniMaxPreviewVoiceId = null
                activeSpeechRequestId = null
                _uiState.update {
                    it.copy(
                        previewingVoiceId = null,
                        miniMaxConnectionState = ConnectionState.ERROR,
                        userMessage = "${voiceId ?: "MiniMax音色"}：${event.message}",
                    )
                }
            } else if (event.requestId == null || event.requestId == activeSpeechRequestId) {
                activeSpeechRequestId = null
                _uiState.update { it.copy(playbackStatus = PlaybackStatus.IDLE, userMessage = event.message) }
            }
            is SpeechEvent.Initialized -> refreshTtsAvailability()
            is SpeechEvent.Started -> if (event.requestId == activeSpeechRequestId &&
                event.requestId != miniMaxTestRequestId
            ) {
                _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
            }
        }
    }

    fun restartSelectedMaterial() {
        val material = _uiState.value.selectedMaterial ?: return
        stopPlayback()
        val progress = MaterialPlaybackProgress(material.id, updatedAt = System.currentTimeMillis())
        _uiState.update {
            it.copy(
                selectedSentenceIndex = if (material.sentences.isEmpty()) -1 else 0,
                playbackProgress = it.playbackProgress + (material.id to progress),
            )
        }
        viewModelScope.launch { repository.savePlaybackProgress(progress) }
    }

    private fun saveResumePosition(material: com.example.englishcantoneselearning.model.PracticeMaterial, index: Int) {
        if (index !in material.sentences.indices) return
        val current = _uiState.value.playbackProgress[material.id] ?: MaterialPlaybackProgress(material.id)
        val updated = current.copy(resumeSentenceIndex = index, updatedAt = System.currentTimeMillis())
        _uiState.update { it.copy(playbackProgress = it.playbackProgress + (material.id to updated)) }
        viewModelScope.launch { repository.savePlaybackProgress(updated) }
    }

    private fun markSentenceCompleted(
        material: com.example.englishcantoneselearning.model.PracticeMaterial,
        index: Int,
    ) {
        if (index !in material.sentences.indices) return
        val current = _uiState.value.playbackProgress[material.id] ?: MaterialPlaybackProgress(material.id)
        val completedIndices = current.completedSentenceIndices + index
        val allCompleted = completedIndices.size >= material.sentences.size
        val updated = current.copy(
            resumeSentenceIndex = if (allCompleted) index else (index + 1).coerceAtMost(material.sentences.lastIndex),
            completedSentenceIndices = completedIndices,
            completed = allCompleted,
            updatedAt = System.currentTimeMillis(),
        )
        _uiState.update { it.copy(playbackProgress = it.playbackProgress + (material.id to updated)) }
        viewModelScope.launch { repository.savePlaybackProgress(updated) }
    }

    private fun missingVoiceMessage(target: TtsAvailability, mandarin: TtsAvailability): String = when {
        target != TtsAvailability.READY || mandarin != TtsAvailability.READY -> "请先在设置中填写MiniMax API Key"
        else -> "MiniMax语音不可用"
    }

    private fun reloadProviderConfigs(message: String?) {
        _uiState.update {
            it.copy(
                materialProviders = providerStore.providers(),
                providerConnectionStates = emptyMap(),
                userMessage = message,
            )
        }
    }

    private fun reloadVoiceCatalog(message: String?) {
        val cached = voiceCatalogStore.catalog()
        val favorites = voiceCatalogStore.favorites()
        val config = miniMaxConfigStore.config()
        _uiState.update {
            it.copy(
                miniMaxConfig = config,
                voiceCatalog = mergeVoiceCatalog(cached?.voices.orEmpty(), favorites, config),
                voiceCatalogFetchedAt = cached?.fetchedAt ?: 0L,
                customVoiceFavorites = favorites,
                userMessage = message,
            )
        }
    }

    private fun mergeVoiceCatalog(
        remote: List<MiniMaxVoice>,
        favorites: List<CustomVoiceFavorite>,
        config: MiniMaxTtsConfig,
    ): List<MiniMaxVoice> {
        val merged = linkedMapOf<String, MiniMaxVoice>()
        BuiltInMiniMaxVoices.voices.forEach { merged[it.id] = it }
        remote.forEach { voice ->
            val builtIn = merged[voice.id]
            merged[voice.id] = voice.copy(
                name = voice.name.ifBlank { builtIn?.name ?: voice.id },
                supportedLanguages = voice.supportedLanguages + builtIn?.supportedLanguages.orEmpty(),
                description = voice.description.ifBlank { builtIn?.description.orEmpty() },
            )
        }
        favorites.forEach { favorite ->
            merged[favorite.voiceId] = MiniMaxVoice(
                id = favorite.voiceId,
                name = favorite.displayName,
                kind = MiniMaxVoiceKind.CUSTOM_FAVORITE,
                supportedLanguages = favorite.languages,
                description = "自定义收藏",
            )
        }
        listOf(config.englishVoice, config.cantoneseVoice, config.mandarinVoice).forEach { id ->
            if (id.isNotBlank() && id !in merged) {
                merged[id] = MiniMaxVoice(id, id, MiniMaxVoiceKind.UNKNOWN, description = "当前但未验证")
            }
        }
        return merged.values.sortedWith(
            compareBy<MiniMaxVoice> { it.kind.ordinal }.thenBy { it.name.lowercase() }.thenBy { it.id },
        )
    }

    private fun previewText(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US -> "Welcome. Let's practice listening together."
        SpeechLanguage.CANTONESE_HK -> "你好，我哋一齊練習廣東話聽力。"
        SpeechLanguage.MANDARIN_CN -> "你好，我们一起练习普通话听力。"
    }

    private fun speechLanguageName(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US -> "英语"
        SpeechLanguage.CANTONESE_HK -> "粤语"
        SpeechLanguage.MANDARIN_CN -> "普通话"
    }

    private fun showMessage(message: String) = _uiState.update { it.copy(userMessage = message) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MaterialViewModel::class.java))
            return MaterialViewModel(
                repository = container.materialRepository,
                generator = container.materialGenerator,
                providerStore = container.serviceConfigStore,
                miniMaxConfigStore = container.serviceConfigStore,
                voiceCatalogStore = container.serviceConfigStore,
                voiceService = container.miniMaxVoiceGateway,
                userPreferences = container.userPreferences,
                speechController = container.speechController,
                audioCache = container.audioCache,
            ) as T
        }
    }

    private companion object {
        const val VOICE_CATALOG_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        const val DEFAULT_PRELOAD_SENTENCES = 3
    }
}
