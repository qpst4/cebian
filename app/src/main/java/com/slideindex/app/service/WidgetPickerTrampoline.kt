package com.slideindex.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetPopupHost

/**
 * Shows the widget picker (overlay when possible, otherwise a transparent Activity)
 * and delivers the picked widget id after [WidgetBindTrampolineActivity] completes.
 */
object WidgetPickerTrampoline {
  private const val TAG = "WidgetPickerTrampoline"
  private val mainHandler = Handler(Looper.getMainLooper())

  private data class WidgetPanelAddContext(
    val appContext: Context,
    val pageIndex: Int,
    val pagesProvider: () -> List<WidgetPanelPage>,
  )

  @Volatile
  private var panelAddContext: WidgetPanelAddContext? = null

  @Volatile
  private var onResult: ((Int) -> Unit)? = null

  @Volatile
  private var onAppResult: ((String, String, String) -> Unit)? = null

  @Volatile
  private var onShortcutResult: ((packageName: String, shortcutId: String, label: String, intentUri: String) -> Unit)? = null

  @Volatile
  private var onActionResult: ((actionPayload: String, label: String) -> Unit)? = null

  @Volatile
  private var onCancel: (() -> Unit)? = null

  fun launch(
    context: Context,
    pageIndex: Int,
    pagesProvider: () -> List<WidgetPanelPage>,
    onAdded: (Int) -> Unit,
    onAppAdded: ((packageName: String, className: String, label: String) -> Unit)? = null,
    onShortcutAdded: ((packageName: String, shortcutId: String, label: String, intentUri: String) -> Unit)? = null,
    onActionAdded: ((actionPayload: String, label: String) -> Unit)? = null,
    onCancelled: () -> Unit = {},
  ) {
    Log.d(TAG, "launch")
    panelAddContext = WidgetPanelAddContext(
      appContext = context.applicationContext,
      pageIndex = pageIndex,
      pagesProvider = pagesProvider,
    )
    onResult = onAdded
    onAppResult = onAppAdded
    onShortcutResult = onShortcutAdded
    onActionResult = onActionAdded
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
    persistWidgetAdd(appWidgetId)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onResult
    clear()
    callback?.invoke(appWidgetId)
  }

  fun deliverAppSuccess(packageName: String, className: String, label: String) {
    Log.d(TAG, "deliverAppSuccess: pkg=$packageName, cls=$className")
    persistAppAdd(packageName, className, label)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onAppResult
    clear()
    callback?.invoke(packageName, className, label)
  }

  fun deliverShortcutSuccess(packageName: String, shortcutId: String, label: String, intentUri: String) {
    Log.d(TAG, "deliverShortcutSuccess: pkg=$packageName, id=$shortcutId")
    persistShortcutAdd(packageName, shortcutId, label, intentUri)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onShortcutResult
    clear()
    callback?.invoke(packageName, shortcutId, label, intentUri)
  }

  fun deliverActionSuccess(actionPayload: String, label: String) {
    Log.d(TAG, "deliverActionSuccess: action=$actionPayload")
    persistActionAdd(actionPayload, label)
    WidgetPopupOverlayWindow.setWidgetAddFlowActive(false)
    val callback = onActionResult
    clear()
    callback?.invoke(actionPayload, label)
  }

  fun deliverCancel() {
    Log.d(TAG, "deliverCancel")
    panelAddContext = null
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
    panelAddContext = null
    onResult = null
    onAppResult = null
    onShortcutResult = null
    onActionResult = null
    onCancel = null
  }

  private fun persistWidgetAdd(appWidgetId: Int) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addWidgetToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      appWidgetId,
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun persistAppAdd(packageName: String, className: String, label: String) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addAppToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      packageName,
      className,
      label,
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun persistShortcutAdd(
    packageName: String,
    shortcutId: String,
    label: String,
    intentUri: String,
  ) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addShortcutToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      packageName,
      shortcutId,
      label,
      intentUri,
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun persistActionAdd(actionPayload: String, label: String) {
    val ctx = panelAddContext ?: return
    val updated = WidgetPanelMutator.addActionToPage(
      ctx.appContext,
      ctx.pagesProvider(),
      ctx.pageIndex,
      actionPayload,
      label,
    ) ?: return
    schedulePersist(ctx.appContext, updated)
  }

  private fun schedulePersist(context: Context, pages: List<WidgetPanelPage>) {
    OverlayDependencyAccess.overlayDependencies(context)
      ?.widgetPanelPersistence
      ?.schedulePersist(pages)
      ?: Log.w(TAG, "schedulePersist skipped: overlay dependencies unavailable")
  }
}
