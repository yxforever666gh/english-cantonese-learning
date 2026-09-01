package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.model.SpeechLanguage
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechSpeedPreferences(
    val english: Float = DEFAULT_SPEECH_SPEED,
    val cantonese: Float = DEFAULT_SPEECH_SPEED,
    val mandarin: Float = DEFAULT_SPEECH_SPEED,
) {
    fun forLanguage(language: SpeechLanguage): Float = when (language) {
        SpeechLanguage.ENGLISH_US -> english
        SpeechLanguage.CANTONESE_HK -> cantonese
        SpeechLanguage.MANDARIN_CN -> mandarin
    }

    fun withSpeed(language: SpeechLanguage, speed: Float): SpeechSpeedPreferences = when (language) {
        SpeechLanguage.ENGLISH_US -> copy(english = speed)
        SpeechLanguage.CANTONESE_HK -> copy(cantonese = speed)
        SpeechLanguage.MANDARIN_CN -> copy(mandarin = speed)
    }
}

const val DEFAULT_SPEECH_SPEED = 0.8f
const val DEFAULT_READING_FONT_SIZE_SP = 16
const val MIN_READING_FONT_SIZE_SP = 12
const val MAX_READING_FONT_SIZE_SP = 32

interface LearnerPreferences {
    fun learnerProfile(): LearnerProfile
    fun setListeningBand(band: Float)
    @Deprecated("Use setListeningBand")
    fun setEnglishListening(band: Float): Unit = setListeningBand(band)
    fun articleLibraryLanguage(): MaterialLanguage
    fun setArticleLibraryLanguage(language: MaterialLanguage)
    val speechSpeeds: StateFlow<SpeechSpeedPreferences>
        get() = MutableStateFlow(
            SpeechSpeedPreferences(
                english = speechSpeed(SpeechLanguage.ENGLISH_US),
                cantonese = speechSpeed(SpeechLanguage.CANTONESE_HK),
                mandarin = speechSpeed(SpeechLanguage.MANDARIN_CN),
            ),
        )
    val showNewsTranslations: StateFlow<Boolean>
        get() = MutableStateFlow(true)
    val readingFontSizeSp: StateFlow<Int>
        get() = MutableStateFlow(DEFAULT_READING_FONT_SIZE_SP)
    fun speechSpeed(language: SpeechLanguage): Float
    fun setSpeechSpeed(language: SpeechLanguage, speed: Float)
    fun setShowNewsTranslations(show: Boolean) = Unit
    fun setReadingFontSizeSp(sizeSp: Int) = Unit
}

class UserPreferences(context: Context) : LearnerPreferences {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableSpeechSpeeds: MutableStateFlow<SpeechSpeedPreferences>
    private val mutableShowNewsTranslations: MutableStateFlow<Boolean>
    private val mutableReadingFontSizeSp: MutableStateFlow<Int>
    override val speechSpeeds: StateFlow<SpeechSpeedPreferences>
        get() = mutableSpeechSpeeds.asStateFlow()
    override val showNewsTranslations: StateFlow<Boolean>
        get() = mutableShowNewsTranslations.asStateFlow()
    override val readingFontSizeSp: StateFlow<Int>
        get() = mutableReadingFontSizeSp.asStateFlow()

    init {
        preferences.edit(commit = true) {
            remove(ENGLISH_SPEAKING)
            remove(ENGLISH_WRITING)
            remove(ENGLISH_READING)
            if (!preferences.getBoolean(SPEECH_SPEED_DEFAULTS_MIGRATED, false)) {
                SpeechLanguage.entries.forEach { language ->
                    putFloat(speedKey(language), DEFAULT_SPEECH_SPEED)
                }
                putBoolean(SPEECH_SPEED_DEFAULTS_MIGRATED, true)
            }
        }
        mutableSpeechSpeeds = MutableStateFlow(readSpeechSpeeds())
        mutableShowNewsTranslations = MutableStateFlow(
            preferences.getBoolean(SHOW_NEWS_TRANSLATIONS, true),
        )
        mutableReadingFontSizeSp = MutableStateFlow(
            normalizeReadingFontSizeSp(
                preferences.getInt(READING_FONT_SIZE_SP, DEFAULT_READING_FONT_SIZE_SP),
            ),
        )
    }

