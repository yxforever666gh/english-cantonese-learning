package com.example.englishcantoneselearning.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.MiniMaxVoiceSelectionPolicy
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.ui.AppDestination
import com.example.englishcantoneselearning.ui.AppNavigationBar
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialEmptyState
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialPrimaryButton
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.editorialContentWidth
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import com.example.englishcantoneselearning.ui.material.ConnectionState
import com.example.englishcantoneselearning.ui.material.MaterialUiState
import com.example.englishcantoneselearning.ui.material.MaterialViewModel
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MaterialUiState,
    viewModel: MaterialViewModel,
    onNavigate: (AppDestination) -> Unit,
) {
    var miniMaxUrl by remember { mutableStateOf(state.miniMaxConfig.baseUrl) }
    var miniMaxKey by remember { mutableStateOf("") }
    var editingProvider by remember { mutableStateOf<MaterialProviderConfig?>(null) }
    var choosingVoiceLanguage by remember { mutableStateOf<SpeechLanguage?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.miniMaxConfig.baseUrl) { miniMaxUrl = state.miniMaxConfig.baseUrl }
    LaunchedEffect(Unit) { viewModel.refreshVoiceCatalogIfStale() }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    choosingVoiceLanguage?.let { language ->
        val close = {
            viewModel.stopVoicePreview()
            choosingVoiceLanguage = null
        }
        VoiceSelectionScreen(
            state = state,
            language = language,
            snackbar = snackbar,
            onBack = close,
            onRefresh = viewModel::refreshVoiceCatalog,
            onPreview = viewModel::previewVoice,
            onSelect = { viewModel.selectVoice(language, it) },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { AppNavigationBar(AppDestination.SETTINGS, onNavigate) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.editorialContentWidth().testTag("settings_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item {
                EditorialPageHeader(
                    eyebrow = "Preferences & Services",
                    title = "设置",
                    subtitle = "管理语音、材料模型和你的个人学习节奏。",
                )
                Spacer(Modifier.height(22.dp))
                SectionTitle("MiniMax语音")
                EditorialCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = miniMaxUrl,
                            onValueChange = { miniMaxUrl = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("minimax_url_input"),
                        )
                        Text("模型：speech-2.8-turbo", style = MaterialTheme.typography.bodySmall)
                        Text("三种语言可在下方分别选择音色。", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = miniMaxKey,
                            onValueChange = { miniMaxKey = it },
                            label = { Text(if (state.miniMaxConfig.apiKey.isBlank()) "MiniMax API Key" else "输入新Key以替换") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("minimax_key_input"),
                        )
                        CredentialStatus(state.miniMaxConfig.apiKey.isNotBlank(), "MiniMax Key")
                        EditorialPrimaryButton(
                            text = "保存MiniMax配置",
                            onClick = { if (viewModel.saveMiniMax(miniMaxUrl, miniMaxKey)) miniMaxKey = "" },
                            enabled = miniMaxKey.isNotBlank() || state.miniMaxConfig.apiKey.isNotBlank(),
                            modifier = Modifier.testTag("save_minimax_button"),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.refreshVoiceCatalog() },
                                enabled = state.miniMaxConfig.apiKey.isNotBlank() &&
                                    state.voiceCatalogState != ConnectionState.CHECKING,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (state.voiceCatalogState == ConnectionState.CHECKING) {
                                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                                } else {
                                    Text("刷新音色列表")
                                }
                            }
                            TextButton(onClick = viewModel::resetMiniMaxUrl) { Text("恢复地址") }
                            TextButton(onClick = viewModel::clearMiniMaxKey, enabled = state.miniMaxConfig.apiKey.isNotBlank()) {
                                Text("清除Key")
                            }
                        }
                        Text("刷新列表不产生语音费用；首次试听某个音色会产生少量合成费用。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                SectionTitle("默认音色")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceSelectionCard(
                        label = "英语",
                        language = SpeechLanguage.ENGLISH_US,
                        voiceId = state.miniMaxConfig.englishVoice,
                        voice = state.voiceCatalog.firstOrNull { it.id == state.miniMaxConfig.englishVoice },
                        previewingVoiceId = state.previewingVoiceId,
                        onChoose = { choosingVoiceLanguage = SpeechLanguage.ENGLISH_US },
                        onPreview = viewModel::previewVoice,
                    )
                    VoiceSelectionCard(
                        label = "粤语",
                        language = SpeechLanguage.CANTONESE_HK,
                        voiceId = state.miniMaxConfig.cantoneseVoice,
                        voice = state.voiceCatalog.firstOrNull { it.id == state.miniMaxConfig.cantoneseVoice },
                        previewingVoiceId = state.previewingVoiceId,
                        onChoose = { choosingVoiceLanguage = SpeechLanguage.CANTONESE_HK },
                        onPreview = viewModel::previewVoice,
                    )
                    VoiceSelectionCard(
                        label = "普通话",
                        language = SpeechLanguage.MANDARIN_CN,
                        voiceId = state.miniMaxConfig.mandarinVoice,
                        voice = state.voiceCatalog.firstOrNull { it.id == state.miniMaxConfig.mandarinVoice },
                        previewingVoiceId = state.previewingVoiceId,
                        onChoose = { choosingVoiceLanguage = SpeechLanguage.MANDARIN_CN },
                        onPreview = viewModel::previewVoice,
                    )
                }
            }

            item {
                SectionTitle("材料大模型（按顺序故障转移）")
                EditorialStatusPanel(
                    title = "按顺序自动故障转移",
                    body = "长按右侧拖动图标调整顺序；仅支持OpenAI Responses兼容接口。" +
                        "文章由应用从固定RSS来源抓取并清洗，大模型只做分级改写和翻译。" +
                        "连续2分钟没有流式活动或HTTP 521时会切换下一服务地址。相同Base URL不是服务器容灾。",
                    tone = EditorialStatusTone.INFO,
                )
            }

            state.materialProviders.forEachIndexed { index, provider ->
                item(key = provider.id) {
                    ProviderCard(
                        provider = provider,
                        index = index,
                        count = state.materialProviders.size,
                        connectionState = state.providerConnectionStates[provider.id] ?: ConnectionState.IDLE,
                        onEdit = { editingProvider = provider },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                        onEnabled = { viewModel.setProviderEnabled(provider.id, it) },
                        onTest = { viewModel.testProvider(provider) },
                        onMove = viewModel::moveProvider,
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        editingProvider = MaterialProviderConfig(
                            id = UUID.randomUUID().toString(), name = "", baseUrl = "", model = "", apiKey = "",
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_provider_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加材料模型")
                }
            }

            item {
                SectionTitle("语音缓存")
                EditorialCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetadataPill("当前占用：${formatBytes(state.audioCacheBytes)} / 500 MB", accent = true)
                        Text("超过500MB时自动删除最久未使用的音频。", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = viewModel::clearAudioCache, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("清除语音缓存")
                        }
                    }
                }
            }

            item {
                SectionTitle("学习档案")
                EditorialCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        MetadataPill("IELTS 听力 ${String.format(Locale.US, "%.1f", state.englishListeningBand)}", accent = true)
                        Slider(
                            value = state.englishListeningBand,
                            onValueChange = viewModel::setEnglishListeningBand,
                            valueRange = 1f..9f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth().testTag("ielts_listening_slider"),
                        )
                        Text("范围 1.0–9.0，步长 0.5；适合档使用所选分数。", style = MaterialTheme.typography.bodySmall)
                        Text("粤语：A0/A1 零基础")
                    }
                }
            }

            item {
                SectionTitle("语速")
                EditorialCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpeedSetting("英语", state.englishSpeed) { viewModel.setSpeechSpeed(SpeechLanguage.ENGLISH_US, it) }
                        SpeedSetting("粤语", state.cantoneseSpeed) { viewModel.setSpeechSpeed(SpeechLanguage.CANTONESE_HK, it) }
                        SpeedSetting("简体普通话", state.mandarinSpeed) { viewModel.setSpeechSpeed(SpeechLanguage.MANDARIN_CN, it) }
                    }
                }
            }

            item {
                Text(
                    "所有Key以明文保存在本机应用私有目录，不参与备份，也不会写入日志。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }

    editingProvider?.let { provider ->
        ProviderEditor(
            original = provider,
            onDismiss = { editingProvider = null },
            onSave = { edited -> if (viewModel.saveProvider(edited)) editingProvider = null },
        )
    }

}

@Composable
private fun VoiceSelectionCard(
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
private fun VoiceSelectionScreen(
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = state.miniMaxConfig.apiKey.isNotBlank() &&
                            state.voiceCatalogState != ConnectionState.CHECKING,
                        modifier = Modifier.testTag("refresh_voice_catalog"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新音色列表")
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
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
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

@Composable
private fun ProviderCard(
    provider: MaterialProviderConfig,
    index: Int,
    count: Int,
    connectionState: ConnectionState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var drag by remember(provider.id, index) { mutableFloatStateOf(0f) }
    EditorialCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${provider.name}", style = MaterialTheme.typography.titleMedium)
                    Text(provider.model, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = provider.enabled, onCheckedChange = onEnabled)
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "拖动排序",
                    modifier = Modifier.pointerInput(provider.id, index) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { drag = 0f },
                            onDragCancel = { drag = 0f },
                        ) { change, amount ->
                            change.consume()
                            drag += amount.y
                            if (abs(drag) >= 48f) {
                                val target = if (drag > 0) index + 1 else index - 1
                                if (target in 0 until count) onMove(index, target)
                                drag = 0f
                            }
                        }
                    },
                )
            }
            Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CredentialStatus(provider.apiKey.isNotBlank(), "API Key")
                MetadataPill(
                    text = when (connectionState) {
                        ConnectionState.IDLE -> if (provider.enabled) "等待测试" else "已停用"
                        ConnectionState.CHECKING -> "测试中…"
                        ConnectionState.READY -> "连接正常"
                        ConnectionState.ERROR -> "连接失败"
                    },
                    accent = connectionState == ConnectionState.READY,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onTest, enabled = connectionState != ConnectionState.CHECKING) {
                    Text(if (connectionState == ConnectionState.CHECKING) "测试中…" else "测试")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = EditorialTerracotta)
                }
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    original: MaterialProviderConfig,
    onDismiss: () -> Unit,
    onSave: (MaterialProviderConfig) -> Unit,
) {
    var name by remember(original.id) { mutableStateOf(original.name) }
    var url by remember(original.id) { mutableStateOf(original.baseUrl) }
    var model by remember(original.id) { mutableStateOf(original.model) }
    var key by remember(original.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (original.name.isBlank()) "添加材料模型" else "编辑${original.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Base URL") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("模型 ID") }, singleLine = true)
                OutlinedTextField(
                    key, { key = it },
                    label = { Text(if (original.apiKey.isBlank()) "API Key" else "输入新Key以替换") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(original.copy(name = name, baseUrl = url, model = model, apiKey = key.trim().ifBlank { original.apiKey }))
                },
                enabled = name.isNotBlank() && url.isNotBlank() && model.isNotBlank() &&
                    (key.isNotBlank() || original.apiKey.isNotBlank()),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CredentialStatus(configured: Boolean, label: String) {
    MetadataPill(
        text = if (configured) "$label 已保存：••••••••" else "$label 尚未保存",
        accent = configured,
    )
}

@Composable
private fun SectionTitle(text: String) {
    EditorialSectionHeader(text, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SpeedSetting(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text("$label ${String.format(Locale.US, "%.1fx", value)}")
        Slider(value, onChange, valueRange = 0.5f..2.0f, steps = 14, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
