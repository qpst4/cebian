package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R

fun formatNativeEnginePackVersion(revision: Int, displayVersion: String?): String =
    if (displayVersion.isNullOrBlank()) {
        "v$revision"
    } else {
        "v$revision · $displayVersion"
    }

@Composable
fun nativeEnginePackVersionStatusText(
    installed: Boolean,
    installedRevision: Int?,
    installedDisplayVersion: String?,
    latestRevision: Int,
    latestDisplayVersion: String?,
    updateAvailable: Boolean,
): String {
    return when {
        installed && updateAvailable -> {
            val installedLabel = formatNativeEnginePackVersion(
                revision = installedRevision ?: latestRevision,
                displayVersion = installedDisplayVersion,
            )
            val latestLabel = formatNativeEnginePackVersion(
                revision = latestRevision,
                displayVersion = latestDisplayVersion,
            )
            stringResource(
                R.string.native_engine_pack_update_available,
                installedLabel,
                latestLabel,
            )
        }
        installed -> {
            stringResource(
                R.string.native_engine_pack_installed_version,
                formatNativeEnginePackVersion(
                    revision = installedRevision ?: latestRevision,
                    displayVersion = installedDisplayVersion ?: latestDisplayVersion,
                ),
            )
        }
        else -> {
            stringResource(
                R.string.native_engine_pack_latest_version,
                formatNativeEnginePackVersion(
                    revision = latestRevision,
                    displayVersion = latestDisplayVersion,
                ),
            )
        }
    }
}
