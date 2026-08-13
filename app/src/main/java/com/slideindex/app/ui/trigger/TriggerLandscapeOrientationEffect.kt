package com.slideindex.app.ui.trigger

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 横屏触钮子页保持横屏方向。
 * 不在 onDispose 中解除方向，由 [TriggerSettingsLandscapeSession.setActive] 统一管理。
 */
@Composable
fun TriggerLandscapeOrientationEffect(landscapeEditing: Boolean) {
    if (!landscapeEditing) return

    val activity = LocalActivity.current ?: return

    DisposableEffect(Unit) {
        TriggerSettingsLandscapeSession.lockLandscapeOrientation(activity)
        onDispose { }
    }
}
