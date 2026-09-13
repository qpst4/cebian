package com.slideindex.app.overlay

import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import com.slideindex.app.di.OverlayDependencyAccess

/**
 * Routes system back (gesture + key) to overlay [ComposeView] windows.
 *
 * When predictive back is enabled for the app (API 33+), uses [OnBackInvokedCallback] only.
 * Flyme must not combine that with legacy key listeners: it registers
 * [android.view.ViewRootImpl.registerCompatOnBackInvokedCallback] for them, which loops
 * with [android.view.ViewRootImpl.injectBackKeyEvents].
 *
 * When predictive back is off at the app level, [OnBackInvokedCallback] is not dispatched;
 * legacy [OnUnhandledKeyEventListenerCompat] handles injected [KeyEvent.KEYCODE_BACK].
 */
internal class OverlayViewBackHandler(
    private val view: View,
    private val onBack: () -> Unit,
) {
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var unhandledKeyListener: ViewCompat.OnUnhandledKeyEventListenerCompat? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var usesUnhandledKeyBackListener = false
    private var handlingBack = false
    private var registerAttempts = 0
    private var predictiveBackEnabled = false

    fun attach(requestViewFocus: Boolean = true) {
        predictiveBackEnabled = resolvePredictiveBackEnabled(view.context)
        if (requestViewFocus) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
        }
        if (shouldUseOnBackInvoked()) {
            registerAttempts = 0
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    /** Retry registration after the overlay window becomes focusable or predictive-back toggles. */
    fun refresh() {
        predictiveBackEnabled = resolvePredictiveBackEnabled(view.context)
        detachInternal(clearHandlingFlag = false)
        if (shouldUseOnBackInvoked()) {
            registerAttempts = 0
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    private fun shouldUseOnBackInvoked(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && predictiveBackEnabled

    private fun scheduleRegisterOnBackInvoked() {
        fun tryRegister() {
            if (backInvokedCallback != null || usesUnhandledKeyBackListener) return
            val dispatcher = resolveBackInvokedDispatcher()
            if (dispatcher != null) {
                registerOnBackInvoked(dispatcher)
                return
            }
            if (!view.isAttachedToWindow) return
            if (registerAttempts++ < MAX_ON_BACK_REGISTER_ATTEMPTS) {
                view.post { tryRegister() }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        view.findOnBackInvokedDispatcher()?.let { return it }
        val root = view.rootView
        if (root !== view) {
            root.findOnBackInvokedDispatcher()?.let { return it }
        }
        return null
    }

    private fun registerOnBackInvoked(dispatcher: OnBackInvokedDispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
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
        try {
            onBack()
        } finally {
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
        detachInternal(clearHandlingFlag = true)
    }

    private fun detachInternal(clearHandlingFlag: Boolean) {
        attachListener?.let { view.removeOnAttachStateChangeListener(it) }
        attachListener = null
        registerAttempts = 0
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
        if (clearHandlingFlag) {
            handlingBack = false
        }
    }

    private companion object {
        private const val MAX_ON_BACK_REGISTER_ATTEMPTS = 12

        private fun resolvePredictiveBackEnabled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
            return OverlayDependencyAccess.overlayDependencies(context.applicationContext)
                ?.settingsRepository
                ?.readSnapshot()
                ?.predictiveBackEnabled
                ?: false
        }
    }
}
