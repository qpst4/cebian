package com.slideindex.app.ui.animationstyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AndroidBackColorSource
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun AndroidBackStyleSettingsScreen(
    colorSource: AndroidBackColorSource,
    enabled: Boolean,
    onBack: () -> Unit,
    onColorSourceChange: (AndroidBackColorSource) -> Unit,
) {
    val colorSourceTitle = stringResource(R.string.android_back_color_source_title)
    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_hint_style_android),
        pageHint = stringResource(R.string.android_back_color_source_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "android-back-color-source-title", title = colorSourceTitle)
        MiuixListSettingsCard(
            keyPrefix = "android-back-color-source",
            selectableGroup = true,
            items = listOf(
                settingsCardScopeItem("app-theme") {
                    SettingRadioRow(
                        title = stringResource(R.string.android_back_color_source_app),
                        subtitle = stringResource(R.string.android_back_color_source_app_desc),
                        selected = colorSource == AndroidBackColorSource.APP_THEME,
                        enabled = enabled,
                        onClick = { onColorSourceChange(AndroidBackColorSource.APP_THEME) },
                    )
                },
                settingsCardScopeItem("system") {
                    SettingRadioRow(
                        title = stringResource(R.string.android_back_color_source_system),
                        subtitle = stringResource(R.string.android_back_color_source_system_desc),
                        selected = colorSource == AndroidBackColorSource.SYSTEM,
                        enabled = enabled,
                        onClick = { onColorSourceChange(AndroidBackColorSource.SYSTEM) },
                    )
                },
            ),
        )
    }
}
