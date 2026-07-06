package com.slideindex.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPopupHost

/**
 * Shows the widget picker (overlay when possible, otherwise a transparent Activity)
 * and delivers the picked widget id after [WidgetBindTrampolineActivity] completes.
 */
object WidgetPickerTrampoline {
  private const val TAG = "WidgetPickerTrampoline"
  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile
  private var onResult: ((Int) -> Unit)? = null

  @Volatile
  private var onCancel: (() -> Unit)? = null

  fun launch(context: Context, onAdded: (Int) -> Unit, onCancelled: () -> Unit = {}) {
    Log.d(TAG, "launch")
    onResult = onAdded
    onCancel = onCancelled

    val runLaunch = {
      WidgetPopupOverlayWindow.setWidgetAddFlowActive(true)
      val canUseOverlay = PermissionHelper.isAccessibilityServiceEnabledForOverlays(context) &&
        SlideIndexAccessibilityService.overlayHostContext() != null
      if (canUseOverlay) {
        if (!WidgetPickerOverlayWindow.show(context)) {
          Log.w(TAG, "overlay picker failed, falling back to activity")
          launchActivityPicker(context)
        }
      } else {
        Log.d(TAG, "accessibility overlay host unavailable, using activity picker")
        launchActivityPicker(context)
      }
    }

    if (Looper.myLooper() == Looper.getMainLooper()) {
      runLaunch()
    } else {
      mainHandler.post { runLaunch() }
    }
  }

  fun startBindFlow(context: Context, provider: ComponentName) {
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(true)
    val appContext = context.applicationContext
    WidgetPopupHost.startListening(appContext)
    val appWidgetId = WidgetPopupHost.allocateAppWidgetId(appContext)
    val intent = WidgetBindTrampolineActivity.createIntent(appContext, appWidgetId, provider)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
  }

  fun deliverSuccess(appWidgetId: Int) {
    Log.d(TAG, "deliverSuccess: id=$appWidgetId")
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onResult
    clear()
    callback?.invoke(appWidgetId)
  }

  fun deliverCancel() {
    Log.d(TAG, "deliverCancel")
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onCancel
    clear()
    callback?.invoke()
  }

  private fun launchActivityPicker(context: Context) {
    val intent = WidgetPickerTrampolineActivity.createIntent(context).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
      .onFailure {
        Log.e(TAG, "activity picker launch failed", it)
        deliverCancel()
      }
  }

  private fun clear() {
    onResult = null
    onCancel = null
  }
}
