package com.example.englishcantoneselearning.ui.news

import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.preferences.SpeechSpeedPreferences
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.data.source.FixedSourceRepository
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.model.BilingualSentence
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.GenerationActivity
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialGenerationRequest
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SourceParagraph
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import com.example.englishcantoneselearning.ui.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sources: FakeSourceRepository
    private lateinit var materials: FakeMaterialRepository
    private lateinit var preferences: FakeNewsPreferences
    private lateinit var speech: FakeNewsSpeechController
    private lateinit var viewModel: NewsViewModel

    @Before
    fun setUp() {
        sources = FakeSourceRepository()
        materials = FakeMaterialRepository()
        preferences = FakeNewsPreferences()
        speech = FakeNewsSpeechController()
        viewModel = NewsViewModel(sources, materials, preferences, speech, now = { 1234L })
    }

    @Test
    fun enteringRefreshesAndTagSelectionFiltersWithoutAnotherRequest() = runTest {
        val technology = news("Technology story", NewsTag.TECHNOLOGY)
        val health = news("Health story", NewsTag.HEALTH)
        sources.feed = listOf(technology, health)

        viewModel.onEnter()
        advanceUntilIdle()

        assertEquals(listOf(true), sources.forceRefreshValues)
        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals(1234L, viewModel.uiState.value.updatedAt)
        viewModel.toggleTag(NewsTag.TECHNOLOGY)
        assertEquals(listOf(technology), viewModel.uiState.value.visibleItems)
        assertEquals(1, viewModel.uiState.value.tagCounts.getValue(NewsTag.HEALTH))
        assertEquals(1, sources.forceRefreshValues.size)
    }

    @Test
    fun failedRefreshKeepsLastSuccessfulList() = runTest {
        val existing = news("Existing", NewsTag.INTERNATIONAL)
        sources.feed = listOf(existing)
        viewModel.onEnter()
        advanceUntilIdle()
        sources.failure = IllegalStateException("offline")

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(existing), viewModel.uiState.value.items)
        assertEquals("offline", viewModel.uiState.value.feedError)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun languageSwitchKeepsBothFeedsAndUsesLanguageSpeed() = runTest {
        sources.feed = listOf(news("English", NewsTag.INTERNATIONAL))
        viewModel.onEnter()
        advanceUntilIdle()
        preferences.setSpeechSpeed(SpeechLanguage.CANTONESE_HK, 1.2f)
        sources.feed = listOf(news("粵語", NewsTag.HONG_KONG, MaterialLanguage.CANTONESE))

        viewModel.setLanguage(MaterialLanguage.CANTONESE)
        advanceUntilIdle()

        assertEquals("粵語", viewModel.uiState.value.items.single().title)
        assertEquals(1.2f, viewModel.uiState.value.speed)
        sources.feed = listOf(news("English", NewsTag.INTERNATIONAL))
        viewModel.setLanguage(MaterialLanguage.ENGLISH)
        advanceUntilIdle()
        assertEquals("English", viewModel.uiState.value.items.single().title)
    }

    @Test
    fun articleIsLocallySegmentedAndSavedWithItsTags() = runTest {
        val item = news("Story", NewsTag.SCIENCE_SPACE)
        sources.article = snapshot(
            SourceParagraph("p1", "First section", "First sentence. Second sentence."),
            SourceParagraph("p2", "Second section", "Third sentence."),
        )

        viewModel.openArticle(item)
        advanceUntilIdle()

        assertEquals(listOf("First sentence.", "Second sentence.", "Third sentence."),
            viewModel.uiState.value.sentences.map { it.text })
        assertEquals(listOf(0, 2), viewModel.uiState.value.sections.map { it.startSentenceIndex })
        viewModel.saveArticle()
        advanceUntilIdle()

        assertEquals(setOf(NewsTag.SCIENCE_SPACE), materials.savedTags)
        assertEquals(viewModel.uiState.value.sentences.map { it.text }, materials.savedSentences)
        assertEquals("saved-news", viewModel.uiState.value.savedMaterialId)
    }

    @Test
    fun closingArticleCancelsPendingLoadAndCannotPublishStaleResult() = runTest {
        val blocker = CompletableDeferred<Unit>()
        sources.articleBlocker = blocker

        viewModel.openArticle(news("Slow", NewsTag.CULTURE_ARTS))
        runCurrent()
        viewModel.closeArticle()
        blocker.complete(Unit)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedItem)
        assertNull(viewModel.uiState.value.article)
        assertTrue(viewModel.uiState.value.sentences.isEmpty())
    }

    @Test
    fun playbackPreloadsExactlyTheNextTwoSentencesConcurrently() = runTest {
        sources.article = snapshot(
            SourceParagraph("p", null, "One. Two. Three. Four."),
        )
        viewModel.openArticle(news("Story", NewsTag.INTERNATIONAL))
        advanceUntilIdle()
        speech.preloadBlocker = CompletableDeferred()

        viewModel.playOrPause()
        runCurrent()

        assertEquals("One.", speech.spoken.single().text)
        assertEquals(setOf("Two.", "Three."), speech.preloadStarted.toSet())
        assertEquals(2, speech.preloadStarted.size)
        speech.preloadBlocker?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun staleSpeechCompletionIsIgnoredAfterJump() = runTest {
        sources.article = snapshot(SourceParagraph("p", null, "One. Two. Three."))
        viewModel.openArticle(news("Story", NewsTag.INTERNATIONAL))
        advanceUntilIdle()
        viewModel.playOrPause()
        val staleId = speech.spoken.single().requestId
        viewModel.selectAndPlay(2)

        speech.emit(SpeechEvent.Done(staleId))
        runCurrent()

        assertEquals(2, viewModel.uiState.value.selectedSentenceIndex)
        assertEquals(2, speech.spoken.size)
    }

    @Test
    fun speedChangeIsGlobalAndRestartsActiveSentence() = runTest {
        sources.article = snapshot(SourceParagraph("p", null, "One. Two."))
        viewModel.openArticle(news("Story", NewsTag.INTERNATIONAL))
        advanceUntilIdle()
        viewModel.playOrPause()

        viewModel.setSpeechSpeed(1.1f)
        viewModel.onSpeechSpeedChangeFinished()

        assertEquals(1.1f, preferences.speechSpeed(SpeechLanguage.ENGLISH_US))
        assertEquals(2, speech.spoken.size)
        assertEquals(1.1f, speech.spoken.last().speed)
    }
}

