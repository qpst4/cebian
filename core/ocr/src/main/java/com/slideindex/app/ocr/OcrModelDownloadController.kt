package com.slideindex.app.ocr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OCR 模型下载的全局状态（主进程内存单例）。
 *
 * 由前台下载 Service 写入，设置页等 UI 只读订阅，不绑定 ViewModel 生命周期。
 */
object OcrModelDownloadController {

    private val _state = MutableStateFlow<OcrModelDownloadState?>(null)
    val state: StateFlow<OcrModelDownloadState?> = _state.asStateFlow()

    /** Service 级单飞：非 null 表示已有下载任务在跑。 */
    @Volatile
    var activeModelId: String? = null
        private set

    fun onStart(modelId: String) {
        activeModelId = modelId
    }

    fun update(state: OcrModelDownloadState) {
        _state.value = state
    }

    fun clearActive() {
        activeModelId = null
    }

    fun reset() {
        _state.value = null
        activeModelId = null
    }
}
