package com.example.englishcantoneselearning.ui.material

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.AppDestination
import com.example.englishcantoneselearning.ui.AppNavigationBar
import com.example.englishcantoneselearning.ui.editorialContentWidth

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
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回材料列表")
                            }
                        } else {
                            IconButton(onClick = viewModel::clearLibrarySelection) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "退出编辑模式")
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
