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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EditorialSectionHeader(
                title = "准备文本",
                subtitle = "选择朗读语言，然后粘贴需要练习的文章。",
            )
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
                    .heightIn(min = 170.dp)
                    .testTag("article_input"),
                label = { Text("输入或粘贴文章") },
                placeholder = {
                    Text(if (state.language == LearningLanguage.ENGLISH) ENGLISH_SAMPLE else CANTONESE_SAMPLE)
                },
                shape = RoundedCornerShape(14.dp),
                minLines = 6,
                maxLines = 12,
            )

            EditorialPrimaryButton(
                text = if (state.sentences.isEmpty()) "自动断句" else "重新断句",
                onClick = onSegmentArticle,
                icon = Icons.Default.Edit,
                modifier = Modifier.testTag("segment_button"),
            )
            if (state.sentences.isNotEmpty()) {
                Text(
                    "保存设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.articleTitle,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth().testTag("manual_article_title"),
                    label = { Text("文章标题") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                EditorialPrimaryButton(
                    text = "保存到文章列表",
                    onClick = onSaveArticle,
                    icon = Icons.Default.Save,
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
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentence_card_$index")
            .clickable(onClick = onPlay),
        containerColor = if (selected) EditorialMint else EditorialSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 48.dp)
                    .background(if (selected) EditorialPine else Color.Transparent, RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.size(10.dp))
            androidx.compose.material3.Surface(
                modifier = Modifier.size(32.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (selected) EditorialPine else MaterialTheme.colorScheme.surfaceContainer,
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
                    Icon(Icons.Default.MoreVert, contentDescription = "句子操作")
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
