@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.ui.messagestyle.CardStyleSettingsSection
import com.slideindex.app.ui.messagestyle.DanmakuSettingsSection
import com.slideindex.app.ui.messagestyle.FloatIconSettingsSection
import com.slideindex.app.ui.messagestyle.MessageStyleChip
import com.slideindex.app.ui.messagestyle.MessageStyleLivePreviewSection
import com.slideindex.app.ui.messagestyle.SideStyleSettingsSection
import com.slideindex.app.ui.messagestyle.messageStyleLabel

private val mainStyleOptions = listOf(
    MessageStyle.FloatIcon,
    MessageStyle.DarkCard,
    MessageStyle.SideBubble,
)

@Composable
fun MessageStyleSettingsScreen(
    settings: MessageSettings,
    initialStyle: MessageStyle? = null,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onStyleIdChange: (String) -> Unit,
    onThemeIdChange: (String) -> Unit,
    onPrimaryStyleEnabledChange: (Boolean) -> Unit,
    onDanmakuEnabledChange: (Boolean) -> Unit,
    onDanmakuThemeIdChange: (String) -> Unit,
    onFloatIconOpacityChange: (Float) -> Unit,
    onCardOpacityChange: (Float) -> Unit,
    onSideBubbleOpacityChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onCardMaxLinesChange: (Int) -> Unit,
    onDanmakuMaxLinesChange: (Int) -> Unit,
    onSideMaxCountChange: (Int) -> Unit,
    onSideMaxWidthDpChange: (Float) -> Unit,
    onSideMaxLinesChange: (Int) -> Unit,
    onFloatIconSizeDpChange: (Float) -> Unit,
    onAutoDismissSecondsChange: (Int) -> Unit,
) {
    var selectedMainStyle by remember(settings.styleId, initialStyle) {
        mutableStateOf(initialStyle ?: settings.style)
    }
    LaunchedEffect(settings.styleId) {
        selectedMainStyle = settings.style
    }

    val controlsEnabled = settings.enabled

    SettingsScreenScaffold(
        title = stringResource(R.string.message_style_title),
        subtitle = stringResource(R.string.message_style_subtitle),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.message_style_section_style_select))
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.message_style_section_main_style),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mainStyleOptions.forEach { style ->
                        MessageStyleChip(
                            label = messageStyleLabel(style),
                            selected = selectedMainStyle == style,
                            enabled = controlsEnabled,
                            onClick = { selectMainStyle(style, settings, onStyleIdChange, onThemeIdChange, onPrimaryStyleEnabledChange) {
                                selectedMainStyle = it
                            } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.message_style_section_danmaku_toggle),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MessageStyleChip(
                    label = stringResource(R.string.message_style_danmaku),
                    selected = settings.danmakuEnabled,
                    enabled = controlsEnabled,
                    onClick = { if (controlsEnabled) onDanmakuEnabledChange(!settings.danmakuEnabled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        MessageStyleLivePreviewSection(
            settings = settings,
            selectedMainStyle = selectedMainStyle,
        )

        when (selectedMainStyle) {
            MessageStyle.FloatIcon -> FloatIconSettingsSection(
                settings = settings,
                enabled = controlsEnabled,
                onOpacityChange = onFloatIconOpacityChange,
                onFloatIconSizeDpChange = onFloatIconSizeDpChange,
            )
            MessageStyle.DarkCard -> CardStyleSettingsSection(
                settings = settings,
                enabled = controlsEnabled,
                onThemeIdChange = onThemeIdChange,
                onOpacityChange = onCardOpacityChange,
                onMaxLinesChange = onCardMaxLinesChange,
                onAutoDismissSecondsChange = onAutoDismissSecondsChange,
            )
            MessageStyle.SideBubble -> SideStyleSettingsSection(
                settings = settings,
                enabled = controlsEnabled,
                onThemeIdChange = onThemeIdChange,
                onOpacityChange = onSideBubbleOpacityChange,
                onMaxLinesChange = onSideMaxLinesChange,
                onAutoDismissSecondsChange = onAutoDismissSecondsChange,
                onSideMaxCountChange = onSideMaxCountChange,
                onSideMaxWidthDpChange = onSideMaxWidthDpChange,
            )
            MessageStyle.Danmaku -> Unit
        }

        DanmakuSettingsSection(
            settings = settings,
            selectedMainStyle = selectedMainStyle,
            controlsEnabled = controlsEnabled,
            bottomContentPadding = bottomContentPadding,
            onDanmakuThemeIdChange = onDanmakuThemeIdChange,
            onDanmakuOpacityChange = onDanmakuOpacityChange,
            onDanmakuMaxLinesChange = onDanmakuMaxLinesChange,
        )
    }
}

private fun selectMainStyle(
    style: MessageStyle,
    settings: MessageSettings,
    onStyleIdChange: (String) -> Unit,
    onThemeIdChange: (String) -> Unit,
    onPrimaryStyleEnabledChange: (Boolean) -> Unit,
    onSelected: (MessageStyle) -> Unit,
) {
    onSelected(style)
    onStyleIdChange(style.id)
    if (style != MessageStyle.FloatIcon) {
        onPrimaryStyleEnabledChange(true)
    }
    if (style in setOf(MessageStyle.DarkCard, MessageStyle.SideBubble)) {
        val themes = MessageThemeCatalog.themesFor(style)
        if (settings.themeId !in themes.map { it.id }) {
            onThemeIdChange(MessageThemeCatalog.defaultThemeIdFor(style))
        }
    }
}
