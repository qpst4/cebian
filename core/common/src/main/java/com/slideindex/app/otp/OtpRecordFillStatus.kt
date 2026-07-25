package com.slideindex.app.otp

enum class OtpRecordFillStatus {
    NONE,
    PENDING,
    LSPOSED,
    ACCESSIBILITY,
    FAILED,
    ;

    fun storageKey(): String = name.lowercase()

    companion object {
        fun fromStorageKey(key: String?): OtpRecordFillStatus =
            entries.firstOrNull { it.storageKey() == key?.lowercase() } ?: NONE

        fun fromFillResult(success: Boolean, strategy: String): OtpRecordFillStatus {
            if (!success) return FAILED
            return when (strategy) {
                "system_inject" -> LSPOSED
                "focused_node",
                "best_editable_node",
                "group_nodes",
                "set_text",
                -> ACCESSIBILITY
                else -> FAILED
            }
        }
    }
}
