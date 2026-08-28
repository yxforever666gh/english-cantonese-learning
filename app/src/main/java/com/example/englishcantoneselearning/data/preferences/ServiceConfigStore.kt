package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.englishcantoneselearning.data.security.ApiKeyStore
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.BuiltInMiniMaxVoices
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceCatalog
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.MiniMaxVoiceSelectionPolicy
import com.example.englishcantoneselearning.model.SpeechLanguage
import java.net.URI
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

interface MaterialProviderStore {
    fun providers(): List<MaterialProviderConfig>
    fun save(providers: List<MaterialProviderConfig>)
}

interface MiniMaxConfigStore {
    fun config(): MiniMaxTtsConfig
    fun save(config: MiniMaxTtsConfig)
}

interface MiniMaxVoiceCatalogStore {
    fun catalog(): MiniMaxVoiceCatalog?
    fun saveCatalog(catalog: MiniMaxVoiceCatalog)
    fun favorites(): List<CustomVoiceFavorite>
    fun saveFavorites(favorites: List<CustomVoiceFavorite>)
}

class ServiceConfigStore(
    context: Context,
    legacyKeyStore: ApiKeyStore? = null,
) : MaterialProviderStore, MiniMaxConfigStore, MiniMaxVoiceCatalogStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        if (!preferences.contains(PROVIDERS)) {
            val migratedKey = legacyKeyStore?.load().orEmpty()
            save(listOf(defaultWawa(migratedKey)))
            legacyKeyStore?.clear()
        }
        sanitizePersistedKeys()
        migrateUnsupportedVoiceSelections()
    }

    override fun providers(): List<MaterialProviderConfig> = runCatching {
        val array = JSONArray(preferences.getString(PROVIDERS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    MaterialProviderConfig(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = item.optString("name"),
                        baseUrl = item.optString("baseUrl"),
                        model = item.optString("model"),
                        apiKey = sanitizeApiKey(item.optString("apiKey")),
                        enabled = item.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    override fun save(providers: List<MaterialProviderConfig>) {
        val array = JSONArray()
        providers.forEach { provider ->
            require(provider.name.isNotBlank()) { "模型名称不能为空" }
            require(provider.model.isNotBlank()) { "模型 ID 不能为空" }
            array.put(
                JSONObject()
                    .put("id", provider.id)
                    .put("name", provider.name.trim())
                    .put("baseUrl", normalizeBaseUrl(provider.baseUrl))
                    .put("model", provider.model.trim())
                    .put("apiKey", sanitizeApiKey(provider.apiKey))
                    .put("enabled", provider.enabled),
            )
        }
        preferences.edit(commit = true) { putString(PROVIDERS, array.toString()) }
    }

    override fun config(): MiniMaxTtsConfig = MiniMaxTtsConfig(
        baseUrl = preferences.getString(MINIMAX_BASE_URL, MiniMaxTtsConfig().baseUrl).orEmpty(),
        apiKey = sanitizeApiKey(preferences.getString(MINIMAX_API_KEY, "").orEmpty()),
        englishVoice = preferences.getString(MINIMAX_ENGLISH_VOICE, MiniMaxTtsConfig().englishVoice)
            .orEmpty().ifBlank { MiniMaxTtsConfig().englishVoice },
        cantoneseVoice = preferences.getString(MINIMAX_CANTONESE_VOICE, MiniMaxTtsConfig().cantoneseVoice)
            .orEmpty().ifBlank { MiniMaxTtsConfig().cantoneseVoice },
        mandarinVoice = preferences.getString(MINIMAX_MANDARIN_VOICE, MiniMaxTtsConfig().mandarinVoice)
            .orEmpty().ifBlank { MiniMaxTtsConfig().mandarinVoice },
    )

    override fun save(config: MiniMaxTtsConfig) {
        val baseUrl = normalizeBaseUrl(config.baseUrl)
        preferences.edit(commit = true) {
            putString(MINIMAX_BASE_URL, baseUrl)
            putString(MINIMAX_API_KEY, sanitizeApiKey(config.apiKey))
            putString(MINIMAX_ENGLISH_VOICE, sanitizeVoiceId(config.englishVoice))
            putString(MINIMAX_CANTONESE_VOICE, sanitizeVoiceId(config.cantoneseVoice))
            putString(MINIMAX_MANDARIN_VOICE, sanitizeVoiceId(config.mandarinVoice))
        }
    }

    override fun catalog(): MiniMaxVoiceCatalog? = runCatching {
        val savedAt = preferences.getLong(MINIMAX_VOICE_CATALOG_FETCHED_AT, 0L)
        val array = JSONArray(preferences.getString(MINIMAX_VOICE_CATALOG, "[]"))
        val voices = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = sanitizeVoiceId(item.optString("id"))
                if (id.isBlank()) continue
                add(
                    MiniMaxVoice(
                        id = id,
                        name = item.optString("name").trim().ifBlank { id },
                        kind = enumValueOfOrDefault(item.optString("kind"), MiniMaxVoiceKind.UNKNOWN),
                        supportedLanguages = parseLanguages(item.optJSONArray("languages")),
                        description = item.optString("description").trim(),
                    ),
                )
            }
        }
        voices.takeIf { it.isNotEmpty() }?.let { MiniMaxVoiceCatalog(it, savedAt) }
    }.getOrNull()

    override fun saveCatalog(catalog: MiniMaxVoiceCatalog) {
        val array = JSONArray()
        catalog.voices.forEach { voice ->
            array.put(
                JSONObject()
                    .put("id", sanitizeVoiceId(voice.id))
                    .put("name", voice.name.trim())
                    .put("kind", voice.kind.name)
                    .put("languages", JSONArray(voice.supportedLanguages.map { it.name }))
                    .put("description", voice.description.trim()),
            )
        }
        preferences.edit(commit = true) {
            putString(MINIMAX_VOICE_CATALOG, array.toString())
            putLong(MINIMAX_VOICE_CATALOG_FETCHED_AT, catalog.fetchedAt)
        }
    }

    override fun favorites(): List<CustomVoiceFavorite> = runCatching {
        val array = JSONArray(preferences.getString(MINIMAX_VOICE_FAVORITES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val voiceId = sanitizeVoiceId(item.optString("voiceId"))
                if (voiceId.isBlank()) continue
                add(
                    CustomVoiceFavorite(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        displayName = item.optString("displayName").trim().ifBlank { voiceId },
                        voiceId = voiceId,
                        languages = parseLanguages(item.optJSONArray("languages")),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    override fun saveFavorites(favorites: List<CustomVoiceFavorite>) {
        require(favorites.map { sanitizeVoiceId(it.voiceId) }.distinct().size == favorites.size) {
            "自定义Voice ID不能重复"
        }
        val array = JSONArray()
        favorites.forEach { favorite ->
            val voiceId = sanitizeVoiceId(favorite.voiceId)
            require(voiceId.isNotBlank()) { "Voice ID不能为空" }
            require(favorite.displayName.isNotBlank()) { "音色名称不能为空" }
            require(favorite.languages.isNotEmpty()) { "至少选择一种适用语言" }
            array.put(
                JSONObject()
                    .put("id", favorite.id)
                    .put("displayName", favorite.displayName.trim())
                    .put("voiceId", voiceId)
                    .put("languages", JSONArray(favorite.languages.map { it.name })),
            )
        }
        preferences.edit(commit = true) { putString(MINIMAX_VOICE_FAVORITES, array.toString()) }
    }

    private fun parseLanguages(array: JSONArray?): Set<SpeechLanguage> = buildSet {
        if (array != null) for (index in 0 until array.length()) {
            runCatching { SpeechLanguage.valueOf(array.optString(index)) }.getOrNull()?.let(::add)
        }
    }

    private fun sanitizePersistedKeys() {
        val originalMiniMaxKey = preferences.getString(MINIMAX_API_KEY, "").orEmpty()
        val cleanMiniMaxKey = sanitizeApiKey(originalMiniMaxKey)
        val originalProviders = preferences.getString(PROVIDERS, "[]").orEmpty()
        var providersChanged = false
        val cleanProviders = runCatching {
            JSONArray(originalProviders).also { array ->
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val originalKey = item.optString("apiKey")
                    val cleanKey = sanitizeApiKey(originalKey)
                    if (originalKey != cleanKey) {
                        item.put("apiKey", cleanKey)
                        providersChanged = true
                    }
                }
            }.toString()
        }.getOrNull()
        if (originalMiniMaxKey != cleanMiniMaxKey || providersChanged) {
            preferences.edit(commit = true) {
                if (originalMiniMaxKey != cleanMiniMaxKey) putString(MINIMAX_API_KEY, cleanMiniMaxKey)
                if (providersChanged && cleanProviders != null) putString(PROVIDERS, cleanProviders)
            }
        }
    }

    private fun migrateUnsupportedVoiceSelections() {
        val current = config()
        val cached = catalog()?.voices.orEmpty().associateBy { it.id }
        fun normalized(language: SpeechLanguage, voiceId: String): String {
            val candidate = cached[voiceId] ?: BuiltInMiniMaxVoices.find(voiceId)
            return if (candidate != null && MiniMaxVoiceSelectionPolicy.isSelectable(language, candidate)) {
                voiceId
            } else {
                MiniMaxVoiceSelectionPolicy.fallbackVoiceId(language)
            }
        }
        val english = normalized(SpeechLanguage.ENGLISH_US, current.englishVoice)
        val cantonese = normalized(SpeechLanguage.CANTONESE_HK, current.cantoneseVoice)
        val mandarin = normalized(SpeechLanguage.MANDARIN_CN, current.mandarinVoice)
        val missingFields = !preferences.contains(MINIMAX_ENGLISH_VOICE) ||
            !preferences.contains(MINIMAX_CANTONESE_VOICE) ||
            !preferences.contains(MINIMAX_MANDARIN_VOICE)
        if (missingFields || english != current.englishVoice || cantonese != current.cantoneseVoice ||
            mandarin != current.mandarinVoice) {
            preferences.edit(commit = true) {
                putString(MINIMAX_ENGLISH_VOICE, english)
                putString(MINIMAX_CANTONESE_VOICE, cantonese)
                putString(MINIMAX_MANDARIN_VOICE, mandarin)
            }
        }
    }

    companion object {
        const val DEFAULT_WAWA_URL = "https://wawazz.xyz"
        const val DEFAULT_WAWA_MODEL = "gpt-5.6-sol"
        const val DEFAULT_MINIMAX_URL = "https://api.minimaxi.com"

        fun defaultWawa(apiKey: String = "") = MaterialProviderConfig(
            id = "wawa-default",
            name = "Wawa",
            baseUrl = DEFAULT_WAWA_URL,
            model = DEFAULT_WAWA_MODEL,
            apiKey = sanitizeApiKey(apiKey),
        )

        fun validateProvider(provider: MaterialProviderConfig) {
            require(provider.name.isNotBlank()) { "模型名称不能为空" }
            require(provider.model.isNotBlank()) { "模型 ID 不能为空" }
            require(sanitizeApiKey(provider.apiKey).isNotBlank()) { "API Key 不能为空" }
            normalizeBaseUrl(provider.baseUrl)
        }

        /** API credentials are HTTP-header tokens: keep visible ASCII and discard paste contamination. */
        fun sanitizeApiKey(value: String): String = value.filter { it.code in 0x21..0x7E }

        fun sanitizeVoiceId(value: String): String = value.filterNot { it.isISOControl() }.trim().take(256)

        fun normalizeBaseUrl(value: String): String {
            val normalized = value.trim().trimEnd('/')
            val uri = runCatching { URI(normalized) }.getOrNull()
            val host = uri?.host.orEmpty()
            val isLocalTestAddress = host.equals("localhost", ignoreCase = true) ||
                host == "127.0.0.1" || host == "::1"
            val validScheme = uri?.scheme.equals("https", ignoreCase = true) ||
                (uri?.scheme.equals("http", ignoreCase = true) && isLocalTestAddress)
            require(validScheme && host.isNotBlank()) {
                "服务地址必须是有效的 HTTPS 地址"
            }
            return normalized
        }

        fun endpoint(baseUrl: String, path: String): String {
            val normalized = normalizeBaseUrl(baseUrl)
            val cleanPath = path.trimStart('/')
            return if (normalized.endsWith("/v1")) "$normalized/$cleanPath" else "$normalized/v1/$cleanPath"
        }

        private const val PREFERENCES = "service_configs"
        private const val PROVIDERS = "material_providers"
        private const val MINIMAX_BASE_URL = "minimax_base_url"
        private const val MINIMAX_API_KEY = "minimax_api_key"
        private const val MINIMAX_ENGLISH_VOICE = "minimax_english_voice"
        private const val MINIMAX_CANTONESE_VOICE = "minimax_cantonese_voice"
        private const val MINIMAX_MANDARIN_VOICE = "minimax_mandarin_voice"
        private const val MINIMAX_VOICE_CATALOG = "minimax_voice_catalog"
        private const val MINIMAX_VOICE_CATALOG_FETCHED_AT = "minimax_voice_catalog_fetched_at"
        private const val MINIMAX_VOICE_FAVORITES = "minimax_voice_favorites"

        private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String, default: T): T =
            runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
