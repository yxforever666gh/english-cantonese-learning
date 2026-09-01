package com.example.englishcantoneselearning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishcantoneselearning.R
import com.example.englishcantoneselearning.ui.theme.EditorialPine
import com.example.englishcantoneselearning.ui.theme.EditorialSurface
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A compact number editor used by speech-speed and reading-font-size controls.
 *
 * Values are committed only by the minus/plus buttons, the keyboard Done action, or when the
 * input loses focus. Callers can therefore apply an expensive side effect (such as rebuilding
 * speech audio) directly from [onValueCommitted] without receiving callbacks for every digit.
 */
@Composable
internal fun NumericStepper(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    decimalPlaces: Int,
    unit: String,
    onValueCommitted: (Float) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    require(step > 0f) { "step must be positive" }
    require(decimalPlaces >= 0) { "decimalPlaces must not be negative" }

    val focusManager = LocalFocusManager.current
    val normalizedValue = normalizeNumericValue(value, range, step)
    var committedValue by rememberSaveable(testTagPrefix) { mutableFloatStateOf(normalizedValue) }
    var draft by rememberSaveable(testTagPrefix) {
        mutableStateOf(formatNumericValue(normalizedValue, decimalPlaces))
    }
    var wasFocused by rememberSaveable(testTagPrefix) { mutableStateOf(false) }

    LaunchedEffect(normalizedValue, decimalPlaces) {
        committedValue = normalizedValue
        if (!wasFocused) draft = formatNumericValue(normalizedValue, decimalPlaces)
    }

    fun commit(candidate: Float?) {
        val normalized = candidate
            ?.takeIf(Float::isFinite)
            ?.let { normalizeNumericValue(it, range, step) }
        if (normalized == null) {
            draft = formatNumericValue(committedValue, decimalPlaces)
            return
        }
        draft = formatNumericValue(normalized, decimalPlaces)
        if (abs(normalized - committedValue) > NUMERIC_EPSILON) {
            committedValue = normalized
            onValueCommitted(normalized)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { commit(committedValue - step) },
            enabled = committedValue > range.start + NUMERIC_EPSILON,
            modifier = Modifier.size(44.dp).testTag("${testTagPrefix}_decrease"),
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { input ->
                if (input.length <= MAX_NUMERIC_INPUT_LENGTH && input.all(::isNumericInputCharacter)) {
                    draft = input
                }
            },
            modifier = Modifier
                .width(76.dp)
                .heightIn(min = 48.dp)
                .onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) commit(parseNumericDraft(draft))
                    wasFocused = focusState.isFocused
                }
                .testTag("${testTagPrefix}_input"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimalPlaces == 0) KeyboardType.Number else KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commit(parseNumericDraft(draft))
                    focusManager.clearFocus()
                },
            ),
        )
        Text(unit, style = MaterialTheme.typography.labelMedium)
        IconButton(
            onClick = { commit(committedValue + step) },
            enabled = committedValue < range.endInclusive - NUMERIC_EPSILON,
            modifier = Modifier.size(44.dp).testTag("${testTagPrefix}_increase"),
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** A reusable bottom player whose collapsed state contains only transport and expand controls. */
@Composable
internal fun CollapsiblePlayerSurface(
    stateKey: String,
    playing: Boolean,
    preparing: Boolean,
    canPlay: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("${testTagPrefix}_surface"),
        color = EditorialSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column {
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = expandedContent,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReadingTransportControls(
                    playing = playing,
                    preparing = preparing,
                    canPlay = canPlay,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    testTagPrefix = testTagPrefix,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp),
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                        .testTag("${testTagPrefix}_${if (expanded) "collapse" else "expand"}"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = if (expanded) "折叠朗读控制" else "展开朗读控制",
                        modifier = Modifier.rotate(if (expanded) -90f else 90f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReadingTransportControls(
    playing: Boolean,
    preparing: Boolean,
    canPlay: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(44.dp).testTag("${testTagPrefix}_previous"),
        ) {
            Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "上一句")
        }
        FilledIconButton(
            onClick = onPlayPause,
            enabled = canPlay && !preparing,
            modifier = Modifier.size(52.dp).testTag("${testTagPrefix}_play_pause"),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = EditorialPine,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (playing) "暂停" else "播放",
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(44.dp).testTag("${testTagPrefix}_next"),
        ) {
            Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "下一句")
        }
    }
}

/** A compact sentence marker that does not reserve a separate column beside the article text. */
@Composable
internal fun CompactSentenceNumberBadge(
    number: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 18.dp)
            .semantics { contentDescription = "第 $number 句" }
            .testTag("sentence_number_$number"),
        color = if (selected) EditorialPine else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun normalizeNumericValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
): Float {
    require(step > 0f) { "step must be positive" }
    val finiteValue = value.takeIf(Float::isFinite) ?: range.start
    val clamped = finiteValue.coerceIn(range.start, range.endInclusive)
    val stepsFromStart = ((clamped - range.start) / step).roundToInt()
    val rounded = range.start + stepsFromStart * step
    val precision = 10f.pow(decimalPlacesForStep(step))
    return ((rounded.coerceIn(range.start, range.endInclusive) * precision).roundToInt() / precision)
}

private fun decimalPlacesForStep(step: Float): Int {
    var scaled = step
    repeat(4) { places ->
        if (abs(scaled - scaled.roundToInt()) < NUMERIC_EPSILON) return places
        scaled *= 10f
    }
    return 4
}

private fun parseNumericDraft(draft: String): Float? {
    val trimmed = draft.trim()
    if (trimmed.isEmpty() || trimmed.endsWith('.') || trimmed == "+" || trimmed == "-") return null
    return trimmed.toFloatOrNull()?.takeIf(Float::isFinite)
}

private fun formatNumericValue(value: Float, decimalPlaces: Int): String =
    String.format(Locale.US, "%.${decimalPlaces}f", value)

private fun isNumericInputCharacter(character: Char): Boolean =
    character.isDigit() || character == '.' || character == '-' || character == '+'

private const val MAX_NUMERIC_INPUT_LENGTH = 8
private const val NUMERIC_EPSILON = 0.0001f
