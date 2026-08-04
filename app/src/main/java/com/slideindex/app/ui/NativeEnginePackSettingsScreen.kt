package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import com.slideindex.app.nativeengine.NativeEnginePackEntry
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NativeEnginePackSettingsScreen(
    settings: AppSettings,
    packs: List<NativeEnginePackEntry>,
    installedPackIds: Set<String>,
    downloadState: NativeEnginePackDownloadState?,
    onBack: () -> Unit,
    onDownloadPack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.native_engine_packs_title),
        subtitle = stringResource(R.string.native_engine_packs_subtitle),
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
            if (state.phase != NativeEnginePackDownloadPhase.READY) {
                NativeEnginePackDownloadProgressCard(state = state)
            }
        }

        MiuixSmallTitle(stringResource(R.string.native_engine_packs_section))

        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                packs.forEachIndexed { index, pack ->
                    NativeEnginePackRow(
                        pack = pack,
                        installed = pack.id in installedPackIds,
                        downloading = downloadState?.packId == pack.id &&
                            downloadState.phase != NativeEnginePackDownloadPhase.READY &&
                            downloadState.phase != NativeEnginePackDownloadPhase.FAILED &&
                            downloadState.phase != NativeEnginePackDownloadPhase.CANCELLED,
                        onDownload = { onDownloadPack(pack.id) },
                        onDelete = { onDeletePack(pack.id) },
                    )
                    if (index < packs.lastIndex) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.native_engine_packs_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NativeEnginePackRow(
    pack: NativeEnginePackEntry,
    installed: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = packTitle(pack.id),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.native_engine_pack_size,
                formatMegabytes(pack.sizeBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (installed) {
                stringResource(R.string.native_engine_pack_installed)
            } else {
                stringResource(R.string.native_engine_pack_not_installed)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (installed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!installed) {
                Button(
                    onClick = onDownload,
                    enabled = !downloading,
                ) {
                    Text(stringResource(R.string.native_engine_pack_download))
                }
            } else {
                OutlinedButton(onClick = onDelete, enabled = !downloading) {
                    Text(stringResource(R.string.native_engine_pack_delete))
                }
            }
        }
    }
}

@Composable
private fun NativeEnginePackDownloadProgressCard(state: NativeEnginePackDownloadState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = packTitle(state.packId),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = "${(progress * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun packTitle(packId: String): String = when (packId) {
    NativeEnginePackIds.OCR -> stringResource(R.string.native_engine_pack_ocr)
    NativeEnginePackIds.TRANSLATE -> stringResource(R.string.native_engine_pack_translate)
    NativeEnginePackIds.SEGMENTATION -> stringResource(R.string.native_engine_pack_segmentation)
    else -> packId
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
