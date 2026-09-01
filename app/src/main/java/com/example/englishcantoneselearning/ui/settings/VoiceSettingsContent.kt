package com.example.englishcantoneselearning.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.MiniMaxVoiceSelectionPolicy
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.ui.material.ConnectionState
import com.example.englishcantoneselearning.ui.material.MaterialUiState

@Composable
internal fun VoiceSelectionRow(
    label: String,
    language: SpeechLanguage,
    voiceId: String,
    voice: MiniMaxVoice?,
    previewingVoiceId: String?,
    onChoose: () -> Unit,
    onPreview: (String, SpeechLanguage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onChoose)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("choose_voice_${language.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label.take(1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (voice?.kind == MiniMaxVoiceKind.UNKNOWN || voice?.supportedLanguages?.isEmpty() == true) {
                    Text("未验证", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                voice?.name ?: voiceId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = { onPreview(voiceId, language) },
            enabled = previewingVoiceId != voiceId,
            modifier = Modifier.heightIn(min = 48.dp).testTag("preview_current_${language.name}"),
        ) {
            if (previewingVoiceId == voiceId) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(6.dp))
                Text("试听中")
            } else {
                Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("试听")
            }
        }
        Icon(
            painterResource(R.drawable.ic_open_in_new),
            contentDescription = "选择$label 音色",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
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
                title = { Text("选择${speechLanguageLabel(language)}音色", fontWeight = FontWeight.SemiBold) },
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
                        if (state.voiceCatalogState == ConnectionState.CHECKING) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新音色列表")
                        }
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
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).testTag("voice_selection_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    VoiceSelectionIntro(
                        language = language,
                        currentVoice = state.voiceCatalog.firstOrNull { it.id == currentVoiceId },
                        currentVoiceId = currentVoiceId,
                    )
                }

                if (voices.isEmpty()) {
                    item {
                        VoiceEmptyState(
                            refreshing = state.voiceCatalogState == ConnectionState.CHECKING,
                            canRefresh = state.miniMaxConfig.apiKey.isNotBlank(),
                            onRefresh = onRefresh,
                        )
                    }
                } else {
                    item {
                        Text(
                            "可用音色 · ${voices.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(voices, key = { it.id }) { voice ->
                        VoiceOptionRow(
                            voice = voice,
                            language = language,
                            selected = voice.id == currentVoiceId,
                            previewing = state.previewingVoiceId == voice.id,
                            onPreview = { onPreview(voice.id, language) },
                            onSelect = { onSelect(voice.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSelectionIntro(
    language: SpeechLanguage,
    currentVoice: MiniMaxVoice?,
    currentVoiceId: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("当前音色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(currentVoice?.name ?: currentVoiceId, style = MaterialTheme.typography.titleMedium)
            Text(
                if (language == SpeechLanguage.ENGLISH_US) {
                    "仅展示普通英文、美国和英国口音的官方系统音色。"
                } else {
                    "仅展示适用于${speechLanguageLabel(language)}的官方系统音色。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VoiceOptionRow(
    voice: MiniMaxVoice,
    language: SpeechLanguage,
    selected: Boolean,
    previewing: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(voice.name, style = MaterialTheme.typography.titleSmall)
                        if (selected) {
                            Text("✓ 已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        voice.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (voice.description.isNotBlank()) {
                        Text(
                            voice.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !previewing,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("preview_voice_${voice.id}_${language.name}"),
                ) {
                    if (previewing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                        Text("试听中")
                    } else {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("试听")
                    }
                }
                Button(
                    onClick = onSelect,
                    enabled = !selected,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("select_voice_${voice.id}_${language.name}"),
                ) { Text(if (selected) "已选择" else "设为当前") }
            }
        }
    }
}

@Composable
private fun VoiceEmptyState(refreshing: Boolean, canRefresh: Boolean, onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("没有可用音色", style = MaterialTheme.typography.titleMedium)
            Text(
                if (canRefresh) "刷新音色目录后再试。" else "请先返回设置并配置 MiniMax API Key。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canRefresh) {
                TextButton(onClick = onRefresh, enabled = !refreshing) {
                    Text(if (refreshing) "正在刷新…" else "刷新音色")
                }
            }
        }
    }
}

private fun speechLanguageLabel(language: SpeechLanguage): String = when (language) {
    SpeechLanguage.ENGLISH_US -> "英语"
    SpeechLanguage.CANTONESE_HK -> "粤语"
    SpeechLanguage.MANDARIN_CN -> "普通话"
}
