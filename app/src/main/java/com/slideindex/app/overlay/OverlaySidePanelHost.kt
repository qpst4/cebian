package com.slideindex.app.overlay

import android.content.Context
import android.os.Looper
import android.os.SystemClock
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
) : OverlayPanelVisibility {
    private val panelHost = OverlayFullScreenPanelHost(
        tag = tag,
        layoutParamsFactory = { context, focusable ->
            OverlayPanelLayoutParams.stashClipboardSidePanel(context, focusable)
        },
        onScreenOff = { dismiss() },
        excludeLeftBackEdge = false,
    )

    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var panelTargetVisibleState: MutableState<Boolean>? = null
    private var gravityEndState: MutableState<Boolean>? = null
    private var attachedBelowChrome = false
    private var lastShowAttemptElapsedMs = 0L
    private var clipboardInputActive = false
    private var panelBackInterceptor: (() -> Boolean)? = null
    private var backHandler: OverlayViewBackHandler? = null

    override val isAttached: Boolean get() = panelHost.isAttached

    override val isUserVisible: Boolean
        get() = panelHost.isAttached &&
            panelVisibilityState?.currentState == true &&
            panelHost.isViewVisible()

    /** User-visible panel; use [isAttached] for warm-up / attach guards. */
    val isShowing: Boolean get() = isUserVisible

    /**
     * Pre-attaches the panel window (GONE) so float-ball chrome added later stays on top
     * without remove/add z-order bumps.
     */
    fun attachHidden(
        context: Context,
        initialGravityEnd: Boolean = true,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit,
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
    ): Boolean {
        if (panelHost.isAttached) {
            attachedBelowChrome = true
            return true
        }
        if (!onAccessibilityRequired()) {
            Log.w(tag, "attachHidden: accessibility service not enabled")
            return false
        }
        val hostContext = onHostContext() ?: run {
            Log.w(tag, "attachHidden: overlay host not connected")
            return false
        }
        val attached = attachPanelWindow(
            hostContext = hostContext,
            initialGravityEnd = initialGravityEnd,
            content = content,
        )
        if (!attached) return false
        panelHost.setViewVisible(false)
        attachedBelowChrome = true
        return attached
    }

    fun show(
        context: Context,
        initialGravityEnd: Boolean = true,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit,
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
        onShown: () -> Unit = { FloatBallOverlay.scheduleChromeAbovePanels() },
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            panelHost.runOnMain {
                result = show(
                    context = context,
                    initialGravityEnd = initialGravityEnd,
                    content = content,
                    onAccessibilityRequired = onAccessibilityRequired,
                    onHostContext = onHostContext,
                    onShown = onShown,
                )
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastShowAttemptElapsedMs < SHOW_DEBOUNCE_MS) {
            if (panelHost.isAttached) {
                gravityEndState?.value = initialGravityEnd
                setPanelTargetVisible(true)
                panelHost.setViewVisible(true)
                panelHost.composeView?.post {
                    panelVisibilityState?.targetState = true
                }
                notifyPanelShown(onShown)
                return true
            }
            return false
        }
        lastShowAttemptElapsedMs = now

        if (panelHost.isAttached) {
            gravityEndState?.value = initialGravityEnd
            setPanelTargetVisible(true)
            panelHost.setViewVisible(true)
            panelHost.composeView?.post {
                panelVisibilityState?.targetState = true
            }
            notifyPanelShown(onShown)
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

        val attached = attachPanelWindow(
            hostContext = hostContext,
            initialGravityEnd = initialGravityEnd,
            content = content,
        )
        if (!attached) return false
        attachedBelowChrome = false

        setPanelTargetVisible(true)
        panelHost.setViewVisible(true)
        panelHost.composeView?.post {
            panelVisibilityState?.targetState = true
        }
        notifyPanelShown(onShown)
        return attached
    }

    private fun setPanelTargetVisible(visible: Boolean) {
        panelTargetVisibleState?.value = visible
    }

    private fun attachPanelWindow(
        hostContext: Context,
        initialGravityEnd: Boolean,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit,
        ) -> Unit,
    ): Boolean {
        val gravityEndHolder = mutableStateOf(initialGravityEnd)
        gravityEndState = gravityEndHolder
        val visibleState = MutableTransitionState(false)
        panelVisibilityState = visibleState
        val targetVisibleHolder = mutableStateOf(false)
        panelTargetVisibleState = targetVisibleHolder

        panelHost.ensureWindow(hostContext, focusable = false) {
            val gravityEnd by gravityEndHolder
            val panelTargetVisible by targetVisibleHolder
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
                    panelTargetVisible,
                    { gravityEndHolder.value = !gravityEndHolder.value },
                    { dismiss() },
                )
            }
        } ?: return false

        val composeView = panelHost.composeView ?: return false
        backHandler?.detach()
        backHandler = OverlayViewBackHandler(composeView, ::handlePanelBack).also { it.attach() }
        return true
    }

    private fun activateBackHandling() {
        panelHost.setInputActive(active = true, requestRootFocus = true)
        val view = panelHost.composeView ?: return
        backHandler?.detach()
        backHandler = OverlayViewBackHandler(view, ::handlePanelBack).also { it.attach() }
    }

    private fun handlePanelBack() {
        if (panelBackInterceptor?.invoke() == true) return
        if (clipboardInputActive) {
            setClipboardInputActive(false)
            return
        }
        dismiss()
    }

    fun setPanelBackInterceptor(interceptor: (() -> Boolean)?) {
        panelBackInterceptor = interceptor
    }

    private fun notifyPanelShown(onShown: () -> Unit) {
        FloatBallOverlay.notifyPanelAttachedAboveChrome()
        activateBackHandling()
        onShown()
        panelHost.composeView?.post {
            activateBackHandling()
            onShown()
        }
        panelHost.composeView?.postDelayed({
            activateBackHandling()
        }, 360L)
    }

    fun dismiss() {
        panelHost.runOnMain {
            if (!panelHost.isAttached) return@runOnMain
            val visibleState = panelVisibilityState
            setPanelTargetVisible(false)
            visibleState?.targetState = false
            val view = panelHost.composeView
            val owner = panelHost.owner
            if (view == null || owner == null || visibleState == null) return@runOnMain
            panelHost.setInputActive(false)
            clipboardInputActive = false
            owner.lifecycleScope.launch(Dispatchers.Main) {
                delay(300)
                if (visibleState.targetState) return@launch
                view.visibility = View.GONE
            }
        }
    }

    fun destroy() {
        panelHost.runOnMain {
            backHandler?.detach()
            backHandler = null
            panelHost.destroy()
            panelVisibilityState = null
            panelTargetVisibleState = null
            gravityEndState = null
            attachedBelowChrome = false
            clipboardInputActive = false
            lastShowAttemptElapsedMs = 0L
        }
    }

    fun setInputActive(active: Boolean, requestRootFocus: Boolean = true) {
        panelHost.setInputActive(active, requestRootFocus)
    }

    fun setClipboardInputActive(active: Boolean) {
        clipboardInputActive = active
        if (active) {
            setInputActive(active = true, requestRootFocus = true)
        } else {
            panelHost.composeView?.clearFocus()
            activateBackHandling()
        }
    }

    fun setDragHidden(hidden: Boolean) {
        panelHost.setDragHidden(hidden)
    }

    fun updateBackgroundBlur(context: Context, blurRadiusDp: Int): Boolean =
        panelHost.updateBackgroundBlur(context, blurRadiusDp)

    companion object {
        private const val SHOW_DEBOUNCE_MS = 300L
    }
}
