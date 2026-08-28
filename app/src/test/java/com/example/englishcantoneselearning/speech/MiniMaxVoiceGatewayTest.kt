package com.example.englishcantoneselearning.speech

import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.SpeechLanguage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MiniMaxVoiceGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: MiniMaxVoiceGateway
    private lateinit var config: MiniMaxTtsConfig

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        gateway = MiniMaxVoiceGateway(
            client = OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build(),
            now = { 1234L },
        )
        config = MiniMaxTtsConfig(
            baseUrl = "http://127.0.0.1:${server.port}",
            apiKey = " test-\r\nkey ",
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun fetchesAndClassifiesSystemClonedAndDesignedVoices() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "system_voice":[
                    {"voice_id":"Cantonese_GentleLady","voice_name":"温柔女声","description":["粤语女声"]},
                    {"voice_id":"English_Graceful_Lady","voice_name":"Graceful Lady","description":[]}
                  ],
                  "voice_cloning":[{"voice_id":"my-clone","description":[]}],
                  "voice_generation":[{"voice_id":"ttv-generated","description":[]}],
                  "music_generation":[{"voice_id":"ignored-music"}],
                  "base_resp":{"status_code":0,"status_msg":"success"}
                }""".trimIndent(),
            ),
        )

        val catalog = gateway.fetchVoices(config)
        val request = server.takeRequest()

        assertEquals("/v1/get_voice", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertEquals("all", JSONObject(request.body.readUtf8()).getString("voice_type"))
        assertEquals(1234L, catalog.fetchedAt)
        assertEquals(4, catalog.voices.size)
        assertEquals(MiniMaxVoiceKind.SYSTEM, catalog.voices.first { it.id == "Cantonese_GentleLady" }.kind)
        assertTrue(SpeechLanguage.CANTONESE_HK in catalog.voices.first { it.id == "Cantonese_GentleLady" }.supportedLanguages)
        assertTrue(SpeechLanguage.ENGLISH_US in catalog.voices.first { it.id == "English_Graceful_Lady" }.supportedLanguages)
        assertEquals(MiniMaxVoiceKind.CLONED, catalog.voices.first { it.id == "my-clone" }.kind)
        assertEquals(MiniMaxVoiceKind.DESIGNED, catalog.voices.first { it.id == "ttv-generated" }.kind)
        assertTrue(catalog.voices.none { it.id == "ignored-music" })
    }

    @Test fun exposesServiceErrorWithoutRetry() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"base_resp":{"status_code":1004,"status_msg":"invalid api key"}}""",
            ),
        )

        val error = assertThrows(MiniMaxSpeechException::class.java) {
            runBlocking { gateway.fetchVoices(config) }
        }

        assertEquals("invalid api key", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectsMalformedOrEmptyCatalog() {
        server.enqueue(MockResponse().setBody("not-json"))
        assertThrows(MiniMaxSpeechException::class.java) { runBlocking { gateway.fetchVoices(config) } }

        server.enqueue(MockResponse().setBody("""{"base_resp":{"status_code":0},"system_voice":[]}"""))
        assertThrows(MiniMaxSpeechException::class.java) { runBlocking { gateway.fetchVoices(config) } }
    }
}
