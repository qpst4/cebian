package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.ui.animationstyle.AnimationStylePreview
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.triggerHandleEdgeWidthDp
import com.slideindex.app.settings.gestureHintStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerAppearanceSettingsScreen(
    side: PanelSide,
    handleId: String,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onShortSwipeDistanceChange: (Float) -> Unit,
    onLongSwipeDistanceChange: (Float) -> Unit,
    onEdgeWidthChange: (Float) -> Unit,
    onTriggerVerticalRangeChange: (String, Float, Float) -> Unit,
    onAlignHandlesChange: (Boolean) -> Unit,
    onInterceptBackChange: (Boolean) -> Unit,
    onLimitInterceptLengthChange: (Boolean) -> Unit,
    onLayoutPreviewStart: () -> Unit,
    onLayoutPreviewStop: () -> Unit,
    onSwipeDistancePreviewStart: () -> Unit,
    onSwipeDistancePreviewStop: () -> Unit,
) {
    val pairIndex = settings.triggerCollectionEntries().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.triggerCollectionEntries().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val handleWidth = settings.triggerHandleEdgeWidthDp(side, handleId)
    val pairSuffix = if (pairCount > 1) " · $pairIndex" else ""

    SettingsScreenScaffold(
        title = stringResource(R.string.trigger_appearance_title),
        subtitle = stringResource(R.string.trigger_appearance_desc) + pairSuffix,
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.side_gestures_preview_hint))

        SettingsSectionTitle(stringResource(R.string.side_gestures_handle_section))
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.handle_width),
                value = handleWidth,
                valueRange = 12f..36f,
                enabled = serviceEnabled,
                label = "${handleWidth.roundToInt()} dp",
                startLabel = stringResource(R.string.handle_width_small),
                endLabel = stringResource(R.string.handle_width_large),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onLayoutPreviewStart,
                onLayoutPreviewStop = onLayoutPreviewStop,
                onValueChange = onEdgeWidthChange,
            )
            SettingsRangeSliderRow(
                title = stringResource(R.string.handle_length),
                values = selectedHandle.topFraction..selectedHandle.bottomFraction,
                valueRange = 0.05f..0.95f,
                startLabel = stringResource(R.string.handle_length_small),
                endLabel = stringResource(R.string.handle_length_large),
                enabled = serviceEnabled,
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onLayoutPreviewStart,
                onLayoutPreviewStop = onLayoutPreviewStop,
                onValueChange = { range ->
                    onTriggerVerticalRangeChange(handleId, range.start, range.endInclusive)
                },
            )
            SettingsSliderRow(
                title = stringResource(R.string.short_swipe_distance),
                value = selectedHandle.shortSwipeDistanceDp,
                valueRange = SwipePathRecognizer.SHORT_DISTANCE_MIN_DP..
                    SwipePathRecognizer.SHORT_DISTANCE_MAX_DP,
                enabled = serviceEnabled,
                label = "",
                formatLabel = { "${it.roundToInt()} dp" },
                startLabel = stringResource(R.string.swipe_distance_small),
                endLabel = stringResource(R.string.swipe_distance_large),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onSwipeDistancePreviewStart,
                onLayoutPreviewStop = onSwipeDistancePreviewStop,
                onValueChange = onShortSwipeDistanceChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.long_swipe_distance),
                value = selectedHandle.longSwipeDistanceDp,
                valueRange = (selectedHandle.shortSwipeDistanceDp + 16f)
                    .coerceAtLeast(SwipePathRecognizer.LONG_DISTANCE_MIN_DP)..
                    SwipePathRecognizer.LONG_DISTANCE_MAX_DP,
                enabled = serviceEnabled,
                label = "",
                formatLabel = { "${it.roundToInt()} dp" },
                startLabel = stringResource(R.string.swipe_distance_small),
                endLabel = stringResource(R.string.swipe_distance_large),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onSwipeDistancePreviewStart,
                onLayoutPreviewStop = onSwipeDistancePreviewStop,
                onValueChange = onLongSwipeDistanceChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.align_handles),
                subtitle = stringResource(R.string.align_handles_desc),
                checked = selectedHandle.alignOppositeSide,
                enabled = serviceEnabled,
                onCheckedChange = onAlignHandlesChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.intercept_system_back),
                subtitle = stringResource(R.string.intercept_system_back_desc),
                checked = settings.interceptSystemBackGesture,
                enabled = serviceEnabled,
                onCheckedChange = onInterceptBackChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.limit_intercept_length),
                subtitle = stringResource(R.string.limit_intercept_length_desc),
                checked = settings.limitMaxInterceptLength,
                enabled = serviceEnabled && settings.interceptSystemBackGesture,
                onCheckedChange = onLimitInterceptLengthChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GestureHintStyleRow(
    style: GestureHintStyle,
    selected: Boolean,
    enabled: Boolean,
    settings: AppSettings,
    onClick: () -> Unit,
) {
    val title = when (style) {
        GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave)
        GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule)
        GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble)
    }
    val subtitle = when (style) {
        GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave_desc)
        GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule_desc)
        GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble_desc)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = pickerSegmentedColors(),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                ) {
                    AnimationStylePreview(
                        style = style,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            trailingContent = {
                androidx.compose.material3.RadioButton(
                    selected = selected,
                    onClick = null,
                )
            },
            content = {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
internal fun gestureHintStyleLabel(style: GestureHintStyle): String = when (style) {
    GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave)
    GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule)
    GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble)
}

@Composable
internal fun triggerAppearanceSummary(settings: AppSettings, side: PanelSide, handleId: String): String {
    val width = settings.triggerHandleEdgeWidthDp(side, handleId).roundToInt()
    return stringResource(R.string.trigger_appearance_summary, width)
}
