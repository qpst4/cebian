@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleFontSize
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

fun LazyListScope.sideBubbleFontSizeSection(
    settings: MessageSettings,
    enabled: Boolean,
    sectionTitle: String,
    fontSizeHint: String,
    onFontSizeLevelChange: (Int) -> Unit,
) {
    settingsLazySmallTitle(key = "message-side-font-size", title = sectionTitle)
    settingsLazyHint(
        key = "message-side-font-size-hint",
        text = fontSizeHint,
    )
    groupedCardItems(
        keyPrefix = "message-side-font-size",
        selectableGroup = true,
        items = listOf(
            settingsCardScopeItem("font-small") {
                SettingRadioRow(
                    title = stringResource(R.string.message_style_side_font_size_small),
                    selected = settings.sideBubbleFontSizeLevel == SideBubbleFontSize.SMALL,
                    enabled = enabled,
                    onClick = { onFontSizeLevelChange(SideBubbleFontSize.SMALL) },
                )
            },
            settingsCardScopeItem("font-normal") {
                SettingRadioRow(
                    title = stringResource(R.string.message_style_side_font_size_normal),
                    selected = settings.sideBubbleFontSizeLevel == SideBubbleFontSize.NORMAL,
                    enabled = enabled,
                    onClick = { onFontSizeLevelChange(SideBubbleFontSize.NORMAL) },
                )
            },
            settingsCardScopeItem("font-large") {
                SettingRadioRow(
                    title = stringResource(R.string.message_style_side_font_size_large),
                    selected = settings.sideBubbleFontSizeLevel == SideBubbleFontSize.LARGE,
                    enabled = enabled,
                    onClick = { onFontSizeLevelChange(SideBubbleFontSize.LARGE) },
                )
            },
        ),
    )
}
