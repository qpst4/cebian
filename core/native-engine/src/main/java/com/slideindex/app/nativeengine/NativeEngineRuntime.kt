package com.slideindex.app.nativeengine

/**
 * 供非 Hilt 入口（如 [com.slideindex.app.segmentation.CppJiebaTokenizer]）访问引擎协调器。
 */
object NativeEngineRuntime {
    @Volatile
    var coordinator: NativeEnginePackCoordinator? = null
}
