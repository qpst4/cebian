package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateLanguageCatalog
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@Composable
fun FloatBallTranslationSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onInstantTranslateChange: (Boolean) -> Unit,
    onEngineChange: (FloatBallTranslateEngine) -> Unit,
    onTargetLangChange: (String) -> Unit,
    onOpenMlKitModels: () -> Unit,
) {
    val engineEntries = FloatBallTranslateEngine.entries
    val langOptions = TranslateLanguageCatalog.options
    val langIndex = langOptions.indexOfFirst {
        it.code.equals(settings.floatBallTranslateTargetLang, ignoreCase = true)
    }.coerceAtLeast(0)

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_translation_settings_title),
        onBack = onBack,
    ) {
        SettingsCard {
            SettingDropdownRow(
                icon = { label -> Icon(Icons.Default.Translate, contentDescription = label) },
                title = stringResource(R.string.float_ball_translate_engine),
                items = engineEntries.map { translateEngineLabel(it) },
                selectedIndex = engineEntries.indexOf(settings.floatBallTranslateEngine).coerceAtLeast(0),
                onSelectedIndexChange = { onEngineChange(engineEntries[it]) },
            )
            SettingDropdownRow(
                icon = { label -> Icon(Icons.Default.Translate, contentDescription = label) },
                title = stringResource(R.string.float_ball_translate_target_lang),
                items = langOptions.map { it.displayName },
                selectedIndex = langIndex,
                onSelectedIndexChange = { onTargetLangChange(langOptions[it].code) },
            )
            SettingSwitchRow(
                title = stringResource(R.string.float_ball_instant_translate),
                subtitle = stringResource(R.string.float_ball_instant_translate_desc),
                checked = settings.floatBallInstantTranslate,
                enabled = true,
                onCheckedChange = onInstantTranslateChange,
            )
        }

        if (settings.floatBallTranslateEngine == FloatBallTranslateEngine.ML_KIT) {
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Download, contentDescription = label) },
                    title = stringResource(R.string.float_ball_translate_mlkit_models),
                    subtitle = stringResource(R.string.float_ball_translate_mlkit_models_desc),
                    enabled = true,
                    onClick = onOpenMlKitModels,
                )
            }
        }
    }
}

@Composable
private fun translateEngineLabel(engine: FloatBallTranslateEngine): String = when (engine) {
    FloatBallTranslateEngine.GOOGLE -> stringResource(R.string.float_ball_translate_engine_google)
    FloatBallTranslateEngine.ML_KIT -> stringResource(R.string.float_ball_translate_engine_mlkit)
}
