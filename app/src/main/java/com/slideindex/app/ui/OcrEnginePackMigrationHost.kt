package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.nativeengine.OcrEnginePackMigrationNotice
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.viewmodel.OcrEnginePackMigrationViewModel

@Composable
fun OcrEnginePackMigrationHost(
    onOpenNativeEnginePacks: () -> Unit,
    viewModel: OcrEnginePackMigrationViewModel = hiltViewModel(),
) {
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkOnLaunch()
    }

    when (val current = notice) {
        is OcrEnginePackMigrationNotice.Upgraded -> {
            val versionLabel = current.displayVersion?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.ocr_engine_migration_version_fallback)
            MiuixConfirmDialog(
                show = true,
                onDismissRequest = viewModel::dismissNotice,
                title = stringResource(R.string.ocr_engine_migration_success_title),
                message = stringResource(R.string.ocr_engine_migration_success_message, versionLabel),
                confirmText = stringResource(R.string.ocr_engine_migration_acknowledge),
                onConfirm = viewModel::dismissNotice,
            )
        }
        is OcrEnginePackMigrationNotice.DownloadRequired -> {
            val versionLabel = current.displayVersion?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.ocr_engine_migration_version_fallback)
            MiuixConfirmDialog(
                show = true,
                onDismissRequest = viewModel::dismissNotice,
                title = stringResource(R.string.ocr_engine_migration_download_title),
                message = stringResource(R.string.ocr_engine_migration_download_message, versionLabel),
                confirmText = stringResource(R.string.ocr_engine_migration_download_action),
                onConfirm = {
                    viewModel.dismissNotice()
                    onOpenNativeEnginePacks()
                },
                dismissText = stringResource(R.string.ocr_engine_migration_later),
            )
        }
        null -> Unit
    }
}
