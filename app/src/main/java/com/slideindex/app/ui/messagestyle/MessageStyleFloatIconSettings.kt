@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.SettingsSliderRow

@Composable
internal fun FloatIconSettingsSection(
    settings: MessageSettings,
    enabled: Boolean,
    onOpacityChange: (Float) -> Unit,
    onFloatIconSizeDpChange: (Float) -> Unit,
    embedded: Boolean = false,
) {
    if (!embedded) {
        SettingsSectionTitle(stringResource(R.string.message_style_section_float_settings))
    }
    SettingsCard {
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
        SettingsSliderRow(
            title = stringResource(R.string.message_style_float_icon_opacity),
            value = settings.floatIconOpacity,
            valueRange = 0f..1f,
            steps = 19,
            enabled = enabled,
            label = "${(settings.floatIconOpacity * 100).toInt()}%",
            formatLabel = { "${(it * 100).toInt()}%" },
            onValueChange = onOpacityChange,
        )
    }
}
