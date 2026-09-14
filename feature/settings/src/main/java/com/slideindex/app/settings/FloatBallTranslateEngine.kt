package com.slideindex.app.settings

enum class FloatBallTranslateEngine(val storageKey: String) {
    GOOGLE("google"),
    ML_KIT("mlkit"),
    CLOUD_LLM("cloud"),
    ;

    companion object {
        fun fromStorageKey(key: String?): FloatBallTranslateEngine =
            entries.firstOrNull { it.storageKey == key } ?: GOOGLE
    }
}
