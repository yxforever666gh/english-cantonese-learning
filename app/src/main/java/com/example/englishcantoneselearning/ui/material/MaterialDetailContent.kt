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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.data.preferences.MAX_READING_FONT_SIZE_SP
import com.example.englishcantoneselearning.data.preferences.MIN_READING_FONT_SIZE_SP
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
import com.example.englishcantoneselearning.ui.CollapsiblePlayerSurface
import com.example.englishcantoneselearning.ui.CompactSentenceNumberBadge
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.NumericStepper
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
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
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        CompactSentenceNumberBadge(
                            number = index + 1,
                            selected = selected,
                        )
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                sentence.targetText,
                                fontSize = state.readingFontSizeSp.sp,
                                lineHeight = (state.readingFontSizeSp * 1.5f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EditorialInk,
                            )
                            sentence.jyutping?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    color = EditorialTerracotta,
                                    fontSize = state.readingFontSizeSp.sp,
                                    lineHeight = (state.readingFontSizeSp * 1.5f).sp,
                                )
                            }
                            sentence.simplifiedChinese?.takeIf {
                                it.isNotBlank() &&
                                    (material.origin != ArticleOrigin.NEWS_FEED || state.showNewsTranslations)
                            }?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = state.readingFontSizeSp.sp,
                                    lineHeight = (state.readingFontSizeSp * 1.5f).sp,
                                )
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

@Composable
internal fun MaterialPlayerPanel(
    state: MaterialUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSpeedChange: (SpeechLanguage, Float) -> Unit,
    onSpeedChangeFinished: (SpeechLanguage) -> Unit,
    onShowNewsTranslations: (Boolean) -> Unit,
    onReadingFontSizeChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val material = state.selectedMaterial ?: return
    val usesTranslation = material.origin == ArticleOrigin.AI_GENERATED ||
        (material.origin == ArticleOrigin.NEWS_FEED && state.showNewsTranslations &&
            material.sentences.any { !it.simplifiedChinese.isNullOrBlank() })
    val ready = state.targetAvailability == TtsAvailability.READY &&
        (!usesTranslation || state.mandarinAvailability == TtsAvailability.READY)
    val hasSelection = state.selectedSentenceIndex in material.sentences.indices
    val progress = state.playbackProgress[material.id]
    val percent = progress?.percent(material.sentences.size) ?: 0
    val targetLanguage = material.language.toSpeechLanguage()
    CollapsiblePlayerSurface(
        stateKey = material.id,
        playing = state.playbackStatus == PlaybackStatus.PLAYING,
        preparing = state.playbackStatus == PlaybackStatus.PREPARING,
        canPlay = ready && material.sentences.isNotEmpty(),
        hasPrevious = hasSelection && state.selectedSentenceIndex > 0,
        hasNext = hasSelection && state.selectedSentenceIndex < material.sentences.lastIndex,
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        testTagPrefix = "material_player",
        modifier = Modifier.fillMaxWidth(),
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
            if (ready && !usesTranslation) "目标语语音已就绪"
            else if (ready) "目标语与普通话语音已就绪" else voiceStatus(state),
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) EditorialPine else MaterialTheme.colorScheme.error,
        )
        if (material.origin == ArticleOrigin.NEWS_FEED &&
            material.sentences.any { !it.simplifiedChinese.isNullOrBlank() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("material_news_translation_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("显示中文翻译", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = state.showNewsTranslations,
                    onCheckedChange = onShowNewsTranslations,
                )
            }
        }
        EditorialSegmentedControl(
            options = listOf(PlaybackMode.SINGLE to "单句", PlaybackMode.CONTINUOUS to "连续"),
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
        if (usesTranslation) {
            MaterialSpeedSetting(
                label = "中文翻译语速",
                language = SpeechLanguage.MANDARIN_CN,
                value = speechSpeed(state, SpeechLanguage.MANDARIN_CN),
                onChange = onSpeedChange,
                onChangeFinished = onSpeedChangeFinished,
            )
        }
        NumericStepper(
            label = "正文字号",
            value = state.readingFontSizeSp.toFloat(),
            range = MIN_READING_FONT_SIZE_SP.toFloat()..MAX_READING_FONT_SIZE_SP.toFloat(),
            step = 1f,
            decimalPlaces = 0,
            unit = "sp",
        onValueCommitted = { onReadingFontSizeChange(it.toInt()) },
        testTagPrefix = "material_font_size",
        modifier = Modifier.testTag("material_font_size"),
        )
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
    NumericStepper(
        label = label,
        value = value,
        range = 0.5f..2.0f,
        step = 0.1f,
        decimalPlaces = 1,
        unit = "x",
        onValueCommitted = {
            onChange(language, it)
            onChangeFinished(language)
        },
        testTagPrefix = "material_speed_${language.name.lowercase()}",
        modifier = Modifier.testTag("material_speed_${language.name.lowercase()}"),
    )
}

private fun speechSpeed(state: MaterialUiState, language: SpeechLanguage): Float = when (language) {
    SpeechLanguage.ENGLISH_US -> state.englishSpeed
    SpeechLanguage.CANTONESE_HK -> state.cantoneseSpeed
    SpeechLanguage.MANDARIN_CN -> state.mandarinSpeed
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
