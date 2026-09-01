package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.data.preferences.MaterialProviderStore
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

interface NewsTranslationService {
    suspend fun translateTitles(inputs: List<NewsTranslationInput>): Map<String, String>
    suspend fun translateSentences(inputs: List<NewsTranslationInput>): Map<String, String>
}

class FailoverNewsTranslator(
    private val store: MaterialProviderStore,
    private val gateway: NewsTranslationGateway,
    private val providerTimeoutMs: Long = DEFAULT_PROVIDER_TIMEOUT_MS,
) : NewsTranslationService {
    override suspend fun translateTitles(inputs: List<NewsTranslationInput>): Map<String, String> =
        execute { provider -> gateway.translateTitles(provider, inputs) }

    override suspend fun translateSentences(inputs: List<NewsTranslationInput>): Map<String, String> =
        execute { provider -> gateway.translateSentences(provider, inputs) }

    private suspend fun execute(operation: suspend (MaterialProviderConfig) -> Map<String, String>): Map<String, String> {
        val providers = store.providers().filter { it.enabled && it.isComplete() }
        if (providers.isEmpty()) throw GatewayException("请先在设置中添加并启用材料模型")

        val failures = mutableListOf<String>()
        val unavailableOrigins = mutableSetOf<String>()
        providers.forEach { provider ->
            val origin = originKey(provider.baseUrl)
            if (origin in unavailableOrigins) {
                failures += "${provider.name}：与已宕机模型使用同一服务地址，已跳过"
                return@forEach
            }
            try {
                return if (providerTimeoutMs > 0) {
                    withTimeout(providerTimeoutMs) { operation(provider) }
                } else {
                    operation(provider)
                }
            } catch (_: TimeoutCancellationException) {
                unavailableOrigins += origin
                failures += "${provider.name}：翻译请求超时"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderOriginUnavailableException) {
                unavailableOrigins += origin
                failures += "${provider.name}：${safeMessage(provider, error)}"
            } catch (error: InactivityTimeoutException) {
                unavailableOrigins += origin
                failures += "${provider.name}：${safeMessage(provider, error)}"
            } catch (error: Throwable) {
                failures += "${provider.name}：${safeMessage(provider, error)}"
            }
        }
        throw GatewayException("所有材料模型均无法完成新闻翻译：${failures.joinToString("；")}")
    }

    private fun MaterialProviderConfig.isComplete(): Boolean =
        name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    private fun originKey(baseUrl: String): String = runCatching {
        val uri = URI(baseUrl.trim())
        val scheme = uri.scheme.lowercase()
        val port = if (uri.port == -1) if (scheme == "https") 443 else 80 else uri.port
        "$scheme://${uri.host.lowercase()}:$port"
    }.getOrDefault(baseUrl.trim().trimEnd('/').lowercase())

    private fun safeMessage(provider: MaterialProviderConfig, error: Throwable): String =
        error.message.orEmpty()
            .replace(provider.apiKey, "••••")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .take(240)
            .ifBlank { "请求失败" }

    private companion object {
        const val DEFAULT_PROVIDER_TIMEOUT_MS = 120_000L
    }
}
