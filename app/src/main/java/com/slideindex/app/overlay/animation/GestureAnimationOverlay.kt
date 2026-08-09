package com.slideindex.app.overlay.animation



/*

 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)

 * Licensed under Apache-2.0. Modified for com.slideindex.app.

 */



import android.annotation.SuppressLint

import android.content.Context

import android.view.View

import android.view.ViewGroup

import android.widget.FrameLayout

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

import com.slideindex.app.ui.theme.OverlayAwareModuleTheme



class GestureAnimationOverlayController(

    val side: PanelSide,

) {

    private var owner: OverlayComposeOwner? = null

    private var composeView: ComposeView? = null

    private var parent: ViewGroup? = null

    private var animationStateRef: GestureAnimationState? = null

    internal var enabled by mutableStateOf(false)

        private set

    internal var animationStyle by mutableStateOf<AnimationStyle?>(null)

        private set

    private var pendingSettings: AppSettings? = null

    private var pendingHandleId: String? = null

    private var appContext: Context? = null



    val animationState: GestureAnimationState?

        get() = animationStateRef



    val isAttached: Boolean

        get() = composeView?.parent != null



    fun applySettings(settings: AppSettings, handleId: String? = null) {

        enabled = settings.gestureHintEnabled

        animationStyle = settings.activeAnimationStyle()

        pendingSettings = settings

        pendingHandleId = handleId

        animationStateRef?.let { applySettingsToState(it, settings, handleId) }

    }



    fun attach(parent: ViewGroup, context: Context) {

        appContext = context.applicationContext

        val overlayContext = OverlayCompose.themedContext(context)

        this.parent = parent

        val view = composeView ?: run {
            val dialogOwner = OverlayComposeOwner()
            OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                @SuppressLint("ClickableViewAccessibility")
                setOnTouchListener { _, _ -> false }
                setContent {
                    GestureAnimationOverlayHost(
                        controller = this@GestureAnimationOverlayController,
                    )
                }
            }.also {
                owner = dialogOwner
                composeView = it
            }
        }

        owner?.let { OverlayCompose.bindOwners(parent, it) }

        if (view.parent !== parent) {
            (view.parent as? ViewGroup)?.removeView(view)
            parent.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        view.bringToFront()

        view.z = GESTURE_ANIMATION_Z_INDEX

        pendingSettings?.let { settings ->

            applySettings(settings, pendingHandleId)

            if (!view.isAttachedToWindow) {

                view.post { applySettings(settings, pendingHandleId) }

            }

        }

    }



    fun attach(context: Context) {

        val host = parent

        if (host != null) {

            attach(host, context)

            return

        }

        appContext = context.applicationContext

    }



    fun bringToFront() {

        val view = composeView ?: return

        view.bringToFront()

        view.z = GESTURE_ANIMATION_Z_INDEX

    }



    fun show() = Unit



    fun hide() {

        animationStateRef?.cancelSilently()

    }



    fun hideAfterGesture() = Unit



    fun detach() {
        hide()
        val view = composeView ?: return
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
        parent?.let { host -> OverlayCompose.clearViewTreeOwners(host) }
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

        state.hintFingerOffsetPx = settings.gestureHintFingerOffsetDp * density()

        state.applyWaveStyle(settings.activeWaveStyle())

    }



    private fun density(): Float =

        appContext?.resources?.displayMetrics?.density ?: 3f



    private companion object {

        private const val GESTURE_ANIMATION_Z_INDEX = 40f

    }

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

        enabled = controller.enabled,

        animationStyle = controller.animationStyle,

        animationState = animationState,

    )

}



@Composable

private fun GestureAnimationOverlayContent(

    enabled: Boolean,

    animationStyle: AnimationStyle?,

    animationState: GestureAnimationState,

) {

    OverlayAwareModuleTheme {

        if (enabled && animationStyle != null) {

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


