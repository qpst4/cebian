package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import com.slideindex.app.overlay.history.HistoryPanelScreen
import com.slideindex.app.overlay.history.StashPanelLaunchState
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

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
    /** 与 [StashPanelLaunchState.epoch] 同步，供 Compose 订阅。 */
    private val searchBootstrapEpoch = mutableIntStateOf(0)

    val isShowing: Boolean get() = sideHost.isShowing

    /**
     * Attaches the panel window below float-ball chrome so opening it later avoids z-order bumps.
     */
    fun warmUpBelowChrome(context: android.content.Context) {
        if (sideHost.isAttached) return
        sideHost.attachHidden(
            context = context,
            initialGravityEnd = true,
            content = ::panelContent,
        )
    }

    fun show(
        context: android.content.Context,
        initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash,
        panelSide: PanelSide? = null,
        searchQuery: String? = null,
    ): Boolean {
        pendingInitialTab = initialTab.toHistoryFloatingTab()
        requestedTabOrdinal.intValue = pendingInitialTab.ordinal
        val q = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        // 先 show（把 targetVisible=true），再写入 pending/epoch，
        // 避免退出动画中的旧组合在 visible=false 时抢先 consume。
        val shown = sideHost.show(
            context = context,
            initialGravityEnd = panelSide.toStashPanelGravityEnd(),
            content = ::panelContent,
        )
        if (shown && q != null) {
            StashPanelLaunchState.setPendingSearch(
                tabOrdinal = pendingInitialTab.ordinal,
                query = q,
            )
            searchBootstrapEpoch.intValue = StashPanelLaunchState.epoch
        }
        return shown
    }

    fun dismiss() {
        sideHost.dismiss()
    }

    fun destroy() {
        sideHost.destroy()
        pendingInitialTab = HistoryFloatingTab.Stash
        requestedTabOrdinal.intValue = HistoryFloatingTab.Stash.ordinal
        StashPanelLaunchState.clearPendingSearch()
        searchBootstrapEpoch.intValue = 0
        sideHost.setPanelBackInterceptor(null)
    }

    fun updateWindowInputActiveForClipboard(active: Boolean) {
        sideHost.setClipboardInputActive(active)
    }

    fun setDragHidden(hidden: Boolean) {
        sideHost.setDragHidden(hidden)
    }

    @Composable
    private fun panelContent(
        gravityEnd: Boolean,
        panelTargetVisible: Boolean,
        onToggleSide: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        OverlayAwareModuleTheme {
            HistoryPanelScreen(
                gravityEnd = gravityEnd,
                panelTargetVisible = panelTargetVisible,
                onDismiss = onDismiss,
                onToggleSide = onToggleSide,
                requestedTabOrdinal = requestedTabOrdinal,
                searchBootstrapEpoch = searchBootstrapEpoch,
                onSearchFocusChanged = { active ->
                    updateWindowInputActiveForClipboard(active)
                },
                onRegisterBackInterceptor = { interceptor ->
                    sideHost.setPanelBackInterceptor(interceptor)
                },
            )
            DisposableEffect(Unit) {
                onDispose {
                    sideHost.setPanelBackInterceptor(null)
                }
            }
        }
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
