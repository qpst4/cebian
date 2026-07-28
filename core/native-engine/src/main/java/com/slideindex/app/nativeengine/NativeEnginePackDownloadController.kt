package com.slideindex.app.nativeengine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NativeEnginePackDownloadController {
    private val _state = MutableStateFlow<NativeEnginePackDownloadState?>(null)
    val state: StateFlow<NativeEnginePackDownloadState?> = _state.asStateFlow()

    @Volatile
    var activePackId: String? = null
        private set

    fun onStart(packId: String) {
        activePackId = packId
    }

    fun update(state: NativeEnginePackDownloadState) {
        _state.value = state
    }

    fun clearActive() {
        activePackId = null
    }

    fun reset() {
        _state.value = null
        activePackId = null
    }
}
