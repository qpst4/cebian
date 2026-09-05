@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

fun LazyListScope.sideStyleSettingsSection(
    settings: MessageSettings,
    enabled: Boolean,
    displayItems: List<CardItem>,
    themeSectionTitle: String,
    displaySectionTitle: String,
    fontSizeSectionTitle: String,
    fontSizeHint: String,
    onThemeIdChange: (String) -> Unit,
    onFontSizeLevelChange: (Int) -> Unit,
) {
    settingsLazySmallTitle(
        key = "message-side-theme",
        title = themeSectionTitle,
        sectionTop = true,
    )
    LazySettingsItem(key = "message-side-theme-grid") {
        MessageThemeGrid(
            themes = MessageThemeCatalog.themesFor(MessageStyle.SideBubble),
            selectedThemeId = settings.sideThemeId,
            enabled = enabled,
            onThemeSelected = onThemeIdChange,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    primaryDisplaySection(displayItems, displaySectionTitle)
    sideBubbleFontSizeSection(
        settings = settings,
        enabled = enabled,
        sectionTitle = fontSizeSectionTitle,
        fontSizeHint = fontSizeHint,
        onFontSizeLevelChange = onFontSizeLevelChange,
    )
}
