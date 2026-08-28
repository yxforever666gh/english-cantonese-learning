package com.example.englishcantoneselearning.ui.material

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialChoiceChip
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialPrimaryButton
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialSegmentedControl
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.theme.EditorialPine

@Composable
internal fun MaterialCreation(
    state: MaterialUiState,
    modifier: Modifier,
    onLanguage: (MaterialLanguage) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onTopic: (MaterialTopic) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onResumeDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onOpenSettings: () -> Unit,
    creationSwitcher: @Composable () -> Unit,
) {
    val providerConfigured = state.materialProviders.any { it.enabled && it.apiKey.isNotBlank() }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            EditorialPageHeader(
                eyebrow = "Curated Practice",
                title = "智能材料",
                subtitle = "从固定来源生成与你当前水平匹配的英粤听力长文。",
            )
            Spacer(Modifier.height(20.dp))
            creationSwitcher()
        }
        if (!providerConfigured) {
            item {
                EditorialStatusPanel(
                    title = "尚未配置 API 密钥",
                    body = "请在设置中添加至少一个已启用的Responses兼容材料模型。",
                    tone = EditorialStatusTone.WARNING,
                    action = { TextButton(onClick = onOpenSettings) { Text("打开设置") } },
                )
            }
        }
        item {
            EditorialCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EditorialSectionHeader(
                        title = "生成设置",
                        subtitle = "选择练习语言、难度与文章主题。",
                    )
                    EditorialSegmentedControl(
                        options = listOf(MaterialLanguage.ENGLISH to "English", MaterialLanguage.CANTONESE to "粤语"),
                        selected = state.language,
                        onSelect = onLanguage,
                        optionModifier = { language ->
                            Modifier.testTag(
                                if (language == MaterialLanguage.ENGLISH) "material_language_english" else "material_language_cantonese",
                            )
                        },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("难度", style = MaterialTheme.typography.labelLarge)
                        if (state.language == MaterialLanguage.ENGLISH) {
                            val levels = "IELTS 听力 ${formatBand(state.englishListeningBand)}：" +
                                "轻松 ${formatBand(MaterialLevelRules.effectiveListeningBand(state.englishListeningBand, Difficulty.EASY))} · " +
                                "适合 ${formatBand(MaterialLevelRules.effectiveListeningBand(state.englishListeningBand, Difficulty.TARGET))} · " +
                                "挑战 ${formatBand(MaterialLevelRules.effectiveListeningBand(state.englishListeningBand, Difficulty.CHALLENGE))}"
                            EditorialStatusPanel(
                                title = "个人化分级",
                                body = levels,
                                tone = EditorialStatusTone.INFO,
                                modifier = Modifier.testTag("material_ielts_levels"),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { difficulty ->
                                EditorialChoiceChip(
                                    selected = state.difficulty == difficulty,
                                    onClick = { onDifficulty(difficulty) },
                                    label = difficultyLabel(difficulty),
                                    modifier = Modifier.testTag("difficulty_${difficulty.name.lowercase()}"),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("主题", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MaterialTopic.entries.forEach { topic ->
                                EditorialChoiceChip(
                                    selected = state.topic == topic,
                                    onClick = { onTopic(topic) },
                                    label = topic.displayName,
                                )
                            }
                        }
                    }

                    if (state.isGenerating) {
                        val activity = state.generationActivity
                        val activityText = if (activity == null) "正在准备生成完整长文…" else
                            if (activity.stage in setOf(
                                    GenerationStage.DISCOVERING_SOURCE,
                                    GenerationStage.FETCHING_SOURCE,
                                    GenerationStage.CLEANING_SOURCE,
                                )) {
                                "${activity.provider} · ${generationStageLabel(activity.stage)}"
                            } else {
                                "${activity.provider} · 第${activity.chapter}章 · ${generationStageLabel(activity.stage)} · 已接收${activity.receivedChars}字符"
                            }
                        EditorialStatusPanel(
                            title = "正在生成",
                            body = activityText,
                            tone = EditorialStatusTone.SUCCESS,
                            action = {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    color = EditorialPine,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                            },
                        )
                    } else {
                        EditorialPrimaryButton(
                            text = "从固定来源生成 1 篇长文",
                            onClick = onGenerate,
                            enabled = providerConfigured,
                            icon = Icons.Default.Search,
                            modifier = Modifier.testTag("generate_materials_button"),
                        )
                    }
                }
            }
        }
        if (state.hasPendingDraft && !state.isGenerating) {
            item {
                EditorialStatusPanel(
                    title = "发现未完成草稿",
                    body = "已有章节已安全保存在本机；继续时会从下一章开始。",
                    tone = EditorialStatusTone.INFO,
                    action = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onResumeDraft, modifier = Modifier.weight(1f)) { Text("继续生成") }
                            TextButton(onClick = onDiscardDraft) { Text("删除草稿") }
                        }
                    },
                )
            }
        }
        state.generationError?.let { error ->
            item {
                EditorialStatusPanel(
                    title = "上次生成失败",
                    body = error,
                    tone = EditorialStatusTone.ERROR,
                )
            }
        }
    }
}
