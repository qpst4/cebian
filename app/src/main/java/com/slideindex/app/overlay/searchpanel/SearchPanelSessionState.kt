package com.slideindex.app.overlay.searchpanel

/** Persists the last text query across search panel dismiss / recreate cycles. */
internal object SearchPanelSessionState {
    var lastTextQuery: String = ""
    var persistBeforeDismiss: (() -> Unit)? = null
    /** Return true if back was consumed (e.g. dismiss file preview only). */
    var onBackPressed: (() -> Boolean)? = null
    /**
     * 窗口获得焦点时由浮层窗口调用：把焦点交给搜索框（返回是否成功，输入框可能还没进入组合）。
     *
     * 走这条回调是为了绕开 Compose 重组延迟，尽早把焦点就绪，好让输入法更早被拉起。
     */
    var focusSearchTextField: (() -> Boolean)? = null
}