    override fun learnerProfile(): LearnerProfile = LearnerProfile(
        MaterialLevelRules.normalizeListeningBand(preferences.getFloat(ENGLISH_LISTENING, 6.0f)),
    )

    override fun setListeningBand(band: Float) {
        preferences.edit { putFloat(ENGLISH_LISTENING, MaterialLevelRules.normalizeListeningBand(band)) }
    }

    override fun articleLibraryLanguage(): MaterialLanguage = runCatching {
        MaterialLanguage.valueOf(
            preferences.getString(ARTICLE_LIBRARY_LANGUAGE, MaterialLanguage.ENGLISH.name)
                ?: MaterialLanguage.ENGLISH.name,
        )
    }.getOrDefault(MaterialLanguage.ENGLISH)

    override fun setArticleLibraryLanguage(language: MaterialLanguage) {
        preferences.edit { putString(ARTICLE_LIBRARY_LANGUAGE, language.name) }
    }

    override fun speechSpeed(language: SpeechLanguage): Float = speechSpeeds.value.forLanguage(language)

    override fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        val normalized = normalizeSpeechSpeed(speed)
        preferences.edit { putFloat(speedKey(language), normalized) }
        mutableSpeechSpeeds.value = mutableSpeechSpeeds.value.withSpeed(language, normalized)
    }

    override fun setShowNewsTranslations(show: Boolean) {
        preferences.edit { putBoolean(SHOW_NEWS_TRANSLATIONS, show) }
        mutableShowNewsTranslations.value = show
    }

    override fun setReadingFontSizeSp(sizeSp: Int) {
        val normalized = normalizeReadingFontSizeSp(sizeSp)
        preferences.edit { putInt(READING_FONT_SIZE_SP, normalized) }
        mutableReadingFontSizeSp.value = normalized
    }

    private fun speedKey(language: SpeechLanguage): String = "speech_speed_${language.name.lowercase()}"

    private fun readSpeechSpeeds(): SpeechSpeedPreferences = SpeechSpeedPreferences(
        english = readSpeechSpeed(SpeechLanguage.ENGLISH_US),
        cantonese = readSpeechSpeed(SpeechLanguage.CANTONESE_HK),
        mandarin = readSpeechSpeed(SpeechLanguage.MANDARIN_CN),
    )

    private fun readSpeechSpeed(language: SpeechLanguage): Float = normalizeSpeechSpeed(
        preferences.getFloat(speedKey(language), DEFAULT_SPEECH_SPEED),
    )

    private companion object {
        const val PREFERENCES = "learner_preferences"
        const val ENGLISH_LISTENING = "english_listening"
        const val ENGLISH_SPEAKING = "english_speaking"
        const val ENGLISH_WRITING = "english_writing"
        const val ENGLISH_READING = "english_reading"
        const val ARTICLE_LIBRARY_LANGUAGE = "article_library_language"
        const val SPEECH_SPEED_DEFAULTS_MIGRATED = "speech_speed_defaults_migrated_v2"
        const val SHOW_NEWS_TRANSLATIONS = "show_news_translations"
        const val READING_FONT_SIZE_SP = "reading_font_size_sp"
    }
}

private fun normalizeSpeechSpeed(speed: Float): Float {
    if (!speed.isFinite()) return DEFAULT_SPEECH_SPEED
    return (speed.coerceIn(0.5f, 2.0f) * 10).roundToInt() / 10f
}

internal fun normalizeReadingFontSizeSp(sizeSp: Int): Int =
    sizeSp.coerceIn(MIN_READING_FONT_SIZE_SP, MAX_READING_FONT_SIZE_SP)
