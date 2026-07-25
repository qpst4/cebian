package com.slideindex.app.overlay

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Overlay 窗口上系统 [ActionMode] / FloatingToolbar 常无法显示；
 * 优先尝试 ActionMode，失败则回退到 [PopupMenu]。
 */
private class OverlayTextToolbar(
    private val view: View,
) : TextToolbar {
    private var actionMode: ActionMode? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun hide() {
        actionMode?.finish()
        actionMode = null
        status = TextToolbarStatus.Hidden
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        showMenuInternal(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        showMenuInternal(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    private fun showMenuInternal(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        hide()
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                addMenuItems(menu, onCutRequested, onCopyRequested, onPasteRequested, onSelectAllRequested)
                return menu.size() > 0
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    MENU_CUT -> onCutRequested?.invoke()
                    MENU_COPY -> onCopyRequested?.invoke()
                    MENU_PASTE -> onPasteRequested?.invoke()
                    MENU_SELECT_ALL -> onSelectAllRequested?.invoke()
                }
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                if (actionMode === mode) {
                    actionMode = null
                    status = TextToolbarStatus.Hidden
                }
            }
        }
        actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
            ?: view.startActionMode(callback)
        if (actionMode != null) {
            status = TextToolbarStatus.Shown
            return
        }
        showPopupFallback(
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
        )
    }

    private fun showPopupFallback(
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        if (
            onCopyRequested == null &&
            onPasteRequested == null &&
            onCutRequested == null &&
            onSelectAllRequested == null
        ) {
            return
        }
        val popup = PopupMenu(view.context, view)
        addMenuItems(popup.menu, onCutRequested, onCopyRequested, onPasteRequested, onSelectAllRequested)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CUT -> onCutRequested?.invoke()
                MENU_COPY -> onCopyRequested?.invoke()
                MENU_PASTE -> onPasteRequested?.invoke()
                MENU_SELECT_ALL -> onSelectAllRequested?.invoke()
            }
            status = TextToolbarStatus.Hidden
            true
        }
        popup.setOnDismissListener {
            status = TextToolbarStatus.Hidden
        }
        status = TextToolbarStatus.Shown
        popup.show()
    }

    private fun addMenuItems(
        menu: Menu,
        onCutRequested: (() -> Unit)?,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        onCutRequested?.let { menu.add(0, MENU_CUT, 0, android.R.string.cut) }
        onCopyRequested?.let { menu.add(0, MENU_COPY, 0, android.R.string.copy) }
        onPasteRequested?.let { menu.add(0, MENU_PASTE, 0, android.R.string.paste) }
        onSelectAllRequested?.let { menu.add(0, MENU_SELECT_ALL, 0, android.R.string.selectAll) }
    }

    private companion object {
        private const val MENU_CUT = 1
        private const val MENU_COPY = 2
        private const val MENU_PASTE = 3
        private const val MENU_SELECT_ALL = 4
    }
}

@Composable
fun OverlayTextToolbarProvider(content: @Composable () -> Unit) {
    val view = LocalView.current
    CompositionLocalProvider(
        LocalTextToolbar provides remember(view) { OverlayTextToolbar(view) },
    ) {
        content()
    }
}
