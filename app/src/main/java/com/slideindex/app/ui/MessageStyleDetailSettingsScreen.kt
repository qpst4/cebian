@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.ui.messagestyle.DanmakuSettingsSection
import com.slideindex.app.ui.messagestyle.FloatIconSettingsSection
import com.slideindex.app.ui.messagestyle.SideBubblePlacementSettings
import com.slideindex.app.ui.messagestyle.SideStyleSettingsSection
import com.slideindex.app.ui.messagestyle.messageStyleLabel

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
        MessageStyle.FloatIcon -> stringResource(com.slideindex.app.R.string.message_style_float_icon_desc)
        MessageStyle.SideBubble -> stringResource(com.slideindex.app.R.string.message_style_side_bubble_desc)
        MessageStyle.Danmaku -> stringResource(com.slideindex.app.R.string.message_style_danmaku_desc)
    }

    SettingsScreenScaffold(
        title = messageStyleLabel(style),
        subtitle = subtitle,
        onBack = onBack,
    ) {
        when (style) {
            MessageStyle.FloatIcon -> {
                FloatIconSettingsSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    onOpacityChange = onFloatIconOpacityChange,
                    onFloatIconSizeDpChange = onFloatIconSizeDpChange,
                )
            }
            MessageStyle.SideBubble -> {
                SideStyleSettingsSection(
                    settings = settings,
                    enabled = controlsEnabled,
                    onThemeIdChange = onSideThemeIdChange,
                    onOpacityChange = onSideBubbleOpacityChange,
                    onMaxLinesChange = onSideMaxLinesChange,
                    onAutoDismissSecondsChange = onAutoDismissSecondsChange,
                    onPickSideCount = onOpenSideCountPick,
                    onFontSizeLevelChange = onSideFontSizeLevelChange,
                )
                SideBubblePlacementSettings(
                    settings = settings,
                    enabled = controlsEnabled,
                    onHorizontalEdgeChange = onSideHorizontalEdgeChange,
                    onVerticalAnchorChange = onSideVerticalAnchorChange,
                )
            }
            MessageStyle.Danmaku -> {
                DanmakuSettingsSection(
                    settings = settings,
                    controlsEnabled = controlsEnabled,
                    bottomContentPadding = bottomContentPadding,
                    onDanmakuThemeIdChange = onDanmakuThemeIdChange,
                    onDanmakuOpacityChange = onDanmakuOpacityChange,
                    onDanmakuMaxLinesChange = onDanmakuMaxLinesChange,
                    onDanmakuSpeedLevelChange = onDanmakuSpeedLevelChange,
                )
            }
        }
    }
}
