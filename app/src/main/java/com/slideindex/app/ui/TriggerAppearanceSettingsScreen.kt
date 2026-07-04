package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.gesture.primaryTriggerHandle
import com.slideindex.app.gesture.sideTriggerPairs
import com.slideindex.app.gesture.triggerHandle
import com.slideindex.app.overlay.GestureHintRenderer
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.edgeTriggerWidthDp
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
    onGestureHintEnabledChange: (Boolean) -> Unit,
    onGestureHintStyleChange: (GestureHintStyle) -> Unit,
    onEdgeWidthChange: (Float) -> Unit,
    onTriggerVerticalRangeChange: (String, Float, Float) -> Unit,
    onAlignHandlesChange: (Boolean) -> Unit,
    onInterceptBackChange: (Boolean) -> Unit,
    onLimitInterceptLengthChange: (Boolean) -> Unit,
    onLayoutPreviewStart: () -> Unit,
    onLayoutPreviewStop: () -> Unit,
) {
    val pairIndex = settings.sideTriggerPairs().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.sideTriggerPairs().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val pairSuffix = if (pairCount > 1) " · $pairIndex" else ""

    DisposableEffect(Unit) {
        onDispose { onLayoutPreviewStop() }
    }

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
                value = settings.edgeTriggerWidthDp(side),
                valueRange = 12f..36f,
                enabled = serviceEnabled,
                label = "${settings.edgeTriggerWidthDp(side).roundToInt()} dp",
                startLabel = stringResource(R.string.handle_width_small),
                endLabel = stringResource(R.string.handle_width_large),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onLayoutPreviewStart,
                onLayoutPreviewStop = onLayoutPreviewStop,
                onValueChange = onEdgeWidthChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingsSliderRow(
                title = stringResource(R.string.short_swipe_distance),
                value = settings.shortSwipeDistanceDp,
                valueRange = SwipePathRecognizer.SHORT_DISTANCE_MIN_DP..
                    SwipePathRecognizer.SHORT_DISTANCE_MAX_DP,
                enabled = serviceEnabled,
                label = "",
                formatLabel = { "${it.roundToInt()} dp" },
                startLabel = stringResource(R.string.swipe_distance_small),
                endLabel = stringResource(R.string.swipe_distance_large),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onLayoutPreviewStart,
                onLayoutPreviewStop = onLayoutPreviewStop,
                onValueChange = onShortSwipeDistanceChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.long_swipe_distance),
                value = settings.longSwipeDistanceDp,
                valueRange = SwipePathRecognizer.LONG_DISTANCE_MIN_DP..
                    SwipePathRecognizer.LONG_DISTANCE_MAX_DP,
                enabled = serviceEnabled,
                label = "",
                formatLabel = { "${it.roundToInt()} dp" },
                startLabel = stringResource(R.string.swipe_distance_small),
                endLabel = stringResource(R.string.swipe_distance_large),
                triggersLayoutPreview = true,
                onLayoutPreviewStart = onLayoutPreviewStart,
                onLayoutPreviewStop = onLayoutPreviewStop,
                onValueChange = onLongSwipeDistanceChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingSwitchRow(
                title = stringResource(R.string.align_handles),
                subtitle = stringResource(R.string.align_handles_desc),
                checked = settings.alignHandlesEnabled,
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

        SettingsSectionTitle(stringResource(R.string.gesture_hint_style_title))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.gesture_hint_enabled),
                subtitle = stringResource(R.string.gesture_hint_enabled_desc),
                checked = settings.gestureHintEnabled,
                enabled = serviceEnabled,
                onCheckedChange = onGestureHintEnabledChange,
            )
            if (settings.gestureHintEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                GestureHintStyle.entries.forEach { style ->
                    GestureHintStyleRow(
                        style = style,
                        selected = settings.gestureHintStyle() == style,
                        enabled = serviceEnabled,
                        themeColorArgb = settings.themeColorArgb,
                        onClick = { onGestureHintStyleChange(style) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun GestureHintStyleRow(
    style: GestureHintStyle,
    selected: Boolean,
    enabled: Boolean,
    themeColorArgb: Int,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val title = when (style) {
        GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave)
        GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule)
        GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble)
        GestureHintStyle.PIXEL_BACK -> stringResource(R.string.gesture_hint_style_pixel_back)
    }
    val subtitle = when (style) {
        GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave_desc)
        GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule_desc)
        GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble_desc)
        GestureHintStyle.PIXEL_BACK -> stringResource(R.string.gesture_hint_style_pixel_back_desc)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                GestureHintRenderer.drawStyleIcon(
                    canvas = drawContext.canvas.nativeCanvas,
                    style = style,
                    boxSizePx = size.minDimension,
                    density = density.density,
                    themeColor = themeColorArgb,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
    }
}

@Composable
internal fun gestureHintStyleLabel(style: GestureHintStyle): String = when (style) {
    GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave)
    GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule)
    GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble)
    GestureHintStyle.PIXEL_BACK -> stringResource(R.string.gesture_hint_style_pixel_back)
}

@Composable
internal fun triggerAppearanceSummary(settings: AppSettings, side: PanelSide): String {
    val width = settings.edgeTriggerWidthDp(side).roundToInt()
    return if (settings.gestureHintEnabled) {
        stringResource(
            R.string.trigger_appearance_summary_with_hint,
            width,
            gestureHintStyleLabel(settings.gestureHintStyle()),
        )
    } else {
        stringResource(R.string.trigger_appearance_summary_no_hint, width)
    }
}
