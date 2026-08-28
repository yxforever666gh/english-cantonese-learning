package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.model.SpeechLanguage

interface LearnerPreferences {
    fun learnerProfile(): LearnerProfile
    fun setEnglishListening(band: Float)
    fun articleLibraryLanguage(): MaterialLanguage
    fun setArticleLibraryLanguage(language: MaterialLanguage)
    fun speechSpeed(language: SpeechLanguage): Float
    fun setSpeechSpeed(language: SpeechLanguage, speed: Float)
}

class UserPreferences(context: Context) : LearnerPreferences {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        preferences.edit {
            remove(ENGLISH_SPEAKING)
            remove(ENGLISH_WRITING)
            remove(ENGLISH_READING)
        }
    }

    override fun learnerProfile(): LearnerProfile = LearnerProfile(
        englishListening = MaterialLevelRules.normalizeListeningBand(
            preferences.getFloat(ENGLISH_LISTENING, 6.0f),
        ),
    )

    override fun setEnglishListening(band: Float) {
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

    override fun speechSpeed(language: SpeechLanguage): Float = preferences.getFloat(
        speedKey(language),
        when (language) {
            SpeechLanguage.ENGLISH_US -> 0.9f
            SpeechLanguage.CANTONESE_HK -> 0.8f
            SpeechLanguage.MANDARIN_CN -> 1.0f
        },
    )

    override fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        val normalized = (speed.coerceIn(0.5f, 2.0f) * 10).toInt() / 10f
        preferences.edit { putFloat(speedKey(language), normalized) }
    }

    private fun speedKey(language: SpeechLanguage): String = "speech_speed_${language.name.lowercase()}"

    private companion object {
        const val PREFERENCES = "learner_preferences"
        const val ENGLISH_LISTENING = "english_listening"
        const val ENGLISH_SPEAKING = "english_speaking"
        const val ENGLISH_WRITING = "english_writing"
        const val ENGLISH_READING = "english_reading"
        const val ARTICLE_LIBRARY_LANGUAGE = "article_library_language"
    }
}
