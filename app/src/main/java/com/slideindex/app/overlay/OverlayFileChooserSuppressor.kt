package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper

/**
 * Temporarily hides overlay windows so system file pickers launched from WebView are reachable.
 */
internal object OverlayFileChooserSuppressor {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var suppressDepth = 0

    fun suppressForFileChooser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForFileChooser() }
            return
        }
        if (suppressDepth++ > 0) return
        FloatBallImageSearchPanel.suppressForFileChooser()
    }

    fun restoreAfterFileChooser() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterFileChooser() }
            return
        }
        if (suppressDepth == 0) return
        suppressDepth = 0
        FloatBallImageSearchPanel.restoreAfterFileChooser()
    }
}
