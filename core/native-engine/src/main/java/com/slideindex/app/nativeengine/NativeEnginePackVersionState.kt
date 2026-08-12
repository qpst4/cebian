package com.slideindex.app.nativeengine

data class NativeEnginePackVersionState(
    val installedRevision: Int?,
    val installedDisplayVersion: String?,
    val latestRevision: Int,
    val latestDisplayVersion: String?,
    val updateAvailable: Boolean,
)
