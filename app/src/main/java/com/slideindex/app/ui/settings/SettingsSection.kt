package com.slideindex.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.settings.components.SettingsCardScope

/**
 * Standard settings section: title + card. Use this instead of manually pairing
 * [SettingsSectionTitle] and [SettingsCard] on new screens.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    SettingsSectionTitle(title = title, modifier = modifier)
    SettingsCard(content = content)
}
