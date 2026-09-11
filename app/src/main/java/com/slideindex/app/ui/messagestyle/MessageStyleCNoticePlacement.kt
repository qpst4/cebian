@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

fun LazyListScope.cNoticePlacementSection(
    settings: MessageSettings,
    enabled: Boolean,
    sectionTitle: String,
    positionHint: String,
    onHorizontalEdgeChange: (SideBubbleHorizontalEdge) -> Unit,
    onYFractionPreviewChange: (Float) -> Unit = {},
    onYFractionPreviewCommit: () -> Unit = {},
    onYFractionChange: (Float) -> Unit,
    onEdgeMarginPreviewChange: (Float) -> Unit = {},
    onEdgeMarginPreviewCommit: () -> Unit = {},
    onEdgeMarginChange: (Float) -> Unit,
) {
    settingsLazySmallTitle(key = "message-c-notice-position", title = sectionTitle)
    settingsLazyHint(key = "message-c-notice-position-hint", text = positionHint)
    groupedCardItems(
        keyPrefix = "message-c-notice-placement",
        selectableGroup = true,
        items = buildList {
            add(
                settingsCardScopeItem("edge-left") {
                    SettingRadioRow(
                        title = stringResource(R.string.message_style_side_edge_left),
                        selected = settings.cNoticeHorizontalEdge == SideBubbleHorizontalEdge.Left,
                        enabled = enabled,
                        onClick = { onHorizontalEdgeChange(SideBubbleHorizontalEdge.Left) },
                    )
                },
            )
            add(
                settingsCardScopeItem("edge-right") {
                    SettingRadioRow(
                        title = stringResource(R.string.message_style_side_edge_right),
                        selected = settings.cNoticeHorizontalEdge == SideBubbleHorizontalEdge.Right,
                        enabled = enabled,
                        onClick = { onHorizontalEdgeChange(SideBubbleHorizontalEdge.Right) },
                    )
                },
            )
            add(
                settingsCardScopeItem("position-y") {
                    SettingsSliderRow(
                        title = stringResource(R.string.message_style_position_y),
                        value = settings.cNoticeYFraction,
                        valueRange = MessagePlacementFractions.MIN_Y..MessagePlacementFractions.MAX_Y,
                        enabled = enabled,
                        label = "",
                        formatLabel = { "${(it * 100).toInt()}%" },
                        keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                        triggersLayoutPreview = true,
                        onLayoutPreviewValueChange = onYFractionPreviewChange,
                        onValueChange = { fraction ->
                            onYFractionPreviewCommit()
                            onYFractionChange(fraction)
                        },
                    )
                },
            )
            add(
                settingsCardScopeItem("edge-margin") {
                    SettingsSliderRow(
                        title = stringResource(R.string.message_style_c_notice_edge_margin),
                        value = settings.cNoticeEdgeMarginDp,
                        valueRange = 0f..32f,
                        steps = 31,
                        enabled = enabled,
                        label = "${settings.cNoticeEdgeMarginDp.toInt()} dp",
                        formatLabel = { "${it.toInt()} dp" },
                        triggersLayoutPreview = true,
                        onLayoutPreviewValueChange = onEdgeMarginPreviewChange,
                        onValueChange = { marginDp ->
                            onEdgeMarginPreviewCommit()
                            onEdgeMarginChange(marginDp)
                        },
                    )
                },
            )
        },
    )
}
