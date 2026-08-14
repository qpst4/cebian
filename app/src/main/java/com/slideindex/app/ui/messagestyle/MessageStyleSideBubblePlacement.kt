@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

fun LazyListScope.sideBubblePlacementSection(
    settings: MessageSettings,
    enabled: Boolean,
    sectionTitle: String,
    onHorizontalEdgeChange: (SideBubbleHorizontalEdge) -> Unit,
    onVerticalAnchorChange: (SideBubbleVerticalAnchor) -> Unit,
) {
    settingsLazySmallTitle(key = "message-side-position", title = sectionTitle)
    LazySettingsItem(key = "message-side-placement") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.message_style_side_horizontal_edge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_edge_left),
                    selected = settings.sideBubbleHorizontalEdge == SideBubbleHorizontalEdge.Left,
                    enabled = enabled,
                    onClick = { onHorizontalEdgeChange(SideBubbleHorizontalEdge.Left) },
                    modifier = Modifier.weight(1f),
                )
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_edge_right),
                    selected = settings.sideBubbleHorizontalEdge == SideBubbleHorizontalEdge.Right,
                    enabled = enabled,
                    onClick = { onHorizontalEdgeChange(SideBubbleHorizontalEdge.Right) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.message_style_side_vertical_anchor),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_anchor_middle),
                    selected = settings.sideBubbleVerticalAnchor == SideBubbleVerticalAnchor.Middle,
                    enabled = enabled,
                    onClick = { onVerticalAnchorChange(SideBubbleVerticalAnchor.Middle) },
                    modifier = Modifier.weight(1f),
                )
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_anchor_bottom),
                    selected = settings.sideBubbleVerticalAnchor == SideBubbleVerticalAnchor.Bottom,
                    enabled = enabled,
                    onClick = { onVerticalAnchorChange(SideBubbleVerticalAnchor.Bottom) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
