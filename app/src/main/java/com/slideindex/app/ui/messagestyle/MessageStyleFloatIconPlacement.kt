@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

fun LazyListScope.floatIconPlacementSection(
    settings: MessageSettings,
    enabled: Boolean,
    sectionTitle: String,
    onCornerChange: (MessageOverlayCorner) -> Unit,
    onYFractionPreviewChange: (Float) -> Unit = {},
    onYFractionPreviewCommit: () -> Unit = {},
    onYFractionChange: (Float) -> Unit,
) {
    settingsLazySmallTitle(key = "message-float-position", title = sectionTitle)
    groupedCardItems(
        keyPrefix = "message-float-placement",
        selectableGroup = true,
        items = buildList {
            MessageOverlayCorner.entries.forEach { corner ->
                add(
                    settingsCardScopeItem("corner-${corner.name}") {
                        SettingRadioRow(
                            title = stringResource(cornerLabelRes(corner)),
                            selected = settings.floatIconCorner == corner,
                            enabled = enabled,
                            onClick = { onCornerChange(corner) },
                        )
                    },
                )
            }
            add(
                settingsCardScopeItem("position-y") {
                    SettingsSliderRow(
                        title = stringResource(R.string.message_style_position_y),
                        value = settings.floatIconYFraction,
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
        },
    )
}

private fun cornerLabelRes(corner: MessageOverlayCorner): Int = when (corner) {
    MessageOverlayCorner.TopStart -> R.string.message_style_corner_top_start
    MessageOverlayCorner.TopEnd -> R.string.message_style_corner_top_end
    MessageOverlayCorner.BottomStart -> R.string.message_style_corner_bottom_start
    MessageOverlayCorner.BottomEnd -> R.string.message_style_corner_bottom_end
}
