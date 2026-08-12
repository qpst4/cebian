package com.slideindex.app.ui.viewmodel

import com.slideindex.app.nativeengine.NativeEnginePackEntry

data class NativeEnginePackRowState(
    val entry: NativeEnginePackEntry,
    val installed: Boolean,
    val installedRevision: Int?,
    val installedDisplayVersion: String?,
    val updateAvailable: Boolean,
)
