package com.example.englishcantoneselearning.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.MiniMaxVoiceSelectionPolicy
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialEmptyState
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.editorialContentWidth
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.material.ConnectionState
import com.example.englishcantoneselearning.ui.material.MaterialUiState

@Composable
internal fun VoiceSelectionCard(
    label: String,
    language: SpeechLanguage,
    voiceId: String,
    voice: MiniMaxVoice?,
    previewingVoiceId: String?,
    onChoose: () -> Unit,
    onPreview: (String, SpeechLanguage) -> Unit,
) {
    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataPill(label, accent = true)
                Column(Modifier.weight(1f)) {
                    Text(voice?.name ?: voiceId, style = MaterialTheme.typography.titleSmall)
                    Text(voiceId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (voice?.kind == MiniMaxVoiceKind.UNKNOWN || voice?.supportedLanguages?.isEmpty() == true) {
                Text("当前但未验证，请先试听确认兼容性。", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onChoose,
                    modifier = Modifier.weight(1f).testTag("choose_voice_${language.name}"),
                ) { Text("选择音色") }
                OutlinedButton(
                    onClick = { onPreview(voiceId, language) },
                    enabled = previewingVoiceId != voiceId,
                    modifier = Modifier.weight(1f).testTag("preview_current_${language.name}"),
                ) { Text(if (previewingVoiceId == voiceId) "试听中…" else "试听当前") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSelectionScreen(
    state: MaterialUiState,
    language: SpeechLanguage,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPreview: (String, SpeechLanguage) -> Unit,
    onSelect: (String) -> Unit,
) {
    val currentVoiceId = when (language) {
        SpeechLanguage.ENGLISH_US -> state.miniMaxConfig.englishVoice
        SpeechLanguage.CANTONESE_HK -> state.miniMaxConfig.cantoneseVoice
        SpeechLanguage.MANDARIN_CN -> state.miniMaxConfig.mandarinVoice
    }
    val voices = MiniMaxVoiceSelectionPolicy.selectableVoices(language, state.voiceCatalog)
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("voice_selection_screen_${language.name}"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("选择${speechLanguageLabel(language)}音色") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("voice_selection_back")) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = state.miniMaxConfig.apiKey.isNotBlank() &&
                            state.voiceCatalogState != ConnectionState.CHECKING,
                        modifier = Modifier.testTag("refresh_voice_catalog"),
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新音色列表")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.editorialContentWidth().testTag("voice_selection_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    EditorialCard(Modifier.fillMaxWidth(), containerColor = EditorialMint) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前音色", style = MaterialTheme.typography.labelSmall, color = EditorialPine)
                            Text(
                                state.voiceCatalog.firstOrNull { it.id == currentVoiceId }?.name ?: currentVoiceId,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(currentVoiceId, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    EditorialStatusPanel(
                        title = "可选范围",
                        body = if (language == SpeechLanguage.ENGLISH_US) {
                            "仅显示普通英文、美国和英国口音的官方系统音色；澳洲、印度等其他口音已隐藏。"
                        } else {
                            "仅显示MiniMax官方系统音色；克隆、设计及自定义音色暂不开放。"
                        },
                        tone = EditorialStatusTone.INFO,
                    )
                }
                if (voices.isEmpty()) {
                    item {
                        EditorialEmptyState(
                            title = "没有可用音色",
                            body = "当前没有符合条件的系统音色，请刷新列表后重试。",
                        )
                    }
                } else {
                    items(voices, key = { it.id }) { voice ->
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (voice.id == currentVoiceId) EditorialMint else EditorialSurface,
                        ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(voice.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (voice.id == currentVoiceId) {
                                    MetadataPill("当前", accent = true)
                                }
                            }
                            Text(voice.id, style = MaterialTheme.typography.bodySmall)
                            Text("官方系统音色", style = MaterialTheme.typography.labelSmall)
                            if (voice.description.isNotBlank()) {
                                Text(voice.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onPreview(voice.id, language) },
                                    enabled = state.previewingVoiceId != voice.id,
                                    modifier = Modifier.weight(1f)
                                        .testTag("preview_voice_${voice.id}_${language.name}"),
                                ) { Text(if (state.previewingVoiceId == voice.id) "试听中…" else "试听") }
                                Button(
                                    onClick = { onSelect(voice.id) },
                                    enabled = voice.id != currentVoiceId,
                                    modifier = Modifier.weight(1f)
                                        .testTag("select_voice_${voice.id}_${language.name}"),
                                ) { Text(if (voice.id == currentVoiceId) "已选择" else "设为当前") }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CustomVoiceCard(
    favorite: CustomVoiceFavorite,
    previewingVoiceId: String?,
    onPreview: (String, SpeechLanguage) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(favorite.displayName, fontWeight = FontWeight.Bold)
            Text(favorite.voiceId, style = MaterialTheme.typography.bodySmall)
            Text(
                "适用：${favorite.languages.sortedBy { it.ordinal }.joinToString("、") { speechLanguageLabel(it) }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                favorite.languages.sortedBy { it.ordinal }.forEach { language ->
                    TextButton(
                        onClick = { onPreview(favorite.voiceId, language) },
                        enabled = previewingVoiceId != favorite.voiceId,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (previewingVoiceId == favorite.voiceId) "试听中" else "试听${speechLanguageLabel(language)}") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun CustomVoiceEditor(
    original: CustomVoiceFavorite,
    onDismiss: () -> Unit,
    onSave: (CustomVoiceFavorite) -> Unit,
) {
    var name by remember(original.id) { mutableStateOf(original.displayName) }
    var voiceId by remember(original.id) { mutableStateOf(original.voiceId) }
    var languages by remember(original.id) { mutableStateOf(original.languages) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (original.voiceId.isBlank()) "添加自定义Voice ID" else "编辑自定义音色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_voice_name_input"),
                )
                OutlinedTextField(
                    value = voiceId,
                    onValueChange = { voiceId = it.take(256) },
                    label = { Text("Voice ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom_voice_id_input"),
                )
                Text("适用语言", fontWeight = FontWeight.SemiBold)
                SpeechLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = language in languages,
                        onClick = {
                            languages = if (language in languages) languages - language else languages + language
                        },
                        label = { Text(speechLanguageLabel(language)) },
                        modifier = Modifier.fillMaxWidth().testTag("custom_voice_language_${language.name}"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(original.copy(displayName = name, voiceId = voiceId, languages = languages)) },
                enabled = name.isNotBlank() && voiceId.isNotBlank() && languages.isNotEmpty(),
                modifier = Modifier.testTag("save_custom_voice_button"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun speechLanguageLabel(language: SpeechLanguage): String = when (language) {
    SpeechLanguage.ENGLISH_US -> "英语"
    SpeechLanguage.CANTONESE_HK -> "粤语"
    SpeechLanguage.MANDARIN_CN -> "普通话"
}

private fun voiceKindLabel(kind: MiniMaxVoiceKind): String = when (kind) {
    MiniMaxVoiceKind.SYSTEM -> "系统音色"
    MiniMaxVoiceKind.CLONED -> "账户复刻音色"
    MiniMaxVoiceKind.DESIGNED -> "账户设计音色"
    MiniMaxVoiceKind.CUSTOM_FAVORITE -> "自定义收藏"
    MiniMaxVoiceKind.UNKNOWN -> "未验证音色"
}
