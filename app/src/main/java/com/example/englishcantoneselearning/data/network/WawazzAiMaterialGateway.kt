package com.example.englishcantoneselearning.data.network

import android.util.Log
import com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.GenerationStage
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
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiResponsesMaterialGateway(
    private val client: OkHttpClient = defaultClient(),
) : AiMaterialGateway {

    override suspend fun supportsConfiguredModel(provider: MaterialProviderConfig): Boolean {
        val request = Request.Builder()
            .url(ServiceConfigStore.endpoint(provider.baseUrl, "models"))
            .header("Authorization", "Bearer ${ServiceConfigStore.sanitizeApiKey(provider.apiKey)}")
            .get()
            .build()
        val response = execute(request)
        ensureSuccessful(response)
        return containsString(JSONObject(response.body), provider.model)
    }

    override suspend fun generate(provider: MaterialProviderConfig, request: MaterialGenerationRequest): GeneratedBatch =
        generate(provider, request) {}

    override suspend fun generate(
        provider: MaterialProviderConfig,
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): GeneratedBatch {
        val structured = execute(createRequest(provider, request, structuredOutput = true), provider, request, onActivity)
        val response = if (structured.code == 400 && isStructuredOutputCompatibilityError(structured.body)) {
            execute(createRequest(provider, request, structuredOutput = false), provider, request, onActivity)
        } else {
            structured
        }
        ensureSuccessful(response)
        return MaterialResponseParser.parse(response.body).copy(
            providerId = provider.id,
            providerName = provider.name,
            model = provider.model,
        )
    }

    private fun createRequest(
        provider: MaterialProviderConfig,
        generationRequest: MaterialGenerationRequest,
        structuredOutput: Boolean,
    ): Request {
        val input = buildString {
            append(MaterialPromptBuilder.input(generationRequest))
            if (!structuredOutput) {
                append("\nReturn one JSON object only: {\"materials\":[one material object]}. ")
                append("Each material requires title, topic, difficulty, target_text, sentences, sources. ")
                append("Each sentence requires target_text, jyutping, simplified_chinese. ")
                append("Each source requires title, publisher, url, published_at, source_language. ")
                append("The material also requires outline_sections, covered_section_ids, covered_paragraph_ids, sections, has_more, next_section_index.")
            }
        }
        val body = JSONObject()
            .put("model", provider.model)
            .put("instructions", MaterialPromptBuilder.instructions)
            .put("input", input)
            .put("reasoning", JSONObject().put("effort", "low"))
            .put("max_output_tokens", 8000)
            .put("stream", true)
            .put("store", false)
        if (structuredOutput) {
            body.put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", "listening_material_batch")
                        .put("strict", true)
                        .put("schema", responseSchema()),
                ),
            )
        }

        return Request.Builder()
            .url(ServiceConfigStore.endpoint(provider.baseUrl, "responses"))
            .header("Authorization", "Bearer ${ServiceConfigStore.sanitizeApiKey(provider.apiKey)}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private suspend fun execute(request: Request): HttpResponse =
        execute(request, null, null) {}

    private suspend fun execute(
        request: Request,
        provider: MaterialProviderConfig?,
        generationRequest: MaterialGenerationRequest?,
        onActivity: (GenerationActivity) -> Unit,
    ): HttpResponse = suspendCancellableCoroutine { continuation ->
        val startedAtNanos = System.nanoTime()
        val startedAtMillis = System.currentTimeMillis()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    logFailure(request, startedAtNanos, e)
                    continuation.resumeWithException(mapNetworkFailure(e))
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    if (!continuation.isActive) {
                        response.close()
                        return
                    }
                    runCatching {
                        response.use {
                            val body = it.body
                            val isEventStream = body?.contentType()?.toString()?.contains("text/event-stream", ignoreCase = true) == true
                            val text = if (isEventStream && it.isSuccessful && body != null) {
                                readEventStream(
                                    body.source(),
                                    provider,
                                    generationRequest,
                                    startedAtMillis,
                                    onActivity,
                                )
                            } else {
                                body?.string().orEmpty()
                            }
                            HttpResponse(it.code, text)
                        }
                    }.onSuccess {
                        logResult(request, startedAtNanos, it.code)
                        continuation.resume(it)
                    }.onFailure { failure ->
                        if (!continuation.isActive) return@onFailure
                        val mapped = if (failure is IOException) mapNetworkFailure(failure) else failure
                        if (failure is IOException) logFailure(request, startedAtNanos, failure)
                        continuation.resumeWithException(mapped)
                    }
                }
            }
        )
    }

    private fun readEventStream(
        source: okio.BufferedSource,
        provider: MaterialProviderConfig?,
        request: MaterialGenerationRequest?,
        startedAt: Long,
        onActivity: (GenerationActivity) -> Unit,
    ): String {
        val raw = StringBuilder()
        val dataLines = mutableListOf<String>()
        var receivedChars = 0
        fun emit(eventType: String, deltaChars: Int = 0) {
            receivedChars += deltaChars
            val stage = when {
                "web_search" in eventType -> GenerationStage.SEARCHING
                "output_text" in eventType || "content_part" in eventType -> GenerationStage.WRITING
                eventType == "response.completed" -> GenerationStage.COMPLETED
                eventType == "response.created" -> GenerationStage.CONNECTING
                else -> GenerationStage.REASONING
            }
            if (provider != null && request != null) runCatching {
                onActivity(
                    GenerationActivity(
                        provider = provider.name,
                        chapter = request.chapterIndex + 1,
                        stage = stage,
                        eventType = eventType,
                        receivedChars = receivedChars,
                        startedAt = startedAt,
                        lastActivityAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        fun flush() {
            if (dataLines.isEmpty()) return
            val data = dataLines.joinToString("\n")
            dataLines.clear()
            if (data == "[DONE]") {
                emit("done")
                return
            }
            val event = runCatching { JSONObject(data) }.getOrNull()
            if (event != null) emit(event.optString("type", "stream.event"), event.optString("delta").length)
        }
        while (true) {
            val line = source.readUtf8Line() ?: break
            raw.append(line).append('\n')
            when {
                line.isBlank() -> flush()
                line.startsWith(":") -> emit("heartbeat")
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
        flush()
        return raw.toString()
    }

    private fun mapNetworkFailure(error: IOException): GatewayException {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val description = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
        val timedOut = causes.any { it is SocketTimeoutException } || "timeout" in description
        if (timedOut) {
            return InactivityTimeoutException()
        }
        val interrupted = listOf(
            "connection reset",
            "unexpected end of stream",
            "eof",
            "broken pipe",
            "stream was reset",
        ).any(description::contains)
        val failureType = causes.lastOrNull()?.javaClass?.simpleName
            ?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        return if (interrupted) {
            GatewayException("代理连接在网关返回前中断（$failureType）", error, retryable = true)
        } else {
            GatewayException("无法连接模型网关（$failureType），请检查手机网络", error, retryable = true)
        }
    }

    private fun logResult(request: Request, startedAtNanos: Long, code: Int) {
        safeLog("${request.method} ${request.url.encodedPath} completed code=$code elapsedMs=${elapsedMillis(startedAtNanos)}")
    }

    private fun logFailure(request: Request, startedAtNanos: Long, error: IOException) {
        val causeTypes = generateSequence<Throwable>(error) { it.cause }
            .map { it.javaClass.simpleName }
            .filter { it.isNotBlank() }
            .joinToString("->")
        val safeMessage = error.message.orEmpty()
            .replace('\r', ' ')
            .replace('\n', ' ')
            .take(160)
        safeLog(
            "${request.method} ${request.url.encodedPath} failed elapsedMs=${elapsedMillis(startedAtNanos)} " +
                "type=$causeTypes message=$safeMessage",
        )
    }

    private fun safeLog(message: String) {
        // Local JVM tests use Android stubs, so logging must never affect request handling.
        runCatching { Log.i(LOG_TAG, message) }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    private fun ensureSuccessful(response: HttpResponse) {
        if (response.code in 200..299) return
        when (response.code) {
            401, 403 -> throw AuthenticationException()
            408 -> throw GatewayException("模型网关请求超时（HTTP 408）", retryable = true)
            429 -> throw RateLimitException()
            521 -> throw ProviderOriginUnavailableException(response.code)
            400 -> {
                val lower = response.body.lowercase()
                val namesWebSearch = "web_search" in lower || "web search" in lower
                val explicitlyUnsupported = listOf(
                    "not supported",
                    "unsupported",
                    "unknown tool",
                    "unrecognized tool",
                    "does not support",
                ).any(lower::contains)
                if (namesWebSearch && explicitlyUnsupported) {
                    throw WebSearchUnsupportedException()
                }
                throw GatewayException(errorMessage(response.body, "网关拒绝了材料生成请求"))
            }
            in 500..599 -> throw GatewayException("模型网关暂时不可用（HTTP ${response.code}）", retryable = true)
            else -> throw GatewayException(errorMessage(response.body, "模型请求失败（HTTP ${response.code}）"))
        }
    }

    private fun errorMessage(body: String, fallback: String): String = runCatching {
        val error = JSONObject(body).optJSONObject("error")
        error?.optString("message")?.takeIf { it.isNotBlank() } ?: fallback
    }.getOrDefault(fallback)

    private fun isStructuredOutputCompatibilityError(body: String): Boolean {
        val lower = body.lowercase()
        return "json_schema" in lower || "structured" in lower || "text.format" in lower
    }

    private fun containsString(value: Any?, expected: String): Boolean = when (value) {
        is JSONObject -> value.keys().asSequence().any { containsString(value.opt(it), expected) }
        is JSONArray -> (0 until value.length()).any { containsString(value.opt(it), expected) }
        is String -> value == expected
        else -> false
    }

    private fun responseSchema(): JSONObject {
        val sentence = objectSchema(
            "target_text" to stringSchema(),
            "jyutping" to stringSchema(),
            "simplified_chinese" to stringSchema(),
        )
        val source = objectSchema(
            "title" to stringSchema(),
            "publisher" to stringSchema(),
            "url" to stringSchema(),
            "published_at" to stringSchema(),
            "source_language" to stringSchema(),
        )
        val material = objectSchema(
            "title" to stringSchema(),
            "topic" to stringSchema(),
            "difficulty" to stringSchema(),
            "target_text" to stringSchema(),
            "sentences" to JSONObject().put("type", "array").put("items", sentence),
            "sources" to JSONObject().put("type", "array").put("items", source),
            "sections" to JSONObject().put("type", "array").put(
                "items",
                objectSchema(
                    "id" to stringSchema(),
                    "title" to stringSchema(),
                    "start_sentence_index" to JSONObject().put("type", "integer"),
                ),
            ),
            "outline_sections" to JSONObject().put("type", "array").put("items", stringSchema()),
            "covered_section_ids" to JSONObject().put("type", "array").put("items", stringSchema()),
            "covered_paragraph_ids" to JSONObject().put("type", "array").put("items", stringSchema()),
            "has_more" to JSONObject().put("type", "boolean"),
            "next_section_index" to JSONObject().put("type", "integer"),
        )
        return objectSchema(
            "materials" to JSONObject()
                .put("type", "array")
                .put("minItems", 1)
                .put("maxItems", 1)
                .put("items", material),
        )
    }

    private fun objectSchema(vararg properties: Pair<String, JSONObject>): JSONObject {
        val propertyObject = JSONObject()
        val required = JSONArray()
        properties.forEach { (name, schema) ->
            propertyObject.put(name, schema)
            required.put(name)
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", propertyObject)
            .put("required", required)
            .put("additionalProperties", false)
    }

    private fun stringSchema(): JSONObject = JSONObject().put("type", "string")

    private data class HttpResponse(val code: Int, val body: String)

    companion object {
        private const val LOG_TAG = "AiMaterialGateway"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

/** Compatibility wrapper retained for existing callers and tests. */
class WawazzAiMaterialGateway(
    client: OkHttpClient = OpenAiResponsesMaterialGateway.defaultClient(),
    private val baseUrl: String = BASE_URL,
) {
    private val delegate = OpenAiResponsesMaterialGateway(client)

    suspend fun supportsConfiguredModel(apiKey: String): Boolean =
        delegate.supportsConfiguredModel(config(apiKey))

    suspend fun generate(apiKey: String, request: MaterialGenerationRequest): GeneratedBatch =
        delegate.generate(config(apiKey), request)

    private fun config(apiKey: String) = MaterialProviderConfig(
        id = "wawa-default",
        name = "Wawa",
        baseUrl = baseUrl,
        model = MODEL,
        apiKey = apiKey,
    )

    companion object {
        const val BASE_URL = ServiceConfigStore.DEFAULT_WAWA_URL
        const val MODEL = ServiceConfigStore.DEFAULT_WAWA_MODEL
    }
}
