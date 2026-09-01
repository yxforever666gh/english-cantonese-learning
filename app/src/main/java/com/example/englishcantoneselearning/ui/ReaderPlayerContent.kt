package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.TtsAvailability
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorialSectionHeader(
                    title = "朗读控制",
                    subtitle = if (hasSelection) "第 ${state.selectedIndex + 1} / ${state.sentences.size} 句" else "选择任一句开始朗读",
                )
                Text(
                    if (state.playbackStatus == PlaybackStatus.PREPARING) "正在生成 MiniMax 语音…"
                    else ttsStatusText(state.ttsAvailability),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.ttsAvailability == TtsAvailability.READY) EditorialPine else MaterialTheme.colorScheme.error,
                )
                ReaderTransportControls(
                    playing = state.playbackStatus == PlaybackStatus.PLAYING,
                    preparing = state.playbackStatus == PlaybackStatus.PREPARING,
                    canPlay = canPlay,
                    hasPrevious = hasSelection && state.selectedIndex > 0,
                    hasNext = hasSelection && state.selectedIndex < state.sentences.lastIndex,
                    onPrevious = onPreviousSentence,
                    onPlayPause = onPlayOrPause,
                    onNext = onNextSentence,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (state.language == LearningLanguage.ENGLISH) "英语语速" else "粤语语速",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = state.speed,
                        onValueChange = onSpeedChange,
                        onValueChangeFinished = onSpeedChangeFinished,
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.weight(1f).testTag("speed_slider"),
                    )
                    Text(
                        String.format(Locale.US, "%.1fx", state.speed),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            if (state.playbackStatus == PlaybackStatus.PREPARING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag("speech_preparing"),
                    color = EditorialTerracotta,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable { expanded = true }.testTag("reader_player_expand"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        if (hasSelection) "第 ${state.selectedIndex + 1} / ${state.sentences.size} 句" else "等待断句",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (state.ttsAvailability == TtsAvailability.READY) {
                            if (state.playbackStatus == PlaybackStatus.PREPARING) "正在准备语音…"
                            else "${if (state.playbackMode == PlaybackMode.SINGLE) "单句" else "连续"} · ${String.format(Locale.US, "%.1fx", state.speed)}"
                        } else {
                            "请到设置填写MiniMax API Key"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.ttsAvailability == TtsAvailability.READY) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = onPreviousSentence,
                    enabled = hasSelection && state.selectedIndex > 0,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一句")
                }
                FilledIconButton(
                    onClick = onPlayOrPause,
                    enabled = canPlay && state.playbackStatus != PlaybackStatus.PREPARING,
                    modifier = Modifier.size(52.dp).testTag("play_pause_button"),
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
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(
                    onClick = onNextSentence,
                    enabled = hasSelection && state.selectedIndex < state.sentences.lastIndex,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一句")
                }
            }
        }
    }
}

@Composable
private fun ReaderTransportControls(
    playing: Boolean,
    preparing: Boolean,
    canPlay: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = hasPrevious,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一句")
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = canPlay && !preparing,
                modifier = Modifier.size(60.dp).testTag("sheet_play_pause_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = EditorialPine,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause
                        else R.drawable.ic_play_arrow,
                    ),
                    contentDescription = if (playing) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onNext,
                enabled = hasNext,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一句")
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
