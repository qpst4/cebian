package com.slideindex.app.service

import android.content.Context
import android.content.Intent

/**
 * Shows shell command output in a transparent [Activity] so scrolling uses the normal window
 * touch pipeline (overlay canvas scroll is unreliable and can wedge the edge session).
 */
object ShellCommandResultTrampoline {
    data class Payload(
        val label: String,
        val command: String,
        val exitCode: Int,
        val output: String
    )

    @Volatile
    private var active = false
    private var payload: Payload? = null
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var activityFinisher: (() -> Unit)? = null

    fun isActive(): Boolean = active

    fun launch(
        context: Context,
        result: Payload,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit
    ) {
        cancelPending()
        active = true
        payload = result
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        runCatching {
            context.startActivity(
                ShellCommandResultTrampolineActivity.createIntent(context)
            )
        }.onFailure {
            deliverDismiss()
        }
    }

    internal fun runPrepareIfNeeded() {
        onPrepare?.invoke()
        onPrepare = null
    }

    internal fun consumePayload(): Payload? {
        val data = payload
        payload = null
        return data
    }

    internal fun registerActivityFinisher(finish: () -> Unit) {
        activityFinisher = finish
    }

    internal fun unregisterActivityFinisher() {
        activityFinisher = null
    }

    internal fun deliverDismiss() {
        if (!active) return
        active = false
        val dismiss = onDismiss
        clearCallbacks()
        dismiss?.invoke()
    }

    fun closeIfActive() {
        if (!active) return
        activityFinisher?.invoke() ?: deliverDismiss()
    }

    fun cancelPending() {
        active = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        payload = null
        onPrepare = null
        onDismiss = null
    }
}
