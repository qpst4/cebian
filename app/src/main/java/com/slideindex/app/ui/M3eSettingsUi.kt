@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.settings.components.LocalSettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsCardScope

/**
 * Renders a group of M3E segmented settings rows. Children declare rows through
 * [SettingsCardScope] helpers; each row is keyed explicitly for stable conditional UI.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    val scope = SettingsCardScope()
    scope.reset()
    CompositionLocalProvider(LocalSettingsCardScope provides scope) {
        scope.content()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        scope.decorations.forEach { decoration ->
            key(decoration.key) {
                decoration.content()
            }
        }
        if (scope.segments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (scope.decorations.isNotEmpty()) {
                            Modifier.padding(top = pickerListSegmentedGap())
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
            ) {
                val count = scope.segments.size
                scope.segments.forEachIndexed { index, segment ->
                    key(segment.key) {
                        segment.content(SegmentPosition(index, count))
                    }
                }
            }
        }
    }
}

typealias SegmentPosition = com.slideindex.app.ui.settings.components.SegmentPosition
