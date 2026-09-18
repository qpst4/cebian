package com.slideindex.app.settings

/** 取词面板底栏「复制选中」主按钮在左或右。 */
enum class PickResultCopyButtonPosition(val storageKey: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultCopyButtonPosition =
            when (key) {
                RIGHT.storageKey -> RIGHT
                else -> LEFT
            }
    }
}
