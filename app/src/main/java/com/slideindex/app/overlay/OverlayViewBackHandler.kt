package com.slideindex.app.overlay

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Routes system back (key + gesture) to overlay [ComposeView] windows. */
internal class OverlayViewBackHandler(
    private val view: View,
    private val onBack: () -> Unit,
) {
    private var backInvokedCallback: OnBackInvokedCallback? = null

    fun attach() {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            onBack()
            true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { onBack() }
            backInvokedCallback = callback
            view.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
        }
    }

    fun detach() {
        view.setOnKeyListener(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
            }
        }
        backInvokedCallback = null
    }
}
