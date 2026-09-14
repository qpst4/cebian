package com.slideindex.app.translate

enum class TranslateEngine {
    GOOGLE,
    ML_KIT,
    CLOUD_LLM,
    ;

    companion object {
        fun fromStorageKey(key: String?): TranslateEngine =
            entries.find { it.name.equals(key, ignoreCase = true) || it.storageKey == key }
                ?: GOOGLE

        val GOOGLE_KEY: String get() = GOOGLE.storageKey
        val ML_KIT_KEY: String get() = ML_KIT.storageKey
        val CLOUD_LLM_KEY: String get() = CLOUD_LLM.storageKey
    }

    val storageKey: String
        get() = when (this) {
            GOOGLE -> "google"
            ML_KIT -> "mlkit"
            CLOUD_LLM -> "cloud"
        }
}
