package com.slideindex.app.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow

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
        GlobalOverlayDismissHelper.dismissAllPanels()
        SearchPanelOverlayWindow.releaseWarmUp()
        FloatBallOverlay.recreateForLocaleChange()
    }
}
