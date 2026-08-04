package com.slideindex.app.ui.settings

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.settings.components.SettingsCardScope

/**
 * Standard settings section: title + card. Use this instead of manually pairing
 * [MiuixSmallTitle] and [SettingsCard] on new screens.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    keyPrefix: String = title,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    MiuixSmallTitle(
        text = title,
        modifier = Modifier.fillMaxWidth().then(modifier),
        lazyKey = "section-title-$keyPrefix",
    )
    SettingsCard(keyPrefix = keyPrefix, content = content)
}
