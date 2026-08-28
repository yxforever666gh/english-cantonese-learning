package com.example.englishcantoneselearning.model

data class ArticleSourceDefinition(
    val id: String,
    val publisher: String,
    val feedUrl: String,
    val language: MaterialLanguage,
    val topics: Set<MaterialTopic>,
    val allowedHosts: Set<String>,
    val contentSelectors: List<String> = emptyList(),
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
