package com.example.englishcantoneselearning.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.TtsAvailability
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onArticleTextChange: (String) -> Unit,
    onLanguageChange: (LearningLanguage) -> Unit,
    onSegmentArticle: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedChangeFinished: () -> Unit,
    onPlayOrPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSelectSentence: (Int) -> Unit,
    onUpdateSentence: (Long, String) -> Boolean,
    onSplitSentence: (Long, String, Int) -> Boolean,
    onMergeSentence: (Long) -> Boolean,
    onMessageShown: () -> Unit,
    onTitleChange: (String) -> Unit = {},
    onSaveArticle: () -> Unit = {},
    creationSwitcher: @Composable () -> Unit = {},
    bottomNavigation: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var editingSentence by remember {
        mutableStateOf<SentenceItem?>(null)
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    LaunchedEffect(state.selectedIndex, state.playbackStatus) {
        if (state.selectedIndex >= 0 && state.sentences.isNotEmpty()) {
            listState.animateScrollToItem(state.selectedIndex + 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                PlayerPanel(
                    state = state,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onSpeedChange = onSpeedChange,
                    onSpeedChangeFinished = onSpeedChangeFinished,
                    onPlayOrPause = onPlayOrPause,
                    onPreviousSentence = onPreviousSentence,
                    onNextSentence = onNextSentence,
                )
                bottomNavigation()
            }
        },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.editorialContentWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "input") {
                    EditorialPageHeader(
                        eyebrow = "Read · Split · Listen",
                        title = "粘贴文章",
                        subtitle = "把真实语料整理成可逐句编辑、反复聆听的学习文本。",
                    )
                    Spacer(Modifier.height(20.dp))
                    creationSwitcher()
                    Spacer(Modifier.height(16.dp))
                    ArticleInputSection(
                        state = state,
                        onArticleTextChange = onArticleTextChange,
                        onLanguageChange = onLanguageChange,
                        onSegmentArticle = onSegmentArticle,
                        onTitleChange = onTitleChange,
                        onSaveArticle = onSaveArticle,
                    )

                    AnimatedVisibility(state.sentences.isNotEmpty()) {
                        Column {
                            Spacer(Modifier.height(24.dp))
                            EditorialSectionHeader(
                                title = "断句结果（${state.sentences.size} 句）",
                                subtitle = "点击句子可立即朗读；右侧菜单可编辑、拆分或合并。",
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = state.sentences,
                    key = { _, sentence -> sentence.id },
                ) { index, sentence ->
                    SentenceCard(
                        index = index,
                        sentence = sentence,
                        selected = index == state.selectedIndex,
                        playing = index == state.selectedIndex &&
                            state.playbackStatus in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING),
                        canMerge = index < state.sentences.lastIndex,
                        onPlay = { onSelectSentence(index) },
                        onEdit = { editingSentence = sentence },
                        onMerge = { onMergeSentence(sentence.id) },
                    )
                }
            }
        }
    }

    editingSentence?.let { sentence ->
        EditSentenceDialog(
            sentence = sentence,
            onDismiss = { editingSentence = null },
            onSave = { text ->
                if (onUpdateSentence(sentence.id, text)) editingSentence = null
            },
            onSplit = { text, cursor ->
                if (onSplitSentence(sentence.id, text, cursor)) editingSentence = null
            },
        )
    }
}

@Composable
private fun ArticleInputSection(
    state: ReaderUiState,
    onArticleTextChange: (String) -> Unit,
    onLanguageChange: (LearningLanguage) -> Unit,
    onSegmentArticle: () -> Unit,
    onTitleChange: (String) -> Unit,
    onSaveArticle: () -> Unit,
) {
    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EditorialSectionHeader(
                title = "准备文本",
                subtitle = "选择朗读语言，然后粘贴需要练习的文章。",
            )
            EditorialSegmentedControl(
                options = listOf(
                    LearningLanguage.ENGLISH to "英语（美国）",
                    LearningLanguage.CANTONESE to "粤语（香港）",
                ),
                selected = state.language,
                onSelect = onLanguageChange,
                optionModifier = { language ->
                    Modifier.testTag(if (language == LearningLanguage.ENGLISH) "language_english" else "language_cantonese")
                },
            )

            OutlinedTextField(
                value = state.articleText,
                onValueChange = onArticleTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp)
                    .testTag("article_input"),
                label = { Text("输入或粘贴文章") },
                placeholder = {
                    Text(if (state.language == LearningLanguage.ENGLISH) ENGLISH_SAMPLE else CANTONESE_SAMPLE)
                },
                shape = RoundedCornerShape(14.dp),
                minLines = 6,
                maxLines = 12,
            )

            EditorialPrimaryButton(
                text = if (state.sentences.isEmpty()) "自动断句" else "重新断句",
                onClick = onSegmentArticle,
                icon = Icons.Default.Edit,
                modifier = Modifier.testTag("segment_button"),
            )
            if (state.sentences.isNotEmpty()) {
                Text(
                    "保存设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.articleTitle,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth().testTag("manual_article_title"),
                    label = { Text("文章标题") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                EditorialPrimaryButton(
                    text = "保存到文章列表",
                    onClick = onSaveArticle,
                    icon = Icons.Default.Save,
                    modifier = Modifier.testTag("save_manual_article"),
                )
            }
        }
    }
}

