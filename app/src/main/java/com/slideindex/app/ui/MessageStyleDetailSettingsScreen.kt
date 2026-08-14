@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.ui.messagestyle.danmakuSettingsCardItems
import com.slideindex.app.ui.messagestyle.danmakuSettingsSection
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
    onSideVerticalAnchorChange: (SideBubbleVerticalAnchor) -> Unit,
    onSideFontSizeLevelChange: (Int) -> Unit,
    onDanmakuSpeedLevelChange: (Int) -> Unit,
) {
    val controlsEnabled = settings.enabled
    val subtitle = when (style) {
        MessageStyle.FloatIcon -> stringResource(R.string.message_style_float_icon_desc)
        MessageStyle.SideBubble -> stringResource(R.string.message_style_side_bubble_desc)
        MessageStyle.Danmaku -> stringResource(R.string.message_style_danmaku_desc)
    }
    val floatIconSectionTitle = stringResource(R.string.message_style_section_float_settings)
    val sideThemeSectionTitle = stringResource(R.string.message_style_section_side_theme)
    val displaySectionTitle = stringResource(R.string.message_style_section_display)
    val fontSizeSectionTitle = stringResource(R.string.message_style_side_font_size)
    val placementSectionTitle = stringResource(R.string.message_style_side_position)
    val danmakuThemeSectionTitle = stringResource(R.string.message_reminder_danmaku_theme)
    val danmakuOverlayHint = stringResource(R.string.message_style_danmaku_overlay_hint)

    val floatIconItems = floatIconSettingsCardItems(
        settings = settings,
        enabled = controlsEnabled,
        onOpacityChange = onFloatIconOpacityChange,
        onFloatIconSizeDpChange = onFloatIconSizeDpChange,
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
            }
            MessageStyle.SideBubble -> {
                sideStyleSettingsSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    displayItems = sideDisplayItems,
                    themeSectionTitle = sideThemeSectionTitle,
                    displaySectionTitle = displaySectionTitle,
                    fontSizeSectionTitle = fontSizeSectionTitle,
                    onThemeIdChange = onSideThemeIdChange,
                    onFontSizeLevelChange = onSideFontSizeLevelChange,
                )
                sideBubblePlacementSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    sectionTitle = placementSectionTitle,
                    onHorizontalEdgeChange = onSideHorizontalEdgeChange,
                    onVerticalAnchorChange = onSideVerticalAnchorChange,
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