private class FakeSourceRepository : FixedSourceRepository {
    var feed: List<NewsItem> = emptyList()
    var article: SourceArticleSnapshot = snapshot(SourceParagraph("p", null, "Default sentence."))
    var failure: Throwable? = null
    var articleBlocker: CompletableDeferred<Unit>? = null
    val forceRefreshValues = mutableListOf<Boolean>()

    override suspend fun refreshFeed(language: MaterialLanguage, forceRefresh: Boolean): List<NewsItem> {
        forceRefreshValues += forceRefresh
        failure?.let { throw it }
        return feed.filter { it.language == language }
    }

    override suspend fun loadArticle(item: NewsItem): SourceArticleSnapshot {
        articleBlocker?.await()
        return article
    }

    override suspend fun discover(
        request: MaterialGenerationRequest,
        onActivity: (GenerationActivity) -> Unit,
    ): SourceArticleSnapshot = article
}

private class FakeMaterialRepository : MaterialRepository {
    var savedTags: Set<NewsTag> = emptySet()
    var savedSentences: List<String> = emptyList()

    override suspend fun listMaterials(): List<PracticeMaterial> = emptyList()
    override suspend fun getMaterial(id: String): PracticeMaterial? = null
    override suspend fun recentSourceUrls(limit: Int): List<String> = emptyList()
    override suspend fun deleteMaterial(id: String) = Unit
    override suspend fun deleteBatch(batchId: String) = Unit

