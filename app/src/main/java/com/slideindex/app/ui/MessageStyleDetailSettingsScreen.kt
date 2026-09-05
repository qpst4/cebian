@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.ui.messagestyle.danmakuSettingsCardItems
import com.slideindex.app.ui.messagestyle.danmakuSettingsSection
import com.slideindex.app.ui.messagestyle.floatIconPlacementSection
import com.slideindex.app.ui.messagestyle.floatIconSettingsCardItems
import com.slideindex.app.ui.messagestyle.floatIconSettingsSection
import com.slideindex.app.ui.messagestyle.messageStyleLabel
import com.slideindex.app.ui.messagestyle.primaryDisplayCardItems
import com.slideindex.app.ui.messagestyle.sideBubblePlacementSection
import com.slideindex.app.ui.messagestyle.sideStyleSettingsSection

@Composable
fun MessageStyleDetailSettingsScreen(
    style: MessageStyle,
    settings: MessageSettings,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onOpenSideCountPick: () -> Unit = {},
    onSideThemeIdChange: (String) -> Unit,
    onDanmakuThemeIdChange: (String) -> Unit,
    onFloatIconOpacityChange: (Float) -> Unit,
    onSideBubbleOpacityChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuMaxLinesChange: (Int) -> Unit,
    onSideMaxCountChange: (Int) -> Unit,
    onSideMaxLinesChange: (Int) -> Unit,
    onFloatIconSizeDpChange: (Float) -> Unit,
    onAutoDismissSecondsChange: (Int) -> Unit,
    onSideHorizontalEdgeChange: (SideBubbleHorizontalEdge) -> Unit,
    onSideBubbleYFractionPreviewChange: (Float) -> Unit = {},
    onSideBubbleYFractionPreviewCommit: () -> Unit = {},
    onSideBubbleYFractionChange: (Float) -> Unit = {},
    onFloatIconCornerChange: (MessageOverlayCorner) -> Unit = {},
    onFloatIconYFractionPreviewChange: (Float) -> Unit = {},
    onFloatIconYFractionPreviewCommit: () -> Unit = {},
    onFloatIconYFractionChange: (Float) -> Unit = {},
    onSideFontSizeLevelChange: (Int) -> Unit,
    onDanmakuSpeedLevelChange: (Int) -> Unit,
    onMessagePreviewChange: (MessageSettings) -> Unit = {},
    onMessagePreviewCommit: () -> Unit = {},
) {
    val controlsEnabled = settings.enabled
    val subtitle = when (style) {
        MessageStyle.FloatIcon -> stringResource(R.string.message_style_float_icon_desc)
        MessageStyle.SideBubble -> stringResource(R.string.message_style_side_bubble_desc)
        MessageStyle.Danmaku -> stringResource(R.string.message_style_danmaku_desc)
    }
    val floatIconSectionTitle = stringResource(R.string.message_style_section_float_settings)
    val floatIconPlacementTitle = stringResource(R.string.message_style_float_position)
    val sideThemeSectionTitle = stringResource(R.string.message_style_section_side_theme)
    val displaySectionTitle = stringResource(R.string.message_style_section_display)
    val fontSizeSectionTitle = stringResource(R.string.message_style_side_font_size)
    val fontSizeHint = stringResource(R.string.message_style_side_font_size_desc)
    val placementSectionTitle = stringResource(R.string.message_style_side_position)
    val placementHint = stringResource(R.string.message_style_side_position_hint)
    val danmakuThemeSectionTitle = stringResource(R.string.message_reminder_danmaku_theme)
    val danmakuOverlayHint = stringResource(R.string.message_style_danmaku_overlay_hint)

    val floatIconItems = floatIconSettingsCardItems(
        settings = settings,
        enabled = controlsEnabled,
        onOpacityChange = onFloatIconOpacityChange,
        onFloatIconSizeDpChange = onFloatIconSizeDpChange,
        onPreviewChange = onMessagePreviewChange,
        onPreviewCommit = onMessagePreviewCommit,
    )
    val sideDisplayItems = primaryDisplayCardItems(
        settings = settings,
        enabled = controlsEnabled,
        maxLines = settings.sideMaxLines,
        opacity = settings.sideBubbleOpacity,
        opacityTitleRes = R.string.message_reminder_side_opacity,
        onOpacityChange = onSideBubbleOpacityChange,
        onMaxLinesChange = onSideMaxLinesChange,
        onAutoDismissSecondsChange = onAutoDismissSecondsChange,
        onPickSideCount = onOpenSideCountPick,
        sideMaxCount = settings.sideMaxCount,
        opacitySteps = 0,
        opacityRange = 0.1f..1f,
        onPreviewChange = onMessagePreviewChange,
        onPreviewCommit = onMessagePreviewCommit,
    )
    val danmakuItems = danmakuSettingsCardItems(
        settings = settings,
        controlsEnabled = controlsEnabled,
        onDanmakuOpacityChange = onDanmakuOpacityChange,
        onDanmakuMaxLinesChange = onDanmakuMaxLinesChange,
        onDanmakuSpeedLevelChange = onDanmakuSpeedLevelChange,
    )

    SettingsScreenScaffold(
        title = messageStyleLabel(style),
        subtitle = subtitle,
        onBack = onBack,
    ) {
        when (style) {
            MessageStyle.FloatIcon -> {
                floatIconSettingsSection(
                    items = floatIconItems,
                    sectionTitle = floatIconSectionTitle,
                )
                floatIconPlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = floatIconPlacementTitle,
                    onCornerChange = onFloatIconCornerChange,
                    onYFractionPreviewChange = onFloatIconYFractionPreviewChange,
                    onYFractionPreviewCommit = onFloatIconYFractionPreviewCommit,
                    onYFractionChange = onFloatIconYFractionChange,
                )
            }
            MessageStyle.SideBubble -> {
                sideStyleSettingsSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    displayItems = sideDisplayItems,
                    themeSectionTitle = sideThemeSectionTitle,
                    displaySectionTitle = displaySectionTitle,
                    fontSizeSectionTitle = fontSizeSectionTitle,
                    fontSizeHint = fontSizeHint,
                    onThemeIdChange = onSideThemeIdChange,
                    onFontSizeLevelChange = onSideFontSizeLevelChange,
                )
                sideBubblePlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = placementSectionTitle,
                    positionHint = placementHint,
                    onHorizontalEdgeChange = onSideHorizontalEdgeChange,
                    onYFractionPreviewChange = onSideBubbleYFractionPreviewChange,
                    onYFractionPreviewCommit = onSideBubbleYFractionPreviewCommit,
                    onYFractionChange = onSideBubbleYFractionChange,
                )
            }
            MessageStyle.Danmaku -> {
                danmakuSettingsSection(
                    settings = settings,
                    controlsEnabled = controlsEnabled,
                    items = danmakuItems,
                    bottomContentPadding = bottomContentPadding,
                    themeSectionTitle = danmakuThemeSectionTitle,
                    overlayHint = danmakuOverlayHint,
                    onDanmakuThemeIdChange = onDanmakuThemeIdChange,
                )
            }
        }
    }
}
