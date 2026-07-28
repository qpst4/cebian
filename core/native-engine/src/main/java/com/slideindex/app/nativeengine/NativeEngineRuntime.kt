package com.slideindex.app.nativeengine

/**
 * 供非 Hilt 入口（如 [com.slideindex.app.segmentation.CppJiebaTokenizer]）访问引擎协调器。
 */
object NativeEngineRuntime {
    @Volatile
    var coordinator: NativeEnginePackCoordinator? = null

    /** 由 Application 注入，供取词分词首次使用时触发引擎下载。 */
    @Volatile
    var onRequestSegmentationPack: (() -> Unit)? = null

    fun requestSegmentationPackIfNeeded() {
        onRequestSegmentationPack?.invoke()
    }
}
