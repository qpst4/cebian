package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandCodec

/**
 * Opens the shell command editor in a transparent [Activity] so text fields use the normal
 * window touch / IME pipeline (overlay [ComposeView] dialogs are unreliable).
 */
object ShellCommandEditorTrampoline {
    @Volatile
    private var active = false
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var onSave: ((ShellCommand) -> Unit)? = null
    private var onDelete: (() -> Unit)? = null
    private var activityFinisher: (() -> Unit)? = null

    fun isActive(): Boolean = active

    fun launch(
        context: Context,
        existing: ShellCommand?,
        shizukuGranted: Boolean,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit,
        onSave: (ShellCommand) -> Unit,
        onDelete: (() -> Unit)?
    ) {
        cancelPending()
        active = true
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        this.onSave = onSave
        this.onDelete = onDelete
        runCatching {
            context.startActivity(
                ShellCommandEditorTrampolineActivity.createIntent(
                    context = context,
                    existing = existing,
                    shizukuGranted = shizukuGranted
                )
            )
        }.onFailure {
            deliverDismiss()
        }
    }

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

    internal fun onItemSave(command: ShellCommand) {
        onSave?.invoke(command)
    }

    internal fun onItemDelete() {
        onDelete?.invoke()
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
        onPrepare = null
        onDismiss = null
        onSave = null
        onDelete = null
    }
}
