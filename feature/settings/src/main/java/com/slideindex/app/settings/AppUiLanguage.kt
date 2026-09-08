package com.slideindex.app.settings

/** 应用界面语言（与系统语言解耦时可覆盖）。 */
enum class AppUiLanguage {
    SYSTEM,
    ZH,
    EN,
    JA,
    ;

    fun toStorageTag(): String = when (this) {
        SYSTEM -> ""
        ZH -> "zh"
        EN -> "en"
        JA -> "ja"
    }

    fun toLanguageTags(): String? = when (this) {
        SYSTEM -> null
        ZH -> "zh"
        EN -> "en"
        JA -> "ja"
    }

    companion object {
        fun fromStorageTag(tag: String?): AppUiLanguage = when (tag?.trim()?.lowercase()) {
            null, "" -> SYSTEM
            "zh", "zh-cn", "zh-hans" -> ZH
            "en" -> EN
            "ja" -> JA
            else -> SYSTEM
        }
    }
}
