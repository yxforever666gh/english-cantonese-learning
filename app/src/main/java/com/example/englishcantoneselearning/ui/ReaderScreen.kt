package com.example.englishcantoneselearning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.SentenceItem

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
    onReadingFontSizeChange: (Int) -> Unit = {},
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
    onBack: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var editingSentence by remember {
        mutableStateOf<SentenceItem?>(null)
    }
    onBack?.let { BackHandler(onBack = it) }

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
        topBar = {
            onBack?.let { callback ->
                TopAppBar(
                    title = { Text("创建") },
                    navigationIcon = {
                        IconButton(onClick = callback) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回新闻")
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                PlayerPanel(
                    state = state,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onSpeedChange = onSpeedChange,
                    onSpeedChangeFinished = onSpeedChangeFinished,
                    onReadingFontSizeChange = onReadingFontSizeChange,
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "input") {
                    EditorialPageHeader(
                        eyebrow = "",
                        title = "创建学习材料",
                        subtitle = "粘贴原文，逐句编辑并反复朗读。",
                    )
                    Spacer(Modifier.height(16.dp))
                    creationSwitcher()
                    Spacer(Modifier.height(12.dp))
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
                            Spacer(Modifier.height(20.dp))
                            EditorialSectionHeader(
                                title = "逐句朗读（${state.sentences.size}）",
                                subtitle = "点击播放；更多菜单可编辑、拆分或合并。",
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
                        readingFontSizeSp = state.readingFontSizeSp,
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
