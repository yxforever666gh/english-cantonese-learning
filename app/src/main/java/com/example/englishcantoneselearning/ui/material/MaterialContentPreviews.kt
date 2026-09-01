package com.example.englishcantoneselearning.ui.material

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.englishcantoneselearning.ui.editorialContentWidth
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme

@Preview(name = "生成未配置 · 360×800", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun MaterialCreationPreview() {
    EnglishCantoneseLearningTheme {
        MaterialCreation(
            state = MaterialUiState(isLoading = false),
            modifier = Modifier.editorialContentWidth(),
            onLanguage = {},
            onListeningBand = {},
            onTopic = {},
            onGenerate = {},
            onCancel = {},
            onResumeDraft = {},
            onDiscardDraft = {},
            onOpenSettings = {},
            creationSwitcher = {},
        )
    }
}

@Preview(name = "空材料库 · 412×915", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(name = "空材料库 · 600×960", widthDp = 600, heightDp = 960, showBackground = true)
@Composable
private fun EmptyMaterialLibraryPreview() {
    EnglishCantoneseLearningTheme {
        ArticleLibrary(
            state = MaterialUiState(isLoading = false),
            modifier = Modifier.editorialContentWidth(),
            onOpen = {},
            onLanguage = {},
            onToggleSelection = {},
            onCacheSelected = {},
            onCancelCaching = {},
            onDeleteSelected = {},
            onCreate = {},
        )
    }
}
