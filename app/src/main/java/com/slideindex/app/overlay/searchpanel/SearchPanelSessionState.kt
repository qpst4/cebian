package com.slideindex.app.overlay.searchpanel

/** Persists the last text query across search panel dismiss / recreate cycles. */
internal object SearchPanelSessionState {
    var lastTextQuery: String = ""
    var persistBeforeDismiss: (() -> Unit)? = null
    /** Return true if back was consumed (e.g. dismiss file preview only). */
    var onBackPressed: (() -> Boolean)? = null
}
