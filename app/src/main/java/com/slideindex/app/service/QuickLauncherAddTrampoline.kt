package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.PanelSide

/**
 * Opens the quick-launcher add picker in a transparent [Activity] so list scrolling uses the
 * normal window touch pipeline (overlay [ComposeView] + [LazyColumn] is unreliable).
 */
object QuickLauncherAddTrampoline {
    @Volatile
    private var active = false
    private var onPrepare: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var onAdd: ((QuickLauncherItem) -> Unit)? = null
    private var onRemove: ((QuickLauncherItem) -> Unit)? = null

    fun isActive(): Boolean = active

    fun launch(
        context: Context,
        panelSide: PanelSide,
        configuredAppPackages: Set<String>,
        configuredShortcutKeys: Set<String>,
        configuredActionKeys: Set<String>,
        onPrepare: () -> Unit,
        onDismiss: () -> Unit,
        onAdd: (QuickLauncherItem) -> Unit,
        onRemove: (QuickLauncherItem) -> Unit
    ) {
        cancelPending()
        active = true
        this.onPrepare = onPrepare
        this.onDismiss = onDismiss
        this.onAdd = onAdd
        this.onRemove = onRemove
        onPrepare()
        runCatching {
            context.startActivity(
                QuickLauncherAddTrampolineActivity.createIntent(
                    context = context,
                    panelSide = panelSide,
                    configuredAppPackages = configuredAppPackages,
                    configuredShortcutKeys = configuredShortcutKeys,
                    configuredActionKeys = configuredActionKeys
                )
            )
        }.onFailure {
            deliverDismiss()
        }
    }

    internal fun onItemAdd(item: QuickLauncherItem) {
        onAdd?.invoke(item)
    }

    internal fun onItemRemove(item: QuickLauncherItem) {
        onRemove?.invoke(item)
    }

    internal fun deliverDismiss() {
        if (!active) return
        active = false
        val dismiss = onDismiss
        clearCallbacks()
        dismiss?.invoke()
    }

    fun cancelPending() {
        active = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onPrepare = null
        onDismiss = null
        onAdd = null
        onRemove = null
    }
}
