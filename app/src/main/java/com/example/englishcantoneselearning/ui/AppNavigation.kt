package com.example.englishcantoneselearning.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.theme.EditorialMint
import com.example.englishcantoneselearning.ui.theme.EditorialOutline
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface

enum class AppDestination(val label: String, @param:DrawableRes val iconRes: Int) {
    SMART_MATERIALS("智能材料", R.drawable.ic_library_books),
    ARTICLE_LIST("文章列表", R.drawable.ic_article),
    SETTINGS("设置", R.drawable.ic_settings),
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
                    icon = { Icon(painterResource(destination.iconRes), contentDescription = destination.label) },
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
