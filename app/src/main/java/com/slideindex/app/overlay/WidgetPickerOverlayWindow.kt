package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import androidx.compose.runtime.DisposableEffect
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
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.ui.WidgetPickerScreen
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPopupHost
import kotlinx.coroutines.delay

@SuppressLint("StaticFieldLeak") // Overlay singleton; views/handlers cleared in cleanup()
object WidgetPickerOverlayWindow {
  private const val TAG = "WidgetPickerOverlay"
  private val mainHandler = Handler(Looper.getMainLooper())
  private var windowManager: WindowManager? = null
  private var composeView: ComposeView? = null
  private var owner: OverlayComposeOwner? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var backHandler: OverlayViewBackHandler? = null
  private var requestAnimatedDismiss: (() -> Unit)? = null
  private var screenOffReceiver: BroadcastReceiver? = null
  private var appContext: Context? = null
  @Volatile
  private var dismissing = false

  val isShowing: Boolean get() = composeView != null && !dismissing

  private fun handlePanelBack() {
    requestAnimatedDismiss?.invoke() ?: dismissFromBack()
  }

  fun show(context: Context): Boolean {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      error("WidgetPickerOverlayWindow.show must be called on the main thread")
    }
    if (isShowing) {
      composeView?.requestFocus()
      return true
    }
    dismissing = false
    if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
      Log.w(TAG, "accessibility service not enabled")
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    val hostContext = SlideIndexAccessibilityService.overlayHostContext()
    if (hostContext == null) {
      Log.w(TAG, "accessibility service not connected")
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    WidgetPopupOverlayWindow.suspendForPickerOverlay()
    WidgetPopupHost.startListening(hostContext)
    val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
      WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
      return false
    }
    val dialogOwner = OverlayComposeOwner()

    val appDeps = runCatching {
      dagger.hilt.android.EntryPointAccessors.fromApplication(
        hostContext.applicationContext,
        com.slideindex.app.di.AppGraphEntryPoint::class.java,
      ).dependencies()
    }.getOrNull()

    val view = OverlayCompose.createComposeView(hostContext, dialogOwner).apply {
      isFocusable = true
      isFocusableInTouchMode = true
      setContent {
        androidx.compose.runtime.CompositionLocalProvider(
          *(listOfNotNull(
            appDeps?.let { com.slideindex.app.ui.compose.LocalAppDependencies provides it },
          ).toTypedArray())
        ) {
          OverlayAwareModuleTheme {
            var picked by remember { mutableStateOf(false) }
            WidgetPickerOverlayRoot(
              onAnimatedDismissReady = { handler -> requestAnimatedDismiss = handler },
              onDismissRequest = {
                if (!picked) {
                  WidgetPickerTrampoline.deliverCancel()
                }
                dismiss()
              },
              onWidgetSelected = { entry ->
                picked = true
                WidgetPickerTrampoline.startBindFlow(hostContext, entry.provider.provider)
                mainHandler.post {
                  requestAnimatedDismiss?.invoke() ?: dismiss()
                }
              },
            )
          }
        }
      }
    }

    val params = OverlayPanelLayoutParams.fullScreenOverlay(
      context = hostContext,
      focusable = true,
    )

    val added = runCatching { wm.addView(view, params) }
      .onFailure { Log.e(TAG, "addView failed", it) }
      .isSuccess
    if (!added) {
      dialogOwner.destroy()
      WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
      WidgetPickerTrampoline.deliverCancel()
      return false
    }

