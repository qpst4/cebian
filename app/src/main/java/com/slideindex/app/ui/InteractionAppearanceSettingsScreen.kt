package com.slideindex.app.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.ui.HomeLeadingIcons
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.appLanguageSettingsCardItems
import com.slideindex.app.ui.miuix.themeAppearanceSettingsCardItems
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt

@Composable
fun InteractionAppearanceSettingsScreen(
    settings: HomeMainSettings,
    onBack: () -> Unit,
    onHapticEnabledChange: (Boolean) -> Unit,
    onHapticStrengthChange: (Int) -> Unit,
    onSwipeDismissEnabledChange: (Boolean) -> Unit,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (Int) -> Unit,
    onThemePaletteStyleChange: (com.slideindex.app.settings.ThemePaletteStyle) -> Unit,
    onThemeModeChange: (com.slideindex.app.settings.AppThemeMode) -> Unit,
    onCustomColorChange: (Boolean) -> Unit,
    onDarkBackgroundStyleChange: (com.slideindex.app.settings.DarkBackgroundStyle) -> Unit,
    onThemeColorSpecChange: (com.slideindex.app.settings.AppColorSpec) -> Unit,
    onBottomNavStyleChange: (com.slideindex.app.settings.BottomNavStyle) -> Unit,
    onBottomNavModeChange: (com.slideindex.app.settings.BottomNavMode) -> Unit,
    onBottomNavGlassEnabledChange: (Boolean) -> Unit,
    onBottomNavBlurRadiusChange: (Float) -> Unit,
    onTopAppBarBlurStyleChange: (TopAppBarBlurStyle) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
    onAppUiLanguageChange: (com.slideindex.app.settings.AppUiLanguage) -> Unit,
    onBottomNavBlurPreviewChange: (Float) -> Unit = {},
    onBottomNavBlurPreviewStop: () -> Unit = {},
) {
    val view = LocalView.current
    val hapticLightLabel = stringResource(R.string.haptic_strength_light)
    val hapticMediumLabel = stringResource(R.string.haptic_strength_medium)
    val hapticStrongLabel = stringResource(R.string.haptic_strength_strong)
    val hapticFormatLabel = remember(hapticLightLabel, hapticMediumLabel, hapticStrongLabel) {
        { level: Float ->
            when (level.roundToInt()) {
                0 -> hapticLightLabel
                2 -> hapticStrongLabel
                else -> hapticMediumLabel
            }
        }
    }
    val themeAppearanceItems = themeAppearanceSettingsCardItems(
        outlinedPreferenceIcons = true,
        themeModeId = settings.themeModeId,
        customColorEnabled = settings.customColorEnabled,
        darkBackgroundStyleId = settings.darkBackgroundStyleId,
        dynamicColorEnabled = settings.dynamicColorEnabled,
        themeColorArgb = settings.themeColorArgb,
        paletteStyleId = settings.themePaletteStyleId,
        themeColorSpecId = settings.themeColorSpecId,
        bottomNavStyleId = settings.bottomNavStyleId,
        bottomNavModeId = settings.bottomNavModeId,
        bottomNavGlassEnabled = settings.bottomNavGlassEnabled,
        bottomNavBlurRadiusDp = settings.bottomNavBlurRadiusDp,
        topAppBarBlurStyleId = settings.topAppBarBlurStyleId,
        uiDensityScale = settings.uiDensityScale,
        onThemeModeChange = onThemeModeChange,
        onCustomColorChange = onCustomColorChange,
        onDarkBackgroundStyleChange = onDarkBackgroundStyleChange,
        onDynamicColorChange = onDynamicColorChange,
        onThemeColorChange = onThemeColorChange,
        onPaletteStyleChange = onThemePaletteStyleChange,
        onThemeColorSpecChange = onThemeColorSpecChange,
        onBottomNavStyleChange = onBottomNavStyleChange,
        onBottomNavModeChange = onBottomNavModeChange,
        onBottomNavGlassEnabledChange = onBottomNavGlassEnabledChange,
        onBottomNavBlurRadiusChange = onBottomNavBlurRadiusChange,
        onTopAppBarBlurStyleChange = onTopAppBarBlurStyleChange,
        onUiDensityScaleChange = onUiDensityScaleChange,
        onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
        onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
    )

    val languageItems = appLanguageSettingsCardItems(
        appUiLanguageTag = settings.appUiLanguageTag,
        onAppUiLanguageChange = onAppUiLanguageChange,
    )

    MiuixListScaffold(
        title = stringResource(R.string.interaction_appearance_settings_title),
        pageHint = stringResource(R.string.interaction_appearance_settings_entry_desc),
        showPageHintCard = true,
        navigationIcon = { MiuixBackNavigationIcon(onBack) },
    ) {
        item(key = "general_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_general),
                modifier = Modifier
                    .fillMaxWidth()
                    ,
            )
        }
        groupedCardItems(
            keyPrefix = "interaction_appearance_general",
            items = languageItems,
        )

        item(key = "theme_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_theme_appearance),
                modifier = Modifier
                    .fillMaxWidth()
                    ,
            )
        }
        groupedCardItems(
            keyPrefix = "interaction_appearance_theme",
            items = themeAppearanceItems,
        )

        item(key = "interaction_feedback_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_interaction_feedback),
                modifier = Modifier
                    .fillMaxWidth()
                    ,
            )
        }
        MiuixListSettingsCard(
            keyPrefix = "interaction-haptic",
            items = listOf(
                settingsCardScopeItem("haptic") {
                    SettingExpandableSwitchRow(
                        title = stringResource(R.string.haptic_enabled),
                        checked = settings.hapticEnabled,
                        enabled = true,
                        onCheckedChange = onHapticEnabledChange,
                    ) {
                        SettingsSliderRow(
                            title = stringResource(R.string.haptic_strength),
                            value = settings.hapticStrengthLevel.toFloat(),
                            valueRange = 0f..2f,
                            steps = 1,
                            enabled = true,
                            label = hapticFormatLabel(settings.hapticStrengthLevel.toFloat()),
                            formatLabel = hapticFormatLabel,
                            commitOnFinish = true,
                            triggersLayoutPreview = true,
                            onLayoutPreviewValueChange = { level ->
                                HapticHelper.preview(
                                    view,
                                    AppSettings(
                                        hapticEnabled = true,
                                        hapticStrengthLevel = level.roundToInt(),
                                    ),
                                )
                            },
                            onValueChange = { onHapticStrengthChange(it.roundToInt()) },
                        )
                    }
                },
            ),
        )

        item(key = "navigation_back_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_navigation_back),
                modifier = Modifier
                    .fillMaxWidth()
                    ,
            )
        }

        MiuixListSettingsCard(
            keyPrefix = "interaction-nav-back",
            items = buildList {
                add(
                    settingsCardScopeItem("swipe-dismiss") {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_swipe_dismiss),
                            subtitle = stringResource(R.string.settings_swipe_dismiss_summary),
                            checked = settings.swipeDismissEnabled,
                            enabled = true,
                            onCheckedChange = onSwipeDismissEnabledChange,
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(
                        settingsCardScopeItem("predictive-back") {
                            SettingSwitchRow(
                                title = stringResource(R.string.settings_predictive_back),
                                subtitle = stringResource(R.string.settings_predictive_back_summary),
                                checked = settings.predictiveBackEnabled,
                                enabled = true,
                                onCheckedChange = onPredictiveBackEnabledChange,
                            )
                        },
                    )
                }
            },
        )

    }
}

@Composable
fun InteractionAppearanceEntryCard(
    onClick: () -> Unit,
) {
    SettingsCardScopeContent {
        SettingNavigationRow(
            icon = { label ->
                androidx.compose.material3.Icon(
                    HomeLeadingIcons.themeMode(outlined = true),
                    contentDescription = label,
                )
            },
            title = stringResource(R.string.interaction_appearance_settings_title),
            subtitle = stringResource(R.string.interaction_appearance_settings_entry_desc),
            onClick = onClick,
        )
    }
}
