package com.example.englishcantoneselearning.ui.news

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.ui.EditorialChoiceChip
import com.example.englishcantoneselearning.ui.EditorialEmptyState
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialSegmentedControl
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.CollapsiblePlayerSurface
import com.example.englishcantoneselearning.ui.CompactSentenceNumberBadge
import com.example.englishcantoneselearning.ui.NumericStepper
import com.example.englishcantoneselearning.ui.editorialContentWidth
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    state: NewsUiState,
    viewModel: NewsViewModel,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSavedMaterial: (String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val selected = state.selectedItem
    BackHandler(enabled = selected != null, onBack = viewModel::closeArticle)

    LaunchedEffect(viewModel) { viewModel.onEnter() }
    DisposableEffect(viewModel) {
        onDispose(viewModel::onLeaveScreen)
    }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selected == null) {
                TopAppBar(
                    title = { Text("实时新闻") },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refresh(forceRefresh = true) },
                            enabled = !state.isRefreshing,
                            modifier = Modifier.testTag("news_refresh"),
                        ) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新新闻")
                        }
                        TextButton(onClick = onCreate, modifier = Modifier.testTag("news_create")) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("创建")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("新闻阅读", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::closeArticle) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回新闻列表")
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                if (selected != null && state.article != null) {
                    NewsPlayerPanel(
                        state = state,
                        onPlaybackModeChange = viewModel::setPlaybackMode,
                        onSpeedChange = viewModel::setSpeechSpeed,
                        onSpeedChangeFinished = viewModel::onSpeechSpeedChangeFinished,
                        onShowTranslations = viewModel::setShowTranslations,
                        onFontSizeChange = viewModel::setReadingFontSizeSp,
                        onPrevious = viewModel::previousSentence,
                        onPlayPause = viewModel::playOrPause,
                        onNext = viewModel::nextSentence,
                    )
                }
                bottomBar()
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (selected == null) {
                NewsFeed(
                    state = state,
                    onLanguage = viewModel::setLanguage,
                    onShowTranslations = viewModel::setShowTranslations,
                    onLatest = viewModel::showLatest,
                    onTag = viewModel::toggleTag,
                    onRefresh = { viewModel.refresh(forceRefresh = true) },
                    onOpen = viewModel::openArticle,
                    modifier = Modifier.editorialContentWidth(),
                )
            } else {
                NewsDetail(
                    state = state,
                    onRetry = viewModel::retryArticle,
                    onRetryTranslation = viewModel::retryArticleTranslations,
                    onPlaySentence = viewModel::selectAndPlay,
                    onSave = viewModel::saveArticle,
                    onOpenSavedMaterial = onOpenSavedMaterial,
                    modifier = Modifier.editorialContentWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewsFeed(
    state: NewsUiState,
    onLanguage: (MaterialLanguage) -> Unit,
    onShowTranslations: (Boolean) -> Unit,
    onLatest: () -> Unit,
    onTag: (com.example.englishcantoneselearning.model.NewsTag) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (NewsItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().testTag("news_feed"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EditorialPageHeader(
                    eyebrow = "",
                    title = "今天发生什么",
                    subtitle = "固定可信来源实时更新，筛选和分类全部在本机完成。",
                )
                Spacer(Modifier.height(14.dp))
                EditorialSegmentedControl(
                    options = listOf(
                        MaterialLanguage.ENGLISH to "English",
                        MaterialLanguage.CANTONESE to "粤语",
                    ),
                    selected = state.language,
                    onSelect = onLanguage,
                    optionModifier = { language ->
                        Modifier.testTag("news_language_${language.name.lowercase()}")
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("显示中文翻译", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.showTranslations,
                        onCheckedChange = onShowTranslations,
                        modifier = Modifier.testTag("news_translation_toggle"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorialChoiceChip(
                        selected = state.selectedTags.isEmpty(),
                        onClick = onLatest,
                        label = "最新 ${state.items.size}",
                        modifier = Modifier.testTag("news_tag_latest"),
                    )
                    state.tagCounts.filterValues { it > 0 }.forEach { (tag, count) ->
                        EditorialChoiceChip(
                            selected = tag in state.selectedTags,
                            onClick = { onTag(tag) },
                            label = "${tag.displayName} $count",
                            modifier = Modifier.testTag("news_tag_${tag.name.lowercase()}"),
                        )
                    }
                }
                state.updatedAt?.let { updatedAt ->
                    Text(
                        "更新于 ${formatTimestamp(updatedAt)}",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.feedError?.let { error ->
                item {
                    EditorialStatusPanel(
                        title = if (state.items.isEmpty()) "暂时无法获取新闻" else "刷新失败，继续显示上次内容",
                        body = error,
                        tone = EditorialStatusTone.ERROR,
                        action = { TextButton(onClick = onRefresh) { Text("重试") } },
                    )
                }
            }
            state.titleTranslationError?.takeIf { state.showTranslations }?.let { translationError ->
                item {
                    EditorialStatusPanel(
                        title = "部分标题翻译失败",
                        body = translationError,
                        tone = EditorialStatusTone.WARNING,
                    )
                }
            }

            if (!state.isRefreshing && state.items.isEmpty()) {
                item {
                    EditorialEmptyState(
                        title = "暂时没有新闻",
                        body = "下拉或点击右上角刷新，获取最新文章。",
                        icon = R.drawable.ic_article,
                    )
                }
            } else if (!state.isRefreshing && state.items.isNotEmpty() && state.visibleItems.isEmpty()) {
                item {
                    EditorialEmptyState(
                        title = "没有符合这些标签的文章",
                        body = "取消一个标签或选择“最新”查看全部新闻。",
                        icon = R.drawable.ic_search,
                    )
                }
            }

            items(state.visibleItems, key = NewsItem::url) { item ->
                NewsCard(
                    item = item,
                    translatedTitle = state.titleTranslations[item.url].takeIf { state.showTranslations },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun NewsCard(item: NewsItem, translatedTitle: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .testTag("news_card_${item.sourceId}_${item.url.hashCode()}"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EditorialInk,
            )
            translatedTitle?.takeIf(String::isNotBlank)?.let { translation ->
                Text(
                    translation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.summary.takeIf(String::isNotBlank)?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.tags.take(5).forEach { tag -> MetadataPill(tag.displayName) }
                }
            }
            Text(
                listOfNotNull(item.publisher, item.publishedAt?.takeIf(String::isNotBlank)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = EditorialPine,
            )
        }
    }
}

@Composable
internal fun NewsDetail(
    state: NewsUiState,
    onRetry: () -> Unit,
    onRetryTranslation: () -> Unit,
    onPlaySentence: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenSavedMaterial: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedItem = state.selectedItem ?: return
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("news_detail"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            EditorialPageHeader(
                eyebrow = "",
                title = selectedItem.title,
                subtitle = listOfNotNull(
                    selectedItem.publisher,
                    selectedItem.publishedAt?.takeIf(String::isNotBlank),
                ).joinToString(" · "),
            )
            state.titleTranslations[selectedItem.url]
                ?.takeIf { state.showTranslations && it.isNotBlank() }
                ?.let { translation ->
                    Text(
                        translation,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (selectedItem.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selectedItem.tags.take(5).forEach { tag -> MetadataPill(tag.displayName) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { uriHandler.openUri(selectedItem.url) }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("查看原文")
                }
                when {
                    state.isSaving -> Button(onClick = {}, enabled = false) { Text("正在保存…") }
                    state.savedMaterialId != null -> OutlinedButton(
                        onClick = { onOpenSavedMaterial(state.savedMaterialId) },
                        modifier = Modifier.testTag("news_open_saved"),
                    ) {
                        Icon(painterResource(R.drawable.ic_article), contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("已保存")
                    }
                    state.article != null && state.hasCompleteArticleTranslation -> Button(
                        onClick = onSave,
                        modifier = Modifier.testTag("news_save"),
                    ) {
                        Icon(painterResource(R.drawable.ic_save), contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("保存到材料库")
                    }
                    state.article != null -> OutlinedButton(onClick = {}, enabled = false) {
                        Text(if (state.isArticleTranslating) "翻译完成后可收藏" else "需要完整中文翻译")
                    }
                }
            }
        }

        when {
            state.isArticleLoading -> item {
                EditorialStatusPanel(
                    title = "正在整理正文",
                    body = "正在下载并清理文章内容，不会调用大模型。",
                )
            }
            state.articleError != null -> item {
                EditorialStatusPanel(
                    title = "无法打开这篇新闻",
                    body = state.articleError,
                    tone = EditorialStatusTone.ERROR,
                    action = { TextButton(onClick = onRetry) { Text("重试") } },
                )
            }
            else -> {
                if (state.showTranslations &&
                    (state.isArticleTranslating || state.articleTranslationError != null)
                ) {
                    item {
                        EditorialStatusPanel(
                            title = if (state.isArticleTranslating) "正在翻译正文" else "部分正文翻译失败",
                            body = state.articleTranslationError ?: "译文会按批次逐步显示，原文阅读不受影响。",
                            tone = if (state.articleTranslationError == null) {
                                EditorialStatusTone.INFO
                            } else {
                                EditorialStatusTone.WARNING
                            },
                            action = if (state.articleTranslationError == null) null else {
                                { TextButton(onClick = onRetryTranslation) { Text("重试缺失句") } }
                            },
                        )
                    }
                }
                items(state.sentences.size) { index ->
                val sentence = state.sentences[index]
                val selected = index == state.selectedSentenceIndex
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.sections.firstOrNull { it.startSentenceIndex == index }?.let { section ->
                        Text(
                            section.title,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            color = EditorialInk,
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPlaySentence(index) }
                            .testTag("news_sentence_$index"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = if (selected) EditorialMint else Color.Transparent,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            CompactSentenceNumberBadge(number = index + 1, selected = selected)
                                Text(
                                    sentence.text,
                                    fontSize = state.readingFontSizeSp.sp,
                                    lineHeight = (state.readingFontSizeSp * 1.5f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EditorialInk,
                                )
                                state.sentenceTranslations[sentence.id]
                                    ?.takeIf { state.showTranslations && it.isNotBlank() }
                                    ?.let { translation ->
                                        Text(
                                            translation,
                                            fontSize = state.readingFontSizeSp.sp,
                                            lineHeight = (state.readingFontSizeSp * 1.5f).sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                if (selected && state.playbackStatus in
                                    setOf(PlaybackStatus.PREPARING, PlaybackStatus.PLAYING)
                                ) {
                                    Text(
                                        if (state.playbackStatus == PlaybackStatus.PREPARING) {
                                            "正在准备语音…"
                                        } else if (state.bilingualPhase == BilingualPhase.TRANSLATION) {
                                            "正在朗读中文翻译"
                                        } else {
                                            "正在朗读原文"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EditorialPine,
                                    )
                                }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                }
            }
        }
    }
}

@Composable
private fun NewsPlayerPanel(
    state: NewsUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (SpeechLanguage, Float) -> Unit,
    onSpeedChangeFinished: (SpeechLanguage) -> Unit,
    onShowTranslations: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val hasSelection = state.selectedSentenceIndex in state.sentences.indices
    val targetLanguage = if (state.language == MaterialLanguage.ENGLISH) {
        SpeechLanguage.ENGLISH_US
    } else {
        SpeechLanguage.CANTONESE_HK
    }
    val ready = state.ttsAvailability == TtsAvailability.READY &&
        (!state.showTranslations || state.mandarinAvailability == TtsAvailability.READY)
    CollapsiblePlayerSurface(
        stateKey = "news-player-${state.selectedItem?.url.orEmpty()}",
        playing = state.playbackStatus == PlaybackStatus.PLAYING,
        preparing = state.playbackStatus == PlaybackStatus.PREPARING,
        canPlay = ready && state.sentences.isNotEmpty(),
        hasPrevious = hasSelection && state.selectedSentenceIndex > 0,
        hasNext = hasSelection && state.selectedSentenceIndex < state.sentences.lastIndex,
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        testTagPrefix = "news",
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (hasSelection) "第 ${state.selectedSentenceIndex + 1} / ${state.sentences.size} 句"
                    else "选择句子开始朗读",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (ready) {
                        if (state.playbackStatus == PlaybackStatus.PREPARING) "正在准备语音…"
                        else if (state.playbackMode == PlaybackMode.CONTINUOUS) "连续" else "单句"
                    } else voiceStatus(state.ttsAvailability),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("显示中文翻译", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.showTranslations,
                    onCheckedChange = onShowTranslations,
                    modifier = Modifier.testTag("news_player_translation_toggle"),
                )
            }
            NumericStepper(
                label = if (state.language == MaterialLanguage.ENGLISH) "英语语速" else "粤语语速",
                value = state.speed,
                range = 0.5f..2.0f,
                step = 0.1f,
                decimalPlaces = 1,
                unit = "x",
                onValueCommitted = {
                    onSpeedChange(targetLanguage, it)
                    onSpeedChangeFinished(targetLanguage)
                },
                testTagPrefix = "news_speed",
            )
            if (state.showTranslations) {
                NumericStepper(
                    label = "中文语速",
                    value = state.mandarinSpeed,
                    range = 0.5f..2.0f,
                    step = 0.1f,
                    decimalPlaces = 1,
                    unit = "x",
                    onValueCommitted = {
                        onSpeedChange(SpeechLanguage.MANDARIN_CN, it)
                        onSpeedChangeFinished(SpeechLanguage.MANDARIN_CN)
                    },
                    testTagPrefix = "news_mandarin_speed",
                )
            }
            NumericStepper(
                label = "正文字号",
                value = state.readingFontSizeSp.toFloat(),
                range = 12f..32f,
                step = 1f,
                decimalPlaces = 0,
                unit = "sp",
                onValueCommitted = { onFontSizeChange(it.toInt()) },
                testTagPrefix = "news_font_size",
            )
            EditorialSegmentedControl(
                options = listOf(PlaybackMode.SINGLE to "单句", PlaybackMode.CONTINUOUS to "连续"),
                selected = state.playbackMode,
                onSelect = onPlaybackModeChange,
                modifier = Modifier.fillMaxWidth(),
                optionModifier = { Modifier },
            )
    }
}

private fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun voiceStatus(availability: TtsAvailability): String = when (availability) {
    TtsAvailability.INITIALIZING -> "语音正在初始化"
    TtsAvailability.READY -> "语音已就绪"
    TtsAvailability.MISSING_DATA -> "请先配置MiniMax语音"
    TtsAvailability.UNSUPPORTED -> "当前语音不受支持"
    TtsAvailability.ERROR -> "语音不可用"
}
