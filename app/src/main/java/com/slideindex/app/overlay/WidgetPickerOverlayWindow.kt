package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.ui.WidgetPickerScreen
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPopupHost
import kotlinx.coroutines.delay

object WidgetPickerOverlayWindow {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var windowManager: WindowManager? = null
  private var composeView: ComposeView? = null
  private var owner: OverlayComposeOwner? = null

  val isShowing: Boolean get() = composeView != null

  fun show(context: Context): Boolean {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      error("WidgetPickerOverlayWindow.show must be called on the main thread")
    }
    if (isShowing) {
      return true
    }
    if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
      Log.w("WidgetPickerOverlay", "accessibility service not enabled")
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    val hostContext = SlideIndexAccessibilityService.overlayHostContext()
    if (hostContext == null) {
      Log.w("WidgetPickerOverlay", "accessibility service not connected")
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    WidgetPopupHost.startListening(hostContext)
    val overlayContext = OverlayCompose.themedContext(hostContext)
    val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
    val dialogOwner = OverlayComposeOwner()

    val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
      setContent {
        SlideIndexTheme(dynamicColor = true) {
          var picked by remember { mutableStateOf(false) }
          WidgetPickerOverlayRoot(
            onDismissRequest = {
              if (!picked) {
                WidgetPickerTrampoline.deliverCancel()
              }
              dismiss()
            },
            onWidgetSelected = { entry ->
              picked = true
              WidgetPickerTrampoline.startBindFlow(hostContext, entry.provider.provider)
              WidgetPickerOverlayWindow.dismiss()
            },
          )
        }
      }
    }

    val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      type,
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }

    val added = runCatching { wm.addView(view, params) }
      .onFailure { Log.e("WidgetPickerOverlay", "addView failed", it) }
      .isSuccess
    if (!added) {
      dialogOwner.destroy()
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    windowManager = wm
    composeView = view
    owner = dialogOwner
    return true
  }

  fun dismiss() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismiss() }
      return
    }
    val view = composeView ?: return
    val wm = windowManager ?: return
    runCatching { wm.removeView(view) }
    owner?.destroy()
    composeView = null
    windowManager = null
    owner = null
  }
}

private const val PICKER_ANIM_IN_MS = 280
private const val PICKER_ANIM_OUT_MS = 240

@Composable
fun WidgetPickerOverlayRoot(
  onDismissRequest: () -> Unit,
  onWidgetSelected: (com.slideindex.app.widget.WidgetProviderEntry) -> Unit,
) {
  var visible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    visible = true
  }

  val progress by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = if (visible) {
      tween(PICKER_ANIM_IN_MS, easing = LinearOutSlowInEasing)
    } else {
      tween(PICKER_ANIM_OUT_MS, easing = FastOutLinearInEasing)
    },
    label = "widgetPickerProgress",
  )

  val dismiss = { visible = false }

  LaunchedEffect(visible) {
    if (!visible) {
      delay(PICKER_ANIM_OUT_MS.toLong())
      onDismissRequest()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.5f * progress))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = dismiss,
        ),
    )

    BoxWithConstraints(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.BottomCenter,
    ) {
      val density = LocalDensity.current
      val sheetHeight = maxHeight * 0.85f
      val offsetY = with(density) { sheetHeight.toPx() * (1f - progress) }

      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .height(sheetHeight)
          .graphicsLayer { translationY = offsetY }
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
      ) {
        WidgetPickerScreen(
          onBack = dismiss,
          onWidgetSelected = onWidgetSelected,
        )
      }
    }
  }
}
