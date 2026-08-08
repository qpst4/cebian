package com.slideindex.app.overlay

import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow

/**
 * Dismisses the topmost user-visible overlay for sidebar [com.slideindex.app.gesture.GestureAction.Back].
 * Order: fullscreen / top layers first, then floating widget panel (matches [GlobalOverlayDismissHelper]).
 */
internal object OverlayBackDismissChain {
    fun dismissTopOverlay(): Boolean {
        return when {
            WidgetPickerOverlayWindow.isShowing -> {
                WidgetPickerOverlayWindow.dismissFromBack()
                true
            }
            WidgetPopupOverlayWindow.isShowing -> {
                WidgetPopupOverlayWindow.dismiss()
                true
            }
            SearchPanelOverlayWindow.isShowing -> {
                SearchPanelOverlayWindow.dismiss()
                true
            }
            SideBubbleOverlayWindow.isShowing -> {
                SideBubbleOverlayWindow.dismiss()
                true
            }
            OhoQuickToolsOverlayWindow.isShowing -> {
                OhoQuickToolsOverlayWindow.dismiss()
                true
            }
            HoneycombAppPickerOverlayWindow.isShowing -> {
                HoneycombAppPickerOverlayWindow.dismiss()
                true
            }
            else -> false
        }
    }
}
