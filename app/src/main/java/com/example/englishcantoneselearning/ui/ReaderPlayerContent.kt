package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.TtsAvailability
import java.util.Locale

@Composable
internal fun PlayerPanel(
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
                Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一句")
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
                    painter = painterResource(
                        if (state.playbackStatus == PlaybackStatus.PLAYING) R.drawable.ic_pause
                        else R.drawable.ic_play_arrow,
                    ),
                    contentDescription = if (state.playbackStatus == PlaybackStatus.PLAYING) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onNextSentence,
                enabled = hasSelection && state.selectedIndex < state.sentences.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一句")
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

private fun ttsStatusText(availability: TtsAvailability): String = when (availability) {
    TtsAvailability.INITIALIZING -> "正在初始化MiniMax语音…"
    TtsAvailability.READY -> "MiniMax语音已就绪"
    TtsAvailability.MISSING_DATA -> "尚未配置MiniMax API Key"
    TtsAvailability.UNSUPPORTED -> "MiniMax语音配置不受支持"
    TtsAvailability.ERROR -> "MiniMax语音不可用"
}

internal const val ENGLISH_SAMPLE = "Paste an English article here. The app will split it into sentences."
internal const val CANTONESE_SAMPLE = "喺呢度貼上粵語文章。應用程式會自動斷句。"
