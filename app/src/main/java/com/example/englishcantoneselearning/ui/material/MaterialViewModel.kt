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
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.MaterialPlaybackProgress
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MaterialViewModel(
    private val repository: MaterialRepository,
    generator: MaterialGenerator,
    providerStore: MaterialProviderStore,
    miniMaxConfigStore: MiniMaxConfigStore,
    voiceCatalogStore: MiniMaxVoiceCatalogStore,
    voiceService: MiniMaxVoiceService,
    private val userPreferences: LearnerPreferences,
    private val speechController: SpeechController,
    private val audioCache: SpeechAudioCache,
) : ViewModel() {
    private val generationCoordinator = MaterialGenerationCoordinator(repository, userPreferences)
    private val settingsCoordinator = MaterialSettingsCoordinator(
        generator,
        providerStore,
        miniMaxConfigStore,
        voiceCatalogStore,
        voiceService,
    )
    private val initialVoiceSettings = settingsCoordinator.voiceSnapshot()
    private val _uiState = MutableStateFlow(
        MaterialUiState(
            materialProviders = settingsCoordinator.providers(),
            miniMaxConfig = initialVoiceSettings.config,
            voiceCatalog = initialVoiceSettings.voices,
            voiceCatalogFetchedAt = initialVoiceSettings.fetchedAt,
            customVoiceFavorites = initialVoiceSettings.favorites,
            audioCacheBytes = audioCache.sizeBytes(),
            listeningBand = userPreferences.learnerProfile().listeningBand,
            libraryLanguage = userPreferences.articleLibraryLanguage(),
            englishSpeed = userPreferences.speechSpeed(SpeechLanguage.ENGLISH_US),
            cantoneseSpeed = userPreferences.speechSpeed(SpeechLanguage.CANTONESE_HK),
            mandarinSpeed = userPreferences.speechSpeed(SpeechLanguage.MANDARIN_CN),
        ),
    )
    val uiState: StateFlow<MaterialUiState> = _uiState.asStateFlow()

    private val speechRequestIds = AtomicLong(1_000_000_000L)
    private val preloadSemaphore = Semaphore(MAX_CONCURRENT_PRELOADS)
    private var activeSpeechRequestId: Long? = null
    private var miniMaxTestRequestId: Long? = null
    private var miniMaxPreviewVoiceId: String? = null
    private var generationJob: Job? = null
    private var audioCachingJob: Job? = null

    init {
        viewModelScope.launch { speechController.events.collect(::handleSpeechEvent) }
        viewModelScope.launch {
            userPreferences.speechSpeeds.collect { speeds ->
                _uiState.update {
                    it.copy(
                        englishSpeed = speeds.english,
                        cantoneseSpeed = speeds.cantonese,
                        mandarinSpeed = speeds.mandarin,
                    )
                }
            }
        }
        reloadMaterials()
        refreshTtsAvailability()
        viewModelScope.launch {
            val pending = runCatching { generationCoordinator.hasPendingDraft() }.getOrDefault(false)
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
        val entries = materials.flatMap { MaterialPlaybackSupport.cacheEntries(it, speedFor = ::speedFor) }
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

    fun setListeningBand(band: Float) {
        val normalized = MaterialLevelRules.normalizeListeningBand(band)
        userPreferences.setListeningBand(normalized)
        _uiState.update { it.copy(listeningBand = normalized, userMessage = null) }
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
                generationCoordinator.generate(current.language, current.topic) { activity ->
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
                generationCoordinator.resume(automatic) { activity ->
                    _uiState.update { it.copy(generationActivity = activity) }
                }
            }.onSuccess { generated ->
                if (generated == null) {
                    val stillPending = runCatching { generationCoordinator.hasPendingDraft() }.getOrDefault(false)
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
                    val pending = runCatching { generationCoordinator.hasPendingDraft() }.getOrDefault(true)
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
            generationCoordinator.discardPendingDraft()
            _uiState.update { it.copy(hasPendingDraft = false, generationError = null, userMessage = "草稿已删除") }
        }
    }

    fun saveProvider(provider: MaterialProviderConfig): Boolean = runCatching {
        settingsCoordinator.saveProvider(provider)
        reloadProviderConfigs("材料模型已保存")
    }.fold(onSuccess = { true }, onFailure = {
        showMessage(it.message ?: "无法保存材料模型")
        false
    })

    fun deleteProvider(id: String) {
        settingsCoordinator.deleteProvider(id)
        reloadProviderConfigs("材料模型已删除")
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        settingsCoordinator.setProviderEnabled(id, enabled)
        reloadProviderConfigs(null)
    }

    fun moveProvider(fromIndex: Int, toIndex: Int) {
        settingsCoordinator.moveProvider(fromIndex, toIndex)
        reloadProviderConfigs(null)
    }

    fun testProvider(provider: MaterialProviderConfig) {
        if (_uiState.value.providerConnectionStates[provider.id] == ConnectionState.CHECKING) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(providerConnectionStates = it.providerConnectionStates + (provider.id to ConnectionState.CHECKING))
            }
            runCatching { settingsCoordinator.testProvider(provider) }
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
        val saved = settingsCoordinator.saveMiniMax(baseUrl, apiKey)
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
        val config = settingsCoordinator.miniMaxConfig()
        if (config.apiKey.isBlank()) {
            showMessage("请先保存MiniMax API Key")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(voiceCatalogState = ConnectionState.CHECKING, userMessage = null) }
            runCatching { settingsCoordinator.refreshVoiceCatalog() }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            miniMaxConfig = snapshot.config,
                            voiceCatalog = snapshot.voices,
                            voiceCatalogFetchedAt = snapshot.fetchedAt,
                            voiceCatalogState = ConnectionState.READY,
                            customVoiceFavorites = snapshot.favorites,
                            userMessage = if (showSuccessMessage) "MiniMax音色列表已更新" else null,
                        )
                    }
                }
                .onFailure { error ->
                    val snapshot = settingsCoordinator.voiceSnapshot()
                    _uiState.update {
                        it.copy(
                            miniMaxConfig = snapshot.config,
                            voiceCatalog = snapshot.voices,
                            voiceCatalogFetchedAt = snapshot.fetchedAt,
                            voiceCatalogState = ConnectionState.ERROR,
                            customVoiceFavorites = snapshot.favorites,
                            userMessage = "音色列表刷新失败，继续使用本地列表：${error.message ?: "网络错误"}",
                        )
                    }
                }
        }
    }

    fun selectVoice(language: SpeechLanguage, voiceId: String) {
        runCatching { settingsCoordinator.selectVoice(language, voiceId) }
            .onSuccess { snapshot ->
                val cleanId = settingsCoordinator.normalizeVoiceId(voiceId)
                applyVoiceSnapshot(snapshot, "${MaterialPlaybackSupport.languageName(language)}音色已设为 $cleanId")
            }
            .onFailure { showMessage(it.message ?: "无法选择音色") }
    }

    fun saveCustomVoice(favorite: CustomVoiceFavorite): Boolean = runCatching {
        applyVoiceSnapshot(settingsCoordinator.saveCustomVoice(favorite), "自定义音色已保存")
    }.fold(onSuccess = { true }, onFailure = {
        showMessage(it.message ?: "无法保存自定义音色")
        false
    })

    fun deleteCustomVoice(id: String) {
        runCatching { settingsCoordinator.deleteCustomVoice(id) }
            .onSuccess { snapshot -> snapshot?.let { applyVoiceSnapshot(it, "自定义音色已删除") } }
            .onFailure { showMessage(it.message ?: "无法删除自定义音色") }
    }

    fun previewVoice(voiceId: String, language: SpeechLanguage) {
        val cleanId = settingsCoordinator.normalizeVoiceId(voiceId)
        if (settingsCoordinator.miniMaxConfig().apiKey.isBlank()) {
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
                MaterialPlaybackSupport.previewText(language),
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
        val cleared = settingsCoordinator.clearMiniMaxKey()
        _uiState.update {
            it.copy(miniMaxConfig = cleared, miniMaxConnectionState = ConnectionState.IDLE,
                targetAvailability = TtsAvailability.MISSING_DATA, mandarinAvailability = TtsAvailability.MISSING_DATA,
                userMessage = "MiniMax API Key已清除")
        }
    }

    fun resetMiniMaxUrl() {
        val current = settingsCoordinator.resetMiniMaxUrl()
        _uiState.update { it.copy(miniMaxConfig = current, userMessage = "已恢复MiniMax官方地址") }
    }

    fun testMiniMax() {
        previewVoice(settingsCoordinator.miniMaxConfig().mandarinVoice, SpeechLanguage.MANDARIN_CN)
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

    fun openMaterialFromRepository(id: String) {
        viewModelScope.launch {
            val materials = runCatching { repository.listMaterials() }.getOrDefault(emptyList())
            val material = materials.firstOrNull { it.id == id } ?: run {
                showMessage("未找到已保存的新闻")
                return@launch
            }
            val progress = runCatching { repository.playbackProgress() }.getOrDefault(emptyMap())
            stopPlayback()
            _uiState.update {
                it.copy(
                    materials = materials,
                    playbackProgress = progress,
                    selectedMaterial = material,
                    selectedSentenceIndex = if (material.sentences.isEmpty()) -1 else
                        progress[id]?.resumeSentenceIndex?.coerceIn(material.sentences.indices) ?: 0,
                    bilingualPhase = BilingualPhase.TARGET,
                    characterOffset = 0,
                    isLoading = false,
                )
            }
            refreshTtsAvailability()
        }
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

    fun onSpeechSpeedChangeFinished(language: SpeechLanguage) {
        val state = _uiState.value
        val material = state.selectedMaterial ?: return
        val index = state.selectedSentenceIndex
        if (index !in material.sentences.indices) return
        val activeLanguage = if (state.bilingualPhase == BilingualPhase.TARGET) {
            material.language.toSpeechLanguage()
        } else {
            SpeechLanguage.MANDARIN_CN
        }
        if (activeLanguage == language && state.playbackStatus in setOf(
                PlaybackStatus.PLAYING,
                PlaybackStatus.PREPARING,
            )
        ) {
            startSpeaking(index, state.bilingualPhase, 0)
        } else {
            preloadUpcomingSentences(material, index)
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
                    userMessage = MaterialPlaybackSupport.missingVoiceMessage(),
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
        val entries = MaterialPlaybackSupport.preloadWindowEntries(material, currentIndex, ::speedFor)
        if (entries.isEmpty()) return
        viewModelScope.launch {
            supervisorScope {
                entries.map { entry ->
                    async {
                        preloadSemaphore.withPermit {
                            try {
                                speechController.preload(entry.text, entry.language, entry.speed)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                false
                            }
                        }
                    }
                }.awaitAll()
            }
            _uiState.update { it.copy(audioCacheBytes = audioCache.sizeBytes()) }
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

    private fun reloadProviderConfigs(message: String?) {
        _uiState.update {
            it.copy(
                materialProviders = settingsCoordinator.providers(),
                providerConnectionStates = emptyMap(),
                userMessage = message,
            )
        }
    }

    private fun reloadVoiceCatalog(message: String?) {
        applyVoiceSnapshot(settingsCoordinator.voiceSnapshot(), message)
    }

    private fun applyVoiceSnapshot(snapshot: VoiceSettingsSnapshot, message: String?) {
        _uiState.update {
            it.copy(
                miniMaxConfig = snapshot.config,
                voiceCatalog = snapshot.voices,
                voiceCatalogFetchedAt = snapshot.fetchedAt,
                customVoiceFavorites = snapshot.favorites,
                userMessage = message,
            )
        }
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
        const val MAX_CONCURRENT_PRELOADS = 4
    }
}
