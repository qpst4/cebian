package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelDownloadStep
import com.slideindex.app.ocr.OcrModelEntry
import com.slideindex.app.nativeengine.NativeEnginePackVersionState
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OcrModelSettingsScreen(
    settings: AppSettings,
    catalogModels: List<OcrModelEntry>,
    installedModelIds: Set<String>,
    downloadState: OcrModelDownloadState?,
    ocrEngineInstalled: Boolean,
    ocrEngineSizeBytes: Long,
    ocrEngineVersionState: NativeEnginePackVersionState? = null,
    onBack: () -> Unit,
    onSelectModel: (String) -> Unit,
    onClearSelectedModel: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDeleteOcrEngine: () -> Unit,
    onOpenEngineManagement: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    val modelsSectionTitle = stringResource(R.string.ocr_models_section_available)
    val modelsHint = stringResource(R.string.ocr_models_hint)
    val clearSelectionText = stringResource(R.string.ocr_models_clear_selection)

    val downloadHeaderCard = settingsCardItems(
        settings.ocrDownloadWifiOnly,
        ocrEngineInstalled,
        downloadState,
    ) {
        SettingSwitchRow(
            title = stringResource(R.string.ocr_download_wifi_only),
            subtitle = stringResource(R.string.ocr_download_wifi_only_desc),
            checked = settings.ocrDownloadWifiOnly,
            enabled = true,
            onCheckedChange = onWifiOnlyChange,
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.ocr_models_title),
        subtitle = stringResource(R.string.ocr_models_subtitle),
        onBack = onBack,
    ) {
        LazySettingsItem(key = "ocr-download-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                downloadHeaderCard.RenderRows()

                NativeEnginePackStatusBanner(
                    title = stringResource(R.string.native_engine_pack_ocr),
                    installed = ocrEngineInstalled,
                    sizeBytes = ocrEngineSizeBytes,
                    installedRevision = ocrEngineVersionState?.installedRevision,
                    installedDisplayVersion = ocrEngineVersionState?.installedDisplayVersion,
                    latestRevision = ocrEngineVersionState?.latestRevision ?: 1,
                    latestDisplayVersion = ocrEngineVersionState?.latestDisplayVersion,
                    updateAvailable = ocrEngineVersionState?.updateAvailable == true,
                    onManage = onOpenEngineManagement,
                    onDelete = if (ocrEngineInstalled) onDeleteOcrEngine else null,
                )

                downloadState?.let { state ->
                    if (state.phase != OcrModelDownloadPhase.READY) {
                        OcrModelDownloadProgressCard(state = state)
                    }
                }
            }
        }

        settingsLazySmallTitle(key = "ocr-models-section", title = modelsSectionTitle, sectionTop = true)

        groupedCardItems(
            keyPrefix = "ocr-models",
            items = catalogModels.map { model ->
                com.slideindex.app.ui.miuix.CardItem("model-${model.id}") {
                    OcrModelRow(
                        model = model,
                        installed = model.id in installedModelIds,
                        selected = settings.floatBallOcrModelId == model.id,
                        downloading = downloadState?.modelId == model.id &&
                            downloadState.phase != OcrModelDownloadPhase.READY &&
                            downloadState.phase != OcrModelDownloadPhase.FAILED &&
                            downloadState.phase != OcrModelDownloadPhase.CANCELLED,
                        onSelect = { onSelectModel(model.id) },
                        onDownload = { onDownloadModel(model.id) },
                        onDelete = { onDeleteModel(model.id) },
                    )
                }
            },
        )

        if (settings.floatBallOcrModelId.isNotBlank()) {
            LazySettingsItem(key = "ocr-clear-selection") {
                TextButton(
                    text = clearSelectionText,
                    onClick = onClearSelectedModel,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        settingsLazyHint(
            key = "ocr-models-hint",
            text = modelsHint,
        )
    }
}

@Composable
private fun OcrModelDownloadProgressCard(state: OcrModelDownloadState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val fraction = state.progress
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = ocrDownloadProgressLabel(state),
                style = MiuixTheme.textStyles.body1,
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
    val rowClickableModifier = if (installed) {
        Modifier.clickable { onSelect() }
    } else if (!downloading) {
        Modifier.clickable { onDownload() }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowClickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = installed,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ocrModelDisplayName(model.id),
                style = MiuixTheme.textStyles.title4,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.ocr_model_meta,
                    formatMegabytes(model.totalDownloadBytes),
                    ocrModelDisplayDescription(model.id),
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Text(
                text = when {
                    selected -> stringResource(R.string.ocr_model_status_selected)
                    installed -> stringResource(R.string.ocr_model_status_installed)
                    downloading -> stringResource(R.string.ocr_model_status_downloading)
                    else -> stringResource(R.string.ocr_model_status_not_installed)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        if (installed) {
            Button(
                onClick = onDelete,
            ) {
                Text(stringResource(R.string.ocr_model_delete))
            }
        } else {
            Button(
                onClick = onDownload,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColorsPrimary(),
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
    val stepPrefix = when (state.step) {
        OcrModelDownloadStep.ENGINE ->
            stringResource(R.string.ocr_download_step_engine, state.stepIndex, state.stepCount)
        OcrModelDownloadStep.MODEL ->
            stringResource(R.string.ocr_download_step_model, state.stepIndex, state.stepCount)
    }
    return when (state.phase) {
        OcrModelDownloadPhase.DOWNLOADING -> {
            val downloaded = formatMegabytes(state.bytesDownloaded)
            val total = state.totalBytes?.let(::formatMegabytes)
            val detail = if (total != null) {
                stringResource(R.string.ocr_download_progress_bytes, modelName, downloaded, total)
            } else {
                stringResource(R.string.ocr_download_progress_indeterminate, modelName, downloaded)
            }
            if (state.stepCount > 1) "$stepPrefix $detail" else detail
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
