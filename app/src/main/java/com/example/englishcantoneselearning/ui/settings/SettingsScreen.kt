package com.example.englishcantoneselearning.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.BuildConfig
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.model.MaterialProviderConfig
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.ui.AppDestination
import com.example.englishcantoneselearning.ui.AppNavigationBar
import com.example.englishcantoneselearning.ui.material.ConnectionState
import com.example.englishcantoneselearning.ui.material.MaterialUiState
import com.example.englishcantoneselearning.ui.material.MaterialViewModel
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme
import java.util.Locale
import java.util.UUID

private val SettingsSectionShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MaterialUiState,
    viewModel: MaterialViewModel,
    onNavigate: (AppDestination) -> Unit,
) {
    var showMiniMaxSheet by remember { mutableStateOf(false) }
    var miniMaxUrl by remember { mutableStateOf(state.miniMaxConfig.baseUrl) }
    var miniMaxKey by remember { mutableStateOf("") }
    var showMiniMaxKey by remember { mutableStateOf(false) }
    val miniMaxSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).testTag("settings_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { SettingsPageHeader() }

                item {
                    SettingsSection(title = "语音服务") {
                        MiniMaxServiceRow(
                            configured = state.miniMaxConfig.apiKey.isNotBlank(),
                            connectionState = state.voiceCatalogState,
                            onConfigure = {
                                miniMaxUrl = state.miniMaxConfig.baseUrl
                                miniMaxKey = ""
                                showMiniMaxKey = false
                                showMiniMaxSheet = true
                            },
                            onRefresh = viewModel::refreshVoiceCatalog,
                        )
                    }
                }

                item {
                    SettingsSection(title = "默认音色") {
                        VoiceSelectionRow(
                            label = "英语",
                            language = SpeechLanguage.ENGLISH_US,
                            voiceId = state.miniMaxConfig.englishVoice,
                            voice = state.voiceCatalog.firstOrNull { it.id == state.miniMaxConfig.englishVoice },
                            previewingVoiceId = state.previewingVoiceId,
                            onChoose = { choosingVoiceLanguage = SpeechLanguage.ENGLISH_US },
                            onPreview = viewModel::previewVoice,
                        )
                        SettingsDivider()
                        VoiceSelectionRow(
                            label = "粤语",
                            language = SpeechLanguage.CANTONESE_HK,
                            voiceId = state.miniMaxConfig.cantoneseVoice,
                            voice = state.voiceCatalog.firstOrNull { it.id == state.miniMaxConfig.cantoneseVoice },
                            previewingVoiceId = state.previewingVoiceId,
                            onChoose = { choosingVoiceLanguage = SpeechLanguage.CANTONESE_HK },
                            onPreview = viewModel::previewVoice,
                        )
                        SettingsDivider()
                        VoiceSelectionRow(
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
                    SettingsSection(
                        title = "材料模型",
                        supportingText = "按列表顺序自动尝试可用服务。长按排序图标可调整优先级。",
                    ) {
                        if (state.materialProviders.isEmpty()) {
                            Text(
                                "尚未添加服务，请先配置一个 Responses 兼容模型。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            state.materialProviders.forEachIndexed { index, provider ->
                                ProviderCard(
                                    provider = provider,
                                    index = index,
                                    count = state.materialProviders.size,
                                    connectionState = state.providerConnectionStates[provider.id]
                                        ?: ConnectionState.IDLE,
                                    onEdit = { editingProvider = provider },
                                    onDelete = { viewModel.deleteProvider(provider.id) },
                                    onEnabled = { viewModel.setProviderEnabled(provider.id, it) },
                                    onTest = { viewModel.testProvider(provider) },
                                    onMove = viewModel::moveProvider,
                                )
                                if (index != state.materialProviders.lastIndex) SettingsDivider()
                            }
                        }
                        SettingsDivider()
                        TextButton(
                            onClick = {
                                editingProvider = MaterialProviderConfig(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    baseUrl = "",
                                    model = "",
                                    apiKey = "",
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_provider_button"),
                        ) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("添加材料模型")
                        }
                    }
                }

                item {
                    SettingsSection(title = "缓存") {
                        SettingsActionRow(
                            title = "语音缓存",
                            supportingText = "${formatBytes(state.audioCacheBytes)} / 500 MB · 超出后自动清理最久未使用内容",
                            actionLabel = "清除",
                            actionContentDescription = "清除语音缓存",
                            onAction = viewModel::clearAudioCache,
                        )
                    }
                }

                item {
                    SettingsSection(title = "关于") {
                        AboutBrandRow()
                        SettingsDivider()
                        Text(
                            "密钥保存在本机应用私有目录，不参与系统备份，也不会写入日志。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showMiniMaxSheet) {
        ModalBottomSheet(
            sheetState = miniMaxSheetState,
            onDismissRequest = {
                showMiniMaxSheet = false
                miniMaxKey = ""
                showMiniMaxKey = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("配置 MiniMax 语音", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "用于在线音色列表与语音合成。已保存的密钥永远不会在这里回显。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = miniMaxUrl,
                    onValueChange = { miniMaxUrl = it },
                    label = { Text("Base URL") },
                    supportingText = { Text("模型固定为 speech-2.8-turbo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("minimax_url_input"),
                )
                OutlinedTextField(
                    value = miniMaxKey,
                    onValueChange = { miniMaxKey = it },
                    label = {
                        Text(if (state.miniMaxConfig.apiKey.isBlank()) "MiniMax API Key" else "输入新 Key 以替换")
                    },
                    visualTransformation = if (showMiniMaxKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showMiniMaxKey = !showMiniMaxKey }) {
                            Text(if (showMiniMaxKey) "隐藏" else "显示")
                        }
                    },
                    supportingText = {
                        Text(if (state.miniMaxConfig.apiKey.isBlank()) "尚未保存密钥" else "已保存：••••••••")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("minimax_key_input"),
                )
                Button(
                    onClick = {
                        if (viewModel.saveMiniMax(miniMaxUrl, miniMaxKey)) {
                            miniMaxKey = ""
                            showMiniMaxKey = false
                            showMiniMaxSheet = false
                        }
                    },
                    enabled = miniMaxKey.isNotBlank() || state.miniMaxConfig.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save_minimax_button"),
                ) { Text("保存配置") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        viewModel.resetMiniMaxUrl()
                        miniMaxUrl = state.miniMaxConfig.baseUrl
                    }) { Text("恢复默认地址") }
                    TextButton(
                        onClick = {
                            viewModel.clearMiniMaxKey()
                            miniMaxKey = ""
                        },
                        enabled = state.miniMaxConfig.apiKey.isNotBlank(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("清除密钥") }
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
private fun SettingsPageHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "管理语音服务和材料模型",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsSectionShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) { Column(content = content) }
    }
}

@Composable
private fun MiniMaxServiceRow(
    configured: Boolean,
    connectionState: ConnectionState,
    onConfigure: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (configured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("MiniMax 语音", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        !configured -> "未配置 · 需要 API Key"
                        connectionState == ConnectionState.CHECKING -> "正在刷新音色…"
                        connectionState == ConnectionState.ERROR -> "音色列表刷新失败"
                        else -> "已配置 · 密钥 ••••••••"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connectionState == ConnectionState.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (configured) {
                IconButton(
                    onClick = onRefresh,
                    enabled = connectionState != ConnectionState.CHECKING,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (connectionState == ConnectionState.CHECKING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新音色", modifier = Modifier.size(20.dp))
                    }
                }
            }
            TextButton(onClick = onConfigure, modifier = Modifier.testTag("minimax_configure_button")) { Text("配置") }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    supportingText: String,
    actionLabel: String,
    actionContentDescription: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = actionContentDescription, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun AboutBrandRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("英粤断句朗读", style = MaterialTheme.typography.titleSmall)
            Text("高级学习助手", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun CredentialStatus(configured: Boolean, label: String) {
    Text(
        text = if (configured) "$label 已保存：••••••••" else "$label 尚未保存",
        style = MaterialTheme.typography.bodySmall,
        color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(name = "设置概览 · 412×915", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun SettingsOverviewPreview() {
    EnglishCantoneseLearningTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsPageHeader()
                SettingsSection("语音服务") {
                    MiniMaxServiceRow(
                        configured = true,
                        connectionState = ConnectionState.READY,
                        onConfigure = {},
                        onRefresh = {},
                    )
                }
                SettingsSection("关于") { AboutBrandRow() }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
