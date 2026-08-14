@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun floatIconSettingsCardItems(
    settings: MessageSettings,
    enabled: Boolean,
    onOpacityChange: (Float) -> Unit,
    onFloatIconSizeDpChange: (Float) -> Unit,
): List<CardItem> = buildList {
    add(
        settingsCardScopeItem("size") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_float_icon_size),
                value = settings.floatIconSizeDp,
                valueRange = 32f..64f,
                steps = 31,
                enabled = enabled,
                label = "${settings.floatIconSizeDp.toInt()} dp",
                formatLabel = { "${it.toInt()} dp" },
                onValueChange = onFloatIconSizeDpChange,
            )
        },
    )
    add(
        settingsCardScopeItem("opacity") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_float_icon_opacity),
                value = settings.floatIconOpacity,
                valueRange = 0f..1f,
                enabled = enabled,
                label = "${(settings.floatIconOpacity * 100).toInt()}%",
                formatLabel = { "${(it * 100).toInt()}%" },
                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                onValueChange = onOpacityChange,
            )
        },
    )
}

fun LazyListScope.floatIconSettingsSection(
    items: List<CardItem>,
    sectionTitle: String,
    embedded: Boolean = false,
) {
    if (!embedded) {
        settingsLazySmallTitle(
            key = "message-float-settings",
            title = sectionTitle,
            sectionTop = true,
        )
    }
    groupedCardItems("message-float-icon", items)
}
