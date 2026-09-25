package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import com.slideindex.app.ui.HomeLeadingIcons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDragPasteEnabledChange: (Boolean) -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenGestureSettings: () -> Unit,
    onOpenPickHandfeelSettings: () -> Unit,
) {
    val sectionFeaturesTitle = stringResource(R.string.settings_section_features)
    val sectionPickTitle = stringResource(R.string.float_ball_pick_section_operation)

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_settings_title),
        pageHint = stringResource(R.string.float_ball_entry_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "section-features",
            title = sectionFeaturesTitle
        )
        groupedCardItems(
            keyPrefix = "float-ball-enabled",
            items = buildList {
                add(
                    settingsCardScopeItem("float-ball-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_enabled),
                            subtitle = if (accessibilityGranted) {
                                stringResource(R.string.float_ball_enabled_desc)
                            } else {
                                stringResource(R.string.float_ball_permission_required)
                            },
                            checked = settings.floatBallEnabled,
                            enabled = accessibilityGranted,
                            onCheckedChange = onEnabledChange
                        )
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "float-ball-navigation",
            items = buildList {
                add(
                    settingsCardScopeItem("appearance") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Palette, contentDescription = label) },
                            title = stringResource(R.string.float_ball_appearance_settings_title),
                            subtitle = stringResource(
                                R.string.float_ball_appearance_settings_summary,
                                settings.floatBallSizeDp,
                                (settings.floatBallOpacity * 100).roundToInt()
                            ),
                            enabled = true,
                            onClick = onOpenAppearanceSettings
                        )
                    }
                )
                add(
                    settingsCardScopeItem("gesture") {
                        SettingNavigationRow(
                            icon = { label -> Icon(HomeLeadingIcons.gesture(true), contentDescription = label) },
                            title = stringResource(R.string.float_ball_gesture_settings_title),
                            subtitle = stringResource(R.string.float_ball_gesture_settings_summary),
                            enabled = true,
                            onClick = onOpenGestureSettings
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-pick",
            title = sectionPickTitle
        )
        groupedCardItems(
            keyPrefix = "float-ball-pick",
            items = buildList {
                add(
                    settingsCardScopeItem("drag-paste") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_drag_paste_enabled),
                            subtitle = stringResource(R.string.float_ball_drag_paste_enabled_desc),
                            checked = settings.floatBallDragPasteEnabled,
                            enabled = accessibilityGranted,
                            onCheckedChange = onDragPasteEnabledChange,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("pick-handfeel") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.TouchApp, contentDescription = label) },
                            title = stringResource(R.string.float_ball_pick_word_config_title),
                            subtitle = stringResource(R.string.float_ball_pick_word_config_moved_desc),
                            enabled = accessibilityGranted,
                            onClick = onOpenPickHandfeelSettings,
                        )
                    }
                )
            }
        )
    }
}

@Composable
internal fun pickPanelTranslationNavSubtitle(settings: AppSettings): String {
    val engine = when (settings.floatBallTranslateEngine) {
        com.slideindex.app.settings.FloatBallTranslateEngine.GOOGLE ->
            stringResource(R.string.float_ball_translate_engine_google)
        com.slideindex.app.settings.FloatBallTranslateEngine.ML_KIT ->
            stringResource(R.string.float_ball_translate_engine_mlkit)
        com.slideindex.app.settings.FloatBallTranslateEngine.CLOUD_LLM ->
            stringResource(R.string.float_ball_translate_engine_cloud)
    }
    val mode = if (settings.floatBallInstantTranslate) {
        stringResource(R.string.float_ball_instant_translate_on)
    } else {
        stringResource(R.string.float_ball_instant_translate_off)
    }
    return "$engine · $mode"
}

@Composable
fun SettingsCardScope.PickSettingsEntryCard(
    accessibilityGranted: Boolean,
    ocrFallbackEnabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = when {
        !accessibilityGranted -> stringResource(R.string.pick_settings_page_hint)
        else -> stringResource(
            R.string.float_ball_pick_settings_summary,
            if (ocrFallbackEnabled) {
                stringResource(R.string.float_ball_ocr_fallback_on)
            } else {
                stringResource(R.string.float_ball_ocr_fallback_off)
            },
        )
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(Icons.Outlined.TextFields, contentDescription = label)
        },
        title = stringResource(R.string.float_ball_pick_settings_title),
        subtitle = subtitle,
        enabled = accessibilityGranted,
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.FloatBallEntryCard(
    floatBallEnabled: Boolean,
    floatBallSizeDp: Float,
    floatBallOpacity: Float,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = when {
        !enabled -> stringResource(R.string.float_ball_entry_desc)
        floatBallEnabled -> stringResource(
            R.string.float_ball_entry_summary_enabled,
            floatBallSizeDp,
            (floatBallOpacity * 100).roundToInt()
        )
        else -> stringResource(R.string.float_ball_entry_summary_disabled)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.floatBall(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.float_ball_settings_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.FloatBallEntryCard(
    settings: AppSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = when {
        !enabled -> stringResource(R.string.float_ball_entry_desc)
        settings.floatBallEnabled -> stringResource(
            R.string.float_ball_entry_summary_enabled,
            settings.floatBallSizeDp,
            (settings.floatBallOpacity * 100).roundToInt()
        )
        else -> stringResource(R.string.float_ball_entry_summary_disabled)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.floatBall(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.float_ball_settings_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick
    )
}
