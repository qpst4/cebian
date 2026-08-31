package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.overlay.appswitcher.AppSwitcherOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow

/**
 * Helper to safely dismiss all active overlay panels when screen turns off or device locks.
 */
object GlobalOverlayDismissHelper {
    private const val TAG = "GlobalOverlayDismiss"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun dismissAllPanels() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissAllPanels() }
            return
        }
        Log.i(TAG, "Dismissing all active overlay panels due to screen off / device lock")

        runCatching { SearchPanelOverlayWindow.dismiss() }
        runCatching { WidgetPickerOverlayWindow.dismiss() }
        runCatching { WidgetPopupOverlayWindow.dismiss() }
        runCatching { SideBubbleOverlayWindow.dismiss() }
        runCatching { FloatBallPickResultPanel.dismiss() }
        runCatching { RegionalPickOverlay.dismiss() }
        runCatching { OhoQuickToolsOverlayWindow.dismiss() }
        runCatching { HoneycombAppPickerOverlayWindow.dismiss() }
        runCatching { AppSwitcherOverlayWindow.dismiss() }
        runCatching { FloatBallImageSearchPanel.dismiss() }
        runCatching { FloatBallStashPanel.dismiss() }
        runCatching { FloatBallTranslatePanel.dismiss() }
        runCatching { FloatIconOverlayWindow.dismiss() }
        runCatching { MessageReplyOverlayWindow.dismiss() }
        runCatching { ForegroundActivityInspectorOverlayWindow.dismiss() }
        runCatching { com.slideindex.app.service.ClipboardFloatService.hideWindowFromStatic() }
    }
}
