package com.example.englishcantoneselearning.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReadingControlsDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun numericStepperCommitsButtonsAndFinishedInputOnlyOnce() {
        var value by mutableFloatStateOf(0.8f)
        val committed = mutableListOf<Float>()
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                NumericStepper(
                    label = "英语语速",
                    value = value,
                    range = 0.5f..2f,
                    step = 0.1f,
                    decimalPlaces = 1,
                    unit = "x",
                    onValueCommitted = {
                        value = it
                        committed += it
                    },
                    testTagPrefix = "speed",
                )
            }
        }

        composeRule.onNodeWithTag("speed_increase").performClick()
        composeRule.onNodeWithTag("speed_input").performTextReplacement("1.26")
        composeRule.onNodeWithTag("speed_input").performImeAction()
        composeRule.onNodeWithTag("speed_input").assertTextEquals("1.3")
        composeRule.runOnIdle { assertEquals(listOf(0.9f, 1.3f), committed) }

        composeRule.onNodeWithTag("speed_input").performTextReplacement(".")
        composeRule.onNodeWithTag("speed_input").performImeAction()
        composeRule.onNodeWithTag("speed_input").assertTextEquals("1.3")
        composeRule.runOnIdle { assertEquals(2, committed.size) }
    }

    @Test
    fun numericStepperDisablesButtonAtBoundary() {
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                NumericStepper(
                    label = "字号",
                    value = 12f,
                    range = 12f..32f,
                    step = 1f,
                    decimalPlaces = 0,
                    unit = "sp",
                    onValueCommitted = {},
                    testTagPrefix = "font",
                )
            }
        }
        composeRule.onNodeWithTag("font_decrease").assertIsNotEnabled()
    }

    @Test
    fun playerStartsCollapsedAndExposesCompactSentenceSemantics() {
        var articleKey by mutableStateOf("first")
        composeRule.setContent {
            EnglishCantoneseLearningTheme(dynamicColor = false) {
                CollapsiblePlayerSurface(
                    stateKey = articleKey,
                    playing = false,
                    preparing = false,
                    canPlay = true,
                    hasPrevious = false,
                    hasNext = true,
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    testTagPrefix = "test_player",
                ) {
                    Text("播放器设置")
                    CompactSentenceNumberBadge(number = 123, selected = true)
                }
            }
        }

        composeRule.onNodeWithText("播放器设置").assertDoesNotExist()
        composeRule.onNodeWithTag("test_player_previous").assertIsNotEnabled()
        composeRule.onNodeWithTag("test_player_expand").performClick()
        composeRule.onNodeWithText("播放器设置").assertExists()
        composeRule.onNodeWithContentDescription("第 123 句").assertExists()

        composeRule.runOnIdle { articleKey = "second" }
        composeRule.onNodeWithText("播放器设置").assertDoesNotExist()
    }
}
