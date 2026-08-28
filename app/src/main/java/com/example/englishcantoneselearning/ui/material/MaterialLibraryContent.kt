package com.example.englishcantoneselearning.ui.material

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialEmptyState
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialProgress
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialSegmentedControl
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import java.text.DateFormat

@Composable
internal fun ArticleLibrary(
    state: MaterialUiState,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onLanguage: (MaterialLanguage) -> Unit,
    onToggleSelection: (String) -> Unit,
    onCacheSelected: () -> Unit,
    onCancelCaching: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val englishCount = state.materials.count { it.language == MaterialLanguage.ENGLISH }
    val cantoneseCount = state.materials.count { it.language == MaterialLanguage.CANTONESE }
    val filteredMaterials = state.materials.filter { it.language == state.libraryLanguage }
    val languageName = if (state.libraryLanguage == MaterialLanguage.ENGLISH) "英语" else "粤语"
    val editing = state.librarySelectedArticleIds.isNotEmpty()
    val selectedMaterials = state.materials.filter { it.id in state.librarySelectedArticleIds }
    val selectedAudioCount = selectedMaterials.sumOf { material ->
        material.sentences.sumOf { sentence ->
            1 + if (material.origin == ArticleOrigin.AI_GENERATED && sentence.simplifiedChinese?.isNotBlank() == true) 1 else 0
        }
    }
    var showCacheConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showCacheConfirmation) {
        AlertDialog(
            onDismissRequest = { showCacheConfirmation = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("提前缓存语音？") },
            text = { Text("将为 ${selectedMaterials.size} 篇文章准备约 $selectedAudioCount 段语音。未缓存内容会调用 MiniMax，可能产生费用。") },
            confirmButton = {
                TextButton(onClick = {
                    showCacheConfirmation = false
                    onCacheSelected()
                }) { Text("开始缓存") }
            },
            dismissButton = { TextButton(onClick = { showCacheConfirmation = false }) { Text("取消") } },
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("删除所选文章？") },
            text = { Text("将删除 ${selectedMaterials.size} 篇文章及其听力进度。已生成的共享语音缓存不会立即删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDeleteSelected()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("取消") } },
        )
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            EditorialPageHeader(
                eyebrow = "Your Reading Shelf",
                title = "文章列表",
                subtitle = "集中保存生成材料与手动文章，随时回到上次的聆听进度。",
            )
            Spacer(Modifier.height(20.dp))
            EditorialSegmentedControl(
                options = listOf(
                    MaterialLanguage.ENGLISH to "English（$englishCount）",
                    MaterialLanguage.CANTONESE to "粤语（$cantoneseCount）",
                ),
                selected = state.libraryLanguage,
                onSelect = onLanguage,
                optionModifier = { language ->
                    Modifier.testTag(
                        if (language == MaterialLanguage.ENGLISH) "library_language_english" else "library_language_cantonese",
                    )
                },
            )
            Spacer(Modifier.height(22.dp))
            EditorialSectionHeader(
                title = "已保存${languageName}文章（${filteredMaterials.size}）",
                subtitle = "生成材料和自建文章都会显示在这里；长按可批量管理。",
            )
        }
        if (editing) {
            item {
                EditorialStatusPanel(
                    title = "已选择 ${state.librarySelectedArticleIds.size} 篇",
                    body = state.audioCachingProgress ?: if (state.isAudioCaching) "正在提前缓存语音" else "可以提前缓存语音或删除所选文章。",
                    tone = EditorialStatusTone.WARNING,
                    action = {
                        if (state.isAudioCaching) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                color = EditorialTerracotta,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            OutlinedButton(
                                onClick = onCancelCaching,
                                modifier = Modifier.fillMaxWidth().testTag("cancel_library_cache"),
                            ) { Text("取消缓存") }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showCacheConfirmation = true },
                                    modifier = Modifier.weight(1f).testTag("cache_selected_articles"),
                                ) { Text("提前缓存语音") }
                                OutlinedButton(
                                    onClick = { showDeleteConfirmation = true },
                                    modifier = Modifier.weight(1f).testTag("delete_selected_articles"),
                                ) { Text("删除文章") }
                            }
                        }
                    },
                )
            }
        }
        if (!state.isLoading && filteredMaterials.isEmpty()) {
            item {
                EditorialEmptyState(
                    title = "书架还是空的",
                    body = "还没有保存的${languageName}文章，请先到“智能材料”生成或粘贴保存。",
                )
            }
        }
        items(filteredMaterials, key = { it.id }) { material ->
            MaterialCard(
                material = material,
                progress = state.playbackProgress[material.id],
                selectionMode = editing,
                selected = material.id in state.librarySelectedArticleIds,
                onClick = {
                    if (editing) onToggleSelection(material.id) else onOpen(material.id)
                },
                onLongClick = { onToggleSelection(material.id) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MaterialCard(
    material: PracticeMaterial,
    progress: com.example.englishcantoneselearning.model.MaterialPlaybackProgress?,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_article_${material.id}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        containerColor = if (selected) EditorialMint else EditorialSurface,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.size(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(material.title, style = MaterialTheme.typography.titleLarge, color = EditorialInk)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetadataPill(languageLabel(material.language), accent = true)
                    MetadataPill(difficultyLabel(material.difficulty))
                    MetadataPill(material.topic)
                }
                Text(
                    "${if (material.origin == ArticleOrigin.AI_GENERATED) "AI生成" else "手动粘贴"} · " +
                        "${material.sentences.size} 句 · ${DateFormat.getDateInstance().format(progress?.updatedAt?.takeIf { it > 0 } ?: material.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val percent = progress?.percent(material.sentences.size) ?: 0
                EditorialProgress(percent / 100f)
                Text(
                    if (progress?.completed == true) "已完成 · 100%" else
                        "已完成 ${progress?.completedSentenceIndices?.size ?: 0}/${material.sentences.size} 句 · $percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = EditorialPine,
                )
            }
        }
    }
}
