package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateLanguageCatalog
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
    var showEngineDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_translation_settings_title),
        onBack = onBack,
    ) {
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Translate, contentDescription = label) },
                title = stringResource(R.string.float_ball_translate_engine),
                subtitle = translateEngineLabel(settings.floatBallTranslateEngine),
                enabled = true,
                onClick = { showEngineDialog = true },
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Translate, contentDescription = label) },
                title = stringResource(R.string.float_ball_translate_target_lang),
                subtitle = TranslateLanguageCatalog.displayName(settings.floatBallTranslateTargetLang),
                enabled = true,
                onClick = { showLanguageDialog = true },
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

    if (showEngineDialog) {
        TranslateEngineDialog(
            selected = settings.floatBallTranslateEngine,
            onDismiss = { showEngineDialog = false },
            onSelect = {
                onEngineChange(it)
                showEngineDialog = false
            },
        )
    }

    if (showLanguageDialog) {
        TranslateTargetLanguageDialog(
            selected = settings.floatBallTranslateTargetLang,
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                onTargetLangChange(it)
                showLanguageDialog = false
            },
        )
    }
}

@Composable
private fun translateEngineLabel(engine: FloatBallTranslateEngine): String = when (engine) {
    FloatBallTranslateEngine.GOOGLE -> stringResource(R.string.float_ball_translate_engine_google)
    FloatBallTranslateEngine.ML_KIT -> stringResource(R.string.float_ball_translate_engine_mlkit)
}

@Composable
private fun TranslateEngineDialog(
    selected: FloatBallTranslateEngine,
    onDismiss: () -> Unit,
    onSelect: (FloatBallTranslateEngine) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.float_ball_translate_engine)) },
        text = {
            Column {
                FloatBallTranslateEngine.entries.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(engine) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = engine == selected,
                            onClick = { onSelect(engine) },
                        )
                        Text(
                            text = translateEngineLabel(engine),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TranslateTargetLanguageDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.float_ball_translate_target_lang)) },
        text = {
            Column {
                TranslateLanguageCatalog.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.code.equals(selected, ignoreCase = true),
                            onClick = { onSelect(option.code) },
                        )
                        Text(
                            text = option.displayName,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
