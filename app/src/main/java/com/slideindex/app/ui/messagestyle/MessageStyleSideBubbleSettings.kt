@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageThemeCatalog

@Composable
internal fun SideStyleSettingsSection(
    settings: MessageSettings,
    enabled: Boolean,
    onThemeIdChange: (String) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onAutoDismissSecondsChange: (Int) -> Unit,
    onPickSideCount: () -> Unit,
    onFontSizeLevelChange: (Int) -> Unit,
) {
    MiuixSmallTitle(stringResource(R.string.message_style_section_side_theme), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
    MessageThemeGrid(
        themes = MessageThemeCatalog.themesFor(MessageStyle.SideBubble),
        selectedThemeId = settings.sideThemeId,
        enabled = enabled,
        onThemeSelected = onThemeIdChange,
    )
    PrimaryDisplaySettings(
        settings = settings,
        enabled = enabled,
        maxLines = settings.sideMaxLines,
        opacity = settings.sideBubbleOpacity,
        opacityTitleRes = R.string.message_reminder_side_opacity,
        onOpacityChange = onOpacityChange,
        onMaxLinesChange = onMaxLinesChange,
        onAutoDismissSecondsChange = onAutoDismissSecondsChange,
        onPickSideCount = onPickSideCount,
        sideMaxCount = settings.sideMaxCount,
        opacitySteps = 0,
        opacityRange = 0.1f..1f,
    )
    SideBubbleFontSizeSettings(
        settings = settings,
        enabled = enabled,
        onFontSizeLevelChange = onFontSizeLevelChange,
    )
}
