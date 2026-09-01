package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import java.io.IOException
import java.net.SocketTimeoutException
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
import org.json.JSONArray
import org.json.JSONObject

data class NewsTranslationInput(
    val id: String,
    val text: String,
    val sourceLanguage: MaterialLanguage,
)

interface NewsTranslationGateway {
    suspend fun translateTitles(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
    ): Map<String, String>

    suspend fun translateSentences(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
    ): Map<String, String>
}

/** Responses-compatible translation gateway. It intentionally has no material-generation behavior. */
class OpenAiResponsesNewsTranslationGateway(
    private val client: OkHttpClient = OpenAiResponsesMaterialGateway.defaultClient(),
) : NewsTranslationGateway {
    override suspend fun translateTitles(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
    ): Map<String, String> {
        validateInputs(inputs, maxItems = MAX_TITLES, maxCharacters = null)
        return translate(provider, inputs, "news_title_translations")
    }

    override suspend fun translateSentences(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
    ): Map<String, String> {
        validateInputs(inputs, maxItems = MAX_SENTENCES, maxCharacters = MAX_SENTENCE_CHARACTERS)
        return translate(provider, inputs, "news_sentence_translations")
    }

    private suspend fun translate(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
        schemaName: String,
    ): Map<String, String> {
        val response = execute(createRequest(provider, inputs, schemaName), provider.apiKey)
        ensureSuccessful(response, provider.apiKey)
        return try {
            NewsTranslationResponseParser.parse(response.body, inputs.mapTo(linkedSetOf()) { it.id })
        } catch (error: GatewayException) {
            throw GatewayFormatException(
                error.message.orEmpty().redact(provider.apiKey).take(240).ifBlank { "新闻翻译响应格式错误" },
            )
        }
    }

    private fun createRequest(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
        schemaName: String,
    ): Request {
        val items = JSONArray().apply {
            inputs.forEach { input ->
                put(
                    JSONObject()
                        .put("id", input.id)
                        .put("source_language", input.sourceLanguage.promptName())
                        .put("text", input.text),
                )
            }
        }
        val body = JSONObject()
            .put("model", provider.model)
            .put("instructions", INSTRUCTIONS)
            .put("input", JSONObject().put("items", items).toString())
            .put("max_output_tokens", 6000)
            .put("stream", false)
            .put("store", false)
            .put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", schemaName)
                        .put("strict", true)
                        .put("schema", responseSchema()),
                ),
            )
        return Request.Builder()
            .url(ServiceConfigStore.endpoint(provider.baseUrl, "responses"))
            .header("Authorization", "Bearer ${ServiceConfigStore.sanitizeApiKey(provider.apiKey)}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private suspend fun execute(request: Request, apiKey: String): HttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isActive) return
                        continuation.resumeWithException(mapNetworkFailure(e, apiKey))
                    }

                    override fun onResponse(call: Call, response: okhttp3.Response) {
                        if (!continuation.isActive) {
                            response.close()
                            return
                        }
                        runCatching {
                            response.use { HttpResponse(it.code, it.body?.string().orEmpty()) }
                        }.onSuccess { value -> continuation.resume(value) }.onFailure { error ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    if (error is IOException) mapNetworkFailure(error, apiKey) else error,
                                )
                            }
                        }
                    }
                },
            )
        }

    private fun ensureSuccessful(response: HttpResponse, apiKey: String) {
        if (response.code in 200..299) return
        when (response.code) {
            401, 403 -> throw AuthenticationException()
            408 -> throw GatewayException("模型网关请求超时（HTTP 408）", retryable = true)
            429 -> throw RateLimitException()
            521 -> throw ProviderOriginUnavailableException(response.code)
            in 500..599 -> throw GatewayException("模型网关暂时不可用（HTTP ${response.code}）", retryable = true)
            else -> throw GatewayException(safeErrorMessage(response.body, apiKey, "新闻翻译请求失败（HTTP ${response.code}）"))
        }
    }

    private fun safeErrorMessage(body: String, apiKey: String, fallback: String): String = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")
            ?.takeIf(String::isNotBlank)
            ?.redact(apiKey)
            ?.replace('\r', ' ')
            ?.replace('\n', ' ')
            ?.take(240)
            ?: fallback
    }.getOrDefault(fallback)

    private fun mapNetworkFailure(error: IOException, apiKey: String): GatewayException {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val description = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
        if (causes.any { it is SocketTimeoutException } || "timeout" in description) {
            return InactivityTimeoutException()
        }
        val type = causes.lastOrNull()?.javaClass?.simpleName.orEmpty().ifBlank { error.javaClass.simpleName }
        val safeType = type.redact(apiKey).take(80)
        return GatewayException("无法连接模型网关（$safeType），请检查手机网络", error, retryable = true)
    }

    private fun validateInputs(inputs: List<NewsTranslationInput>, maxItems: Int, maxCharacters: Int?) {
        require(inputs.isNotEmpty()) { "翻译内容不能为空" }
        require(inputs.size <= maxItems) { "单次翻译最多 $maxItems 项" }
        require(inputs.all { it.id.isNotBlank() && it.text.isNotBlank() }) { "翻译 ID 和原文不能为空" }
        require(inputs.map { it.id }.distinct().size == inputs.size) { "翻译 ID 不能重复" }
        if (maxCharacters != null) {
            require(inputs.sumOf { it.text.length } <= maxCharacters) {
                "单次句子翻译原文不能超过 $maxCharacters 字符"
            }
        }
    }

    private fun responseSchema(): JSONObject {
        val itemProperties = JSONObject()
            .put("id", JSONObject().put("type", "string"))
            .put("translation", JSONObject().put("type", "string"))
        val item = JSONObject()
            .put("type", "object")
            .put("properties", itemProperties)
            .put("required", JSONArray().put("id").put("translation"))
            .put("additionalProperties", false)
        val rootProperties = JSONObject().put(
            "translations",
            JSONObject().put("type", "array").put("items", item),
        )
        return JSONObject()
            .put("type", "object")
            .put("properties", rootProperties)
            .put("required", JSONArray().put("translations"))
            .put("additionalProperties", false)
    }

    private fun MaterialLanguage.promptName(): String = when (this) {
        MaterialLanguage.ENGLISH -> "English"
        MaterialLanguage.CANTONESE -> "Cantonese (Traditional Chinese)"
    }

    private fun String.redact(apiKey: String): String =
        if (apiKey.isBlank()) this else replace(apiKey, "••••")

    private data class HttpResponse(val code: Int, val body: String)

    companion object {
        const val PROMPT_VERSION = "news-translation-v1"
        const val MAX_TITLES = 20
        const val MAX_SENTENCES = 25
        const val MAX_SENTENCE_CHARACTERS = 6000

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val INSTRUCTIONS = """
            Prompt version: $PROMPT_VERSION.
            Translate every input item faithfully into Simplified Chinese.
            Preserve IDs exactly and return exactly one translation for every input ID.
            Preserve proper nouns, numbers, quotations, and factual meaning.
            Do not summarize, explain, add facts, or produce Jyutping.
            English and Cantonese source text must both be translated into natural Simplified Chinese.
        """.trimIndent()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

internal object NewsTranslationResponseParser {
    fun parse(responseBody: String, expectedIds: Set<String>): Map<String, String> {
        val root = runCatching { JSONObject(responseBody) }
            .getOrElse { throw GatewayFormatException("翻译网关返回的不是有效 JSON") }
        root.optJSONObject("error")?.let {
            throw GatewayFormatException(it.optString("message", "新闻翻译失败"))
        }
        if (root.optString("status") == "incomplete") {
            throw GatewayFormatException("新闻翻译输出未完成")
        }
        val outputText = extractOutputText(root)
            ?: throw GatewayFormatException("翻译响应中没有可读取的内容")
        val payload = runCatching { JSONObject(stripCodeFence(outputText)) }
            .getOrElse { throw GatewayFormatException("模型没有返回约定的翻译 JSON") }
        val array = payload.optJSONArray("translations")
            ?: throw GatewayFormatException("翻译 JSON 缺少 translations")
        val result = linkedMapOf<String, String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw GatewayFormatException("第 ${index + 1} 条翻译格式错误")
            val id = item.optString("id")
            val translation = item.optString("translation").trim()
            if (id !in expectedIds) throw GatewayFormatException("翻译响应包含未知 ID")
            if (id in result) throw GatewayFormatException("翻译响应包含重复 ID")
            if (translation.isBlank()) throw GatewayFormatException("翻译响应包含空译文")
            result[id] = translation
        }
        if (result.keys != expectedIds) throw GatewayFormatException("翻译响应缺少部分 ID")
        return result
    }

    private fun extractOutputText(root: JSONObject): String? {
        root.optString("output_text").takeIf(String::isNotBlank)?.let { return it }
        val output = root.optJSONArray("output") ?: return null
        for (outputIndex in 0 until output.length()) {
            val content = output.optJSONObject(outputIndex)?.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val block = content.optJSONObject(contentIndex) ?: continue
                if (block.optString("type") == "output_text") {
                    block.optString("text").takeIf(String::isNotBlank)?.let { return it }
                }
            }
        }
        return null
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }
}
