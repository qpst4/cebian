package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import com.slideindex.app.ui.HomeLeadingIcons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.AggregatedImageSearchEnginePreferencesStore
import com.slideindex.app.ui.settings.components.SettingsCardScope
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenGestureSettings: () -> Unit,
    onOpenPickSettings: () -> Unit,
    onOpenTranslationSettings: () -> Unit,
    onOpenSearchEngineSettings: () -> Unit,
    onOpenImageSearchEngineSettings: () -> Unit,
) {
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_settings_title),
        onBack = onBack,
    ) {
        MiuixSmallTitle(stringResource(R.string.settings_section_features), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.float_ball_enabled),
                subtitle = if (accessibilityGranted) {
                    stringResource(R.string.float_ball_enabled_desc)
                } else {
                    stringResource(R.string.float_ball_permission_required)
                },
                checked = settings.floatBallEnabled,
                enabled = accessibilityGranted,
                onCheckedChange = onEnabledChange,
            )
        }

        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Palette, contentDescription = label) },
                title = stringResource(R.string.float_ball_appearance_settings_title),
                subtitle = stringResource(
                    R.string.float_ball_appearance_settings_summary,
                    settings.floatBallSizeDp,
                    (settings.floatBallOpacity * 100).roundToInt(),
                ),
                enabled = controlsEnabled,
                onClick = onOpenAppearanceSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(HomeLeadingIcons.gesture(true), contentDescription = label) },
                title = stringResource(R.string.float_ball_gesture_settings_title),
                subtitle = stringResource(R.string.float_ball_gesture_settings_summary),
                enabled = controlsEnabled,
                onClick = onOpenGestureSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.TextFields, contentDescription = label) },
                title = stringResource(R.string.float_ball_pick_settings_title),
                subtitle = stringResource(
                    R.string.float_ball_pick_settings_summary,
                    settings.floatBallPickOffsetDp,
                    if (settings.floatBallOcrFallbackEnabled) {
                        stringResource(R.string.float_ball_ocr_fallback_on)
                    } else {
                        stringResource(R.string.float_ball_ocr_fallback_off)
                    },
                ),
                enabled = controlsEnabled,
                onClick = onOpenPickSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Translate, contentDescription = label) },
                title = stringResource(R.string.float_ball_translation_settings_title),
                subtitle = floatBallTranslationSubtitle(settings),
                enabled = controlsEnabled,
                onClick = onOpenTranslationSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Search, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_title),
                subtitle = pluralStringResource(
                    R.plurals.search_engine_settings_summary,
                    SearchEngineStore.textPickPanelEngines(settings.searchEngines).size,
                    SearchEngineStore.textPickPanelEngines(settings.searchEngines).size,
                ),
                enabled = controlsEnabled,
                onClick = onOpenSearchEngineSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.ImageSearch, contentDescription = label) },
                title = stringResource(R.string.image_search_engine_settings_title),
                subtitle = pluralStringResource(
                    R.plurals.image_search_engine_settings_summary,
                    SearchEngineStore.imageSharePanelEngines(settings.searchEngines).size,
                    SearchEngineStore.imageSharePanelEngines(settings.searchEngines).size,
                    AggregatedImageSearchEnginePreferencesStore.panelConfigs(
                        settings.aggregatedImageSearchEngines,
                    ).size,
                ),
                enabled = controlsEnabled,
                onClick = onOpenImageSearchEngineSettings,
            )
        }
    }
}

@Composable
private fun floatBallTranslationSubtitle(settings: AppSettings): String {
    val engine = when (settings.floatBallTranslateEngine) {
        com.slideindex.app.settings.FloatBallTranslateEngine.GOOGLE ->
            stringResource(R.string.float_ball_translate_engine_google)
        com.slideindex.app.settings.FloatBallTranslateEngine.ML_KIT ->
            stringResource(R.string.float_ball_translate_engine_mlkit)
    }
    val mode = if (settings.floatBallInstantTranslate) {
        stringResource(R.string.float_ball_instant_translate_on)
    } else {
        stringResource(R.string.float_ball_instant_translate_off)
    }
    return "$engine · $mode"
}

@Composable
fun SettingsCardScope.FloatBallEntryCard(
    floatBallEnabled: Boolean,
    floatBallSizeDp: Float,
    floatBallOpacity: Float,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = when {
        !enabled -> stringResource(R.string.float_ball_entry_desc)
        floatBallEnabled -> stringResource(
            R.string.float_ball_entry_summary_enabled,
            floatBallSizeDp,
            (floatBallOpacity * 100).roundToInt(),
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
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.FloatBallEntryCard(
    settings: AppSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = when {
        !enabled -> stringResource(R.string.float_ball_entry_desc)
        settings.floatBallEnabled -> stringResource(
            R.string.float_ball_entry_summary_enabled,
            settings.floatBallSizeDp,
            (settings.floatBallOpacity * 100).roundToInt(),
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
        onClick = onClick,
    )
}
