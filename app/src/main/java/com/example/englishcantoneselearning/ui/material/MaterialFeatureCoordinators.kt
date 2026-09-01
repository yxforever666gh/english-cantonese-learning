package com.example.englishcantoneselearning.ui.material

import com.example.englishcantoneselearning.data.network.MaterialGenerator
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxConfigStore
import com.example.englishcantoneselearning.data.preferences.MiniMaxVoiceCatalogStore
import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.BuiltInMiniMaxVoices
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.MiniMaxVoiceSelectionPolicy
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.toSpeechLanguage
import com.example.englishcantoneselearning.speech.MiniMaxVoiceService
import java.time.LocalDate

internal class MaterialGenerationCoordinator(
    private val repository: MaterialRepository,
    private val userPreferences: LearnerPreferences,
) {
    suspend fun generate(
        language: MaterialLanguage,
        topic: MaterialTopic,
        onActivity: (GenerationActivity) -> Unit,
    ) = repository.generate(
        MaterialGenerationRequest(
            language = language,
            difficulty = com.example.englishcantoneselearning.model.Difficulty.TARGET,
            topic = topic,
            profile = userPreferences.learnerProfile(),
            excludedSourceUrls = repository.recentSourceUrls(20),
            currentDate = LocalDate.now().toString(),
        ),
        onActivity,
    )

    suspend fun resume(automatic: Boolean, onActivity: (GenerationActivity) -> Unit) =
        repository.resumePendingGeneration(automatic, onActivity)

    suspend fun hasPendingDraft(): Boolean = repository.hasPendingGeneration()

    suspend fun discardPendingDraft() = repository.discardPendingGeneration()
}

internal data class VoiceSettingsSnapshot(
    val config: MiniMaxTtsConfig,
    val voices: List<MiniMaxVoice>,
    val fetchedAt: Long,
    val favorites: List<CustomVoiceFavorite>,
)