@Composable
private fun SentenceCard(
    index: Int,
    sentence: SentenceItem,
    selected: Boolean,
    playing: Boolean,
    canMerge: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentence_card_$index")
            .clickable(onClick = onPlay),
        containerColor = if (selected) EditorialMint else EditorialSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 48.dp)
                    .background(if (selected) EditorialPine else Color.Transparent, RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.size(10.dp))
            androidx.compose.material3.Surface(
                modifier = Modifier.size(32.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (selected) EditorialPine else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (playing) "▶" else "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else EditorialPine,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = sentence.text,
                style = MaterialTheme.typography.bodyLarge,
                color = EditorialInk,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "句子操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑或拆分") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("与下一句合并") },
                        enabled = canMerge,
                        onClick = {
                            menuExpanded = false
                            onMerge()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerPanel(
    state: ReaderUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedChangeFinished: () -> Unit,
    onPlayOrPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
) {
    val canPlay = state.sentences.isNotEmpty() && state.ttsAvailability == TtsAvailability.READY
    val hasSelection = state.selectedIndex in state.sentences.indices

    EditorialPlayerSurface(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (hasSelection) "第 ${state.selectedIndex + 1} / ${state.sentences.size} 句" else "等待断句",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (state.playbackStatus == PlaybackStatus.PREPARING) {
                        "正在生成 MiniMax 语音…"
                    } else {
                        ttsStatusText(state.ttsAvailability)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.ttsAvailability == TtsAvailability.READY) EditorialPine else MaterialTheme.colorScheme.error,
                )
            }
            MetadataPill(
                text = if (state.playbackMode == PlaybackMode.SINGLE) "单句" else "连续",
                accent = true,
            )
        }

        if (state.ttsAvailability != TtsAvailability.READY) {
            Text(
                "请到设置填写MiniMax API Key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.playbackStatus == PlaybackStatus.PREPARING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("speech_preparing"),
                color = EditorialTerracotta,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPreviousSentence,
                enabled = hasSelection && state.selectedIndex > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
            }
            FilledIconButton(
                onClick = onPlayOrPause,
                enabled = canPlay && state.playbackStatus != PlaybackStatus.PREPARING,
                modifier = Modifier.size(58.dp).testTag("play_pause_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = EditorialPine,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = if (state.playbackStatus == PlaybackStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playbackStatus == PlaybackStatus.PLAYING) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onNextSentence,
                enabled = hasSelection && state.selectedIndex < state.sentences.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一句")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EditorialChoiceChip(
                selected = state.playbackMode == PlaybackMode.SINGLE,
                onClick = { onPlaybackModeChange(PlaybackMode.SINGLE) },
                label = "单句",
                modifier = Modifier.testTag("mode_single"),
            )
            EditorialChoiceChip(
                selected = state.playbackMode == PlaybackMode.CONTINUOUS,
                onClick = { onPlaybackModeChange(PlaybackMode.CONTINUOUS) },
                label = "连续",
                modifier = Modifier.testTag("mode_continuous"),
            )
            Text("${String.format(Locale.US, "%.1fx", state.speed)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.speed,
                onValueChange = onSpeedChange,
                onValueChangeFinished = onSpeedChangeFinished,
                valueRange = 0.5f..2.0f,
                steps = 14,
                modifier = Modifier.weight(1f).testTag("speed_slider"),
            )
        }
    }
}

@Composable
private fun EditSentenceDialog(
    sentence: SentenceItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSplit: (String, Int) -> Unit,
) {
    var value by remember(sentence.id) {
        mutableStateOf(
            TextFieldValue(
                text = sentence.text,
                selection = TextRange(sentence.text.length),
            ),
        )
    }
    val splitPosition = value.selection.start
    val canSplit = value.selection.collapsed &&
        splitPosition in 1 until value.text.length &&
        value.text.substring(0, splitPosition).isNotBlank() &&
        value.text.substring(splitPosition).isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("编辑句子") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sentence_editor"),
                    minLines = 3,
                )
                Text(
                    text = "要拆分句子，请把光标放到拆分位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onSplit(value.text, splitPosition) },
                    enabled = canSplit,
                    modifier = Modifier.testTag("split_sentence_button"),
                ) {
                    Text("从光标拆分")
                }
                TextButton(
                    onClick = { onSave(value.text) },
                    enabled = value.text.isNotBlank(),
                    modifier = Modifier.testTag("save_sentence_button"),
                ) {
                    Text("保存")
                }
            }
        },
    )
}

private fun ttsStatusText(availability: TtsAvailability): String = when (availability) {
    TtsAvailability.INITIALIZING -> "正在初始化MiniMax语音…"
    TtsAvailability.READY -> "MiniMax语音已就绪"
    TtsAvailability.MISSING_DATA -> "尚未配置MiniMax API Key"
    TtsAvailability.UNSUPPORTED -> "MiniMax语音配置不受支持"
    TtsAvailability.ERROR -> "MiniMax语音不可用"
}

private const val ENGLISH_SAMPLE = "Paste an English article here. The app will split it into sentences."
private const val CANTONESE_SAMPLE = "喺呢度貼上粵語文章。應用程式會自動斷句。"
