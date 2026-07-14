package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.slideindex.app.overlay.PanelSide

@Composable
fun TriggerHandlePreviewLifecycle(
    enabled: Boolean,
    side: PanelSide,
    handleId: String,
    onPreviewStart: (PanelSide, String) -> Unit,
    onPreviewStop: () -> Unit = {},
) {
    DisposableEffect(enabled, side, handleId) {
        if (enabled) {
            onPreviewStart(side, handleId)
        }
        onDispose {
            if (enabled) {
                onPreviewStop()
            }
        }
    }
}
