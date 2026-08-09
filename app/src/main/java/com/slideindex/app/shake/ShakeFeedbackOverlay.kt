package com.slideindex.app.shake

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

object ShakeFeedbackOverlay {
    private const val TAG = "ShakeFeedbackOverlay"
    private const val ATTACH_RETRY_MS = 30_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var composeOwner: OverlayComposeOwner? = null
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var attachFailedAt = 0L
    private var feedbackGeneration by mutableIntStateOf(0)
    private var activeGestureType by mutableStateOf<ShakeGestureType?>(null)
    private var activeAction by mutableStateOf<GestureAction?>(null)
    private var activeColorArgb by mutableIntStateOf(0xFF424242.toInt())

    fun showGestureFeedback(
        context: Context,
        gestureType: ShakeGestureType,
        action: GestureAction,
        colorArgb: Int,
    ) {
        mainHandler.post {
            if (!ensureAttached(context)) return@post
            feedbackGeneration++
            activeGestureType = gestureType
            activeAction = action
            activeColorArgb = colorArgb
            composeView?.setContent {
                OverlayAwareModuleTheme {
                    key(feedbackGeneration) {
                        val gesture = activeGestureType
                        val feedbackAction = activeAction
                        if (gesture != null && feedbackAction != null) {
                            ShakeFeedbackBubble(
                                gestureType = gesture,
                                action = feedbackAction,
                                colorArgb = activeColorArgb,
                            )
                        }
                    }
                }
            }
            bringToFront()
        }
    }

    fun detach() {
        mainHandler.post {
            val wm = windowManager
            val view = composeView
            if (wm != null && view != null) {
                runCatching { wm.removeView(view) }
            }
            OverlayCompose.disposeComposeView(view)
            composeOwner?.destroy()
            composeOwner = null
            composeView = null
            windowManager = null
            attachFailedAt = 0L
            activeGestureType = null
            activeAction = null
        }
    }

    private fun bringToFront() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        runCatching {
            wm.removeView(view)
            wm.addView(view, params)
        }.onFailure { error ->
            Log.w(TAG, "bringToFront failed", error)
        }
    }

    private fun ensureAttached(context: Context): Boolean {
        if (composeView != null) return true

        val now = System.currentTimeMillis()
        if (attachFailedAt > 0L && now - attachFailedAt < ATTACH_RETRY_MS) {
            return false
        }
        attachFailedAt = 0L

        val hostContext = SlideIndexAccessibilityService.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                attachFailedAt = now
                return false
            }
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return false
        val params = OverlayWindowTypes.createPresentationParams(hostContext).apply {
            OverlayWindowTypes.applyFullScreen(this)
        }
        val owner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, owner).apply {
            isClickable = false
            isFocusable = false
        }

        val attached = runCatching { wm.addView(view, params) }
            .onFailure { error ->
                attachFailedAt = System.currentTimeMillis()
                Log.e(TAG, "addView failed", error)
            }
            .isSuccess
        if (!attached) {
            owner.destroy()
            return false
        }

        composeOwner = owner
        composeView = view
        windowManager = wm
        return true
    }

    @Composable
    private fun ShakeFeedbackBubble(
        gestureType: ShakeGestureType,
        action: GestureAction,
        colorArgb: Int,
    ) {
        val scale = remember { Animatable(0.45f) }
        val alpha = remember { Animatable(0.92f) }

        LaunchedEffect(gestureType, action) {
            scale.snapTo(0.45f)
            alpha.snapTo(0.92f)
            scale.animateTo(1.12f, tween(220))
            alpha.animateTo(0f, tween(280))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(alignmentForGesture(gestureType))
                    .padding(24.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
                    .size(56.dp)
                    .background(Color(colorArgb).copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = gestureActionIcon(action),
                    contentDescription = stringResource(R.string.cd_shake_feedback),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    private fun alignmentForGesture(type: ShakeGestureType): Alignment = when (type) {
        ShakeGestureType.LEFT_FLIP -> Alignment.CenterEnd
        ShakeGestureType.RIGHT_FLIP -> Alignment.CenterStart
        ShakeGestureType.FORWARD_FLIP,
        ShakeGestureType.BACKWARD_FLIP,
        -> Alignment.TopCenter
        ShakeGestureType.LEFT_FLICK -> Alignment.TopStart
        ShakeGestureType.RIGHT_FLICK -> Alignment.TopEnd
    }
}
