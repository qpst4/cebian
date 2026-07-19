package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.ui.gestureActionImageVector
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlin.math.roundToInt

/**
 * FV-style 24dp gesture hint icon during float-ball drag.
 * Positioned away from the finger so it stays visible while dragging.
 */
internal class FloatBallGestureHintWindow {
    companion object {
        const val HINT_SIZE_DP = 24f
        const val HINT_OFFSET_DP = 48f

        /** Left-edge drag: top-right of finger; right-edge drag: top-left of finger. */
        fun hintTopLeftForFingerPx(
            fingerX: Float,
            fingerY: Float,
            dockSide: FloatBallSide,
            hintSizePx: Int,
            gapPx: Int,
        ): Pair<Int, Int> {
            val x = when (dockSide) {
                FloatBallSide.LEFT -> (fingerX + gapPx).roundToInt()
                FloatBallSide.RIGHT -> (fingerX - hintSizePx - gapPx).roundToInt()
            }
            val y = (fingerY - hintSizePx - gapPx).roundToInt()
            return x to y
        }
    }

    private var windowManager: WindowManager? = null
    private var hintView: ComposeView? = null
    private var hintParams: WindowManager.LayoutParams? = null
    private var owner: OverlayComposeOwner? = null
    private val visibleState: MutableState<Boolean> = mutableStateOf(false)
    private val iconState: MutableState<ImageVector?> = mutableStateOf(null)
    private val tintArgbState: MutableState<Int> = mutableStateOf(0xFF6750A4.toInt())

    fun attach(context: Context, wm: WindowManager) {
        if (hintView != null) return
        val overlayContext = OverlayCompose.themedContext(context)
        val dialogOwner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
            setContent {
                SlideIndexTheme {
                    FloatBallGestureHintContent(
                        visibleState = visibleState,
                        iconState = iconState,
                        tintArgbState = tintArgbState,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(composeView, params)
        windowManager = wm
        hintView = composeView
        hintParams = params
        owner = dialogOwner
    }

    fun update(
        action: GestureAction,
        themeColorArgb: Int,
        fingerX: Float,
        fingerY: Float,
        dockSide: FloatBallSide,
        density: Float,
    ) {
        val view = hintView ?: return
        val params = hintParams ?: return
        val wm = windowManager ?: return
        val hintSizePx = (HINT_SIZE_DP * density).roundToInt()
        val gapPx = (HINT_OFFSET_DP * density).roundToInt()
        iconState.value = gestureActionImageVector(action)
        tintArgbState.value = themeColorArgb
        visibleState.value = true
        val (x, y) = hintTopLeftForFingerPx(
            fingerX = fingerX,
            fingerY = fingerY,
            dockSide = dockSide,
            hintSizePx = hintSizePx,
            gapPx = gapPx,
        )
        params.width = hintSizePx
        params.height = hintSizePx
        params.x = x
        params.y = y
        view.visibility = View.VISIBLE
        runCatching { wm.updateViewLayout(view, params) }
    }

    fun hide() {
        visibleState.value = false
        iconState.value = null
        hintView?.visibility = View.GONE
    }

    fun detach() {
        hide()
        val wm = windowManager
        hintView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        OverlayCompose.disposeComposeView(hintView)
        owner?.destroy()
        windowManager = null
        hintView = null
        hintParams = null
        owner = null
    }
}

@Composable
private fun FloatBallGestureHintContent(
    visibleState: MutableState<Boolean>,
    iconState: MutableState<ImageVector?>,
    tintArgbState: MutableState<Int>,
) {
    val visible by visibleState
    val icon = iconState.value
    val tint = Color(tintArgbState.value)
    if (!visible || icon == null) return
    Box(
        modifier = Modifier
            .size(FloatBallGestureHintWindow.HINT_SIZE_DP.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
    }
}
