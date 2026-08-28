package com.example.englishcantoneselearning.speech

import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import kotlinx.coroutines.flow.SharedFlow

sealed interface SpeechEvent {
    data class Initialized(val successful: Boolean) : SpeechEvent
    data class Started(val requestId: Long) : SpeechEvent
    data class Range(val requestId: Long, val sentenceOffset: Int) : SpeechEvent
    data class Done(val requestId: Long) : SpeechEvent
    data class Error(val requestId: Long?, val message: String) : SpeechEvent
}

interface SpeechController {
    val events: SharedFlow<SpeechEvent>

    fun checkAvailability(language: SpeechLanguage): TtsAvailability

    fun speak(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        speed: Float,
        startOffset: Int = 0,
    ): Boolean

    fun preview(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        voiceId: String,
        speed: Float,
    ): Boolean = speak(requestId, text, language, speed)

    suspend fun preload(
        text: String,
        language: SpeechLanguage,
        speed: Float,
    ): Boolean = false

    fun pause(): Boolean = false

    fun resume(): Boolean = false

    fun stop()

    fun shutdown()
}
