package com.slideindex.app.settings

/** 取词面板图片区底栏：分享引擎主芯片在左或右；图标组在另一侧。 */
enum class PickResultImageToolbarPosition(val storageKey: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultImageToolbarPosition =
            when (key) {
                RIGHT.storageKey -> RIGHT
                else -> LEFT
            }
    }
}
