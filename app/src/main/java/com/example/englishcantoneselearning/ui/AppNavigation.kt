package com.example.englishcantoneselearning.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.theme.AppDimensions

enum class AppDestination(val label: String, @param:DrawableRes val iconRes: Int) {
    SMART_MATERIALS("创建", R.drawable.ic_library_books),
    ARTICLE_LIST("材料库", R.drawable.ic_article),
    SETTINGS("设置", R.drawable.ic_settings),
}

@Composable
fun AppNavigationBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.navigationBarHeight),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
        ) {
            AppDestination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = { Icon(painterResource(destination.iconRes), contentDescription = destination.label) },
                    label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                )
            }
        }
    }
}
