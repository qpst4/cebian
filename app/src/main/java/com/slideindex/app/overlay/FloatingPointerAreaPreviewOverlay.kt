package com.slideindex.app.overlay

import com.slideindex.app.di.AppDependencies
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full-screen on-device preview for joystick area settings. Drawn above the system UI;
 * touches pass through so real edge gestures still work. Trigger position updates from
 * actual edge touch-down events.
 */
object FloatingPointerAreaPreviewOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var displayView: ComposeView? = null
    private var displayOwner: OverlayComposeOwner? = null
    private var settingsState: androidx.compose.runtime.MutableState<AppSettings>? = null
    private var triggerPositionState: androidx.compose.runtime.MutableState<Offset>? = null
    private var settingsCollectJob: Job? = null
    private var showToken = 0

    val isShowing: Boolean get() = displayView != null

    fun show(deps: AppDependencies) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(deps) }
            return
        }
        if (isShowing) return
        val token = showToken
        val hostContext = SlideIndexAccessibilityService.overlayHostContext() ?: return
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(hostContext)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return
        }
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dm = hostContext.resources.displayMetrics
        val bounds = readOverlayScreenBounds(wm, dm)

        val settingsHolder = mutableStateOf(deps.settingsRepository.readSnapshot())
        val triggerHolder = mutableStateOf(
            Offset(0f, bounds.second * DEFAULT_TRIGGER_Y_NORM),
        )

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
            setContent {
                SlideIndexTheme {
                    val settings by settingsHolder
                    val triggerPosition by triggerHolder
                    FloatingPointerAreaPreviewDisplay(
                        settings = settings,
                        screenWidth = bounds.first,
                        screenHeight = bounds.second,
                        density = dm.density,
                        triggerPosition = triggerPosition,
                    )
                }
            }
        }

        if (token != showToken) {
            OverlayCompose.disposeComposeView(composeView)
            owner.destroy()
            return
        }

        val displayParams = buildDisplayParams(hostContext)

        val displayAdded = runCatching { wm.addView(composeView, displayParams) }.isSuccess
        if (!displayAdded || token != showToken) {
            runCatching { wm.removeView(composeView) }
            OverlayCompose.disposeComposeView(composeView)
            owner.destroy()
            return
        }

        windowManager = wm
        displayView = composeView
        displayOwner = owner
        settingsState = settingsHolder
        triggerPositionState = triggerHolder

        settingsCollectJob?.cancel()
        settingsCollectJob = overlayScope.launch {
            deps.settingsRepository.settings.collectLatest { latest ->
                settingsHolder.value = latest
            }
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        showToken++
        settingsCollectJob?.cancel()
        settingsCollectJob = null
        val wm = windowManager
        val view = displayView
        val owner = displayOwner
        displayView = null
        displayOwner = null
        windowManager = null
        settingsState = null
        triggerPositionState = null
        view?.let { runCatching { wm?.removeView(it) } }
        OverlayCompose.disposeComposeView(view)
        owner?.destroy()
    }

    fun updateSettings(settings: AppSettings) {
        settingsState?.value = settings
    }

    fun onEdgeTriggerTouch(rawX: Float, rawY: Float) {
        if (!isShowing) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onEdgeTriggerTouch(rawX, rawY) }
            return
        }
        triggerPositionState?.value = Offset(rawX, rawY)
    }

    private fun buildDisplayParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun readOverlayScreenBounds(wm: WindowManager, fallback: DisplayMetrics): Pair<Float, Float> {
        val bounds = wm.currentWindowMetrics.bounds
        return bounds.width().toFloat() to bounds.height().toFloat()
    }

    private const val TAG = "FpAreaPreview"
    private const val DEFAULT_TRIGGER_Y_NORM = 0.82f
}

@Composable
private fun FloatingPointerAreaPreviewDisplay(
    settings: AppSettings,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    triggerPosition: Offset,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val layout = FloatingPointerBounds.computeAreaPreviewLayout(
        settings = settings,
        density = density,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        triggerRawX = triggerPosition.x,
        triggerRawY = triggerPosition.y,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFloatingPointerAreaPreview(
                layout = layout,
                settings = settings,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            )
        }
        Text(
            text = stringResource(R.string.floating_pointer_preview_pointer_range),
            style = labelStyle,
            color = labelColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp),
        )
    }
}
