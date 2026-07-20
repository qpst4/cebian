package com.slideindex.app.gesture.executor

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.service.ShellCommandPanelTrampoline
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.shell.ShellCommand

internal class ActionExecutorOverlayPanels(
    private val context: Context,
    private val onShellCommandsPersist: ((List<ShellCommand>) -> Unit)?,
) {
    /** Edge-gesture overlay panels (index, quick launcher, task switcher, volume/brightness bars). */
    fun showEdgeHostedPanel(action: GestureAction, anchorRawY: Float?): Boolean {
        val y = anchorRawY ?: screenCenterY()
        return SlideIndexAccessibilityService.dispatchExternalGestureAction(action, y)
    }

    fun showStandaloneOverlay(
        anchorRawY: Float?,
        show: (anchorY: Float) -> Boolean,
    ): Boolean = show(anchorRawY ?: screenCenterY())

    fun openShellCommandPanelStandalone(): Boolean {
        if (ShellCommandPanelTrampoline.isActive()) return true
        val persist = onShellCommandsPersist ?: return false
        return runCatching {
            ShellCommandPanelTrampoline.launch(
                context = context,
                continuousPick = false,
                onPrepare = {},
                onDismiss = {},
                onPersist = persist,
            )
            true
        }.getOrDefault(false)
    }

    private fun screenCenterY(): Float =
        context.resources.displayMetrics.heightPixels / 2f

    fun showSearchPanel(
        context: Context,
        settings: com.slideindex.app.settings.AppSettings,
        side: com.slideindex.app.overlay.PanelSide?
    ): Boolean {
        return com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow.show(context)
    }
}
