package com.slideindex.app.overlay

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat

/**
 * Routes system back (gesture + key) to overlay [ComposeView] windows.
 *
 * API 33+ must not call [View.setOnKeyListener] on overlay roots: Flyme registers
 * [android.view.ViewRootImpl.registerCompatOnBackInvokedCallback] for legacy key listeners,
 * which loops with [android.view.ViewRootImpl.injectBackKeyEvents].
 */
internal class OverlayViewBackHandler(
    private val view: View,
    private val onBack: () -> Unit
) {
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var unhandledKeyListener: ViewCompat.OnUnhandledKeyEventListenerCompat? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var usesUnhandledKeyBackListener = false
    private var handlingBack = false

    fun attach(requestViewFocus: Boolean = true) {
        if (requestViewFocus) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    private fun scheduleRegisterOnBackInvoked() {
        fun tryRegister() {
            if (backInvokedCallback != null || usesUnhandledKeyBackListener) return
            val dispatcher = resolveBackInvokedDispatcher()
            if (dispatcher != null) {
                registerOnBackInvoked(dispatcher)
                return
            }
            if (view.isAttachedToWindow) {
                registerUnhandledKeyBackListener()
            }
        }

        if (view.isAttachedToWindow) {
            view.post { tryRegister() }
            return
        }

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                view.removeOnAttachStateChangeListener(this)
                attachListener = null
                view.post { tryRegister() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        attachListener = listener
        view.addOnAttachStateChangeListener(listener)
    }

    private fun resolveBackInvokedDispatcher(): OnBackInvokedDispatcher? {
        view.findOnBackInvokedDispatcher()?.let { return it }
        val root = view.rootView
        if (root !== view) {
            root.findOnBackInvokedDispatcher()?.let { return it }
        }
        return null
    }

    private fun registerOnBackInvoked(dispatcher: OnBackInvokedDispatcher) {
        val callback = OnBackInvokedCallback { dispatchBack() }
        backInvokedCallback = callback
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
    }

    private fun dispatchBack() {
        if (handlingBack) return
        handlingBack = true
        val callback = backInvokedCallback
        val dispatcher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveBackInvokedDispatcher()
        } else {
            null
        }
        if (callback != null && dispatcher != null) {
            dispatcher.unregisterOnBackInvokedCallback(callback)
        }
        try {
            onBack()
        } finally {
            if (callback != null && dispatcher != null) {
                dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    callback,
                )
            }
            view.post { handlingBack = false }
        }
    }

    private fun registerUnhandledKeyBackListener() {
        if (usesUnhandledKeyBackListener) return
        usesUnhandledKeyBackListener = true
        val keyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            if (event.keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@OnUnhandledKeyEventListenerCompat false
            }
            dispatchBack()
            true
        }
        unhandledKeyListener = keyListener
        ViewCompat.addOnUnhandledKeyEventListener(view, keyListener)
    }

    fun detach() {
        attachListener?.let { view.removeOnAttachStateChangeListener(it) }
        attachListener = null
        if (usesUnhandledKeyBackListener) {
            unhandledKeyListener?.let { listener ->
                ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
            }
            unhandledKeyListener = null
            usesUnhandledKeyBackListener = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                resolveBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
            }
        }
        backInvokedCallback = null
        handlingBack = false
    }
}
