package com.example.englishcantoneselearning.speech

import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.SpeechLanguage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MiniMaxSpeechGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: MiniMaxSpeechGateway
    private lateinit var config: MiniMaxTtsConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        gateway = MiniMaxSpeechGateway(
            OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build(),
        )
        config = MiniMaxTtsConfig(
            baseUrl = "http://127.0.0.1:${server.port}",
            apiKey = "test-\r\nsecret",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mapsAllThreeLanguagesToConfiguredVoicesAndBoosts() = runBlocking {
        repeat(3) { server.enqueue(successResponse()) }

        val cases = listOf(
            Triple(SpeechLanguage.ENGLISH_US, "Serene_Woman", "English"),
            Triple(SpeechLanguage.CANTONESE_HK, "Cantonese_GentleLady", "Chinese,Yue"),
            Triple(SpeechLanguage.MANDARIN_CN, "female-tianmei", "Chinese"),
        )
        cases.forEach { (language, voice, boost) ->
            assertArrayEquals(byteArrayOf(0x49, 0x44, 0x33), gateway.synthesize(config, MiniMaxSpeechRequest("test", language, 0.9f)))
            val recorded = server.takeRequest()
            val body = JSONObject(recorded.body.readUtf8())
            assertEquals("/v1/t2a_v2", recorded.path)
            assertEquals("Bearer test-secret", recorded.getHeader("Authorization"))
            assertEquals("speech-2.8-turbo", body.getString("model"))
            assertEquals(voice, body.getJSONObject("voice_setting").getString("voice_id"))
            assertEquals(boost, body.getString("language_boost"))
            assertEquals("mp3", body.getJSONObject("audio_setting").getString("format"))
            assertEquals("hex", body.getString("output_format"))
        }
    }

    @Test
    fun rejectsServiceErrorAndMalformedAudioWithoutRetry() {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"base_resp":{"status_code":1004,"status_msg":"invalid api key"}}""",
            ),
        )
        val serviceError = assertThrows(MiniMaxSpeechException::class.java) {
            runBlocking { gateway.synthesize(config, MiniMaxSpeechRequest("test", SpeechLanguage.ENGLISH_US, 1f)) }
        }
        assertEquals("invalid api key", serviceError.message)
        assertEquals(1, server.requestCount)

        server.enqueue(
            MockResponse().setBody(
                """{"base_resp":{"status_code":0,"status_msg":"success"},"data":{"audio":"not-hex"}}""",
            ),
        )
        assertThrows(MiniMaxSpeechException::class.java) {
            runBlocking { gateway.synthesize(config, MiniMaxSpeechRequest("test", SpeechLanguage.ENGLISH_US, 1f)) }
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun cacheIdentityChangesWithSpeedVoiceAndText() {
        val request = MiniMaxSpeechRequest("hello", SpeechLanguage.ENGLISH_US, 0.9f)
        val original = gateway.cacheIdentity(config, request)

        org.junit.Assert.assertNotEquals(original, gateway.cacheIdentity(config, request.copy(speed = 1f)))
        org.junit.Assert.assertNotEquals(original, gateway.cacheIdentity(config.copy(englishVoice = "another"), request))
        org.junit.Assert.assertNotEquals(
            original,
            gateway.cacheIdentity(config, request.copy(voiceIdOverride = "preview-voice")),
        )
        org.junit.Assert.assertNotEquals(original, gateway.cacheIdentity(config, request.copy(text = "other")))
    }

    @Test
    fun explicitPreviewVoiceOverridesConfiguredDefault() = runBlocking {
        server.enqueue(successResponse())

        gateway.synthesize(
            config,
            MiniMaxSpeechRequest("preview", SpeechLanguage.CANTONESE_HK, 0.8f, voiceIdOverride = "custom-yue"),
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("custom-yue", body.getJSONObject("voice_setting").getString("voice_id"))
        assertEquals("Chinese,Yue", body.getString("language_boost"))
    }

    private fun successResponse() = MockResponse().setBody(
        """{"base_resp":{"status_code":0,"status_msg":"success"},"data":{"audio":"494433"}}""",
    )
}
