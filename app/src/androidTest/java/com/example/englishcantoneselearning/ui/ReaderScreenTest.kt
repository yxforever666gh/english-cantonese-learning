package com.example.englishcantoneselearning.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.SentenceItem
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inputLanguageAndSegmentControlsDispatchEvents() {
        var enteredText = ""
        var selectedLanguage = LearningLanguage.ENGLISH
        var segmentClicked = false
        var screenState by mutableStateOf(ReaderUiState(ttsAvailability = TtsAvailability.READY))

        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                ReaderScreen(
                    state = screenState,
                    onArticleTextChange = {
                        enteredText = it
                        screenState = screenState.copy(articleText = it)
                    },
                    onLanguageChange = {
                        selectedLanguage = it
                        screenState = screenState.copy(language = it)
                    },
                    onSegmentArticle = { segmentClicked = true },
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
                )
            }
        }

        composeRule.onNodeWithTag("article_input").performTextReplacement("Hello world.")
        composeRule.onNodeWithTag("language_cantonese").performClick()
        composeRule.onNodeWithTag("segment_button").performClick()

        composeRule.runOnIdle {
            assertEquals("Hello world.", enteredText)
            assertEquals(LearningLanguage.CANTONESE, selectedLanguage)
            assertTrue(segmentClicked)
        }
    }

    @Test
    fun missingMiniMaxConfigurationShowsSettingsHint() {
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                ReaderScreen(
                    state = ReaderUiState(ttsAvailability = TtsAvailability.MISSING_DATA),
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
                )
            }
        }

        composeRule.onNodeWithText("请到设置填写MiniMax API Key").assertExists()
        composeRule.onNodeWithTag("play_pause_button").assertIsNotEnabled()
    }

    @Test
    fun sentenceAndPlaybackModeControlsDispatchEvents() {
        var selectedSentence = -1
        var mode = PlaybackMode.CONTINUOUS
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                ReaderScreen(
                    state = ReaderUiState(
                        sentences = listOf(SentenceItem(1, "First sentence.")),
                        selectedIndex = 0,
                        ttsAvailability = TtsAvailability.READY,
                    ),
                    onArticleTextChange = {},
                    onLanguageChange = {},
                    onSegmentArticle = {},
                    onPlaybackModeChange = { mode = it },
                    onSpeedChange = {},
                    onSpeedChangeFinished = {},
                    onPlayOrPause = {},
                    onPreviousSentence = {},
                    onNextSentence = {},
                    onSelectSentence = { selectedSentence = it },
                    onUpdateSentence = { _, _ -> true },
                    onSplitSentence = { _, _, _ -> true },
                    onMergeSentence = { true },
                    onMessageShown = {},
                )
            }
        }

        composeRule.onNodeWithText("First sentence.").assertExists().performClick()
        composeRule.onNodeWithTag("mode_single").performClick()
        composeRule.runOnIdle {
            assertEquals(0, selectedSentence)
            assertEquals(PlaybackMode.SINGLE, mode)
        }
    }
}
