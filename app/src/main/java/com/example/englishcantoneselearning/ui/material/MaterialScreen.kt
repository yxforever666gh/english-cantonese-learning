package com.example.englishcantoneselearning.ui.material

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.englishcantoneselearning.model.BilingualPhase
import com.example.englishcantoneselearning.model.Difficulty
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialLevelRules
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.PlaybackMode
import com.example.englishcantoneselearning.model.PlaybackStatus
import com.example.englishcantoneselearning.model.PracticeMaterial
import com.example.englishcantoneselearning.model.TtsAvailability
import com.example.englishcantoneselearning.model.GenerationStage
import com.example.englishcantoneselearning.model.ArticleOrigin
import com.example.englishcantoneselearning.ui.AppDestination
import com.example.englishcantoneselearning.ui.AppNavigationBar
import com.example.englishcantoneselearning.ui.EditorialCard
import com.example.englishcantoneselearning.ui.EditorialChoiceChip
import com.example.englishcantoneselearning.ui.EditorialEmptyState
import com.example.englishcantoneselearning.ui.EditorialPageHeader
import com.example.englishcantoneselearning.ui.EditorialPlayerSurface
import com.example.englishcantoneselearning.ui.EditorialPrimaryButton
import com.example.englishcantoneselearning.ui.EditorialProgress
import com.example.englishcantoneselearning.ui.EditorialSectionHeader
import com.example.englishcantoneselearning.ui.EditorialSegmentedControl
import com.example.englishcantoneselearning.ui.EditorialStatusPanel
import com.example.englishcantoneselearning.ui.EditorialStatusTone
import com.example.englishcantoneselearning.ui.MetadataPill
import com.example.englishcantoneselearning.ui.editorialContentWidth
import com.example.englishcantoneselearning.ui.theme.EditorialInk
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta
import java.text.DateFormat

