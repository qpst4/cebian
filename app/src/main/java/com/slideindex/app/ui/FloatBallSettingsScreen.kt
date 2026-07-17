package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
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
) {
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_settings_title),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.float_ball_settings_desc))

        SettingsSectionTitle(stringResource(R.string.settings_section_features))
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
                icon = { label -> Icon(Icons.Default.Palette, contentDescription = label) },
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
                icon = { label -> Icon(Icons.Default.Gesture, contentDescription = label) },
                title = stringResource(R.string.float_ball_gesture_settings_title),
                subtitle = stringResource(R.string.float_ball_gesture_settings_summary),
                enabled = controlsEnabled,
                onClick = onOpenGestureSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.TextFields, contentDescription = label) },
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
                icon = { label -> Icon(Icons.Default.Translate, contentDescription = label) },
                title = stringResource(R.string.float_ball_translation_settings_title),
                subtitle = floatBallTranslationSubtitle(settings),
                enabled = controlsEnabled,
                onClick = onOpenTranslationSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Search, contentDescription = label) },
                title = stringResource(R.string.search_engine_settings_title),
                subtitle = pluralStringResource(
                    R.plurals.search_engine_settings_summary,
                    SearchEngineStore.textPickPanelEngines(settings.searchEngines).size,
                    SearchEngineStore.textPickPanelEngines(settings.searchEngines).size,
                ),
                enabled = controlsEnabled,
                onClick = onOpenSearchEngineSettings,
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
fun FloatBallEntryCard(
    settings: AppSettings,
    enabled: Boolean,
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
        icon = { label -> Icon(Icons.Default.RadioButtonChecked, contentDescription = label) },
        title = stringResource(R.string.float_ball_settings_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
    )
}
