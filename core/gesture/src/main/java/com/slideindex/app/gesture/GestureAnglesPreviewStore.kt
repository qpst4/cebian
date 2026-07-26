package com.slideindex.app.gesture

/**
 * 手势角度设置页的临时预览值；不落盘，离开预览后应清空。
 */
object GestureAnglesPreviewStore {
    @Volatile
    var current: GestureAngles? = null
}
