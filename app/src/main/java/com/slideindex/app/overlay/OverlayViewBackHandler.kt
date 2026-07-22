package com.slideindex.app.overlay

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat

/** Routes system back (key + gesture) to overlay [ComposeView] windows. */
internal class OverlayViewBackHandler(
    private val view: View,
    private val onBack: () -> Unit,
) {
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var unhandledKeyListener: ViewCompat.OnUnhandledKeyEventListenerCompat? = null

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
        val keyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            if (event.keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@OnUnhandledKeyEventListenerCompat false
            }
            onBack()
            true
        }
        unhandledKeyListener = keyListener
        ViewCompat.addOnUnhandledKeyEventListener(view, keyListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { onBack() }
            backInvokedCallback = callback
            view.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
        }
    }

    fun detach() {
        view.setOnKeyListener(null)
        unhandledKeyListener?.let { listener ->
            ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
        }
        unhandledKeyListener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
            }
        }
        backInvokedCallback = null
    }
}
