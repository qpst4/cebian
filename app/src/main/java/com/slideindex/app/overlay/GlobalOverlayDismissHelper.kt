package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.copy.UniversalCopyOverlay
import com.slideindex.app.freezer.FreezerOverlayWindow
import com.slideindex.app.overlay.appswitcher.AppSwitcherOverlayWindow
import com.slideindex.app.overlay.holographic.HolographicLauncherOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.overlay.volumepanel.VolumePanelOverlayWindow
import com.slideindex.app.translate.overlay.ScreenTranslationController

/**
 * Helper to safely dismiss all active overlay panels when screen turns off or device locks.
 */
object GlobalOverlayDismissHelper {
    private const val TAG = "GlobalOverlayDismiss"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var dismissPosted = false

    fun dismissAllPanels() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            if (dismissPosted) return
            dismissPosted = true
            mainHandler.post {
                dismissPosted = false
                dismissAllPanels()
            }
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
        runCatching { com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.dismiss() }
        runCatching { com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.dismiss() }
        runCatching { FloatBallImageSearchPanel.dismiss() }
        runCatching { FloatBallStashPanel.dismiss() }
        runCatching { FreezerOverlayWindow.dismiss() }
        runCatching { FloatBallTranslatePanel.dismiss() }
        runCatching { FloatIconOverlayWindow.dismiss() }
        runCatching { MessageReplyOverlayWindow.dismiss() }
        runCatching { ForegroundActivityInspectorOverlayWindow.dismiss() }
        runCatching { VolumePanelOverlayWindow.dismiss() }
        runCatching { FloatingPointerOverlayWindow.dismiss() }
        runCatching { HolographicLauncherOverlayWindow.dismiss() }
        runCatching { DanmakuOverlayWindow.detach() }
        runCatching { UniversalCopyOverlay.dismiss() }
        runCatching { ScreenTranslationController.dismissIfActive() }
        runCatching { com.slideindex.app.service.ClipboardFloatService.hideWindowFromStatic() }
    }
}
