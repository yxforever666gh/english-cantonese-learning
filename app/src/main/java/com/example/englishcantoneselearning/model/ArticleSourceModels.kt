package com.example.englishcantoneselearning.model

data class ArticleSourceDefinition(
    val id: String,
    val publisher: String,
    val feedUrl: String,
    val language: MaterialLanguage,
    val topics: Set<MaterialTopic>,
    val allowedHosts: Set<String>,
    val contentSelectors: List<String> = emptyList(),
    val defaultNewsTags: Set<NewsTag> = emptySet(),
)

enum class NewsTag(val displayName: String) {
    INTERNATIONAL("国际"),
    HONG_KONG("香港"),
    LOCAL_LIFE("本地民生"),
    GOVERNMENT_POLICY("政府政策"),
    SAFETY_LAW("安全法律"),
    FINANCE("财经"),
    CAREERS("就业职场"),
    TECHNOLOGY("科技"),
    AI_DIGITAL("AI数码"),
    SCIENCE_SPACE("科学太空"),
    ENVIRONMENT_CLIMATE("环境气候"),
    HEALTH("健康"),
    EDUCATION("教育"),
    CULTURE_ARTS("文化艺术"),
    ENTERTAINMENT_MUSIC("影视音乐"),
}

data class NewsItem(
    val sourceId: String,
    val publisher: String,
    val title: String,
    val url: String,
    val publishedAt: String?,
    val publishedAtEpochMillis: Long?,
    val summary: String,
    val language: MaterialLanguage,
    val tags: Set<NewsTag>,
)

data class SourceCandidate(
    val sourceId: String,
    val publisher: String,
    val title: String,
    val url: String,
    val publishedAt: String?,
    val summary: String,
    val score: Int = 0,
)

data class SourceParagraph(
    val id: String,
    val heading: String?,
    val text: String,
)

data class SourceArticleSnapshot(
    val sourceId: String,
    val publisher: String,
    val title: String,
    val url: String,
    val publishedAt: String?,
    val sourceLanguage: String,
    val paragraphs: List<SourceParagraph>,
    val contentHash: String,
    val fetchedAt: Long,
    val cleanerVersion: String,
)
