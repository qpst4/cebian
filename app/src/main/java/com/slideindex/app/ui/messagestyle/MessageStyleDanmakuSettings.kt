@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.DanmakuSpeed
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsHintText
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.SettingsSliderRow

@Composable
internal fun DanmakuSettingsSection(
    settings: MessageSettings,
    controlsEnabled: Boolean,
    bottomContentPadding: Dp,
    onDanmakuThemeIdChange: (String) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuMaxLinesChange: (Int) -> Unit,
    onDanmakuSpeedLevelChange: (Int) -> Unit,
) {
    SettingsSectionTitle(stringResource(R.string.message_reminder_danmaku_theme))
    SettingsHintText(stringResource(R.string.message_style_danmaku_overlay_hint))
    MessageThemeGrid(
        themes = MessageThemeCatalog.themesFor(MessageStyle.Danmaku),
        selectedThemeId = settings.danmakuThemeId,
        enabled = controlsEnabled,
        onThemeSelected = onDanmakuThemeIdChange,
    )
    SettingsCard {
        SettingsSliderRow(
            title = stringResource(R.string.message_danmaku_speed),
            value = settings.danmakuSpeedLevel.toFloat(),
            valueRange = DanmakuSpeed.SLOW.toFloat()..DanmakuSpeed.FAST.toFloat(),
            steps = 1,
            enabled = controlsEnabled,
            label = when (settings.danmakuSpeedLevel.coerceIn(DanmakuSpeed.SLOW, DanmakuSpeed.FAST)) {
                DanmakuSpeed.SLOW -> stringResource(R.string.message_danmaku_speed_slow)
                DanmakuSpeed.FAST -> stringResource(R.string.message_danmaku_speed_fast)
                else -> stringResource(R.string.message_danmaku_speed_normal)
            },
            formatLabel = { level ->
                when (level.toInt().coerceIn(DanmakuSpeed.SLOW, DanmakuSpeed.FAST)) {
                    DanmakuSpeed.SLOW -> "慢"
                    DanmakuSpeed.FAST -> "快"
                    else -> "标准"
                }
            },
            onValueChange = { onDanmakuSpeedLevelChange(it.toInt()) },
        )
        SettingsSliderRow(
            title = stringResource(R.string.message_reminder_danmaku_opacity),
            value = settings.danmakuOpacity,
            valueRange = 0.2f..1f,
            steps = 7,
            enabled = controlsEnabled,
            label = "${(settings.danmakuOpacity * 100).toInt()}%",
            formatLabel = { "${(it * 100).toInt()}%" },
            onValueChange = onDanmakuOpacityChange,
        )
        SettingsSliderRow(
            title = stringResource(R.string.message_style_max_lines),
            value = settings.danmakuMaxLines.toFloat(),
            valueRange = 1f..3f,
            steps = 1,
            enabled = controlsEnabled,
            label = settings.danmakuMaxLines.toString(),
            formatLabel = { it.toInt().toString() },
            onValueChange = { onDanmakuMaxLinesChange(it.toInt()) },
        )
    }

    Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
}
