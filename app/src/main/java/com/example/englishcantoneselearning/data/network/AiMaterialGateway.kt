package com.example.englishcantoneselearning.data.network

import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.SourceReference

interface AiMaterialGateway {
    suspend fun supportsConfiguredModel(provider: MaterialProviderConfig): Boolean
    suspend fun generate(provider: MaterialProviderConfig, request: MaterialGenerationRequest): GeneratedBatch
    suspend fun generate(
        provider: MaterialProviderConfig,
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): GeneratedBatch = generate(provider, request)
}

data class GeneratedBatch(
    val responseId: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val materials: List<GeneratedMaterial>,
    val webSourceUrls: Set<String>,
    val providerId: String = "",
    val providerName: String = "",
    val model: String = "",
)

data class GeneratedMaterial(
    val title: String,
    val topic: String,
    val difficulty: String,
    val targetText: String,
    val sentences: List<GeneratedSentence>,
    val sources: List<SourceReference>,
    val sections: List<GeneratedSection> = emptyList(),
    val outlineSections: List<String> = emptyList(),
    val coveredSectionIds: List<String> = emptyList(),
    val coveredParagraphIds: List<String> = emptyList(),
    val hasMore: Boolean = false,
    val nextSectionIndex: Int = 0,
)

data class GeneratedSection(
    val id: String,
    val title: String,
    val startSentenceIndex: Int,
)

data class GeneratedSentence(
    val targetText: String,
    val jyutping: String,
    val simplifiedChinese: String,
)

open class GatewayException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : Exception(message, cause)
class AuthenticationException : GatewayException("API 密钥无效或没有访问权限")
class RateLimitException : GatewayException("请求过于频繁或账户额度不足，请稍后再试", retryable = true)
class ProviderOriginUnavailableException(statusCode: Int) : GatewayException(
    "服务商源站不可用（HTTP $statusCode），不是 Key 错误；请等待服务商恢复或配置不同 Base URL",
    retryable = true,
)
class InactivityTimeoutException : GatewayException(
    "连续2分钟没有收到模型活动，已停止该服务地址",
    retryable = true,
)
class WebSearchUnsupportedException : GatewayException("当前网关没有转发网页搜索工具，无法生成带真实来源的材料")
class GatewayFormatException(message: String) : GatewayException(message)