    override suspend fun saveNewsArticle(
        snapshot: SourceArticleSnapshot,
        language: MaterialLanguage,
        tags: Set<NewsTag>,
        sentenceTexts: List<String>,
    ): PracticeMaterial {
        savedTags = tags
        savedSentences = sentenceTexts
        return PracticeMaterial(
            id = "saved-news",
            batchId = "saved-news",
            batchPosition = 0,
            language = language,
            difficulty = Difficulty.TARGET,
            topic = "新闻收藏",
            title = snapshot.title,
            targetText = sentenceTexts.joinToString(" "),
            sentences = sentenceTexts.mapIndexed { index, text ->
                BilingualSentence("$index", text, null, null)
            },
            sources = emptyList(),
            createdAt = 0,
            promptVersion = "news-feed-v1",
            providerName = snapshot.publisher,
            model = "",
            responseId = "",
            inputTokens = 0,
            outputTokens = 0,
            requestFingerprint = snapshot.contentHash,
            origin = ArticleOrigin.NEWS_FEED,
        )
    }
}

private class FakeNewsPreferences : LearnerPreferences {
    private val mutableSpeeds = MutableStateFlow(SpeechSpeedPreferences())
    override val speechSpeeds: StateFlow<SpeechSpeedPreferences> = mutableSpeeds
    private var language = MaterialLanguage.ENGLISH
    override fun learnerProfile() = LearnerProfile()
    override fun setListeningBand(band: Float) = Unit
    override fun articleLibraryLanguage(): MaterialLanguage = language
    override fun setArticleLibraryLanguage(language: MaterialLanguage) { this.language = language }
    override fun speechSpeed(language: SpeechLanguage): Float = mutableSpeeds.value.forLanguage(language)
    override fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        mutableSpeeds.value = mutableSpeeds.value.withSpeed(language, speed)
    }
}

private class FakeNewsSpeechController : SpeechController {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<SpeechEvent> = mutableEvents
    val spoken = mutableListOf<SpokenNewsRequest>()
    val preloadStarted = mutableListOf<String>()
    var preloadBlocker: CompletableDeferred<Unit>? = null

    override fun checkAvailability(language: SpeechLanguage): TtsAvailability = TtsAvailability.READY
    override fun speak(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        speed: Float,
        startOffset: Int,
    ): Boolean {
        spoken += SpokenNewsRequest(requestId, text, language, speed)
        return true
    }
    override suspend fun preload(text: String, language: SpeechLanguage, speed: Float): Boolean {
        preloadStarted += text
        preloadBlocker?.await()
        return true
    }
    override fun stop() = Unit
    override fun shutdown() = Unit
    suspend fun emit(event: SpeechEvent) { mutableEvents.emit(event) }
}

private data class SpokenNewsRequest(
    val requestId: Long,
    val text: String,
    val language: SpeechLanguage,
    val speed: Float,
)

private fun news(
    title: String,
    tag: NewsTag,
    language: MaterialLanguage = MaterialLanguage.ENGLISH,
) = NewsItem(
    sourceId = title.lowercase().replace(' ', '-'),
    publisher = "Publisher",
    title = title,
    url = "https://example.com/${title.hashCode()}",
    publishedAt = "2026-09-01",
    publishedAtEpochMillis = 0,
    summary = "Summary",
    language = language,
    tags = setOf(tag),
)

private fun snapshot(vararg paragraphs: SourceParagraph) = SourceArticleSnapshot(
    sourceId = "source",
    publisher = "Publisher",
    title = "Story",
    url = "https://example.com/story",
    publishedAt = "2026-09-01",
    sourceLanguage = "English",
    paragraphs = paragraphs.toList(),
    contentHash = "hash",
    fetchedAt = 0,
    cleanerVersion = "test",
)
