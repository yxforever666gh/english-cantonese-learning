package com.example.englishcantoneselearning.speech

import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.model.BuiltInMiniMaxVoices
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceCatalog
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
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
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

interface MiniMaxVoiceService {
    suspend fun fetchVoices(config: MiniMaxTtsConfig): MiniMaxVoiceCatalog
}

class MiniMaxVoiceGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
    private val now: () -> Long = System::currentTimeMillis,
) : MiniMaxVoiceService {
    override suspend fun fetchVoices(config: MiniMaxTtsConfig): MiniMaxVoiceCatalog {
        require(config.apiKey.isNotBlank()) { "请先保存MiniMax API Key" }
        val request = Request.Builder()
            .url(ServiceConfigStore.endpoint(config.baseUrl, "get_voice"))
            .header("Authorization", "Bearer ${ServiceConfigStore.sanitizeApiKey(config.apiKey)}")
            .header("Content-Type", "application/json")
            .post(JSONObject().put("voice_type", "all").toString().toRequestBody(JSON))
            .build()
        val response = execute(request)
        val json = runCatching { JSONObject(response.body) }
            .getOrElse { throw MiniMaxSpeechException("MiniMax音色列表格式无效") }
        val base = json.optJSONObject("base_resp")
        val status = base?.optInt("status_code", if (response.code in 200..299) 0 else -1) ?: -1
        if (response.code !in 200..299 || status != 0) {
            val message = base?.optString("status_msg")?.takeIf(String::isNotBlank)
                ?: "MiniMax音色查询失败（HTTP ${response.code}）"
            throw MiniMaxSpeechException(message)
        }
        val voices = buildList {
            addAll(parse(json.optJSONArray("system_voice"), MiniMaxVoiceKind.SYSTEM))
            addAll(parse(json.optJSONArray("voice_cloning"), MiniMaxVoiceKind.CLONED))
            addAll(parse(json.optJSONArray("voice_generation"), MiniMaxVoiceKind.DESIGNED))
        }.distinctBy { it.id }
        if (voices.isEmpty()) throw MiniMaxSpeechException("MiniMax没有返回可用音色")
        return MiniMaxVoiceCatalog(voices, now())
    }

    private fun parse(array: JSONArray?, kind: MiniMaxVoiceKind): List<MiniMaxVoice> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = sanitizeVoiceId(item.optString("voice_id"))
            if (id.isBlank()) continue
            val descriptions = item.optJSONArray("description")
            val description = buildList {
                if (descriptions != null) for (i in 0 until descriptions.length()) {
                    descriptions.optString(i).takeIf(String::isNotBlank)?.let(::add)
                }
            }.joinToString("；")
            add(
                MiniMaxVoice(
                    id = id,
                    name = item.optString("voice_name").trim().ifBlank {
                        BuiltInMiniMaxVoices.nameFor(id) ?: id
                    },
                    kind = kind,
                    supportedLanguages = BuiltInMiniMaxVoices.languagesFor(id),
                    description = description,
                ),
            )
        }
    }

    private suspend fun execute(request: Request): HttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(
                    MiniMaxSpeechException("无法连接MiniMax音色服务，请检查网络", e),
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (continuation.isActive) continuation.resume(HttpResponse(it.code, it.body?.string().orEmpty()))
                }
            }
        })
    }

    private data class HttpResponse(val code: Int, val body: String)

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun sanitizeVoiceId(value: String): String = value
            .filterNot { it.isISOControl() }
            .trim()
            .take(256)
    }
}
