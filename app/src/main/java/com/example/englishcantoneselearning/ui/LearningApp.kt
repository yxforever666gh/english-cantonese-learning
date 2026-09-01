package com.example.englishcantoneselearning.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.englishcantoneselearning.ui.material.MaterialScreen
import com.example.englishcantoneselearning.ui.material.MaterialScreenMode
import com.example.englishcantoneselearning.ui.material.MaterialViewModel
import com.example.englishcantoneselearning.ui.settings.SettingsScreen
import com.example.englishcantoneselearning.ui.theme.AppMotion

@Composable
fun LearningApp(
    readerViewModel: ReaderViewModel,
    materialViewModel: MaterialViewModel,
) {
    val readerState by readerViewModel.uiState.collectAsState()
    val materialState by materialViewModel.uiState.collectAsState()
    var destination by rememberSaveable { mutableStateOf(AppDestination.SMART_MATERIALS) }
    var createTab by rememberSaveable { mutableStateOf(SmartCreateTab.AI) }

    fun navigate(target: AppDestination) {
        if (target == destination) return
        when (destination) {
            AppDestination.SMART_MATERIALS -> if (createTab == SmartCreateTab.AI) {
                materialViewModel.onAppBackgrounded()
            } else {
                readerViewModel.onAppBackgrounded()
            }
            AppDestination.ARTICLE_LIST -> materialViewModel.onAppBackgrounded()
            AppDestination.SETTINGS -> Unit
        }
        if (target == AppDestination.ARTICLE_LIST) materialViewModel.reloadMaterials()
        destination = target
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
            (fadeIn(tween(AppMotion.standard)) + slideInHorizontally(tween(AppMotion.standard)) { width -> direction * width / 18 }) togetherWith
                (fadeOut(tween(AppMotion.fast)) + slideOutHorizontally(tween(AppMotion.fast)) { width -> -direction * width / 24 })
        },
        label = "destination_transition",
    ) { currentDestination ->
        when (currentDestination) {
            AppDestination.SMART_MATERIALS -> if (createTab == SmartCreateTab.AI) {
                MaterialScreen(
                    state = materialState,
                    viewModel = materialViewModel,
                    onNavigate = ::navigate,
                    onOpenSettings = { navigate(AppDestination.SETTINGS) },
                    mode = MaterialScreenMode.CREATION,
                    creationSwitcher = { CreationSwitcher(createTab) { createTab = it } },
                )
            } else {
                ReaderScreen(
                    state = readerState,
                    onArticleTextChange = readerViewModel::onArticleTextChange,
                    onLanguageChange = readerViewModel::onLanguageChange,
                    onSegmentArticle = readerViewModel::segmentArticle,
                    onPlaybackModeChange = readerViewModel::onPlaybackModeChange,
                    onSpeedChange = readerViewModel::onSpeedChange,
                    onSpeedChangeFinished = readerViewModel::onSpeedChangeFinished,
                    onPlayOrPause = readerViewModel::playOrPause,
                    onPreviousSentence = readerViewModel::previousSentence,
                    onNextSentence = readerViewModel::nextSentence,
                    onSelectSentence = readerViewModel::selectAndPlay,
                    onUpdateSentence = readerViewModel::updateSentence,
                    onSplitSentence = readerViewModel::splitSentence,
                    onMergeSentence = readerViewModel::mergeWithNext,
                    onMessageShown = readerViewModel::clearMessage,
                    onTitleChange = readerViewModel::onArticleTitleChange,
                    onSaveArticle = readerViewModel::saveToArticleList,
                    creationSwitcher = { CreationSwitcher(createTab) { createTab = it } },
                    bottomNavigation = { AppNavigationBar(AppDestination.SMART_MATERIALS, ::navigate) },
                )
            }
            AppDestination.ARTICLE_LIST -> MaterialScreen(
                state = materialState,
                viewModel = materialViewModel,
                onNavigate = ::navigate,
                onOpenSettings = { navigate(AppDestination.SETTINGS) },
                mode = MaterialScreenMode.LIBRARY,
            )
            AppDestination.SETTINGS -> SettingsScreen(
                state = materialState,
                viewModel = materialViewModel,
                onNavigate = ::navigate,
            )
        }
    }
}

private enum class SmartCreateTab { AI, PASTE }

@Composable
private fun CreationSwitcher(selected: SmartCreateTab, onSelect: (SmartCreateTab) -> Unit) {
    EditorialSegmentedControl(
        options = listOf(SmartCreateTab.AI to "AI生成", SmartCreateTab.PASTE to "粘贴文章"),
        selected = selected,
        onSelect = onSelect,
    )
}
