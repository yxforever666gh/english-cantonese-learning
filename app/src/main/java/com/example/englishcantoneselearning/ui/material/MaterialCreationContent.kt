package com.example.englishcantoneselearning.ui.material

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.MaterialLanguage
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
    onListeningBand: (Float) -> Unit,
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EditorialPageHeader(
                eyebrow = "",
                title = "创建学习材料",
                subtitle = "选择语言、IELTS 听力等级和主题。",
            )
            Spacer(Modifier.height(16.dp))
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
                        subtitle = "生成内容会保存到材料库。",
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("IELTS 听力等级", style = MaterialTheme.typography.labelLarge)
                            Text(
                                formatBand(state.listeningBand),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = state.listeningBand,
                            onValueChange = onListeningBand,
                            valueRange = 1f..9f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth().testTag("ielts_listening_slider"),
                        )
                        Text(
                            "英语和粤语共用此等级；分数越高，词汇、语法和表达越复杂。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("主题", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            text = "生成学习材料",
                            onClick = onGenerate,
                            enabled = providerConfigured,
                            icon = R.drawable.ic_search,
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
