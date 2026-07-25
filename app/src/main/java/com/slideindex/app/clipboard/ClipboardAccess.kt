package com.slideindex.app.clipboard

object ClipboardAccess {
    @Suppress("StaticFieldLeak")
    var repository: ClipboardHistoryRepository? = null
}
