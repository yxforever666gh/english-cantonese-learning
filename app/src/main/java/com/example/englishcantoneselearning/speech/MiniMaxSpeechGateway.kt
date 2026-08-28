package com.example.englishcantoneselearning.speech

import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.SpeechLanguage
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class MiniMaxSpeechRequest(
    val text: String,
    val language: SpeechLanguage,
    val speed: Float,
    val voiceIdOverride: String? = null,
)

class MiniMaxSpeechGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
) {
    suspend fun synthesize(config: MiniMaxTtsConfig, request: MiniMaxSpeechRequest): ByteArray {
        require(config.apiKey.isNotBlank()) { "请先在设置中填写 MiniMax API Key" }
        val body = JSONObject()
            .put("model", config.model)
            .put("text", request.text)
            .put("stream", false)
            .put(
                "voice_setting",
                JSONObject()
                    .put("voice_id", voiceFor(config, request))
                    .put("speed", request.speed.coerceIn(0.5f, 2.0f))
                    .put("vol", 1)
                    .put("pitch", 0),
            )
            .put(
                "audio_setting",
                JSONObject()
                    .put("sample_rate", 32_000)
                    .put("bitrate", 128_000)
                    .put("format", "mp3")
                    .put("channel", 1),
            )
            .put("language_boost", languageBoost(request.language))
            .put("output_format", "hex")

        val httpRequest = Request.Builder()
            .url(ServiceConfigStore.endpoint(config.baseUrl, "t2a_v2"))
            .header("Authorization", "Bearer ${ServiceConfigStore.sanitizeApiKey(config.apiKey)}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = execute(httpRequest)
        val json = runCatching { JSONObject(response.body) }
            .getOrElse { throw MiniMaxSpeechException("MiniMax 返回了无法解析的数据") }
        val status = json.optJSONObject("base_resp")?.optInt("status_code", -1) ?: -1
        if (response.code !in 200..299 || status != 0) {
            val message = json.optJSONObject("base_resp")?.optString("status_msg")
                ?.takeIf { it.isNotBlank() }
                ?: "MiniMax 语音请求失败（HTTP ${response.code}）"
            throw MiniMaxSpeechException(message)
        }
        val hex = json.optJSONObject("data")?.optString("audio").orEmpty()
        if (hex.isBlank() || hex.length % 2 != 0) throw MiniMaxSpeechException("MiniMax 没有返回有效音频")
        val audio = runCatching { hexToBytes(hex) }
            .getOrElse { throw MiniMaxSpeechException("MiniMax 返回的音频格式无效") }
        if (!looksLikeMp3(audio)) throw MiniMaxSpeechException("MiniMax 返回的音频不是有效 MP3")
        return audio
    }

    fun cacheIdentity(config: MiniMaxTtsConfig, request: MiniMaxSpeechRequest): String = listOf(
        config.model, voiceFor(config, request),
        languageBoost(request.language), "%.1f".format(java.util.Locale.US, request.speed), request.text,
    ).joinToString("\u0000")

    private suspend fun execute(request: Request): HttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(
                    MiniMaxSpeechException("无法连接 MiniMax 语音服务，请检查网络", e),
                )
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                response.use { continuation.resume(HttpResponse(it.code, it.body?.string().orEmpty())) }
            }
        })
    }

    private fun voiceFor(config: MiniMaxTtsConfig, request: MiniMaxSpeechRequest): String =
        request.voiceIdOverride?.let(ServiceConfigStore::sanitizeVoiceId)?.takeIf(String::isNotBlank)
            ?: when (request.language) {
                SpeechLanguage.ENGLISH_US -> config.englishVoice
                SpeechLanguage.CANTONESE_HK -> config.cantoneseVoice
                SpeechLanguage.MANDARIN_CN -> config.mandarinVoice
            }

    private fun languageBoost(language: SpeechLanguage): String = when (language) {
        SpeechLanguage.ENGLISH_US -> "English"
        SpeechLanguage.CANTONESE_HK -> "Chinese,Yue"
        SpeechLanguage.MANDARIN_CN -> "Chinese"
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private fun looksLikeMp3(bytes: ByteArray): Boolean =
        bytes.size >= 3 && (
            bytes.copyOfRange(0, 3).contentEquals("ID3".toByteArray()) ||
                (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0
            )

    private data class HttpResponse(val code: Int, val body: String)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class MiniMaxSpeechException(message: String, cause: Throwable? = null) : Exception(message, cause)
