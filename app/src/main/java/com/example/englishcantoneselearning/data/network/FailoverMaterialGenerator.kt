package com.example.englishcantoneselearning.data.network

import android.util.Log
import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.data.repository.MaterialValidator
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.GenerationStage
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

interface MaterialGenerator {
    suspend fun test(provider: MaterialProviderConfig): Boolean
    suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch
    suspend fun generate(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): GeneratedBatch = generate(request)
}

class FailoverMaterialGenerator(
    private val store: MaterialProviderStore,
    private val gateway: AiMaterialGateway,
    private val providerTimeoutMs: Long = DEFAULT_PROVIDER_TIMEOUT_MS,
) : MaterialGenerator {
    override suspend fun test(provider: MaterialProviderConfig): Boolean =
        gateway.supportsConfiguredModel(provider)

    override suspend fun generate(request: MaterialGenerationRequest): GeneratedBatch = generate(request) {}

    override suspend fun generate(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): GeneratedBatch {
        val providers = store.providers().filter { it.enabled && isComplete(it) }
        if (providers.isEmpty()) throw GatewayException("请先在设置中添加并启用材料模型")

        val failures = mutableListOf<String>()
        val unavailableOrigins = mutableSetOf<String>()
        providers.forEach { provider ->
            val origin = originKey(provider.baseUrl)
            if (origin in unavailableOrigins) {
                failures += "${provider.name}：与已宕机模型使用同一服务地址，已跳过"
                safeLog("skip provider=${provider.name} reason=same_unavailable_origin")
                return@forEach
            }
            try {
                safeLog("start provider=${provider.name} model=${provider.model}")
                onActivity(activity(provider, request, GenerationStage.CONNECTING, "provider.start"))
                val operation: suspend () -> GeneratedBatch = {
                    gateway.generate(provider, request, onActivity).also {
                        onActivity(activity(provider, request, GenerationStage.VALIDATING, "client.validating"))
                        MaterialValidator.validate(request, it)
                        safeLog("validated provider=${provider.name} materials=${it.materials.size}")
                    }
                }
                return if (providerTimeoutMs > 0) withTimeout(providerTimeoutMs) { operation() } else operation()
            } catch (_: TimeoutCancellationException) {
                val timeoutSeconds = (providerTimeoutMs + 999) / 1_000
                unavailableOrigins += origin
                failures += "${provider.name}：生成超过${formatTimeout(timeoutSeconds)}，已停止该服务地址"
                safeLog("failed provider=${provider.name} reason=timeout seconds=$timeoutSeconds")
            } catch (cancelled: CancellationException) {
                safeLog("cancelled provider=${provider.name}")
                throw cancelled
            } catch (error: ProviderOriginUnavailableException) {
                unavailableOrigins += origin
                failures += "${provider.name}：${safeMessage(provider, error)}"
                safeLog("failed provider=${provider.name} reason=${error.javaClass.simpleName} message=${safeMessage(provider, error)}")
                onActivity(activity(provider, request, GenerationStage.FAILOVER, "provider.origin_unavailable"))
            } catch (error: InactivityTimeoutException) {
                unavailableOrigins += origin
                failures += "${provider.name}：${safeMessage(provider, error)}"
                safeLog("failed provider=${provider.name} reason=inactivity_timeout")
                onActivity(activity(provider, request, GenerationStage.FAILOVER, "provider.inactivity_timeout"))
            } catch (error: Throwable) {
                failures += "${provider.name}：${safeMessage(provider, error)}"
                safeLog("failed provider=${provider.name} reason=${error.javaClass.simpleName} message=${safeMessage(provider, error)}")
            }
        }
        throw GatewayException("所有材料模型均失败：${failures.joinToString("；")}")
    }

    private fun activity(
        provider: MaterialProviderConfig,
        request: MaterialGenerationRequest,
        stage: GenerationStage,
        eventType: String,
    ) = GenerationActivity(provider.name, request.chapterIndex + 1, stage, eventType)

    private fun isComplete(provider: MaterialProviderConfig): Boolean =
        provider.name.isNotBlank() && provider.baseUrl.isNotBlank() &&
            provider.model.isNotBlank() && provider.apiKey.isNotBlank()

    private fun originKey(baseUrl: String): String = runCatching {
        val uri = URI(baseUrl.trim())
        val scheme = uri.scheme.lowercase()
        val port = when {
            uri.port == -1 -> if (scheme == "https") 443 else 80
            else -> uri.port
        }
        "$scheme://${uri.host.lowercase()}:$port"
    }.getOrDefault(baseUrl.trim().trimEnd('/').lowercase())

    private fun safeMessage(provider: MaterialProviderConfig, error: Throwable): String =
        error.message.orEmpty()
            .replace(provider.apiKey, "••••")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .take(240)
            .ifBlank { "请求失败" }

    private fun safeLog(message: String) {
        // Never include credentials, prompts, generated text, or response bodies.
        runCatching { Log.i(LOG_TAG, message.replace('\r', ' ').replace('\n', ' ').take(360)) }
    }

    private fun formatTimeout(seconds: Long): String =
        if (seconds >= 60 && seconds % 60L == 0L) "${seconds / 60}分钟" else "${seconds}秒"

    private companion object {
        const val LOG_TAG = "MaterialFailover"
        // Web search plus structured generation frequently needs more than 90 seconds.
        // Five minutes matches the proven research-chain budget while still providing a hard stop.
        const val DEFAULT_PROVIDER_TIMEOUT_MS = 0L
    }
}
