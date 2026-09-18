package com.slideindex.app.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.util.PermissionHelper

/**
 * 应用内语言变更后，清理仍持有旧 Configuration 的浮窗壳层，便于下次展示时重建。
 */
object OverlayLocaleCoordinator {
    private const val TAG = "OverlayLocaleCoordinator"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onApplicationLocaleChanged(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onApplicationLocaleChanged(context) }
            return
        }
        Log.i(TAG, "refreshing overlay shells after app locale change")
        val appContext = context.applicationContext
        GlobalOverlayDismissHelper.dismissAllPanels()
        OverlayCompose.clearWindowContextCache()
        FloatBallPickResultPanel.releaseWarmUpForLocale()
        FloatBallStashPanel.releaseWarmUpForLocale()
        SearchPanelOverlayWindow.releaseWarmUp()
        recreateClipboardHistoryFloats(appContext)
        ClipboardFloatLifecycle.recreateForLocaleChange(appContext)
        FloatBallOverlay.recreateForLocaleChange()
    }

    private fun recreateClipboardHistoryFloats(appContext: Context) {
        if (!PermissionHelper.canDrawOverlays(appContext)) return
        val settings = OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return
        if (!settings.clipboardHistoryFloatEnabled) return
        HistoryFloatLifecycle.recreateForLocaleChange(
            context = appContext,
            handleWidthDp = settings.clipboardHistoryFloatHandleWidthDp,
            lockPosition = settings.clipboardHistoryFloatLockPosition,
            landscapeEnabled = settings.clipboardHistoryFloatEnabledLandscape,
        )
    }
}
