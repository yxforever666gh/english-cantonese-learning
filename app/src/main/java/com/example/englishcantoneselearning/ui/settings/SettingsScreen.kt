package com.example.englishcantoneselearning.ui.settings

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.ui.AppDestination
import com.example.englishcantoneselearning.ui.AppNavigationBar
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialPrimaryButton
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.editorialContentWidth
import com.example.englishcantoneselearning.ui.material.ConnectionState
import com.example.englishcantoneselearning.ui.material.MaterialUiState
import com.example.englishcantoneselearning.ui.material.MaterialViewModel
import java.util.Locale
import java.util.UUID

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
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
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
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
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
internal fun CredentialStatus(configured: Boolean, label: String) {
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
