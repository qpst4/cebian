package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelEntry
import com.slideindex.app.settings.AppSettings
import java.util.Locale
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSectionTitle
import kotlin.math.roundToInt

@Composable
fun OcrModelSettingsScreen(
    settings: AppSettings,
    catalogModels: List<OcrModelEntry>,
    installedModelIds: Set<String>,
    downloadState: OcrModelDownloadState?,
    onBack: () -> Unit,
    onSelectModel: (String) -> Unit,
    onClearSelectedModel: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.ocr_models_title),
        subtitle = stringResource(R.string.ocr_models_subtitle),
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
            if (state.phase != OcrModelDownloadPhase.READY) {
                OcrModelDownloadProgressCard(state = state)
            }
        }

        SettingsSectionTitle(stringResource(R.string.ocr_models_section_available))

        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                catalogModels.forEachIndexed { index, model ->
                    OcrModelRow(
                        model = model,
                        installed = model.id in installedModelIds,
                        selected = settings.floatBallOcrModelId == model.id,
                        downloading = downloadState?.modelId == model.id &&
                            downloadState.phase == OcrModelDownloadPhase.DOWNLOADING,
                        onSelect = { onSelectModel(model.id) },
                        onDownload = { onDownloadModel(model.id) },
                        onDelete = { onDeleteModel(model.id) },
                    )
                    if (index < catalogModels.lastIndex) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }

        if (settings.floatBallOcrModelId.isNotBlank()) {
            TextButton(onClick = onClearSelectedModel) {
                Text(stringResource(R.string.ocr_models_clear_selection))
            }
        }

        Text(
            text = stringResource(R.string.ocr_models_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OcrModelDownloadProgressCard(state: OcrModelDownloadState) {
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
                text = ocrDownloadProgressLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OcrModelRow(
    model: OcrModelEntry,
    installed: Boolean,
    selected: Boolean,
    downloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val selectable = installed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = if (selectable) onSelect else onDownload,
            enabled = selectable,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ocrModelDisplayName(model.id),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.ocr_model_meta,
                    formatMegabytes(model.sizeBytes),
                    ocrModelDisplayDescription(model.id),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when {
                    selected -> stringResource(R.string.ocr_model_status_selected)
                    installed -> stringResource(R.string.ocr_model_status_installed)
                    downloading -> stringResource(R.string.ocr_model_status_downloading)
                    else -> stringResource(R.string.ocr_model_status_not_installed)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (installed) {
            OutlinedButton(onClick = onDelete) {
                Text(stringResource(R.string.ocr_model_delete))
            }
        } else {
            Button(
                onClick = onDownload,
                enabled = !downloading,
            ) {
                Text(stringResource(R.string.ocr_model_download))
            }
        }
    }
}

@Composable
private fun ocrModelDisplayName(modelId: String): String = when (modelId) {
    "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese)
    "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng)
    "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny)
    "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small)
    "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium)
    else -> modelId
}

@Composable
private fun ocrModelDisplayDescription(modelId: String): String = when (modelId) {
    "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese_desc)
    "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng_desc)
    "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny_desc)
    "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small_desc)
    "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium_desc)
    else -> ""
}

@Composable
private fun ocrDownloadProgressLabel(state: OcrModelDownloadState): String {
    val modelName = ocrModelDisplayName(state.modelId)
    return when (state.phase) {
        OcrModelDownloadPhase.DOWNLOADING -> {
            val downloaded = formatMegabytes(state.bytesDownloaded)
            val total = state.totalBytes?.let(::formatMegabytes)
            if (total != null) {
                stringResource(R.string.ocr_download_progress_bytes, modelName, downloaded, total)
            } else {
                stringResource(R.string.ocr_download_progress_indeterminate, modelName, downloaded)
            }
        }
        OcrModelDownloadPhase.VERIFYING ->
            stringResource(R.string.ocr_download_verifying, modelName)
        OcrModelDownloadPhase.FINALIZING ->
            stringResource(R.string.ocr_download_finalizing, modelName)
        OcrModelDownloadPhase.FAILED ->
            stringResource(R.string.ocr_download_failed, state.errorMessage.orEmpty())
        OcrModelDownloadPhase.CANCELLED ->
            stringResource(R.string.ocr_download_cancelled)
        OcrModelDownloadPhase.READY ->
            stringResource(R.string.ocr_download_ready, modelName)
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
