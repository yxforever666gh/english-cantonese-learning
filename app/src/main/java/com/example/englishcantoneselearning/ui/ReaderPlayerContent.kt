package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.data.preferences.MAX_READING_FONT_SIZE_SP
import com.example.englishcantoneselearning.data.preferences.MIN_READING_FONT_SIZE_SP
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import kotlin.math.roundToInt

@Composable
internal fun PlayerPanel(
    state: ReaderUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedChangeFinished: () -> Unit,
    onReadingFontSizeChange: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
) {
    val canPlay = state.sentences.isNotEmpty() && state.ttsAvailability == TtsAvailability.READY
    val hasSelection = state.selectedIndex in state.sentences.indices
    val stateKey = "${state.language}:${state.articleText.hashCode()}"

    CollapsiblePlayerSurface(
        stateKey = stateKey,
        playing = state.playbackStatus == PlaybackStatus.PLAYING,
        preparing = state.playbackStatus == PlaybackStatus.PREPARING,
        canPlay = canPlay,
        hasPrevious = hasSelection && state.selectedIndex > 0,
        hasNext = hasSelection && state.selectedIndex < state.sentences.lastIndex,
        onPrevious = onPreviousSentence,
        onPlayPause = onPlayOrPause,
        onNext = onNextSentence,
        testTagPrefix = "reader_player",
    ) {
        EditorialSectionHeader(
            title = "朗读控制",
            subtitle = if (hasSelection) {
                "第 ${state.selectedIndex + 1} / ${state.sentences.size} 句"
            } else {
                "选择任一句开始朗读"
            },
        )
        Text(
            text = if (state.playbackStatus == PlaybackStatus.PREPARING) {
                "正在生成 MiniMax 语音…"
            } else {
                ttsStatusText(state.ttsAvailability)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.ttsAvailability == TtsAvailability.READY) {
                EditorialPine
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorialChoiceChip(
                selected = state.playbackMode == PlaybackMode.SINGLE,
                onClick = { onPlaybackModeChange(PlaybackMode.SINGLE) },
                label = "单句",
                modifier = Modifier.weight(1f).testTag("mode_single"),
            )
            EditorialChoiceChip(
                selected = state.playbackMode == PlaybackMode.CONTINUOUS,
                onClick = { onPlaybackModeChange(PlaybackMode.CONTINUOUS) },
                label = "连续",
                modifier = Modifier.weight(1f).testTag("mode_continuous"),
            )
        }
        NumericStepper(
            label = if (state.language == LearningLanguage.ENGLISH) "英语语速" else "粤语语速",
            value = state.speed,
            range = 0.5f..2.0f,
            step = 0.1f,
            decimalPlaces = 1,
            unit = "x",
            onValueCommitted = { speed ->
                onSpeedChange(speed)
                onSpeedChangeFinished()
            },
            testTagPrefix = "reader_speed",
        )
        NumericStepper(
            label = "正文字号",
            value = state.readingFontSizeSp.toFloat(),
            range = MIN_READING_FONT_SIZE_SP.toFloat()..MAX_READING_FONT_SIZE_SP.toFloat(),
            step = 1f,
            decimalPlaces = 0,
            unit = "sp",
            onValueCommitted = { onReadingFontSizeChange(it.roundToInt()) },
            testTagPrefix = "reader_font_size",
        )
    }
}

private fun ttsStatusText(availability: TtsAvailability): String = when (availability) {
    TtsAvailability.INITIALIZING -> "正在初始化MiniMax语音…"
    TtsAvailability.READY -> "MiniMax语音已就绪"
    TtsAvailability.MISSING_DATA -> "尚未配置MiniMax API Key"
    TtsAvailability.UNSUPPORTED -> "MiniMax语音配置不受支持"
    TtsAvailability.ERROR -> "MiniMax语音不可用"
}

internal const val ENGLISH_SAMPLE = "Paste an English article here. The app will split it into sentences."
internal const val CANTONESE_SAMPLE = "喺呢度貼上粵語文章。應用程式會自動斷句。"