    windowManager = wm
    composeView = view
    owner = dialogOwner
    layoutParams = params
    appContext = hostContext.applicationContext
    backHandler = OverlayViewBackHandler(view, ::handlePanelBack).also { it.attach() }
    OverlayPanelSystemGestureExclusion.attach(view, excludeLeftBackEdge = false)
    if (FloatBallOverlay.isShowing) {
      FloatBallOverlay.notifyPanelAttachedAboveChrome()
    }
    OverlaySceneController.onContentPanelShown()
    registerScreenOffReceiver(hostContext)
    view.requestFocus()
    view.post { view.requestFocus() }
    return true
  }

  fun dismissFromBack() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismissFromBack() }
      return
    }
    WidgetPickerTrampoline.deliverCancel()
    dismiss()
  }

  fun dismiss() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { dismiss() }
      return
    }
    if (dismissing) return
    dismissing = true
    backHandler?.detach()
    backHandler = null
    requestAnimatedDismiss = null
    unregisterScreenOffReceiver()
    val view = composeView
    val wm = windowManager
    val dialogOwner = owner
    composeView = null
    windowManager = null
    layoutParams = null
    owner = null
    appContext = null
    if (view != null && wm != null) {
      runCatching { wm.removeView(view) }
    }
    view?.let { OverlayCompose.clearViewTreeOwners(it) }
    dialogOwner?.destroy()
    dismissing = false
    OverlaySceneController.onContentPanelHidden()
    WidgetPopupOverlayWindow.resumeAfterPickerOverlay()
  }

  private fun registerScreenOffReceiver(context: Context) {
    if (screenOffReceiver != null) return
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
          dismissFromBack()
        }
      }
    }
    screenOffReceiver = receiver
    runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
  }

  private fun unregisterScreenOffReceiver() {
    val receiver = screenOffReceiver ?: return
    appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
    screenOffReceiver = null
  }
}

private const val PICKER_ANIM_IN_MS = 280
private const val PICKER_ANIM_OUT_MS = 240

@Composable
fun WidgetPickerOverlayRoot(
  onAnimatedDismissReady: ((() -> Unit)?) -> Unit,
  onDismissRequest: () -> Unit,
  onWidgetSelected: (com.slideindex.app.widget.WidgetProviderEntry) -> Unit,
) {
  var visible by remember { mutableStateOf(false) }
  var hasOpened by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    visible = true
    hasOpened = true
  }

  val dismissAnimated = remember { { visible = false } }

  DisposableEffect(Unit) {
    onAnimatedDismissReady(dismissAnimated)
    onDispose { onAnimatedDismissReady(null) }
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

  val dismiss = dismissAnimated

  LaunchedEffect(visible, hasOpened) {
    if (!visible && hasOpened) {
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
        color = Color(0xFFF7F7F7),
      ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val (initialAppPkgs, initialShortcutKeys) = remember {
          WidgetPickerTrampoline.getCurrentPageConfiguredItems()
        }
        WidgetPickerScreen(
          onBack = dismiss,
          onWidgetSelected = onWidgetSelected,
          configuredAppPackages = initialAppPkgs,
          configuredShortcutKeys = initialShortcutKeys,
          onToggleApp = { app, _ ->
            WidgetPickerTrampoline.toggleApp(app.packageName, app.className, app.appLabel)
          },
          onToggleShortcut = { sc, _ ->
            WidgetPickerTrampoline.toggleShortcut(sc.packageName, sc.shortcutId, sc.label, sc.intentUri)
          },
          onAppSelected = { app ->
            WidgetPickerTrampoline.toggleApp(app.packageName, app.className, app.appLabel)
          },
          onShortcutSelected = { sc ->
            WidgetPickerTrampoline.toggleShortcut(sc.packageName, sc.shortcutId, sc.label, sc.intentUri)
          },
          launchCreateShortcut = { createHost ->
            val hostContext = SlideIndexAccessibilityService.overlayHostContext() ?: context.applicationContext
            if (hostContext != null) {
              com.slideindex.app.service.CreateShortcutTrampoline.launch(
                context = hostContext,
                host = createHost,
                onPrepare = {
                  dismiss()
                },
                onResult = { created ->
                  if (created != null) {
                    WidgetPickerTrampoline.deliverShortcutSuccess(
                      packageName = created.hostPackageName,
                      shortcutId = "created_${created.label.hashCode()}",
                      label = created.label,
                      intentUri = created.intentUri.orEmpty(),
                    )
                  } else {
                    WidgetPickerTrampoline.deliverCancel()
                  }
                },
              )
            }
          },
          // Overlay ComposeView 没有 OnBackPressedDispatcherOwner；返回由 OverlayViewBackHandler 处理。
          enableBackHandler = false,
          overlayMode = true,
        )
      }
    }
  }
}
