package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.data.repository.MaterialValidator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WawazzAiMaterialGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: WawazzAiMaterialGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = WawazzAiMaterialGateway(
            client = OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build(),
            baseUrl = "http://127.0.0.1:${server.port}",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun modelCheckUsesGetWithoutGenerationTokens() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"data\":[{\"id\":\"gpt-5.6-sol\"}]}"))

        assertTrue(gateway.supportsConfiguredModel("new-test-key"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer new-test-key", request.getHeader("Authorization"))
    }

    @Test
    fun generationSendsLocalSourceAdaptationWithoutWebSearchTools() = runBlocking {
        server.enqueue(MockResponse().setBody(successResponse()))

        gateway.generate("new-test-key", request())

        val recordedRequest = server.takeRequest()
        val body = JSONObject(recordedRequest.body.readUtf8())
        assertEquals("gpt-5.6-sol", body.getString("model"))
        assertFalse(body.has("tools"))
        assertFalse(body.has("tool_choice"))
        assertFalse(body.has("include"))
        assertEquals("low", body.getJSONObject("reasoning").getString("effort"))
        assertFalse(body.has("max_tool_calls"))
        assertEquals(8000, body.getInt("max_output_tokens"))
        assertTrue(body.getBoolean("stream"))
        assertFalse(body.getBoolean("store"))
        assertEquals("json_schema", body.getJSONObject("text").getJSONObject("format").getString("type"))
        assertEquals("text/event-stream", recordedRequest.getHeader("Accept"))
    }

    @Test
    fun structuredOutputCompatibilityFallbackStillAvoidsWebSearch() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":{\"message\":\"json_schema unsupported\"}}"))
        server.enqueue(MockResponse().setBody(successResponse()))

        gateway.generate("new-test-key", request())

        val first = JSONObject(server.takeRequest().body.readUtf8())
        val fallback = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue(first.has("text"))
        assertFalse(fallback.has("text"))
        assertFalse(fallback.has("tools"))
    }

    @Test(expected = AuthenticationException::class)
    fun mapsUnauthorizedResponse() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            gateway.supportsConfiguredModel("bad-key")
        }
    }

    @Test
    fun mapsRateLimitAndDoesNotRetry() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))

        assertThrows(RateLimitException::class.java) {
            runBlocking { gateway.generate("new-test-key", request()) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rejectsGatewayWithoutWebSearchSupport() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"web_search tool is not supported\"}}"),
        )

        assertThrows(WebSearchUnsupportedException::class.java) {
            runBlocking { gateway.generate("new-test-key", request()) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun preservesOtherToolRelatedBadRequestMessage() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"web_search tool quota is temporarily exhausted\"}}"),
        )

        val error = assertThrows(GatewayException::class.java) {
            runBlocking { gateway.generate("new-test-key", request()) }
        }

        assertEquals("web_search tool quota is temporarily exhausted", error.message)
        assertFalse(error is WebSearchUnsupportedException)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun mapsServerFailureWithoutAutomaticRetry() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))

        val error = assertThrows(GatewayException::class.java) {
            runBlocking { gateway.generate("new-test-key", request()) }
        }
        assertTrue(error.message.orEmpty().contains("503"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun mapsCloudflare521ToOriginUnavailableInsteadOfKeyOrPhoneNetworkError() {
        server.enqueue(MockResponse().setResponseCode(521).setBody("Web server is down"))

        val error = assertThrows(ProviderOriginUnavailableException::class.java) {
            runBlocking { gateway.supportsConfiguredModel("new-test-key") }
        }

        assertTrue(error.message.orEmpty().contains("源站不可用"))
        assertTrue(error.message.orEmpty().contains("不是 Key 错误"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun malformedSuccessfulResponseIsRejected() {
        server.enqueue(MockResponse().setBody("{\"id\":\"resp\",\"output_text\":\"not-json\"}"))

        assertThrows(GatewayFormatException::class.java) {
            runBlocking { gateway.generate("new-test-key", request()) }
        }
    }

    @Test
    fun streamedWebSearchItemsProduceOneGroundedLongMaterial() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(completeMaterialSseResponse()),
        )

        val generationRequest = request().copy(difficulty = Difficulty.TARGET)
        val batch = gateway.generate("new-test-key", generationRequest)

        MaterialValidator.validate(generationRequest, batch)
        assertEquals(1, batch.materials.size)
        assertEquals(1, batch.webSourceUrls.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun timeoutHasManualRetryMessage() {
        val shortTimeoutGateway = WawazzAiMaterialGateway(
            client = OkHttpClient.Builder()
                .callTimeout(100, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build(),
            baseUrl = "http://127.0.0.1:${server.port}",
        )
        server.enqueue(MockResponse().setHeadersDelay(5, TimeUnit.SECONDS).setBody("{}"))

        val error = assertThrows(GatewayException::class.java) {
            runBlocking { shortTimeoutGateway.generate("new-test-key", request()) }
        }
        assertTrue(error.message.orEmpty().contains("连续2分钟没有收到模型活动"))
        assertEquals(1, server.requestCount)
    }

    private fun request() = MaterialGenerationRequest(
        language = MaterialLanguage.ENGLISH,
        difficulty = Difficulty.EASY,
        topic = MaterialTopic.DAILY,
        currentDate = "2026-08-22",
    )

    private fun successResponse(): String {
        val output = JSONArray().put(
            JSONObject()
                .put("type", "web_search_call")
                .put("action", JSONObject().put("sources", JSONArray().put(JSONObject().put("url", "https://example.com/a")))),
        )
        return JSONObject()
            .put("id", "resp_test")
            .put("output_text", JSONObject().put("materials", JSONArray()).toString())
            .put("output", output)
            .toString()
    }

    private fun completeMaterialSseResponse(): String {
        val sourceUrls = listOf("https://example.com/article-1")
        val materials = JSONArray()
        sourceUrls.forEachIndexed { materialIndex, sourceUrl ->
            val sentences = JSONArray()
            repeat(20) { sentenceIndex ->
                val words = (1..13).joinToString(" ") { wordIndex ->
                    "word${materialIndex}_${sentenceIndex}_$wordIndex"
                }
                sentences.put(
                    JSONObject()
                        .put("target_text", words)
                        .put("jyutping", "")
                        .put("simplified_chinese", "这是第 ${sentenceIndex + 1} 句译文。"),
                )
            }
            materials.put(
                JSONObject()
                    .put("title", "Material ${materialIndex + 1}")
                    .put("topic", "日常")
                    .put("difficulty", "TARGET")
                    .put(
                        "target_text",
                        (0 until sentences.length()).joinToString(" ") {
                            sentences.getJSONObject(it).getString("target_text")
                        },
                    )
                    .put("sentences", sentences)
                    .put("outline_sections", JSONArray().put("main"))
                    .put("covered_section_ids", JSONArray().put("main"))
                    .put("sections", JSONArray().put(JSONObject().put("id", "main").put("title", "Main").put("start_sentence_index", 0)))
                    .put("has_more", false)
                    .put("next_section_index", 1)
                    .put(
                        "sources",
                        JSONArray().put(
                            JSONObject()
                                .put("title", "Source ${materialIndex + 1}")
                                .put("publisher", "Publisher")
                                .put("url", sourceUrl)
                                .put("published_at", "2026-08-20")
                                .put("source_language", "English"),
                        ),
                    ),
            )
        }
        val outputText = JSONObject().put("materials", materials).toString()
        val completedResponse = JSONObject()
            .put("id", "resp_stream_test")
            .put("status", "completed")
            .put(
                "output",
                JSONArray().put(
                    JSONObject()
                        .put("type", "message")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject().put("type", "output_text").put("text", outputText),
                            ),
                        ),
                ),
            )
            .put("usage", JSONObject().put("input_tokens", 100).put("output_tokens", 900))
        return buildString {
            sourceUrls.forEach { url ->
                val item = JSONObject()
                    .put("type", "web_search_call")
                    .put(
                        "action",
                        JSONObject().put(
                            "sources",
                            JSONArray().put(JSONObject().put("url", url)),
                        ),
                    )
                append("event: response.output_item.done\n")
                append("data: ")
                append(JSONObject().put("type", "response.output_item.done").put("item", item))
                append("\n\n")
            }
            append("event: response.completed\n")
            append("data: ")
            append(JSONObject().put("type", "response.completed").put("response", completedResponse))
            append("\n\ndata: [DONE]\n\n")
        }
    }
}