internal class MaterialSettingsCoordinator(
    private val generator: MaterialGenerator,
    private val providerStore: MaterialProviderStore,
    private val miniMaxConfigStore: MiniMaxConfigStore,
    private val voiceCatalogStore: MiniMaxVoiceCatalogStore,
    private val voiceService: MiniMaxVoiceService,
) {
    fun providers(): List<MaterialProviderConfig> = providerStore.providers()

    fun miniMaxConfig(): MiniMaxTtsConfig = miniMaxConfigStore.config()

    fun normalizeVoiceId(voiceId: String): String = ServiceConfigStore.sanitizeVoiceId(voiceId)

    fun voiceSnapshot(): VoiceSettingsSnapshot {
        val cached = voiceCatalogStore.catalog()
        val favorites = voiceCatalogStore.favorites()
        val config = miniMaxConfigStore.config()
        return VoiceSettingsSnapshot(
            config = config,
            voices = mergeVoiceCatalog(cached?.voices.orEmpty(), favorites, config),
            fetchedAt = cached?.fetchedAt ?: 0L,
            favorites = favorites,
        )
    }

    fun saveProvider(provider: MaterialProviderConfig) {
        val normalized = provider.copy(apiKey = ServiceConfigStore.sanitizeApiKey(provider.apiKey))
        ServiceConfigStore.validateProvider(normalized)
        val providers = providerStore.providers().toMutableList()
        val index = providers.indexOfFirst { it.id == normalized.id }
        if (index >= 0) providers[index] = normalized else providers += normalized
        providerStore.save(providers)
    }

    fun deleteProvider(id: String) {
        providerStore.save(providerStore.providers().filterNot { it.id == id })
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        providerStore.save(providerStore.providers().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun moveProvider(fromIndex: Int, toIndex: Int) {
        val providers = providerStore.providers().toMutableList()
        if (fromIndex !in providers.indices || toIndex !in providers.indices || fromIndex == toIndex) return
        providers.add(toIndex, providers.removeAt(fromIndex))
        providerStore.save(providers)
    }

    suspend fun testProvider(provider: MaterialProviderConfig): Boolean = generator.test(provider)

    fun saveMiniMax(baseUrl: String, apiKey: String): MiniMaxTtsConfig {
        val current = miniMaxConfigStore.config()
        val saved = current.copy(
            baseUrl = ServiceConfigStore.normalizeBaseUrl(baseUrl),
            apiKey = ServiceConfigStore.sanitizeApiKey(apiKey).ifBlank { current.apiKey },
        )
        require(saved.apiKey.isNotBlank()) { "MiniMax API Key 不能为空" }
        miniMaxConfigStore.save(saved)
        return saved
    }

    suspend fun refreshVoiceCatalog(): VoiceSettingsSnapshot {
        val config = miniMaxConfigStore.config()
        require(config.apiKey.isNotBlank()) { "请先保存MiniMax API Key" }
        voiceCatalogStore.saveCatalog(voiceService.fetchVoices(config))
        return voiceSnapshot()
    }

    fun selectVoice(language: SpeechLanguage, voiceId: String): VoiceSettingsSnapshot {
        val cleanId = ServiceConfigStore.sanitizeVoiceId(voiceId)
        require(cleanId.isNotBlank()) { "Voice ID不能为空" }
        val candidate = voiceSnapshot().voices.firstOrNull { it.id == cleanId }
            ?: BuiltInMiniMaxVoices.find(cleanId)
        require(candidate != null && MiniMaxVoiceSelectionPolicy.isSelectable(language, candidate)) {
            "当前版本只允许选择该语言的MiniMax官方系统音色"
        }
        val current = miniMaxConfigStore.config()
        val updated = when (language) {
            SpeechLanguage.ENGLISH_US -> current.copy(englishVoice = cleanId)
            SpeechLanguage.CANTONESE_HK -> current.copy(cantoneseVoice = cleanId)
            SpeechLanguage.MANDARIN_CN -> current.copy(mandarinVoice = cleanId)
        }
        miniMaxConfigStore.save(updated)
        return voiceSnapshot()
    }

    fun saveCustomVoice(favorite: CustomVoiceFavorite): VoiceSettingsSnapshot {
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
        return voiceSnapshot()
    }

    fun deleteCustomVoice(id: String): VoiceSettingsSnapshot? {
        val favorite = voiceCatalogStore.favorites().firstOrNull { it.id == id } ?: return null
        val config = miniMaxConfigStore.config()
        require(favorite.voiceId !in setOf(config.englishVoice, config.cantoneseVoice, config.mandarinVoice)) {
            "该音色正在使用，请先为对应语言选择其他音色"
        }
        voiceCatalogStore.saveFavorites(voiceCatalogStore.favorites().filterNot { it.id == id })
        return voiceSnapshot()
    }

    fun clearMiniMaxKey(): MiniMaxTtsConfig = miniMaxConfigStore.config().copy(apiKey = "").also(miniMaxConfigStore::save)

    fun resetMiniMaxUrl(): MiniMaxTtsConfig = miniMaxConfigStore.config()
        .copy(baseUrl = ServiceConfigStore.DEFAULT_MINIMAX_URL)
        .also(miniMaxConfigStore::save)

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
}

internal data class SpeechCacheEntry(
    val text: String,
    val language: SpeechLanguage,
    val speed: Float,
)

internal object MaterialPlaybackSupport {
    fun cacheEntries(
        material: PracticeMaterial,
        indices: Iterable<Int> = material.sentences.indices.asIterable(),
        speedFor: (SpeechLanguage) -> Float,
        includeTranslations: Boolean = material.origin != ArticleOrigin.MANUAL_PASTE,
    ): List<SpeechCacheEntry> = buildList {
        indices.forEach { index ->
            val sentence = material.sentences.getOrNull(index) ?: return@forEach
            val targetLanguage = material.language.toSpeechLanguage()
            if (sentence.targetText.isNotBlank()) {
                add(SpeechCacheEntry(sentence.targetText, targetLanguage, speedFor(targetLanguage)))
            }
            if (includeTranslations && sentence.simplifiedChinese?.isNotBlank() == true) {
                add(SpeechCacheEntry(sentence.simplifiedChinese, SpeechLanguage.MANDARIN_CN, speedFor(SpeechLanguage.MANDARIN_CN)))
            }
        }
    }

    fun preloadWindowEntries(
        material: PracticeMaterial,
        currentIndex: Int,
        speedFor: (SpeechLanguage) -> Float,
        includeTranslations: Boolean = material.origin != ArticleOrigin.MANUAL_PASTE,
    ): List<SpeechCacheEntry> = buildList {
        val currentSentence = material.sentences.getOrNull(currentIndex) ?: return@buildList
        if (includeTranslations &&
            currentSentence.simplifiedChinese?.isNotBlank() == true
        ) {
            add(
                SpeechCacheEntry(
                    text = currentSentence.simplifiedChinese,
                    language = SpeechLanguage.MANDARIN_CN,
                    speed = speedFor(SpeechLanguage.MANDARIN_CN),
                ),
            )
        }
        addAll(
            cacheEntries(
                material = material,
                indices = (currentIndex + 1)..(currentIndex + PRELOAD_AHEAD_SENTENCES),
                speedFor = speedFor,
                includeTranslations = includeTranslations,
            ),
        )
    }

    fun previewText(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US -> "Welcome. Let's practice listening together."
        SpeechLanguage.CANTONESE_HK -> "你好，我哋一齊練習廣東話聽力。"
        SpeechLanguage.MANDARIN_CN -> "你好，我们一起练习普通话听力。"
    }

    fun languageName(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US -> "英语"
        SpeechLanguage.CANTONESE_HK -> "粤语"
        SpeechLanguage.MANDARIN_CN -> "普通话"
    }

    fun missingVoiceMessage(): String = "请先在设置中填写MiniMax API Key"

    private const val PRELOAD_AHEAD_SENTENCES = 2
}
