package com.example.englishcantoneselearning.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.theme.AppDimensions
import com.example.englishcantoneselearning.ui.theme.AppMotion
import com.example.englishcantoneselearning.ui.theme.AppRadii
import com.example.englishcantoneselearning.ui.theme.AppSpacing
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialTerracotta

const val EditorialMaxContentWidth = 720

enum class EditorialStatusTone { INFO, SUCCESS, WARNING, ERROR }

/** Compact, neutral page heading used by every top-level destination. */
@Composable
internal fun AppPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** A section heading with optional content, keeping hierarchy consistent across screens. */
@Composable
internal fun AppSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
        content?.invoke(this)
    }
}

/** The single elevated grouping surface. Avoid nesting this component inside itself. */
@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadii.card),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Consistent, fully clickable settings/library row with stable 48dp touch targets. */
@Composable
internal fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionModifier = if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .then(interactionModifier)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun AppStatusBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: EditorialStatusTone = EditorialStatusTone.INFO,
    action: (@Composable () -> Unit)? = null,
) {
    val (background, accent) = when (tone) {
        EditorialStatusTone.INFO -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        EditorialStatusTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        EditorialStatusTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer to EditorialTerracotta
        EditorialStatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadii.control),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = accent, modifier = Modifier.size(AppSpacing.xs)) {}
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action?.invoke()
        }
    }
}

@Composable
internal fun <T> AppSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadii.control),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(AppSpacing.xxs), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            options.forEach { (value, label) ->
                AppSegment(
                    selected = selected == value,
                    label = label,
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f).then(optionModifier(value)),
                )
            }
        }
    }
}

@Composable
private fun RowScope.AppSegment(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(AppMotion.standard),
        label = "segment_background",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(AppMotion.standard),
        label = "segment_foreground",
    )
    Surface(
        modifier = modifier
            .heightIn(min = AppDimensions.minimumTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        shape = RoundedCornerShape(AppRadii.label),
        color = background,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
        }
    }
}

@Composable
internal fun AppMetadataChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppRadii.label),
        color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AppEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_auto_stories,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadii.card),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppDimensions.icon),
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let {
                Spacer(Modifier.height(AppSpacing.xxs))
                it()
            }
        }
    }
}

/** Compact playback surface. The caller owns transport controls and expanded-sheet state. */
@Composable
internal fun AppMiniPlayer(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    controls: @Composable RowScope.() -> Unit,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onClick)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppDimensions.miniPlayerHeight)
            .clip(RoundedCornerShape(topStart = AppRadii.card, topEnd = AppRadii.card))
            .then(clickModifier),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column {
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(AppDimensions.activeIndicator),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    drawStopIndicator = {},
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                controls()
            }
        }
    }
}

@Composable
internal fun AppProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(AppSpacing.xxs),
        color = accent,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        drawStopIndicator = {},
    )
}

@Composable
internal fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = AppDimensions.primaryButtonHeight),
        shape = RoundedCornerShape(AppRadii.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
    ) {
        icon?.let {
            Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(AppDimensions.icon))
            Spacer(Modifier.size(AppSpacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun AppPlayerSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = AppRadii.card, topEnd = AppRadii.card),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm), content = content)
    }
}

// Compatibility layer: existing screens keep their signatures while inheriting the new system.
@Composable
fun EditorialPageHeader(
    @Suppress("UNUSED_PARAMETER") eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) = AppPageHeader(title = title, subtitle = subtitle, modifier = modifier, trailing = trailing)

@Composable
fun EditorialSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) = AppSection(title = title, subtitle = subtitle, modifier = modifier, trailing = trailing)

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) = AppCard(modifier = modifier, containerColor = containerColor, contentPadding = PaddingValues(0.dp), content = content)

@Composable
fun <T> EditorialSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
) = AppSegmentedControl(options, selected, onSelect, modifier, optionModifier)

@Composable
fun EditorialChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.heightIn(min = AppDimensions.minimumTouchTarget),
        shape = RoundedCornerShape(AppRadii.label),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun MetadataPill(text: String, modifier: Modifier = Modifier, accent: Boolean = false) =
    AppMetadataChip(text = text, modifier = modifier, accent = accent)

@Composable
fun EditorialStatusPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: EditorialStatusTone = EditorialStatusTone.INFO,
    action: (@Composable () -> Unit)? = null,
) = AppStatusBanner(title, body, modifier, tone, action)

@Composable
fun EditorialProgress(progress: Float, modifier: Modifier = Modifier, accent: Color = EditorialPine) =
    AppProgress(progress = progress, modifier = modifier, accent = accent)

@Composable
fun EditorialPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) = AppPrimaryButton(text, onClick, modifier, enabled, icon)

@Composable
fun EditorialEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_auto_stories,
) = AppEmptyState(title = title, body = body, modifier = modifier, icon = icon)

@Composable
fun EditorialPlayerSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = AppPlayerSurface(modifier, content)

fun Modifier.editorialContentWidth(): Modifier = fillMaxWidth().widthIn(max = EditorialMaxContentWidth.dp)
