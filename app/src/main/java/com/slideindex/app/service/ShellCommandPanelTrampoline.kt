package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.shell.ShellCommand

/**
 * Opens the shell command panel in a transparent [Activity] so the grid, editor, and result
 * sheets share one window (no z-order fight with [TYPE_ACCESSIBILITY_OVERLAY]).
 */
object ShellCommandPanelTrampoline {
    @Volatile
    private var active = false
    @Volatile
    private var continuousPick = false
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var onPersist: ((List<ShellCommand>) -> Unit)? = null
    private var activityFinisher: (() -> Unit)? = null
    private var continuousDismissRequest: (() -> Unit)? = null

    fun isActive(): Boolean = active

    fun launch(
        context: Context,
        continuousPick: Boolean,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit,
        onPersist: (List<ShellCommand>) -> Unit
    ) {
        cancelPending()
        active = true
        this.continuousPick = continuousPick
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        this.onPersist = onPersist
        runCatching {
            context.startActivity(
                ShellCommandPanelTrampolineActivity.createIntent(context)
            )
        }.onFailure {
            deliverDismiss()
        }
    }

    internal fun launchedAsContinuousPick(): Boolean = continuousPick

    internal fun runPrepareIfNeeded() {
        onPrepare?.invoke()
        onPrepare = null
    }

    internal fun registerActivityFinisher(finish: () -> Unit) {
        activityFinisher = finish
    }

    internal fun unregisterActivityFinisher() {
        activityFinisher = null
    }

    internal fun registerContinuousDismissRequest(handler: () -> Unit) {
        continuousDismissRequest = handler
    }

    internal fun unregisterContinuousDismissRequest() {
        continuousDismissRequest = null
    }

    internal fun onCommandsPersist(commands: List<ShellCommand>) {
        onPersist?.invoke(commands)
    }

    internal fun deliverDismiss() {
        if (!active) return
        active = false
        continuousPick = false
        val dismiss = onDismiss
        clearCallbacks()
        dismiss?.invoke()
    }

    fun closeIfActive() {
        activityFinisher?.invoke()
    }

    fun closeIfContinuous() {
        continuousDismissRequest?.invoke()
    }

    fun cancelPending() {
        active = false
        continuousPick = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onPrepare = null
        onDismiss = null
        onPersist = null
        activityFinisher = null
        continuousDismissRequest = null
    }
}
