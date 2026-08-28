package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.SourceReference
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FailoverMaterialGeneratorTest {
    private val first = MaterialProviderConfig("first", "First", "https://first.example", "model-a", "key-a")
    private val second = MaterialProviderConfig("second", "Second", "https://second.example", "model-b", "key-b")

    @Test
    fun transientFailureIsCalledOnceThenSecondProviderTakesOver() = runBlocking {
        val gateway = ScriptedGateway(
            mapOf(
                "first" to { throw GatewayException("HTTP 524", retryable = true) },
                "second" to { validBatch(second) },
            ),
        )
        val generator = FailoverMaterialGenerator(FixedProviderStore(listOf(first, second)), gateway)

        val result = generator.generate(request())

        assertEquals("Second", result.providerName)
        assertEquals("model-b", result.model)
        assertEquals(listOf("first", "second"), gateway.calls)
    }

    @Test
    fun origin521SkipsDuplicateBaseUrlsAndUsesNextDifferentHost() = runBlocking {
        val duplicate = MaterialProviderConfig(
            "duplicate", "Duplicate", "https://first.example/v1", "model-b", "key-b",
        )
        val different = MaterialProviderConfig(
            "different", "Different", "https://different.example", "model-c", "key-c",
        )
        val gateway = ScriptedGateway(
            mapOf(
                "first" to { throw ProviderOriginUnavailableException(521) },
                "duplicate" to { error("same origin must be skipped") },
                "different" to { validBatch(different) },
            ),
        )
        val generator = FailoverMaterialGenerator(
            FixedProviderStore(listOf(first, duplicate, different)),
            gateway,
        )

        val result = generator.generate(request())

        assertEquals("Different", result.providerName)
        assertEquals(listOf("first", "different"), gateway.calls)
    }

    @Test
    fun allProvidersOnDeadOriginProduceOneRequestAndClearExplanation() {
        val duplicate = MaterialProviderConfig(
            "duplicate", "Duplicate", "https://first.example/v1/", "model-b", "key-b",
        )
        val gateway = ScriptedGateway(
            mapOf("first" to { throw ProviderOriginUnavailableException(521) }),
        )
        val generator = FailoverMaterialGenerator(FixedProviderStore(listOf(first, duplicate)), gateway)

        val error = assertThrows(GatewayException::class.java) {
            runBlocking { generator.generate(request()) }
        }

        assertEquals(listOf("first"), gateway.calls)
        assertTrue(error.message.orEmpty().contains("源站不可用"))
        assertTrue(error.message.orEmpty().contains("同一服务地址，已跳过"))
    }

    @Test
    fun everyFailureCategoryImmediatelyMovesToNextProvider() {
        val failures = listOf(
            AuthenticationException(),
            RateLimitException(),
            GatewayException("network", retryable = true),
            WebSearchUnsupportedException(),
            GatewayFormatException("invalid sources"),
        )
        failures.forEach { failure ->
            val gateway = ScriptedGateway(
                mapOf(
                    "first" to { throw failure },
                    "second" to { validBatch(second) },
                ),
            )
            val result = runBlocking {
                FailoverMaterialGenerator(FixedProviderStore(listOf(first, second)), gateway).generate(request())
            }
            assertEquals("Second", result.providerName)
            assertEquals(listOf("first", "second"), gateway.calls)
        }
    }

    @Test
    fun providerTimeoutCancelsFirstAndStartsSecond() = runTest {
        val gateway = ScriptedGateway(
            mapOf(
                "first" to { awaitCancellation() },
                "second" to { validBatch(second) },
            ),
        )
        val generator = FailoverMaterialGenerator(
            FixedProviderStore(listOf(first, second)),
            gateway,
            providerTimeoutMs = 90,
        )

        val result = generator.generate(request())

        assertEquals("Second", result.providerName)
        assertEquals(listOf("first", "second"), gateway.calls)
    }

    @Test
    fun providerTimeoutSkipsDuplicateOriginAndUsesDifferentService() = runTest {
        val duplicate = MaterialProviderConfig(
            "duplicate", "Duplicate", "https://first.example/v1", "model-b", "key-b",
        )
        val different = MaterialProviderConfig(
            "different", "Different", "https://different.example", "model-c", "key-c",
        )
        val gateway = ScriptedGateway(
            mapOf(
                "first" to { awaitCancellation() },
                "duplicate" to { error("same timed-out origin must be skipped") },
                "different" to { validBatch(different) },
            ),
        )
        val generator = FailoverMaterialGenerator(
            FixedProviderStore(listOf(first, duplicate, different)),
            gateway,
            providerTimeoutMs = 90,
        )

        val result = generator.generate(request())

        assertEquals("Different", result.providerName)
        assertEquals(listOf("first", "different"), gateway.calls)
    }

    @Test
    fun compatibilityWorkSharesOneProviderTimeoutBudget() = runTest {
        val gateway = ScriptedGateway(
            mapOf(
                "first" to {
                    delay(60)
                    // Represents the strict-JSON compatibility request after a fast schema rejection.
                    delay(50)
                    validBatch(first)
                },
                "second" to { validBatch(second) },
            ),
        )
        val generator = FailoverMaterialGenerator(
            FixedProviderStore(listOf(first, second)),
            gateway,
            providerTimeoutMs = 90,
        )

        val result = generator.generate(request())

        assertEquals("Second", result.providerName)
        assertEquals(listOf("first", "second"), gateway.calls)
    }

    @Test
    fun userCancellationStopsWithoutCallingNextProvider() = runTest {
        val gateway = ScriptedGateway(
            mapOf(
                "first" to { awaitCancellation() },
                "second" to { validBatch(second) },
            ),
        )
        val generator = FailoverMaterialGenerator(
            FixedProviderStore(listOf(first, second)),
            gateway,
            providerTimeoutMs = 90_000,
        )

        val job = launch { generator.generate(request()) }
        testScheduler.runCurrent()
        job.cancelAndJoin()

        assertEquals(listOf("first"), gateway.calls)
    }

    @Test
    fun onlyProviderTimeoutProducesImmediateAggregateError() {
        val gateway = ScriptedGateway(mapOf("first" to { awaitCancellation() }))
        val generator = FailoverMaterialGenerator(
            FixedProviderStore(listOf(first)),
            gateway,
            providerTimeoutMs = 25,
        )

        val error = assertThrows(GatewayException::class.java) {
            runBlocking { generator.generate(request()) }
        }

        assertEquals(listOf("first"), gateway.calls)
        assertTrue(error.message.orEmpty().contains("First"))
        assertTrue(error.message.orEmpty().contains("超过1秒"))
    }

    @Test
    fun validationFailureMovesNextAndAggregateErrorsRemainProviderOrdered() {
        val invalid = validBatch(first).copy(materials = emptyList())
        val gateway = ScriptedGateway(
            mapOf(
                "first" to { invalid },
                "second" to { throw GatewayException("not supported") },
            ),
        )
        val generator = FailoverMaterialGenerator(FixedProviderStore(listOf(first, second)), gateway)

        val error = assertThrows(GatewayException::class.java) {
            runBlocking { generator.generate(request()) }
        }

        assertEquals(listOf("first", "second"), gateway.calls)
        assertTrue(error.message.orEmpty().indexOf("First") < error.message.orEmpty().indexOf("Second"))
        assertTrue(error.message.orEmpty().contains("每章必须返回 1 篇"))
    }

    private fun request() = MaterialGenerationRequest(
        language = MaterialLanguage.ENGLISH,
        difficulty = Difficulty.EASY,
        topic = MaterialTopic.DAILY,
        currentDate = "2026-08-28",
    )

    private fun validBatch(provider: MaterialProviderConfig): GeneratedBatch {
        val sources = listOf("https://source.example/article-1")
        val materials = sources.mapIndexed { index, url ->
            val sentences = (1..12).map { sentence ->
                val text = (1..11).joinToString(" ") { word -> "word${index}_${sentence}_$word" }
                GeneratedSentence(text, "", "这是对应的简体中文译文。")
            }
            GeneratedMaterial(
                title = "Material $index",
                topic = "日常",
                difficulty = Difficulty.EASY.name,
                targetText = sentences.joinToString(" ") { it.targetText },
                sentences = sentences,
                sources = listOf(SourceReference("Source $index", "Publisher", url, null, "English")),
                hasMore = true,
            )
        }
        return GeneratedBatch(
            responseId = "response",
            inputTokens = 1,
            outputTokens = 2,
            materials = materials,
            webSourceUrls = sources.toSet(),
            providerId = provider.id,
            providerName = provider.name,
            model = provider.model,
        )
    }
}

private class FixedProviderStore(private val values: List<MaterialProviderConfig>) : MaterialProviderStore {
    override fun providers() = values
    override fun save(providers: List<MaterialProviderConfig>) = Unit
}

private class ScriptedGateway(
    private val behaviors: Map<String, suspend () -> GeneratedBatch>,
) : AiMaterialGateway {
    val calls = mutableListOf<String>()

    override suspend fun supportsConfiguredModel(provider: MaterialProviderConfig) = true

    override suspend fun generate(
        provider: MaterialProviderConfig,
        request: MaterialGenerationRequest,
    ): GeneratedBatch {
        calls += provider.id
        return behaviors.getValue(provider.id).invoke()
    }
}
