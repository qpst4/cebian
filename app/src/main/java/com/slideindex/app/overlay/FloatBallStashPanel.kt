package com.slideindex.app.overlay

import androidx.compose.runtime.mutableIntStateOf
import com.slideindex.app.overlay.history.HistoryPanelScreen
import com.slideindex.app.ui.theme.SlideIndexTheme

enum class StashPanelInitialTab {
    Stash,
    Clipboard,
}

/**
 * Stash / clipboard side panel. Window lifecycle is handled by [OverlaySidePanelHost].
 */
object FloatBallStashPanel {
    private val sideHost = OverlaySidePanelHost(TAG)

    private var pendingInitialTab: HistoryFloatingTab = HistoryFloatingTab.Stash
    private val requestedTabOrdinal = mutableIntStateOf(HistoryFloatingTab.Stash.ordinal)

    val isShowing: Boolean get() = sideHost.isShowing

    fun show(
        context: android.content.Context,
        initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash,
        panelSide: PanelSide? = null,
    ): Boolean {
        pendingInitialTab = initialTab.toHistoryFloatingTab()
        requestedTabOrdinal.intValue = pendingInitialTab.ordinal
        return sideHost.show(
            context = context,
            initialGravityEnd = panelSide.toStashPanelGravityEnd(),
            content = { gravityEnd, onToggleSide, onDismiss ->
                SlideIndexTheme {
                    HistoryPanelScreen(
                        gravityEnd = gravityEnd,
                        onDismiss = onDismiss,
                        onToggleSide = onToggleSide,
                        requestedTabOrdinal = requestedTabOrdinal,
                        onClipboardSearchFocusChanged = { active ->
                            updateWindowInputActiveForClipboard(active)
                        },
                    )
                }
            },
        )
    }

    fun dismiss() {
        sideHost.dismiss()
    }

    fun destroy() {
        sideHost.destroy()
        pendingInitialTab = HistoryFloatingTab.Stash
        requestedTabOrdinal.intValue = HistoryFloatingTab.Stash.ordinal
    }

    fun updateWindowInputActiveForClipboard(active: Boolean) {
        sideHost.setInputActive(active, requestRootFocus = false)
    }

    private fun PanelSide?.toStashPanelGravityEnd(): Boolean = when (this) {
        PanelSide.LEFT -> false
        PanelSide.RIGHT -> true
        PanelSide.BOTTOM, PanelSide.TOP, null -> true
    }

    private fun StashPanelInitialTab.toHistoryFloatingTab(): HistoryFloatingTab = when (this) {
        StashPanelInitialTab.Stash -> HistoryFloatingTab.Stash
        StashPanelInitialTab.Clipboard -> HistoryFloatingTab.Clipboard
    }

    private enum class HistoryFloatingTab {
        Stash,
        Clipboard,
    }

    private const val TAG = "FloatBallStashPanel"
}
