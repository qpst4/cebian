package com.slideindex.app.ui.quicklauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect

data class QuickLauncherEmbedParentConfirm(
    val enabled: Boolean,
    val onConfirm: () -> Unit,
)

@Composable
fun ReportQuickLauncherEmbedParentConfirm(
    enabled: Boolean,
    onConfirm: () -> Unit,
    onReport: (QuickLauncherEmbedParentConfirm?) -> Unit,
) {
    SideEffect(enabled) {
        onReport(QuickLauncherEmbedParentConfirm(enabled, onConfirm))
    }
    DisposableEffect(onReport) {
        onDispose { onReport(null) }
    }
}
