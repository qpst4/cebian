@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
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
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun danmakuSettingsCardItems(
    settings: MessageSettings,
    controlsEnabled: Boolean,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuMaxLinesChange: (Int) -> Unit,
    onDanmakuSpeedLevelChange: (Int) -> Unit,
): List<CardItem> = buildList {
    add(
        settingsCardScopeItem("speed") {
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
        },
    )
    add(
        settingsCardScopeItem("opacity") {
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
        },
    )
    add(
        settingsCardScopeItem("max-lines") {
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
        },
    )
}

fun LazyListScope.danmakuSettingsSection(
    settings: MessageSettings,
    controlsEnabled: Boolean,
    items: List<CardItem>,
    bottomContentPadding: Dp,
    themeSectionTitle: String,
    overlayHint: String,
    onDanmakuThemeIdChange: (String) -> Unit,
) {
    settingsLazySmallTitle(
        key = "message-danmaku-theme",
        title = themeSectionTitle,
        sectionTop = true,
    )
    settingsLazyHint(
        key = "message-danmaku-hint",
        text = overlayHint,
    )
    LazySettingsItem(key = "message-danmaku-theme-grid") {
        MessageThemeGrid(
            themes = MessageThemeCatalog.themesFor(MessageStyle.Danmaku),
            selectedThemeId = settings.danmakuThemeId,
            enabled = controlsEnabled,
            onThemeSelected = onDanmakuThemeIdChange,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    groupedCardItems("message-danmaku-sliders", items)
    LazySettingsItem(key = "message-danmaku-bottom-inset") {
        Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
    }
}
