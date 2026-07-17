package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.translate.TranslateDownloadPhase
import com.slideindex.app.translate.TranslateDownloadState
import com.slideindex.app.translate.TranslateLanguageCatalog
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSectionTitle
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun TranslateModelSettingsScreen(
    settings: AppSettings,
    installedLanguageCodes: Set<String>,
    downloadState: TranslateDownloadState?,
    onBack: () -> Unit,
    onDownloadLanguage: (String) -> Unit,
    onDeleteLanguage: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_translate_mlkit_models),
        subtitle = stringResource(R.string.float_ball_translate_mlkit_models_subtitle),
        onBack = onBack,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.ocr_download_wifi_only),
            subtitle = stringResource(R.string.ocr_download_wifi_only_desc),
            checked = settings.ocrDownloadWifiOnly,
            enabled = true,
            onCheckedChange = onWifiOnlyChange,
        )

        downloadState?.let { state ->
            if (state.phase == TranslateDownloadPhase.DOWNLOADING) {
                TranslateDownloadProgressCard(state = state)
            }
        }

        SettingsSectionTitle(stringResource(R.string.float_ball_translate_languages_section))

        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TranslateLanguageCatalog.options.forEachIndexed { index, option ->
                    val rowDownloadState = downloadState?.takeIf { it.languageCode == option.code }
                    TranslateLanguageRow(
                        displayName = option.displayName,
                        installed = option.code in installedLanguageCodes,
                        downloadState = rowDownloadState,
                        onDownload = { onDownloadLanguage(option.code) },
                        onDelete = { onDeleteLanguage(option.code) },
                    )
                    if (index < TranslateLanguageCatalog.options.lastIndex) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslateDownloadProgressCard(state: TranslateDownloadState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val fraction = state.progress
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = translateDownloadProgressLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TranslateLanguageRow(
    displayName: String,
    installed: Boolean,
    downloadState: TranslateDownloadState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloading = downloadState?.phase == TranslateDownloadPhase.DOWNLOADING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when {
                    downloading -> translateDownloadProgressLabel(downloadState)
                    installed -> stringResource(R.string.ocr_model_status_installed)
                    else -> stringResource(R.string.ocr_model_status_not_installed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            downloading -> {
                val fraction = downloadState.progress
                if (fraction != null) {
                    Text(
                        text = "${(fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                }
            }
            installed -> {
                OutlinedButton(onClick = onDelete) {
                    Text(stringResource(R.string.ocr_model_delete))
                }
            }
            else -> {
                Button(onClick = onDownload) {
                    Text(stringResource(R.string.ocr_model_download))
                }
            }
        }
    }
}

@Composable
private fun translateDownloadProgressLabel(state: TranslateDownloadState): String {
    val languageName = TranslateLanguageCatalog.displayName(state.languageCode)
    return when (state.phase) {
        TranslateDownloadPhase.DOWNLOADING -> {
            val downloaded = formatMegabytes(state.bytesDownloaded)
            val total = state.totalBytes?.let(::formatMegabytes)
            if (total != null) {
                stringResource(R.string.ocr_download_progress_bytes, languageName, downloaded, total)
            } else {
                stringResource(R.string.ocr_download_progress_indeterminate, languageName, downloaded)
            }
        }
        TranslateDownloadPhase.FAILED ->
            stringResource(R.string.ocr_download_failed, state.errorMessage.orEmpty())
        TranslateDownloadPhase.CANCELLED ->
            stringResource(R.string.ocr_download_cancelled)
        TranslateDownloadPhase.READY ->
            stringResource(R.string.ocr_download_ready, languageName)
    }
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${mb.roundToInt()} MB"
    }
}
