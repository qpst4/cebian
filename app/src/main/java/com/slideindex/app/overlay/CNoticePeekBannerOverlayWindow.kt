package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.roundToInt

/**
 * 独立悬浮窗承载 peek 横幅：仅包裹横幅本体，[FLAG_NOT_TOUCHABLE] 不挡底层触摸。
 */
internal object CNoticePeekBannerOverlayWindow {
    private const val TAG = "CNoticePeekBanner"
    private const val ANIM_MS = 200L

    data class BallAnchor(
        val ballLeft: Int,
        val ballTop: Int,
        val ballRight: Int,
        val ballBottom: Int,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var owner: OverlayComposeOwner? = null
    private val visibleState: MutableState<Boolean> = mutableStateOf(false)
    private val displayTextState: MutableState<String?> = mutableStateOf(null)
    private val horizontalEdgeState: MutableState<SideBubbleHorizontalEdge> =
        mutableStateOf(SideBubbleHorizontalEdge.Right)
    private var lastAnchor: BallAnchor? = null
    private var gapPx: Int = 0
    private var lastBannerSize: IntSize = IntSize.Zero
    private var dismissCleanupRunnable: Runnable? = null

    fun update(
        context: Context,
        anchor: BallAnchor,
        text: String,
        horizontalEdge: SideBubbleHorizontalEdge,
        gapPx: Int,
    ) {
        if (text.isBlank()) {
            dismiss()
            return
        }
        cancelDismissCleanup()
        lastAnchor = anchor
        this.gapPx = gapPx
        horizontalEdgeState.value = horizontalEdge
        displayTextState.value = text
        ensureWindow(context)
        composeView?.let { view ->
            view.visibility = View.VISIBLE
            visibleState.value = true
            view.post {
                if (lastBannerSize.width > 0 && lastBannerSize.height > 0) {
                    applyPosition(lastBannerSize.width, lastBannerSize.height)
                } else {
                    view.requestLayout()
                }
            }
        }
    }

    fun dismiss() {
        cancelDismissCleanup()
        if (!visibleState.value && displayTextState.value.isNullOrBlank()) {
            composeView?.visibility = View.GONE
            return
        }
        visibleState.value = false
        val runnable = Runnable {
            dismissCleanupRunnable = null
            displayTextState.value = null
            lastAnchor = null
            lastBannerSize = IntSize.Zero
            composeView?.visibility = View.GONE
        }
        dismissCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, ANIM_MS)
    }

    fun dismissImmediate() {
        cancelDismissCleanup()
        visibleState.value = false
        displayTextState.value = null
        lastAnchor = null
        lastBannerSize = IntSize.Zero
        val wm = windowManager
        val view = composeView
        val dialogOwner = owner
        windowManager = null
        composeView = null
        layoutParams = null
        owner = null
        view?.let { v -> wm?.let { runCatching { it.removeView(v) } } }
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
    }

    private fun cancelDismissCleanup() {
        dismissCleanupRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissCleanupRunnable = null
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return
        val hostContext = MessageOverlayHost.resolveContentPanelContext(context)
            ?: MessageOverlayHost.resolveHostContext(context)
            ?: context
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val dialogOwner = OverlayComposeOwner()
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            dialogOwner.destroy()
            return
        }
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                OverlayAwareModuleTheme {
                    CNoticePeekBannerOverlayContent(
                        visibleState = visibleState,
                        displayTextState = displayTextState,
                        horizontalEdgeState = horizontalEdgeState,
                        onBannerSized = ::onBannerSized,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.appSwitcherWindowType(hostContext),
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
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            OverlayCompose.clearViewTreeOwners(view)
            dialogOwner.destroy()
            return
        }
        windowManager = wm
        composeView = view
        layoutParams = params
        owner = dialogOwner
    }

    private fun onBannerSized(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        lastBannerSize = IntSize(width, height)
        applyPosition(width, height)
    }

    private fun applyPosition(width: Int, height: Int) {
        val anchor = lastAnchor ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        val view = composeView ?: return
        val centerY = (anchor.ballTop + anchor.ballBottom) / 2
        params.x = when (horizontalEdgeState.value) {
            SideBubbleHorizontalEdge.Right -> anchor.ballLeft - gapPx - width
            SideBubbleHorizontalEdge.Left -> anchor.ballRight + gapPx
        }
        params.y = centerY - height / 2
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "updateViewLayout failed", it) }
    }
}

@Composable
private fun CNoticePeekBannerOverlayContent(
    visibleState: MutableState<Boolean>,
    displayTextState: MutableState<String?>,
    horizontalEdgeState: MutableState<SideBubbleHorizontalEdge>,
    onBannerSized: (Int, Int) -> Unit,
) {
    val visible by visibleState
    val text by displayTextState
    val horizontalEdge by horizontalEdgeState
    val bannerText = text?.takeIf { it.isNotBlank() }
    if (bannerText == null && !visible) return

    val bannerSlidePx = with(LocalDensity.current) { 10.dp.roundToPx() }
    val bannerFadeSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val slideOffset = when (horizontalEdge) {
        SideBubbleHorizontalEdge.Right -> bannerSlidePx
        SideBubbleHorizontalEdge.Left -> -bannerSlidePx
    }
    val bannerScaleOrigin = when (horizontalEdge) {
        SideBubbleHorizontalEdge.Right -> TransformOrigin(1f, 0.5f)
        SideBubbleHorizontalEdge.Left -> TransformOrigin(0f, 0.5f)
    }
    val bannerEnter = fadeIn(bannerFadeSpec) +
        slideInHorizontally(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            initialOffsetX = { slideOffset },
        ) +
        scaleIn(
            animationSpec = bannerFadeSpec,
            initialScale = 0.92f,
            transformOrigin = bannerScaleOrigin,
        )
    val bannerExit = fadeOut(bannerFadeSpec) +
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            targetOffsetX = { slideOffset },
        ) +
        scaleOut(
            animationSpec = bannerFadeSpec,
            targetScale = 0.92f,
            transformOrigin = bannerScaleOrigin,
        )

    AnimatedVisibility(
        visible = visible,
        enter = bannerEnter,
        exit = bannerExit,
        modifier = Modifier.onSizeChanged { size ->
            if (visible && bannerText != null) {
                onBannerSized(size.width, size.height)
            }
        },
    ) {
        bannerText?.let { content ->
            CNoticePeekBanner(
                text = content,
                horizontalEdge = horizontalEdge,
            )
        }
    }
}

@Composable
internal fun CNoticePeekBanner(
    text: String,
    horizontalEdge: SideBubbleHorizontalEdge,
    modifier: Modifier = Modifier,
) {
    val shape = when (horizontalEdge) {
        SideBubbleHorizontalEdge.Right -> RoundedCornerShape(
            topStart = 12.dp,
            bottomStart = 12.dp,
            topEnd = 4.dp,
            bottomEnd = 4.dp,
        )
        SideBubbleHorizontalEdge.Left -> RoundedCornerShape(
            topStart = 4.dp,
            bottomStart = 4.dp,
            topEnd = 12.dp,
            bottomEnd = 12.dp,
        )
    }
    Text(
        text = text,
        modifier = modifier
            .widthIn(max = 200.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
