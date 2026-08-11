package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.slideindex.app.overlay.history.HistoryPanelScreen
import com.slideindex.app.overlay.history.HistorySearchBootstrap
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
    private val pendingSearchBootstrap = mutableStateOf<HistorySearchBootstrap?>(null)

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
        if (q != null) {
            // 必须在 show/可见性切换前写入：内容在 AnimatedVisibility 内，进入组合时再消费。
            pendingSearchBootstrap.value = HistorySearchBootstrap(
                tabOrdinal = pendingInitialTab.ordinal,
                query = q,
            )
        }
        return sideHost.show(
            context = context,
            initialGravityEnd = panelSide.toStashPanelGravityEnd(),
            content = ::panelContent,
        )
    }

    fun dismiss() {
        sideHost.dismiss()
    }

    fun destroy() {
        sideHost.destroy()
        pendingInitialTab = HistoryFloatingTab.Stash
        requestedTabOrdinal.intValue = HistoryFloatingTab.Stash.ordinal
        pendingSearchBootstrap.value = null
        sideHost.setPanelBackInterceptor(null)
    }

    fun updateWindowInputActiveForClipboard(active: Boolean) {
        sideHost.setClipboardInputActive(active)
    }

    @Composable
    private fun panelContent(
        gravityEnd: Boolean,
        onToggleSide: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        OverlayAwareModuleTheme {
            HistoryPanelScreen(
                gravityEnd = gravityEnd,
                onDismiss = onDismiss,
                onToggleSide = onToggleSide,
                requestedTabOrdinal = requestedTabOrdinal,
                pendingSearchBootstrap = pendingSearchBootstrap,
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
