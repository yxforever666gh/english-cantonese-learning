package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialOutline
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface

enum class AppDestination(val label: String, val icon: ImageVector) {
    SMART_MATERIALS("智能材料", Icons.AutoMirrored.Filled.LibraryBooks),
    ARTICLE_LIST("文章列表", Icons.AutoMirrored.Filled.Article),
    SETTINGS("设置", Icons.Default.Settings),
}

@Composable
fun AppNavigationBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(26.dp),
        color = EditorialSurface,
        border = BorderStroke(1.dp, EditorialOutline.copy(alpha = 0.82f)),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp)) {
            AppDestination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EditorialPine,
                        selectedTextColor = EditorialPine,
                        indicatorColor = EditorialMint,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledIconColor = Color.Gray,
                        disabledTextColor = Color.Gray,
                    ),
                )
            }
        }
    }
}
