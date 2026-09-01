package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.ReaderUiState
import com.example.englishcantoneselearning.model.SentenceItem

@Composable
internal fun ArticleInputSection(
    state: ReaderUiState,
    onArticleTextChange: (String) -> Unit,
    onLanguageChange: (LearningLanguage) -> Unit,
    onSegmentArticle: () -> Unit,
    onTitleChange: (String) -> Unit,
    onSaveArticle: () -> Unit,
) {
    var editingInput by rememberSaveable { mutableStateOf(state.sentences.isEmpty()) }
    val showEditor = state.sentences.isEmpty() || editingInput

    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EditorialSectionHeader(
                title = if (showEditor) "准备原文" else "原文已断句",
                subtitle = if (showEditor) "选择语言并粘贴需要练习的内容。" else
                    "${state.sentences.size} 句 · ${if (state.language == LearningLanguage.ENGLISH) "英语" else "粤语"}",
            )
            if (showEditor) {
                EditorialSegmentedControl(
                    options = listOf(
                        LearningLanguage.ENGLISH to "英语（美国）",
                        LearningLanguage.CANTONESE to "粤语（香港）",
                    ),
                    selected = state.language,
                    onSelect = onLanguageChange,
                    optionModifier = { language ->
                        Modifier.testTag(if (language == LearningLanguage.ENGLISH) "language_english" else "language_cantonese")
                    },
                )
                OutlinedTextField(
                    value = state.articleText,
                    onValueChange = onArticleTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 156.dp)
                        .testTag("article_input"),
                    label = { Text("输入或粘贴原文") },
                    placeholder = {
                        Text(if (state.language == LearningLanguage.ENGLISH) ENGLISH_SAMPLE else CANTONESE_SAMPLE)
                    },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 5,
                    maxLines = 10,
                )
                EditorialPrimaryButton(
                    text = if (state.sentences.isEmpty()) "自动断句" else "重新断句",
                    onClick = {
                        onSegmentArticle()
                        editingInput = false
                    },
                    icon = R.drawable.ic_edit,
                    modifier = Modifier.testTag("segment_button"),
                )
            } else {
                Text(
                    state.articleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                OutlinedButton(
                    onClick = { editingInput = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("edit_article_input"),
                ) { Text("编辑原文") }
                Text(
                    "保存到材料库",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.articleTitle,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth().testTag("manual_article_title"),
                    label = { Text("文章标题") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                EditorialPrimaryButton(
                    text = "保存学习材料",
                    onClick = onSaveArticle,
                    icon = R.drawable.ic_save,
                    modifier = Modifier.testTag("save_manual_article"),
                )
            }
        }
    }
}

@Composable
internal fun SentenceCard(
    index: Int,
    sentence: SentenceItem,
    selected: Boolean,
    playing: Boolean,
    canMerge: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentence_card_$index")
            .clickable(onClick = onPlay),
        color = if (selected) EditorialMint else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 48.dp)
                    .background(if (selected) EditorialPine else Color.Transparent, RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.size(10.dp))
            Surface(
                modifier = Modifier.size(32.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (selected) EditorialPine else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (playing) "▶" else "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else EditorialPine,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = sentence.text,
                style = MaterialTheme.typography.bodyLarge,
                color = EditorialInk,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "句子操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑或拆分") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("与下一句合并") },
                        enabled = canMerge,
                        onClick = {
                            menuExpanded = false
                            onMerge()
                        },
                    )
                }
            }
        }
    }
}
