package com.example.englishcantoneselearning.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.englishcantoneselearning.data.preferences.DEFAULT_SPEECH_SPEED
import com.example.englishcantoneselearning.data.preferences.LearnerPreferences
import com.example.englishcantoneselearning.data.news.ArticleTranslationCache
import com.example.englishcantoneselearning.data.news.ArticleTranslationCacheKey
import com.example.englishcantoneselearning.data.news.TitleTranslationCache
import com.example.englishcantoneselearning.data.network.NewsTranslationInput
import com.example.englishcantoneselearning.data.network.NewsTranslationService
import com.example.englishcantoneselearning.data.repository.MaterialRepository
import com.example.englishcantoneselearning.data.source.FixedSourceRepository
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.SourceArticleSnapshot
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.toSpeechLanguage
import com.example.englishcantoneselearning.segmentation.RuleBasedSentenceSegmenter
import com.example.englishcantoneselearning.segmentation.SentenceSegmenter
import com.example.englishcantoneselearning.speech.SpeechController
import com.example.englishcantoneselearning.speech.SpeechEvent
import java.util.concurrent.atomic.AtomicLong
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NewsViewModel(
    private val sourceRepository: FixedSourceRepository,
    private val materialRepository: MaterialRepository,
    private val userPreferences: LearnerPreferences,
    private val speechController: SpeechController,
    private val translationService: NewsTranslationService,
    private val titleTranslationCache: TitleTranslationCache,
    private val articleTranslationCache: ArticleTranslationCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sentenceSegmenter: SentenceSegmenter = RuleBasedSentenceSegmenter(),
    private val now: () -> Long = System::currentTimeMillis,
    private val shutdownSpeechControllerOnClear: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        NewsUiState(
            speed = userPreferences.speechSpeed(SpeechLanguage.ENGLISH_US),
            mandarinSpeed = userPreferences.speechSpeed(SpeechLanguage.MANDARIN_CN),
            showTranslations = userPreferences.showNewsTranslations.value,
            readingFontSizeSp = userPreferences.readingFontSizeSp.value,
        ),
    )
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val feedJobs = mutableMapOf<MaterialLanguage, Job>()
    private val titleTranslationJobs = mutableMapOf<MaterialLanguage, Job>()
    private var articleJob: Job? = null
    private var articleTranslationJob: Job? = null
    private var preloadJob: Job? = null
    private var activeRequestId: Long? = null

    init {
        viewModelScope.launch { speechController.events.collect(::handleSpeechEvent) }
        viewModelScope.launch {
            userPreferences.speechSpeeds.collect { speeds ->
                _uiState.update { state ->
                    state.copy(
                        speed = speeds.forLanguage(state.language.toSpeechLanguage()),
                        mandarinSpeed = speeds.mandarin,
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferences.showNewsTranslations.collect(::applyTranslationVisibility)
        }
        viewModelScope.launch {
            userPreferences.readingFontSizeSp.collect { size ->
                _uiState.update { it.copy(readingFontSizeSp = size) }
            }
        }
        refreshTtsAvailability()
    }

    /** Called whenever the news destination becomes visible. */
    fun onEnter() {
        refresh(forceRefresh = true)
    }

    fun refresh(forceRefresh: Boolean = true) {
        val language = _uiState.value.language
        feedJobs.remove(language)?.cancel()
        _uiState.update { state ->
            state.copy(
                refreshingLanguages = state.refreshingLanguages + language,
                feedErrors = state.feedErrors - language,
            )
        }
        feedJobs[language] = viewModelScope.launch {
            try {
                val items = sourceRepository.refreshFeed(language, forceRefresh)
                _uiState.update { state ->
                    state.copy(
                        feeds = state.feeds + (language to items),
                        refreshingLanguages = state.refreshingLanguages - language,
                        feedErrors = state.feedErrors - language,
                        lastUpdatedAt = state.lastUpdatedAt + (language to now()),
                    )
                }
                if (_uiState.value.showTranslations) startTitleTranslation(language, items)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        refreshingLanguages = state.refreshingLanguages - language,
                        feedErrors = state.feedErrors +
                            (language to (error.message ?: "新闻刷新失败，请稍后重试")),
                    )
                }
            }
        }
    }

    fun setLanguage(language: MaterialLanguage) {
        if (language == _uiState.value.language) return
        closeArticle()
        _uiState.update {
            it.copy(
                language = language,
                selectedTags = emptySet(),
                speed = userPreferences.speechSpeed(language.toSpeechLanguage()),
                userMessage = null,
            )
        }
        refresh(forceRefresh = true)
        refreshTtsAvailability()
    }

    fun toggleTag(tag: NewsTag) {
        _uiState.update { state ->
            state.copy(
                selectedTags = if (tag in state.selectedTags) {
                    state.selectedTags - tag
                } else {
                    state.selectedTags + tag
                },
            )
        }
    }

    fun showLatest() {
        _uiState.update { it.copy(selectedTags = emptySet()) }
    }

    fun setShowTranslations(show: Boolean) {
        userPreferences.setShowNewsTranslations(show)
    }

    fun setReadingFontSizeSp(size: Int) {
        userPreferences.setReadingFontSizeSp(size)
    }

    fun openArticle(item: NewsItem) {
        articleJob?.cancel()
        articleTranslationJob?.cancel()
        stopPlayback()
        _uiState.update {
            it.copy(
                language = item.language,
                selectedItem = item,
                article = null,
                sentences = emptyList(),
                sections = emptyList(),
                isArticleLoading = true,
                articleError = null,
                sentenceTranslations = emptyMap(),
                isArticleTranslating = false,
                articleTranslationError = null,
                isSaving = false,
                savedMaterialId = null,
                selectedSentenceIndex = -1,
                bilingualPhase = BilingualPhase.TARGET,
                characterOffset = 0,
                speed = userPreferences.speechSpeed(item.language.toSpeechLanguage()),
                userMessage = null,
            )
        }
        articleJob = viewModelScope.launch {
            try {
                val snapshot = sourceRepository.loadArticle(item)
                val segmented = segment(snapshot, item.language)
                if (segmented.sentences.isEmpty()) error("正文中没有可朗读的句子")
                if (_uiState.value.selectedItem?.url != item.url) return@launch
                _uiState.update {
                    it.copy(
                        article = snapshot,
                        sentences = segmented.sentences,
                        sections = segmented.sections,
                        isArticleLoading = false,
                        articleError = null,
                        selectedSentenceIndex = 0,
                    )
                }
                prepareArticleTranslation(snapshot, segmented.sentences, item.language)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_uiState.value.selectedItem?.url == item.url) {
                    _uiState.update {
                        it.copy(
                            isArticleLoading = false,
                            articleError = error.message ?: "新闻正文加载失败",
                        )
                    }
                }
            }
        }
    }

    fun retryArticle() {
        _uiState.value.selectedItem?.let(::openArticle)
    }

    fun closeArticle() {
        articleJob?.cancel()
        articleJob = null
        articleTranslationJob?.cancel()
        articleTranslationJob = null
        stopPlayback()
        _uiState.update {
            it.copy(
                selectedItem = null,
                article = null,
                sentences = emptyList(),
                sections = emptyList(),
                isArticleLoading = false,
                articleError = null,
                sentenceTranslations = emptyMap(),
                isArticleTranslating = false,
                articleTranslationError = null,
                isSaving = false,
                savedMaterialId = null,
                selectedSentenceIndex = -1,
                bilingualPhase = BilingualPhase.TARGET,
                characterOffset = 0,
            )
        }
    }

    fun onLeaveScreen() {
        closeArticle()
    }

    fun saveArticle() {
        val state = _uiState.value
        val item = state.selectedItem ?: return
        val snapshot = state.article ?: return
        if (state.sentences.isEmpty() || state.isSaving) return
        val translations = state.sentences.map { sentence ->
            state.sentenceTranslations[sentence.id].orEmpty().trim()
        }
        if (translations.any(String::isBlank)) {
            _uiState.update { it.copy(userMessage = "请等待全文翻译完成后再收藏") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val saved = materialRepository.saveNewsArticle(
                    snapshot = snapshot,
                    language = item.language,
                    tags = item.tags,
                    sentenceTexts = state.sentences.map(SentenceItem::text),
                    sentenceTranslations = translations,
                )
                if (_uiState.value.selectedItem?.url == item.url) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedMaterialId = saved.id,
                            userMessage = "已保存到材料库",
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(isSaving = false, userMessage = error.message ?: "保存新闻失败")
                }
            }
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _uiState.update { it.copy(playbackMode = mode) }
    }

    fun setSpeechSpeed(speed: Float) {
        setSpeechSpeed(_uiState.value.language.toSpeechLanguage(), speed)
    }

    fun setSpeechSpeed(language: SpeechLanguage, speed: Float) {
        val normalized = (speed.coerceIn(0.5f, 2.0f) * 10).roundToInt() / 10f
        _uiState.update {
            if (language == SpeechLanguage.MANDARIN_CN) it.copy(mandarinSpeed = normalized)
            else it.copy(speed = normalized)
        }
        userPreferences.setSpeechSpeed(language, normalized)
    }

    fun onSpeechSpeedChangeFinished() {
        onSpeechSpeedChangeFinished(_uiState.value.language.toSpeechLanguage())
    }

    fun onSpeechSpeedChangeFinished(language: SpeechLanguage) {
        val state = _uiState.value
        if (state.selectedSentenceIndex !in state.sentences.indices) return
        if (state.playbackStatus == PlaybackStatus.PLAYING ||
            state.playbackStatus == PlaybackStatus.PREPARING
        ) {
            val activeLanguage = if (state.bilingualPhase == BilingualPhase.TRANSLATION) {
                SpeechLanguage.MANDARIN_CN
            } else {
                state.language.toSpeechLanguage()
            }
            if (activeLanguage == language) {
                startSpeaking(state.selectedSentenceIndex, 0, state.bilingualPhase)
            } else {
                preloadWindow(state.selectedSentenceIndex, includeCurrent = true)
            }
        } else {
            preloadWindow(state.selectedSentenceIndex, includeCurrent = true)
        }
    }

    fun playOrPause() {
        when (_uiState.value.playbackStatus) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PREPARING -> stopPlayback()
            PlaybackStatus.PAUSED -> {
                if (speechController.resume()) {
                    _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
                } else {
                    startSpeaking(_uiState.value.selectedSentenceIndex, 0, _uiState.value.bilingualPhase)
                }
            }
            PlaybackStatus.IDLE -> {
                val index = _uiState.value.selectedSentenceIndex.coerceAtLeast(0)
                startSpeaking(index, 0, BilingualPhase.TARGET)
            }
        }
    }

    fun previousSentence() = navigateBy(-1)

    fun nextSentence() = navigateBy(1)

    fun selectAndPlay(index: Int) {
        if (index !in _uiState.value.sentences.indices) return
        stopPlayback()
        startSpeaking(index, 0, BilingualPhase.TARGET)
    }

    fun refreshTtsAvailability() {
        val target = speechController.checkAvailability(_uiState.value.language.toSpeechLanguage())
        val mandarin = speechController.checkAvailability(SpeechLanguage.MANDARIN_CN)
        if ((target != TtsAvailability.READY ||
            (_uiState.value.showTranslations && mandarin != TtsAvailability.READY)) &&
            _uiState.value.playbackStatus != PlaybackStatus.IDLE
        ) {
            stopPlayback()
        }
        _uiState.update { it.copy(ttsAvailability = target, mandarinAvailability = mandarin) }
    }

    fun onAppBackgrounded() {
        stopPlayback()
    }

    fun clearMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun retryTitleTranslations() {
        val state = _uiState.value
        if (state.showTranslations) startTitleTranslation(state.language, state.items)
    }

    fun retryArticleTranslations() {
        val state = _uiState.value
        val snapshot = state.article ?: return
        if (state.showTranslations) {
            startArticleTranslation(snapshot, state.sentences, state.language)
        }
    }

    override fun onCleared() {
        articleJob?.cancel()
        articleTranslationJob?.cancel()
        titleTranslationJobs.values.forEach(Job::cancel)
        preloadJob?.cancel()
        activeRequestId = null
        speechController.stop()
        if (shutdownSpeechControllerOnClear) speechController.shutdown()
        super.onCleared()
    }

    private fun applyTranslationVisibility(show: Boolean) {
        val before = _uiState.value
        val previous = before.showTranslations
        _uiState.update { it.copy(showTranslations = show) }
        if (!show) {
            titleTranslationJobs.values.forEach(Job::cancel)
            articleTranslationJob?.cancel()
            _uiState.update {
                it.copy(
                    translatingTitleLanguages = emptySet(),
                    isArticleTranslating = false,
                )
            }
            if (before.bilingualPhase == BilingualPhase.TRANSLATION &&
                before.playbackStatus != PlaybackStatus.IDLE
            ) {
                activeRequestId = null
                preloadJob?.cancel()
                speechController.stop()
                val next = before.selectedSentenceIndex + 1
                if (before.playbackMode == PlaybackMode.CONTINUOUS && next in before.sentences.indices) {
                    startSpeaking(next, 0, BilingualPhase.TARGET)
                } else {
                    _uiState.update {
                        it.copy(
                            playbackStatus = PlaybackStatus.IDLE,
                            bilingualPhase = BilingualPhase.TARGET,
                            characterOffset = 0,
                        )
                    }
                }
            } else if (before.playbackStatus != PlaybackStatus.IDLE) {
                preloadWindow(before.selectedSentenceIndex, includeCurrent = false)
            }
        } else if (!previous || _uiState.value.titleTranslations.isEmpty()) {
            val state = _uiState.value
            startTitleTranslation(state.language, state.items)
            state.article?.let { snapshot ->
                startArticleTranslation(snapshot, state.sentences, state.language)
            }
        }
        refreshTtsAvailability()
    }

    private fun startTitleTranslation(language: MaterialLanguage, items: List<NewsItem>) {
        titleTranslationJobs.remove(language)?.cancel()
        if (items.isEmpty() || !_uiState.value.showTranslations) return
        _uiState.update {
            it.copy(
                translatingTitleLanguages = it.translatingTitleLanguages + language,
                titleTranslationErrors = it.titleTranslationErrors - language,
            )
        }
        titleTranslationJobs[language] = viewModelScope.launch {
            try {
                val cached = withContext(ioDispatcher) {
                    buildMap {
                        items.forEach { item ->
                            val normalizedUrl = normalizeUrl(item.url)
                            runCatching {
                                titleTranslationCache.get(
                                    TRANSLATION_PROMPT_VERSION,
                                    language,
                                    normalizedUrl,
                                    item.title,
                                )
                            }.getOrNull()?.takeIf(String::isNotBlank)?.let { translation ->
                                put(item.url, translation.trim())
                            }
                        }
                    }
                }
                if (cached.isNotEmpty()) {
                    _uiState.update { it.copy(titleTranslations = it.titleTranslations + cached) }
                }
                val missing = items.filter { item ->
                    cached[item.url].isNullOrBlank() &&
                        _uiState.value.titleTranslations[item.url].isNullOrBlank()
                }
                if (missing.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            translatingTitleLanguages = it.translatingTitleLanguages - language,
                            titleTranslationErrors = it.titleTranslationErrors - language,
                        )
                    }
                    return@launch
                }
                val itemById = missing.associateBy { normalizeUrl(it.url) }
                for (batchItems in missing.chunked(MAX_TITLE_BATCH_SIZE)) {
                    val inputs = batchItems.map { item ->
                        NewsTranslationInput(normalizeUrl(item.url), item.title, item.language)
                    }
                    val translated = translateMissing(inputs, translationService::translateTitles)
                    val updates = buildMap {
                        translated.forEach { (id, translation) ->
                            val item = itemById[id] ?: return@forEach
                            put(item.url, translation)
                        }
                    }
                    withContext(ioDispatcher) {
                        translated.forEach { (id, translation) ->
                            val item = itemById[id] ?: return@forEach
                            runCatching {
                                titleTranslationCache.put(
                                    TRANSLATION_PROMPT_VERSION,
                                    language,
                                    id,
                                    item.title,
                                    translation,
                                )
                            }
                        }
                    }
                    if (updates.isNotEmpty()) {
                        _uiState.update { state ->
                            state.copy(titleTranslations = state.titleTranslations + updates)
                        }
                    }
                }
                val unresolved = missing.count { item ->
                    _uiState.value.titleTranslations[item.url].isNullOrBlank()
                }
                _uiState.update { state ->
                    state.copy(
                        translatingTitleLanguages = state.translatingTitleLanguages - language,
                        titleTranslationErrors = if (unresolved == 0) {
                            state.titleTranslationErrors - language
                        } else {
                            state.titleTranslationErrors +
                                (language to "有 $unresolved 个标题未返回译文，可点击重试")
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { state ->
                    state.copy(
                        translatingTitleLanguages = state.translatingTitleLanguages - language,
                        titleTranslationErrors = state.titleTranslationErrors +
                            (language to (error.message ?: "标题翻译失败")),
                    )
                }
            }
        }
    }

    private suspend fun prepareArticleTranslation(
        snapshot: SourceArticleSnapshot,
        sentences: List<SentenceItem>,
        language: MaterialLanguage,
    ) {
        val key = articleCacheKey(snapshot, sentences, language)
        val cachedByIndex = runCatching {
            withContext(ioDispatcher) { articleTranslationCache.load(key) }
        }
            .getOrNull().orEmpty()
            .mapValues { (_, value) -> value.trim() }
            .filterValues(String::isNotBlank)
        val cachedById = sentences.mapIndexedNotNull { index, sentence ->
            cachedByIndex[index.toString()]?.let { sentence.id to it }
        }.toMap()
        _uiState.update {
            it.copy(
                sentenceTranslations = cachedById,
                isArticleTranslating = false,
                articleTranslationError = null,
            )
        }
        if (_uiState.value.showTranslations && cachedById.size < sentences.size) {
            startArticleTranslation(snapshot, sentences, language)
        }
    }

    private fun startArticleTranslation(
        snapshot: SourceArticleSnapshot,
        sentences: List<SentenceItem>,
        language: MaterialLanguage,
    ) {
        articleTranslationJob?.cancel()
        if (sentences.isEmpty() || !_uiState.value.showTranslations) return
        val key = articleCacheKey(snapshot, sentences, language)
        val existingByIndex = sentences.mapIndexedNotNull { index, sentence ->
            _uiState.value.sentenceTranslations[sentence.id]
                ?.takeIf(String::isNotBlank)
                ?.let { index.toString() to it }
        }.toMap()
        val missingInputs = sentences.mapIndexedNotNull { index, sentence ->
            if (index.toString() in existingByIndex) null else {
                NewsTranslationInput(index.toString(), sentence.text, language)
            }
        }
        if (missingInputs.isEmpty()) {
            _uiState.update { it.copy(isArticleTranslating = false, articleTranslationError = null) }
            return
        }

        _uiState.update { it.copy(isArticleTranslating = true, articleTranslationError = null) }
        articleTranslationJob = viewModelScope.launch {
            val translationsByIndex = existingByIndex.toMutableMap()
            try {
                val batches = buildSentenceBatches(missingInputs)
                for (batch in batches) {
                    val translated = translateMissing(batch, translationService::translateSentences)
                    translationsByIndex.putAll(translated)
                    runCatching {
                        withContext(ioDispatcher) {
                            articleTranslationCache.save(key, translationsByIndex)
                        }
                    }
                    if (_uiState.value.article?.contentHash != snapshot.contentHash) return@launch
                    val updates = translated.mapNotNull { (id, translation) ->
                        sentences.getOrNull(id.toIntOrNull() ?: -1)?.let { sentence -> sentence.id to translation }
                    }.toMap()
                    _uiState.update { state ->
                        state.copy(sentenceTranslations = state.sentenceTranslations + updates)
                    }
                }
                val unresolved = sentences.indices.filter { it.toString() !in translationsByIndex }
                _uiState.update {
                    it.copy(
                        isArticleTranslating = false,
                        articleTranslationError = if (unresolved.isEmpty()) null
                        else "有 ${unresolved.size} 句未返回译文，可点击重试",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isArticleTranslating = false,
                        articleTranslationError = error.message ?: "正文翻译失败",
                    )
                }
            }
        }
    }

    private suspend fun translateMissing(
        inputs: List<NewsTranslationInput>,
        operation: suspend (List<NewsTranslationInput>) -> Map<String, String>,
    ): Map<String, String> {
        val translations = mutableMapOf<String, String>()
        var remaining = inputs
        repeat(MISSING_TRANSLATION_ATTEMPTS) {
            if (remaining.isEmpty()) return@repeat
            val allowedIds = remaining.mapTo(mutableSetOf(), NewsTranslationInput::id)
            operation(remaining).forEach { (id, value) ->
                value.trim().takeIf { id in allowedIds && it.isNotEmpty() }?.let { translations[id] = it }
            }
            remaining = remaining.filter { it.id !in translations }
        }
        return translations
    }

    private fun buildSentenceBatches(
        inputs: List<NewsTranslationInput>,
    ): List<List<NewsTranslationInput>> {
        val batches = mutableListOf<List<NewsTranslationInput>>()
        var current = mutableListOf<NewsTranslationInput>()
        var characters = 0
        inputs.forEach { input ->
            require(input.text.length <= MAX_SENTENCE_BATCH_CHARACTERS) {
                "存在超过 $MAX_SENTENCE_BATCH_CHARACTERS 字符的单句，无法安全翻译"
            }
            if (current.isNotEmpty() &&
                (current.size >= MAX_SENTENCE_BATCH_SIZE ||
                    characters + input.text.length > MAX_SENTENCE_BATCH_CHARACTERS)
            ) {
                batches += current
                current = mutableListOf()
                characters = 0
            }
            current += input
            characters += input.text.length
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private fun articleCacheKey(
        snapshot: SourceArticleSnapshot,
        sentences: List<SentenceItem>,
        language: MaterialLanguage,
    ) = ArticleTranslationCacheKey(
        promptVersion = TRANSLATION_PROMPT_VERSION,
        language = language,
        contentHash = snapshot.contentHash,
        sentenceHash = sha256(sentences.joinToString("\u0000") { it.text.trim() }),
    )

    private fun normalizeUrl(value: String): String = runCatching {
        val uri = URI(value.trim())
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme.isBlank() || host.isBlank()) return@runCatching value.trim()
        "$scheme://$host${uri.path.orEmpty().trimEnd('/')}"
    }.getOrDefault(value.trim())

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun segment(snapshot: SourceArticleSnapshot, language: MaterialLanguage): SegmentedArticle {
        val learningLanguage = if (language == MaterialLanguage.ENGLISH) {
            LearningLanguage.ENGLISH
        } else {
            LearningLanguage.CANTONESE
        }
        val sentences = mutableListOf<SentenceItem>()
        val sections = mutableListOf<NewsSection>()
        snapshot.paragraphs.forEach { paragraph ->
            val start = sentences.size
            val paragraphSentences = sentenceSegmenter.segment(paragraph.text, learningLanguage)
            if (paragraphSentences.isNotEmpty()) {
                paragraph.heading?.trim()?.takeIf(String::isNotEmpty)?.let { heading ->
                    sections += NewsSection(heading, start)
                }
                sentences += paragraphSentences
            }
        }
        return SegmentedArticle(sentences, sections)
    }

    private fun pause() {
        if (speechController.pause()) {
            _uiState.update { it.copy(playbackStatus = PlaybackStatus.PAUSED) }
        } else {
            stopPlayback()
        }
    }

    private fun navigateBy(delta: Int) {
        val state = _uiState.value
        if (state.sentences.isEmpty()) return
        val current = state.selectedSentenceIndex.coerceAtLeast(0)
        val next = (current + delta).coerceIn(state.sentences.indices)
        val continuePlaying = state.playbackStatus in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)
        val remainPaused = state.playbackStatus == PlaybackStatus.PAUSED
        activeRequestId = null
        preloadJob?.cancel()
        speechController.stop()
        _uiState.update {
            it.copy(
                selectedSentenceIndex = next,
                bilingualPhase = BilingualPhase.TARGET,
                characterOffset = 0,
                playbackStatus = if (remainPaused) PlaybackStatus.PAUSED else PlaybackStatus.IDLE,
            )
        }
        if (continuePlaying) startSpeaking(next, 0, BilingualPhase.TARGET)
    }

    private fun startSpeaking(
        index: Int,
        startOffset: Int,
        phase: BilingualPhase,
    ) {
        val state = _uiState.value
        if (index !in state.sentences.indices) return
        val translation = state.sentenceTranslations[state.sentences[index].id]
        val effectivePhase = if (phase == BilingualPhase.TRANSLATION &&
            state.showTranslations && !translation.isNullOrBlank()
        ) {
            BilingualPhase.TRANSLATION
        } else {
            BilingualPhase.TARGET
        }
        val speechLanguage = if (effectivePhase == BilingualPhase.TRANSLATION) {
            SpeechLanguage.MANDARIN_CN
        } else {
            state.language.toSpeechLanguage()
        }
        val availability = speechController.checkAvailability(speechLanguage)
        if (availability != TtsAvailability.READY) {
            activeRequestId = null
            _uiState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.IDLE,
                    ttsAvailability = availability,
                    userMessage = availabilityMessage(availability),
                )
            }
            return
        }

        activeRequestId = null
        preloadJob?.cancel()
        speechController.stop()
        val sentence = state.sentences[index]
        val text = if (effectivePhase == BilingualPhase.TRANSLATION) translation.orEmpty() else sentence.text
        val safeOffset = startOffset.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        // Other screens use positive counters with the shared controller. Reserve negative IDs
        // for news so a late event from a hidden player can never match this request.
        val requestId = requestIds.getAndDecrement()
        activeRequestId = requestId
        _uiState.update {
            it.copy(
                selectedSentenceIndex = index,
                bilingualPhase = effectivePhase,
                characterOffset = safeOffset,
                playbackStatus = PlaybackStatus.PREPARING,
                ttsAvailability = TtsAvailability.READY,
                userMessage = null,
            )
        }
        val accepted = speechController.speak(
            requestId = requestId,
            text = text,
            language = speechLanguage,
            speed = if (effectivePhase == BilingualPhase.TRANSLATION) state.mandarinSpeed else state.speed,
            startOffset = safeOffset,
        )
        if (!accepted) {
            activeRequestId = null
            _uiState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.IDLE,
                    userMessage = "无法开始播放，请先配置MiniMax语音",
                )
            }
        } else {
            preloadWindow(index, includeCurrent = false)
        }
    }

    private fun preloadWindow(index: Int, includeCurrent: Boolean) {
        preloadJob?.cancel()
        val state = _uiState.value
        val requests = buildList {
            val last = (index + PRELOAD_FUTURE_SENTENCES).coerceAtMost(state.sentences.lastIndex)
            if (index !in state.sentences.indices) return@buildList
            for (sentenceIndex in index..last) {
                val sentence = state.sentences[sentenceIndex]
                val current = sentenceIndex == index
                if (!current || includeCurrent) {
                    add(SpeechPreload(sentence.text, state.language.toSpeechLanguage(), state.speed))
                }
                if (state.showTranslations &&
                    (!current || includeCurrent || state.bilingualPhase == BilingualPhase.TARGET)
                ) {
                    state.sentenceTranslations[sentence.id]?.takeIf(String::isNotBlank)?.let { translation ->
                        add(SpeechPreload(translation, SpeechLanguage.MANDARIN_CN, state.mandarinSpeed))
                    }
                }
            }
        }
        if (requests.isEmpty()) return
        preloadJob = viewModelScope.launch {
            val semaphore = Semaphore(MAX_PARALLEL_PRELOADS)
            supervisorScope {
                requests.forEach { request ->
                    launch {
                        semaphore.withPermit {
                            try {
                                speechController.preload(request.text, request.language, request.speed)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                // Preloading is best-effort. Playback retries the same audio normally.
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        activeRequestId = null
        preloadJob?.cancel()
        preloadJob = null
        speechController.stop()
        _uiState.update {
            it.copy(
                playbackStatus = PlaybackStatus.IDLE,
                bilingualPhase = BilingualPhase.TARGET,
                characterOffset = 0,
            )
        }
    }

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Initialized -> if (event.successful) {
                refreshTtsAvailability()
            } else {
                _uiState.update {
                    it.copy(ttsAvailability = TtsAvailability.ERROR, userMessage = "MiniMax语音初始化失败")
                }
            }
            is SpeechEvent.Started -> if (event.requestId == activeRequestId) {
                _uiState.update { it.copy(playbackStatus = PlaybackStatus.PLAYING) }
            }
            is SpeechEvent.Range -> if (event.requestId == activeRequestId) {
                _uiState.update { it.copy(characterOffset = event.sentenceOffset) }
            }
            is SpeechEvent.Done -> if (event.requestId == activeRequestId) {
                activeRequestId = null
                val state = _uiState.value
                val sentence = state.sentences.getOrNull(state.selectedSentenceIndex)
                val translation = sentence?.let { state.sentenceTranslations[it.id] }
                if (state.bilingualPhase == BilingualPhase.TARGET &&
                    state.showTranslations && !translation.isNullOrBlank()
                ) {
                    startSpeaking(state.selectedSentenceIndex, 0, BilingualPhase.TRANSLATION)
                } else {
                    val next = state.selectedSentenceIndex + 1
                    if (state.playbackMode == PlaybackMode.CONTINUOUS && next in state.sentences.indices) {
                        startSpeaking(next, 0, BilingualPhase.TARGET)
                    } else {
                        _uiState.update {
                            it.copy(
                                playbackStatus = PlaybackStatus.IDLE,
                                bilingualPhase = BilingualPhase.TARGET,
                                characterOffset = 0,
                            )
                        }
                    }
                }
            }
            is SpeechEvent.Error -> if (activeRequestId != null &&
                (event.requestId == null || event.requestId == activeRequestId)
            ) {
                activeRequestId = null
                _uiState.update {
                    it.copy(
                        playbackStatus = PlaybackStatus.IDLE,
                        characterOffset = 0,
                        userMessage = event.message,
                    )
                }
            }
        }
    }

    private fun availabilityMessage(availability: TtsAvailability): String = when (availability) {
        TtsAvailability.INITIALIZING -> "MiniMax语音正在初始化，请稍候"
        TtsAvailability.MISSING_DATA -> "请先在设置中填写MiniMax API Key"
        TtsAvailability.UNSUPPORTED -> "MiniMax语音配置不受支持"
        TtsAvailability.ERROR -> "MiniMax语音不可用"
        TtsAvailability.READY -> ""
    }

    class Factory(
        private val sourceRepository: FixedSourceRepository,
        private val materialRepository: MaterialRepository,
        private val userPreferences: LearnerPreferences,
        private val speechController: SpeechController,
        private val translationService: NewsTranslationService,
        private val titleTranslationCache: TitleTranslationCache,
        private val articleTranslationCache: ArticleTranslationCache,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NewsViewModel::class.java))
            return NewsViewModel(
                sourceRepository = sourceRepository,
                materialRepository = materialRepository,
                userPreferences = userPreferences,
                speechController = speechController,
                translationService = translationService,
                titleTranslationCache = titleTranslationCache,
                articleTranslationCache = articleTranslationCache,
            ) as T
        }
    }

    private data class SegmentedArticle(
        val sentences: List<SentenceItem>,
        val sections: List<NewsSection>,
    )

    private data class SpeechPreload(
        val text: String,
        val language: SpeechLanguage,
        val speed: Float,
    )

    private companion object {
        const val PRELOAD_FUTURE_SENTENCES = 2
        const val MAX_PARALLEL_PRELOADS = 4
        const val MAX_TITLE_BATCH_SIZE = 20
        const val MAX_SENTENCE_BATCH_SIZE = 25
        const val MAX_SENTENCE_BATCH_CHARACTERS = 6_000
        const val MISSING_TRANSLATION_ATTEMPTS = 2
        const val TRANSLATION_PROMPT_VERSION = "news-translation-v1"
        val requestIds = AtomicLong(-1L)
    }
}
