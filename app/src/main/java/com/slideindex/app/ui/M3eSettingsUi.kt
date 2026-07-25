@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.slideindex.app.ui.settings.components.LocalSettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsCardScope

/**
 * Renders a group of M3E segmented settings rows. Row helpers compose directly into this
 * [Column]; there is no deferred segment collection.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    val scope = SettingsCardScope()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        CompositionLocalProvider(LocalSettingsCardScope provides scope) {
            scope.content()
        }
    }
}

typealias SegmentPosition = com.slideindex.app.ui.settings.components.SegmentPosition
