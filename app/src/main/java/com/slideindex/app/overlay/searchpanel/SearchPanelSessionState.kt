package com.slideindex.app.overlay.searchpanel

/** Persists the last text query across search panel dismiss / recreate cycles. */
internal object SearchPanelSessionState {
    var lastTextQuery: String = ""
}
