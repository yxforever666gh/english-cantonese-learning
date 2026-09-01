package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
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

class NewsTranslationGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: OpenAiResponsesNewsTranslationGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = OpenAiResponsesNewsTranslationGateway(
            OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun titleRequestUsesDedicatedStrictSchemaWithoutSearchOrReasoning() = runBlocking {
        server.enqueue(successResponse("a" to "中国新闻", "b" to "香港消息"))

        val result = gateway.translateTitles(
            provider(),
            listOf(
                NewsTranslationInput("a", "China news", MaterialLanguage.ENGLISH),
                NewsTranslationInput("b", "香港消息", MaterialLanguage.CANTONESE),
            ),
        )

        assertEquals(mapOf("a" to "中国新闻", "b" to "香港消息"), result)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(false, body.getBoolean("stream"))
        assertFalse(body.has("reasoning"))
        assertFalse(body.has("tools"))
        assertFalse(body.toString().contains("web_search"))
        assertTrue(body.getString("instructions").contains("news-translation-v1"))
        assertEquals("news_title_translations", body.getJSONObject("text").getJSONObject("format").getString("name"))
        val input = JSONObject(body.getString("input")).getJSONArray("items")
        assertEquals("English", input.getJSONObject(0).getString("source_language"))
        assertEquals("Cantonese (Traditional Chinese)", input.getJSONObject(1).getString("source_language"))
    }

    @Test
    fun parserReordersResultsToStableInputIds() = runBlocking {
        server.enqueue(successResponse("second" to "二", "first" to "一"))

        val result = gateway.translateSentences(
            provider(),
            listOf(
                NewsTranslationInput("first", "One", MaterialLanguage.ENGLISH),
                NewsTranslationInput("second", "Two", MaterialLanguage.ENGLISH),
            ),
        )

        assertEquals(setOf("first", "second"), result.keys)
        assertEquals("一", result["first"])
        assertEquals("二", result["second"])
    }

    @Test
    fun responseMustContainEveryIdExactlyOnceAndNonBlank() {
        val expected = setOf("a", "b")
        assertThrows(GatewayFormatException::class.java) {
            NewsTranslationResponseParser.parse(responseBody("a" to "译文"), expected)
        }
        assertThrows(GatewayFormatException::class.java) {
            NewsTranslationResponseParser.parse(responseBody("a" to "译文", "a" to "重复", "b" to "二"), expected)
        }
        assertThrows(GatewayFormatException::class.java) {
            NewsTranslationResponseParser.parse(responseBody("a" to "译文", "b" to " "), expected)
        }
        assertThrows(GatewayFormatException::class.java) {
            NewsTranslationResponseParser.parse(responseBody("a" to "译文", "x" to "未知"), setOf("a"))
        }
    }

    @Test
    fun gatewayRejectsBatchLimitsAndDuplicateIdsBeforeNetwork() {
        val tooManyTitles = (0..20).map {
            NewsTranslationInput("t$it", "title $it", MaterialLanguage.ENGLISH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { gateway.translateTitles(provider(), tooManyTitles) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                gateway.translateSentences(
                    provider(),
                    listOf(NewsTranslationInput("long", "x".repeat(6001), MaterialLanguage.ENGLISH)),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                gateway.translateTitles(
                    provider(),
                    listOf(
                        NewsTranslationInput("same", "one", MaterialLanguage.ENGLISH),
                        NewsTranslationInput("same", "two", MaterialLanguage.ENGLISH),
                    ),
                )
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpErrorsMapWithoutLeakingApiKey() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertThrows(AuthenticationException::class.java) {
            runBlocking { gateway.translateTitles(provider(), listOf(input())) }
        }
        server.enqueue(MockResponse().setResponseCode(429))
        assertThrows(RateLimitException::class.java) {
            runBlocking { gateway.translateTitles(provider(), listOf(input())) }
        }
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                JSONObject().put("error", JSONObject().put("message", "bad ${API_KEY}" )).toString(),
            ),
        )
        val error = assertThrows(GatewayException::class.java) {
            runBlocking { gateway.translateTitles(provider(), listOf(input())) }
        }
        assertFalse(error.message.orEmpty().contains(API_KEY))
    }

    private fun input() = NewsTranslationInput("id", "Headline", MaterialLanguage.ENGLISH)

    private fun provider() = MaterialProviderConfig(
        id = "provider",
        name = "Provider",
        baseUrl = server.url("/").newBuilder().host("127.0.0.1").build().toString(),
        model = "model",
        apiKey = API_KEY,
    )

    private fun successResponse(vararg translations: Pair<String, String>) =
        MockResponse().setResponseCode(200).setBody(responseBody(*translations))

    private fun responseBody(vararg translations: Pair<String, String>): String {
        val translated = JSONArray().apply {
            translations.forEach { (id, value) ->
                put(JSONObject().put("id", id).put("translation", value))
            }
        }
        val outputText = JSONObject().put("translations", translated).toString()
        return JSONObject().put("id", "response").put("output_text", outputText).toString()
    }

    private companion object {
        const val API_KEY = "secret-key-must-not-leak"
    }
}

class FailoverNewsTranslatorTest {
    private val first = MaterialProviderConfig("first", "First", "https://first.example", "a", "key-a")
    private val second = MaterialProviderConfig("second", "Second", "https://second.example", "b", "key-b")

    @Test
    fun enabledProvidersAreTriedInSavedOrder() = runBlocking {
        val gateway = ScriptedNewsGateway { provider, _ ->
            if (provider.id == "first") throw RateLimitException()
            mapOf("id" to "译文")
        }
        val service = FailoverNewsTranslator(FixedNewsProviderStore(listOf(first, second)), gateway)

        val result = service.translateTitles(listOf(NewsTranslationInput("id", "title", MaterialLanguage.ENGLISH)))

        assertEquals(mapOf("id" to "译文"), result)
        assertEquals(listOf("first", "second"), gateway.calls)
    }

    @Test
    fun disabledAndIncompleteProvidersAreIgnored() = runBlocking {
        val disabled = first.copy(enabled = false)
        val incomplete = first.copy(id = "incomplete", apiKey = "")
        val gateway = ScriptedNewsGateway { _, _ -> mapOf("id" to "好") }
        val service = FailoverNewsTranslator(
            FixedNewsProviderStore(listOf(disabled, incomplete, second)),
            gateway,
        )

        service.translateSentences(listOf(NewsTranslationInput("id", "good", MaterialLanguage.ENGLISH)))

        assertEquals(listOf("second"), gateway.calls)
    }

    @Test
    fun combinedFailureMessageNeverIncludesProviderKey() {
        val gateway = ScriptedNewsGateway { provider, _ ->
            throw GatewayException("rejected ${provider.apiKey}")
        }
        val service = FailoverNewsTranslator(FixedNewsProviderStore(listOf(first)), gateway)

        val error = assertThrows(GatewayException::class.java) {
            runBlocking {
                service.translateTitles(listOf(NewsTranslationInput("id", "title", MaterialLanguage.ENGLISH)))
            }
        }

        assertFalse(error.message.orEmpty().contains(first.apiKey))
        assertTrue(error.message.orEmpty().contains("••••"))
    }
}

private class FixedNewsProviderStore(private val values: List<MaterialProviderConfig>) : MaterialProviderStore {
    override fun providers(): List<MaterialProviderConfig> = values
    override fun save(providers: List<MaterialProviderConfig>) = Unit
}

private class ScriptedNewsGateway(
    private val behavior: suspend (MaterialProviderConfig, List<NewsTranslationInput>) -> Map<String, String>,
) : NewsTranslationGateway {
    val calls = mutableListOf<String>()

    override suspend fun translateTitles(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
    ): Map<String, String> {
        calls += provider.id
        return behavior(provider, inputs)
    }

    override suspend fun translateSentences(
        provider: MaterialProviderConfig,
        inputs: List<NewsTranslationInput>,
    ): Map<String, String> {
        calls += provider.id
        return behavior(provider, inputs)
    }
}
