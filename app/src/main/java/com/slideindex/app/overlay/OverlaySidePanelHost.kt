package com.slideindex.app.overlay

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Side-sliding overlay panel host (stash / clipboard history). Wraps [OverlayFullScreenPanelHost]
 * with horizontal enter/exit animation and optional left/right gravity.
 */
class OverlaySidePanelHost(
    private val tag: String = "OverlaySidePanelHost",
) {
    private val panelHost = OverlayFullScreenPanelHost(
        tag = tag,
        onScreenOff = { dismiss() },
    )

    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var gravityEndState: MutableState<Boolean>? = null

    val isShowing: Boolean get() = panelHost.isAttached

    fun show(
        context: Context,
        content: @Composable (
            gravityEnd: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit,
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
        onShown: () -> Unit = { FloatBallOverlay.bringChromeAbovePanels() },
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            panelHost.runOnMain {
                result = show(context, content, onAccessibilityRequired, onHostContext, onShown)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        if (panelHost.isAttached) {
            panelHost.setViewVisible(true)
            panelHost.composeView?.post {
                panelVisibilityState?.targetState = true
            }
            onShown()
            return true
        }

        if (!onAccessibilityRequired()) {
            Log.w(tag, "show: accessibility service not enabled")
            return false
        }
        val hostContext = onHostContext() ?: run {
            Log.w(tag, "show: overlay host not connected")
            return false
        }

        val gravityEndHolder = mutableStateOf(true)
        gravityEndState = gravityEndHolder
        val visibleState = MutableTransitionState(false)
        panelVisibilityState = visibleState

        val attached = panelHost.ensureWindow(hostContext, focusable = false) {
            val gravityEnd by gravityEndHolder
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInHorizontally(
                    initialOffsetX = { if (gravityEnd) it else -it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                ) + fadeIn(animationSpec = tween(250)),
                exit = slideOutHorizontally(
                    targetOffsetX = { if (gravityEnd) it else -it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                ) + fadeOut(animationSpec = tween(250)),
            ) {
                content(
                    gravityEnd,
                    { gravityEndHolder.value = !gravityEndHolder.value },
                    { dismiss() },
                )
            }
        } ?: return false

        panelHost.setViewVisible(true)
        panelHost.composeView?.post {
            visibleState.targetState = true
        }
        onShown()
        return true
    }

    fun dismiss() {
        panelHost.runOnMain {
            if (!panelHost.isAttached) return@runOnMain
            val visibleState = panelVisibilityState
            visibleState?.targetState = false
            val view = panelHost.composeView
            val owner = panelHost.owner
            if (view == null || owner == null || visibleState == null) return@runOnMain
            panelHost.setInputActive(false)
            owner.lifecycleScope.launch(Dispatchers.Main) {
                delay(300)
                if (visibleState.targetState) return@launch
                view.visibility = View.GONE
            }
        }
    }

    fun destroy() {
        panelHost.runOnMain {
            panelHost.destroy()
            panelVisibilityState = null
            gravityEndState = null
        }
    }

    fun setInputActive(active: Boolean) {
        panelHost.setInputActive(active)
    }
}
