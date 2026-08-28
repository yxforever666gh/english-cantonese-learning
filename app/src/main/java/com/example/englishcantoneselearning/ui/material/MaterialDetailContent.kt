package com.example.englishcantoneselearning.ui.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialPlayerSurface
import com.example.englishcantoneselearning.ui.EditorialProgress
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialSegmentedControl
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta

@Composable
internal fun MaterialDetail(
    state: MaterialUiState,
    material: PracticeMaterial,
    modifier: Modifier,
    onPlaySentence: (Int) -> Unit,
    onDelete: () -> Unit,
    onDeleteBatch: () -> Unit,
    onRestart: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.testTag("material_detail_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val progress = state.playbackProgress[material.id]
            val percent = progress?.percent(material.sentences.size) ?: 0
            EditorialPageHeader(
                eyebrow = "Long-form Listening",
                title = material.title,
                subtitle = if (material.origin == ArticleOrigin.AI_GENERATED) {
                    "点击任一句，将按“目标语 → 简体中文”播放。"
                } else {
                    "点击任一句播放粘贴文章原文。"
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetadataPill(languageLabel(material.language), accent = true)
                MetadataPill(difficultyLabel(material.difficulty))
                MetadataPill(material.topic)
                MetadataPill("${material.sentences.size} 句")
            }
            Spacer(Modifier.height(14.dp))
            EditorialCard(Modifier.fillMaxWidth(), containerColor = EditorialMint) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("听力进度", style = MaterialTheme.typography.titleSmall)
                        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = EditorialPine)
                    }
                    EditorialProgress(percent / 100f)
                    Text(
                        if (progress?.completed == true) "听力进度：已完成 · 100%" else
                            "听力进度：${progress?.completedSentenceIndices?.size ?: 0}/${material.sentences.size}句 · $percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress?.completed == true) TextButton(onClick = onRestart) { Text("重新播放") }
                }
            }
        }
        items(material.sentences.size) { index ->
            val sentence = material.sentences[index]
            val selected = index == state.selectedSentenceIndex
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                material.sections.firstOrNull { it.startSentenceIndex == index }?.let { section ->
                    Text(
                        section.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = EditorialInk,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                EditorialCard(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaySentence(index) },
                    containerColor = if (selected) EditorialMint else EditorialSurface,
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetadataPill("${index + 1}", accent = selected)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                sentence.targetText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = EditorialInk,
                            )
                            sentence.jyutping?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = EditorialTerracotta, style = MaterialTheme.typography.bodyMedium)
                            }
                            sentence.simplifiedChinese?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected && state.playbackStatus in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)) {
                                Text(
                                    if (state.playbackStatus == PlaybackStatus.PREPARING) {
                                        "正在生成 MiniMax 语音…"
                                    } else if (state.bilingualPhase == BilingualPhase.TARGET) {
                                        "正在朗读目标语"
                                    } else {
                                        "正在朗读简体中文"
                                    },
                                    color = EditorialPine,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            EditorialCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (material.sources.isNotEmpty()) {
                        EditorialSectionHeader(title = "资料来源", subtitle = "查看生成或整理本文时使用的原始资料。")
                    }
                    material.sources.forEach { source ->
                        TextButton(onClick = { uriHandler.openUri(source.url) }) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(source.title)
                                Text(
                                    listOfNotNull(source.publisher, source.publishedAt, source.sourceLanguage)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开来源")
                        }
                    }
                    if (material.origin == ArticleOrigin.AI_GENERATED) {
                        Text(
                            "模型 ${material.providerName} / ${material.model} · 输入 ${material.inputTokens} / 输出 ${material.outputTokens} tokens · ${material.promptVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("删除本篇")
                        }
                        if (material.batchId != material.id) TextButton(onClick = onDeleteBatch) { Text("删除旧批次") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MaterialPlayerPanel(
    state: MaterialUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val material = state.selectedMaterial ?: return
    val ready = state.targetAvailability == TtsAvailability.READY &&
        (material.origin == ArticleOrigin.MANUAL_PASTE || state.mandarinAvailability == TtsAvailability.READY)
    val hasSelection = state.selectedSentenceIndex in material.sentences.indices
    val progress = state.playbackProgress[material.id]
    val percent = progress?.percent(material.sentences.size) ?: 0
    EditorialPlayerSurface(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (hasSelection) "第 ${state.selectedSentenceIndex + 1} / ${material.sentences.size} 句" else "等待播放",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "已完成 ${progress?.completedSentenceIndices?.size ?: 0}/${material.sentences.size}句 · $percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetadataPill(if (state.playbackMode == PlaybackMode.SINGLE) "单组" else "连续", accent = true)
        }
        EditorialProgress(percent / 100f, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (ready && material.origin == ArticleOrigin.MANUAL_PASTE) "目标语语音已就绪"
            else if (ready) "目标语与普通话语音已就绪" else voiceStatus(state),
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) EditorialPine else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (!ready) {
            Text(
                "请到设置填写MiniMax API Key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.playbackStatus == PlaybackStatus.PREPARING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("material_speech_preparing"),
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
                onClick = onPrevious,
                enabled = hasSelection && state.selectedSentenceIndex > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = ready && material.sentences.isNotEmpty() && state.playbackStatus != PlaybackStatus.PREPARING,
                modifier = Modifier.size(58.dp).testTag("material_play_pause"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = EditorialPine,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    if (state.playbackStatus == PlaybackStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playbackStatus == PlaybackStatus.PLAYING) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onNext,
                enabled = hasSelection && state.selectedSentenceIndex < material.sentences.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一句")
            }
        }
        EditorialSegmentedControl(
            options = listOf(PlaybackMode.SINGLE to "单组", PlaybackMode.CONTINUOUS to "连续"),
            selected = state.playbackMode,
            onSelect = onPlaybackModeChange,
            modifier = Modifier.padding(top = 8.dp),
            optionModifier = { mode -> Modifier.testTag("material_mode_${mode.name.lowercase()}") },
        )
    }
}

private fun voiceStatus(state: MaterialUiState): String = when {
    state.targetAvailability != TtsAvailability.READY && state.mandarinAvailability != TtsAvailability.READY -> "缺少目标语和普通话语音"
    state.targetAvailability != TtsAvailability.READY -> "缺少目标语语音"
    else -> "缺少普通话语音"
}

internal fun languageLabel(language: MaterialLanguage): String = when (language) {
    MaterialLanguage.ENGLISH -> "English"
    MaterialLanguage.CANTONESE -> "粤语"
}

internal fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> "轻松"
    Difficulty.TARGET -> "适合"
    Difficulty.CHALLENGE -> "挑战"
}

internal fun formatBand(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)

internal fun generationStageLabel(stage: GenerationStage): String = when (stage) {
    GenerationStage.DISCOVERING_SOURCE -> "查找固定来源"
    GenerationStage.FETCHING_SOURCE -> "下载文章"
    GenerationStage.CLEANING_SOURCE -> "清洗正文"
    GenerationStage.CONNECTING -> "连接模型"
    GenerationStage.SEARCHING -> "搜索来源"
    GenerationStage.REASONING -> "分析结构"
    GenerationStage.WRITING -> "生成正文"
    GenerationStage.VALIDATING -> "验证内容"
    GenerationStage.SAVING -> "保存章节"
    GenerationStage.FAILOVER -> "切换模型"
    GenerationStage.COMPLETED -> "完成"
}