enum class MaterialScreenMode { CREATION, LIBRARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialScreen(
    state: MaterialUiState,
    viewModel: MaterialViewModel,
    onNavigate: (AppDestination) -> Unit,
    onOpenSettings: () -> Unit,
    mode: MaterialScreenMode = MaterialScreenMode.CREATION,
    creationSwitcher: @Composable () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val selected = state.selectedMaterial.takeIf { mode == MaterialScreenMode.LIBRARY }
    val libraryEditing = mode == MaterialScreenMode.LIBRARY && selected == null &&
        state.librarySelectedArticleIds.isNotEmpty()
    BackHandler(enabled = selected != null, onBack = viewModel::closeMaterial)
    BackHandler(enabled = libraryEditing, onBack = viewModel::clearLibrarySelection)

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            if (selected != null || libraryEditing) {
                TopAppBar(
                    title = { Text(if (selected != null) "阅读详情" else "已选择 ${state.librarySelectedArticleIds.size} 篇") },
                    navigationIcon = {
                        if (selected != null) {
                            IconButton(onClick = viewModel::closeMaterial) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回材料列表")
                            }
                        } else {
                            IconButton(onClick = viewModel::clearLibrarySelection) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出编辑模式")
                            }
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (selected != null) {
                    MaterialPlayerPanel(
                        state = state,
                        onPlaybackModeChange = viewModel::setPlaybackMode,
                        onPrevious = viewModel::previousSentence,
                        onPlayPause = viewModel::playOrPause,
                        onNext = viewModel::nextSentence,
                    )
                }
                AppNavigationBar(
                    if (mode == MaterialScreenMode.CREATION) AppDestination.SMART_MATERIALS else AppDestination.ARTICLE_LIST,
                    onNavigate,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (selected == null) {
                if (mode == MaterialScreenMode.CREATION) {
                    MaterialCreation(
                        state = state,
                        modifier = Modifier.editorialContentWidth(),
                        onLanguage = viewModel::setLanguage,
                        onDifficulty = viewModel::setDifficulty,
                        onTopic = viewModel::setTopic,
                        onGenerate = viewModel::generateNewBatch,
                        onCancel = viewModel::cancelGeneration,
                        onResumeDraft = { viewModel.resumePendingDraft() },
                        onDiscardDraft = viewModel::discardPendingDraft,
                        onOpenSettings = onOpenSettings,
                        creationSwitcher = creationSwitcher,
                    )
                } else {
                    ArticleLibrary(
                        state = state,
                        modifier = Modifier.editorialContentWidth(),
                        onOpen = viewModel::openMaterial,
                        onLanguage = viewModel::setLibraryLanguage,
                        onToggleSelection = viewModel::toggleLibraryArticleSelection,
                        onCacheSelected = viewModel::cacheSelectedLibraryArticles,
                        onCancelCaching = viewModel::cancelAudioCaching,
                        onDeleteSelected = viewModel::deleteSelectedLibraryArticles,
                    )
                }
            } else {
                MaterialDetail(
                    state = state,
                    material = selected,
                    modifier = Modifier.editorialContentWidth(),
                    onPlaySentence = viewModel::selectAndPlaySentence,
                    onDelete = viewModel::deleteSelectedMaterial,
                    onDeleteBatch = viewModel::deleteSelectedBatch,
                    onRestart = viewModel::restartSelectedMaterial,
                )
            }
        }
    }
}

@Composable
private fun MaterialCreation(
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

@Composable
private fun ArticleLibrary(
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

@Composable
private fun MaterialDetail(
    state: MaterialUiState,
    material: PracticeMaterial,
    modifier: Modifier,
    onPlaySentence: (Int) -> Unit,
    onDelete: () -> Unit,
    onDeleteBatch: () -> Unit,
    onRestart: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.testTag("material_detail_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val progress = state.playbackProgress[material.id]
            val percent = progress?.percent(material.sentences.size) ?: 0
            EditorialPageHeader(
                eyebrow = "Long-form Listening",
                title = material.title,
                subtitle = if (material.origin == ArticleOrigin.AI_GENERATED) {
                    "点击任一句，将按“目标语 → 简体中文”播放。"
                } else {
                    "点击任一句播放粘贴文章原文。"
                },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetadataPill(languageLabel(material.language), accent = true)
                MetadataPill(difficultyLabel(material.difficulty))
                MetadataPill(material.topic)
                MetadataPill("${material.sentences.size} 句")
            }
            Spacer(Modifier.height(14.dp))
            EditorialCard(Modifier.fillMaxWidth(), containerColor = EditorialMint) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("听力进度", style = MaterialTheme.typography.titleSmall)
                        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = EditorialPine)
                    }
                    EditorialProgress(percent / 100f)
                    Text(
                        if (progress?.completed == true) "听力进度：已完成 · 100%" else
                            "听力进度：${progress?.completedSentenceIndices?.size ?: 0}/${material.sentences.size}句 · $percent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress?.completed == true) TextButton(onClick = onRestart) { Text("重新播放") }
                }
            }
        }
        items(material.sentences.size) { index ->
            val sentence = material.sentences[index]
            val selected = index == state.selectedSentenceIndex
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                material.sections.firstOrNull { it.startSentenceIndex == index }?.let { section ->
                    Text(
                        section.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = EditorialInk,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                EditorialCard(
                    modifier = Modifier.fillMaxWidth().clickable { onPlaySentence(index) },
                    containerColor = if (selected) EditorialMint else EditorialSurface,
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetadataPill("${index + 1}", accent = selected)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                sentence.targetText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = EditorialInk,
                            )
                            sentence.jyutping?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = EditorialTerracotta, style = MaterialTheme.typography.bodyMedium)
                            }
                            sentence.simplifiedChinese?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected && state.playbackStatus in setOf(PlaybackStatus.PLAYING, PlaybackStatus.PREPARING)) {
                                Text(
                                    if (state.playbackStatus == PlaybackStatus.PREPARING) {
                                        "正在生成 MiniMax 语音…"
                                    } else if (state.bilingualPhase == BilingualPhase.TARGET) {
                                        "正在朗读目标语"
                                    } else {
                                        "正在朗读简体中文"
                                    },
                                    color = EditorialPine,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            EditorialCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (material.sources.isNotEmpty()) {
                        EditorialSectionHeader(title = "资料来源", subtitle = "查看生成或整理本文时使用的原始资料。")
                    }
                    material.sources.forEach { source ->
                        TextButton(onClick = { uriHandler.openUri(source.url) }) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(source.title)
                                Text(
                                    listOfNotNull(source.publisher, source.publishedAt, source.sourceLanguage)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开来源")
                        }
                    }
                    if (material.origin == ArticleOrigin.AI_GENERATED) {
                        Text(
                            "模型 ${material.providerName} / ${material.model} · 输入 ${material.inputTokens} / 输出 ${material.outputTokens} tokens · ${material.promptVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("删除本篇")
                        }
                        if (material.batchId != material.id) TextButton(onClick = onDeleteBatch) { Text("删除旧批次") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialPlayerPanel(
    state: MaterialUiState,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val material = state.selectedMaterial ?: return
    val ready = state.targetAvailability == TtsAvailability.READY &&
        (material.origin == ArticleOrigin.MANUAL_PASTE || state.mandarinAvailability == TtsAvailability.READY)
    val hasSelection = state.selectedSentenceIndex in material.sentences.indices
    val progress = state.playbackProgress[material.id]
    val percent = progress?.percent(material.sentences.size) ?: 0
    EditorialPlayerSurface(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (hasSelection) "第 ${state.selectedSentenceIndex + 1} / ${material.sentences.size} 句" else "等待播放",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "已完成 ${progress?.completedSentenceIndices?.size ?: 0}/${material.sentences.size}句 · $percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MetadataPill(if (state.playbackMode == PlaybackMode.SINGLE) "单组" else "连续", accent = true)
        }
        EditorialProgress(percent / 100f, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (ready && material.origin == ArticleOrigin.MANUAL_PASTE) "目标语语音已就绪"
            else if (ready) "目标语与普通话语音已就绪" else voiceStatus(state),
            style = MaterialTheme.typography.bodySmall,
            color = if (ready) EditorialPine else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (!ready) {
            Text(
                "请到设置填写MiniMax API Key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.playbackStatus == PlaybackStatus.PREPARING) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("material_speech_preparing"),
                color = EditorialTerracotta,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = hasSelection && state.selectedSentenceIndex > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一句")
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = ready && material.sentences.isNotEmpty() && state.playbackStatus != PlaybackStatus.PREPARING,
                modifier = Modifier.size(58.dp).testTag("material_play_pause"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = EditorialPine,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    if (state.playbackStatus == PlaybackStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playbackStatus == PlaybackStatus.PLAYING) "暂停" else "播放",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onNext,
                enabled = hasSelection && state.selectedSentenceIndex < material.sentences.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一句")
            }
        }
        EditorialSegmentedControl(
            options = listOf(PlaybackMode.SINGLE to "单组", PlaybackMode.CONTINUOUS to "连续"),
            selected = state.playbackMode,
            onSelect = onPlaybackModeChange,
            modifier = Modifier.padding(top = 8.dp),
            optionModifier = { mode -> Modifier.testTag("material_mode_${mode.name.lowercase()}") },
        )
    }
}

private fun voiceStatus(state: MaterialUiState): String = when {
    state.targetAvailability != TtsAvailability.READY && state.mandarinAvailability != TtsAvailability.READY -> "缺少目标语和普通话语音"
    state.targetAvailability != TtsAvailability.READY -> "缺少目标语语音"
    else -> "缺少普通话语音"
}

private fun languageLabel(language: MaterialLanguage): String = when (language) {
    MaterialLanguage.ENGLISH -> "English"
    MaterialLanguage.CANTONESE -> "粤语"
}

private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> "轻松"
    Difficulty.TARGET -> "适合"
    Difficulty.CHALLENGE -> "挑战"
}

private fun formatBand(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)

private fun generationStageLabel(stage: GenerationStage): String = when (stage) {
    GenerationStage.DISCOVERING_SOURCE -> "查找固定来源"
    GenerationStage.FETCHING_SOURCE -> "下载文章"
    GenerationStage.CLEANING_SOURCE -> "清洗正文"
    GenerationStage.CONNECTING -> "连接模型"
    GenerationStage.SEARCHING -> "搜索来源"
    GenerationStage.REASONING -> "分析结构"
    GenerationStage.WRITING -> "生成正文"
    GenerationStage.VALIDATING -> "验证内容"
    GenerationStage.SAVING -> "保存章节"
    GenerationStage.FAILOVER -> "切换模型"
    GenerationStage.COMPLETED -> "完成"
}
