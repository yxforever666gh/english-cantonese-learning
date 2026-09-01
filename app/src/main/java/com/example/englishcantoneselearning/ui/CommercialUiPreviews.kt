package com.example.englishcantoneselearning.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme

@Preview(name = "朗读 · 360×800", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "朗读 · 412×915", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun ReaderReadyPreview() {
    EnglishCantoneseLearningTheme {
        ReaderScreen(
            state = ReaderUiState(
                articleText = "First sentence. Second sentence.",
                articleTitle = "Everyday English practice",
                language = LearningLanguage.ENGLISH,
                sentences = listOf(
                    SentenceItem(1, "First sentence."),
                    SentenceItem(2, "Second sentence."),
                ),
                selectedIndex = 0,
                playbackMode = PlaybackMode.CONTINUOUS,
                ttsAvailability = TtsAvailability.READY,
            ),
            onArticleTextChange = {},
            onLanguageChange = {},
            onSegmentArticle = {},
            onPlaybackModeChange = {},
            onSpeedChange = {},
            onSpeedChangeFinished = {},
            onPlayOrPause = {},
            onPreviousSentence = {},
            onNextSentence = {},
            onSelectSentence = {},
            onUpdateSentence = { _, _ -> true },
            onSplitSentence = { _, _, _ -> true },
            onMergeSentence = { true },
            onMessageShown = {},
            onTitleChange = {},
            onSaveArticle = {},
            creationSwitcher = {
                EditorialSegmentedControl(
                    options = listOf(false to "AI生成", true to "粘贴文章"),
                    selected = true,
                    onSelect = {},
                )
            },
            bottomNavigation = { AppNavigationBar(AppDestination.NEWS, onSelect = {}) },
        )
    }
}
