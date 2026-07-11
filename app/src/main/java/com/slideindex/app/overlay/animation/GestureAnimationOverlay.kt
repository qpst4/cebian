package com.slideindex.app.overlay.animation

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AnimationStyle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.activeAnimationStyle
import com.slideindex.app.settings.activeWaveStyle
import com.slideindex.app.ui.theme.SlideIndexTheme

class GestureAnimationOverlayController(
    val side: PanelSide,
) {
    private var owner: OverlayComposeOwner? = null
    private var composeView: ComposeView? = null
    private var parent: ViewGroup? = null
    private var animationStateRef: GestureAnimationState? = null
    internal var visible by mutableStateOf(false)
        private set
    internal var enabled by mutableStateOf(false)
        private set
    internal var animationStyle by mutableStateOf<AnimationStyle?>(null)
        private set
    private var pendingSettings: AppSettings? = null
    private var pendingHandleId: String? = null
    private var appContext: Context? = null

    val animationState: GestureAnimationState?
        get() = animationStateRef

    val isAttached: Boolean get() = composeView != null

    fun applySettings(settings: AppSettings, handleId: String? = null) {
        enabled = settings.gestureHintEnabled
        animationStyle = settings.activeAnimationStyle()
        pendingSettings = settings
        pendingHandleId = handleId
        animationStateRef?.let { applySettingsToState(it, settings, handleId) }
    }

    fun attach(parent: ViewGroup, context: Context) {
        if (composeView != null) return
        appContext = context.applicationContext
        val overlayContext = OverlayCompose.themedContext(context)
        val dialogOwner = OverlayComposeOwner()
        OverlayCompose.bindOwners(parent, dialogOwner)
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            @SuppressLint("ClickableViewAccessibility") // Passthrough overlay; never consumes clicks
            setOnTouchListener { _, _ -> false }
            setContent {
                GestureAnimationOverlayHost(
                    controller = this@GestureAnimationOverlayController,
                )
            }
        }
        parent.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        this.parent = parent
        composeView = view
        owner = dialogOwner
    }

    fun show() {
        visible = true
    }

    fun hide() {
        visible = false
        animationStateRef?.onDragCancel()
    }

    fun hideAfterGesture() {
        visible = false
    }

    fun detach() {
        hide()
        val view = composeView ?: return
        runCatching { parent?.removeView(view) }
        owner?.destroy()
        composeView = null
        owner = null
        parent = null
        animationStateRef = null
    }

    internal fun bindAnimationState(state: GestureAnimationState) {
        animationStateRef = state
        pendingSettings?.let { applySettingsToState(state, it, pendingHandleId) }
    }

    internal fun clearAnimationState(state: GestureAnimationState) {
        if (animationStateRef === state) {
            animationStateRef = null
        }
    }

    private fun applySettingsToState(
        state: GestureAnimationState,
        settings: AppSettings,
        handleId: String? = null,
    ) {
        val handle = if (handleId != null) {
            settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
        } else {
            settings.primaryTriggerHandle(side)
        }
        state.shortTriggerDistancePx = handle.shortSwipeDistanceDp * density()
        state.longTriggerDistancePx = handle.longSwipeDistanceDp * density()
        state.applyWaveStyle(settings.activeWaveStyle())
    }

    private fun density(): Float =
        appContext?.resources?.displayMetrics?.density ?: 3f
}

@Composable
private fun GestureAnimationOverlayHost(
    controller: GestureAnimationOverlayController,
) {
    val scope = rememberCoroutineScope()
    val animationState = remember(controller.side, scope) {
        GestureAnimationState(scope, controller.side)
    }
    DisposableEffect(animationState) {
        controller.bindAnimationState(animationState)
        onDispose {
            controller.clearAnimationState(animationState)
        }
    }
    GestureAnimationOverlayContent(
        visible = controller.visible,
        enabled = controller.enabled,
        animationStyle = controller.animationStyle,
        animationState = animationState,
    )
}

@Composable
private fun GestureAnimationOverlayContent(
    visible: Boolean,
    enabled: Boolean,
    animationStyle: AnimationStyle?,
    animationState: GestureAnimationState,
) {
    SlideIndexTheme {
        if (visible && enabled && animationStyle != null && animationState.isActive) {
            Box(modifier = Modifier.fillMaxSize()) {
                GestureAnimation(
                    animationStyle = animationStyle,
                    animationState = animationState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

object GestureAnimationOverlayRegistry {
    private val controllers = mutableMapOf<PanelSide, GestureAnimationOverlayController>()

    fun controller(side: PanelSide): GestureAnimationOverlayController =
        controllers.getOrPut(side) { GestureAnimationOverlayController(side) }

    fun detachAll() {
        controllers.values.forEach { it.detach() }
        controllers.clear()
    }
}
