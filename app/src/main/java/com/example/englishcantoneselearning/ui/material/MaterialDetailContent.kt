package com.example.englishcantoneselearning.ui.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.toSpeechLanguage
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialProgress
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialSegmentedControl
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import java.util.Locale

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
    var informationExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.testTag("material_detail_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val progress = state.playbackProgress[material.id]
            val percent = progress?.percent(material.sentences.size) ?: 0
            EditorialPageHeader(
                eyebrow = "",
                title = material.title,
                subtitle = when (material.origin) {
                    ArticleOrigin.AI_GENERATED -> "点击句子，按目标语和简体中文顺序朗读。"
                    ArticleOrigin.MANUAL_PASTE -> "点击任一句，立即从这里开始朗读。"
                    ArticleOrigin.NEWS_FEED -> "新闻原文已保存，点击任一句开始朗读。"
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetadataPill(languageLabel(material.language), accent = true)
                material.listeningBand?.let { MetadataPill("IELTS 听力 ${formatBand(it)}") }
                MetadataPill(material.topic)
                MetadataPill("${material.sentences.size} 句")
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = EditorialMint,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaySentence(index) },
                    color = if (selected) EditorialMint else Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .width(3.dp)
                                .height(44.dp)
                                .background(
                                    if (selected) EditorialPine else Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(99.dp),
                                ),
                        )
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) EditorialPine else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp).padding(top = 2.dp),
                        )
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
                HorizontalDivider(
                    modifier = Modifier.padding(start = 49.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            TextButton(
                onClick = { informationExpanded = !informationExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("材料信息")
                Spacer(Modifier.weight(1f))
                Text(if (informationExpanded) "收起" else "展开")
            }
            if (informationExpanded) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "打开来源")
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
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                            Text("删除本篇")
                        }
                        if (material.batchId != material.id) TextButton(onClick = onDeleteBatch) { Text("删除旧批次") }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialPlayerPanel(
    state: MaterialUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (SpeechLanguage, Float) -> Unit,
    onSpeedChangeFinished: (SpeechLanguage) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val material = state.selectedMaterial ?: return
    val ready = state.targetAvailability == TtsAvailability.READY &&
        (material.origin != ArticleOrigin.AI_GENERATED || state.mandarinAvailability == TtsAvailability.READY)
    val hasSelection = state.selectedSentenceIndex in material.sentences.indices
    val progress = state.playbackProgress[material.id]
    val percent = progress?.percent(material.sentences.size) ?: 0
    val targetLanguage = material.language.toSpeechLanguage()
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorialSectionHeader(
                    title = "朗读控制",
                    subtitle = if (hasSelection) {
                        "第 ${state.selectedSentenceIndex + 1} / ${material.sentences.size} 句 · 已完成 $percent%"
                    } else {
                        "选择任一句开始朗读"
                    },
                )
                EditorialProgress(percent / 100f)
                Text(
                    if (ready && material.origin != ArticleOrigin.AI_GENERATED) "目标语语音已就绪"
                    else if (ready) "目标语与普通话语音已就绪" else voiceStatus(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) EditorialPine else MaterialTheme.colorScheme.error,
                )
                PlayerTransportControls(
                    playing = state.playbackStatus == PlaybackStatus.PLAYING,
                    preparing = state.playbackStatus == PlaybackStatus.PREPARING,
                    canPlay = ready && material.sentences.isNotEmpty(),
                    hasPrevious = hasSelection && state.selectedSentenceIndex > 0,
                    hasNext = hasSelection && state.selectedSentenceIndex < material.sentences.lastIndex,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    playTag = "material_sheet_play_pause",
                )
                EditorialSegmentedControl(
                    options = listOf(PlaybackMode.SINGLE to "单组", PlaybackMode.CONTINUOUS to "连续"),
                    selected = state.playbackMode,
                    onSelect = onPlaybackModeChange,
                    optionModifier = { mode -> Modifier.testTag("material_mode_${mode.name.lowercase()}") },
                )
                MaterialSpeedSetting(
                    label = "${MaterialPlaybackSupport.languageName(targetLanguage)}语速",
                    language = targetLanguage,
                    value = speechSpeed(state, targetLanguage),
                    onChange = onSpeedChange,
                    onChangeFinished = onSpeedChangeFinished,
                )
                if (material.origin == ArticleOrigin.AI_GENERATED) {
                    MaterialSpeedSetting(
                        label = "中文翻译语速",
                        language = SpeechLanguage.MANDARIN_CN,
                        value = speechSpeed(state, SpeechLanguage.MANDARIN_CN),
                        onChange = onSpeedChange,
                        onChangeFinished = onSpeedChangeFinished,
                    )
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EditorialSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            EditorialProgress(percent / 100f)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).clickable { expanded = true }.testTag("material_player_expand"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        if (hasSelection) "第 ${state.selectedSentenceIndex + 1} / ${material.sentences.size} 句" else "选择句子开始朗读",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when {
                            state.playbackStatus == PlaybackStatus.PREPARING -> "正在准备语音…"
                            !ready -> voiceStatus(state)
                            else -> "${if (state.playbackMode == PlaybackMode.SINGLE) "单组" else "连续"} · " +
                                "${formatBand(speechSpeed(state, targetLanguage))}x · 已完成 $percent%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    enabled = hasSelection && state.selectedSentenceIndex > 0,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一句")
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = ready && material.sentences.isNotEmpty() && state.playbackStatus != PlaybackStatus.PREPARING,
                    modifier = Modifier.size(52.dp).testTag("material_play_pause"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = EditorialPine,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        painterResource(
                            if (state.playbackStatus == PlaybackStatus.PLAYING) R.drawable.ic_pause
                            else R.drawable.ic_play_arrow,
                        ),
                        contentDescription = if (state.playbackStatus == PlaybackStatus.PLAYING) "暂停" else "播放",
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = hasSelection && state.selectedSentenceIndex < material.sentences.lastIndex,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一句")
                }
            }
            if (state.playbackStatus == PlaybackStatus.PREPARING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag("material_speech_preparing"),
                    color = EditorialTerracotta,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun MaterialSpeedSetting(
    label: String,
    language: SpeechLanguage,
    value: Float,
    onChange: (SpeechLanguage, Float) -> Unit,
    onChangeFinished: (SpeechLanguage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                String.format(Locale.US, "%.1fx", value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = { onChange(language, it) },
            onValueChangeFinished = { onChangeFinished(language) },
            valueRange = 0.5f..2.0f,
            steps = 14,
            modifier = Modifier.fillMaxWidth().testTag("material_speed_${language.name.lowercase()}"),
        )
    }
}

private fun speechSpeed(state: MaterialUiState, language: SpeechLanguage): Float = when (language) {
    SpeechLanguage.ENGLISH_US -> state.englishSpeed
    SpeechLanguage.CANTONESE_HK -> state.cantoneseSpeed
    SpeechLanguage.MANDARIN_CN -> state.mandarinSpeed
}

@Composable
private fun PlayerTransportControls(
    playing: Boolean,
    preparing: Boolean,
    canPlay: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    playTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一句")
        }
        FilledIconButton(
            onClick = onPlayPause,
            enabled = canPlay && !preparing,
            modifier = Modifier.size(60.dp).testTag(playTag),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = EditorialPine,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (playing) "暂停" else "播放",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onNext, enabled = hasNext, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一句")
        }
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
