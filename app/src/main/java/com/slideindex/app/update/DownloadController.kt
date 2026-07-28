package com.slideindex.app.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DownloadController {
    enum class DownloadStatus { IDLE, DOWNLOADING, FAILED }

    data class DownloadUiState(
        val status: DownloadStatus = DownloadStatus.IDLE,
        val version: String = "",
        val progress: Int = 0,
    )

    private val _flow = MutableStateFlow(DownloadUiState())
    val flow: StateFlow<DownloadUiState> = _flow.asStateFlow()

    fun onStart(version: String) {
        _flow.value = DownloadUiState(DownloadStatus.DOWNLOADING, version, 0)
    }

    fun onProgress(percent: Int) {
        _flow.update { it.copy(progress = percent.coerceIn(0, 100)) }
    }

    fun onFinish() {
        _flow.value = DownloadUiState()
    }

    fun onFailed(version: String) {
        _flow.update { it.copy(status = DownloadStatus.FAILED, version = version) }
    }

    fun reset() {
        _flow.value = DownloadUiState()
    }
}
