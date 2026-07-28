package com.slideindex.app.nativeengine

enum class NativeEnginePackDownloadPhase {
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    READY,
    FAILED,
    CANCELLED,
}

data class NativeEnginePackDownloadState(
    val packId: String,
    val phase: NativeEnginePackDownloadPhase,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
) {
    val progress: Float?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return (bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}
