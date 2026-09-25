package com.slideindex.app.settings

enum class ClipboardMonitoringMode(val storageValue: String) {
    FOLLOW_PRIVILEGE("follow_privilege"),
    SHIZUKU_LOGS("shizuku_logs"),
    SHIZUKU_HIDDEN_API("shizuku_hidden_api"),
    ROOT_LOGS("root_logs"),
    ROOT_HIDDEN_API("root_hidden_api"),
    LSPOSED("lsposed"),
    STANDARD("standard"),
    ;

    val usesRoot: Boolean
        get() = this == ROOT_LOGS || this == ROOT_HIDDEN_API

    val usesHiddenApi: Boolean
        get() = this == SHIZUKU_HIDDEN_API || this == ROOT_HIDDEN_API

    val usesStandardApi: Boolean
        get() = this == STANDARD

    /** LSPosed 白名单：把白名单里的包伪装成默认输入法，从而免焦点后台读剪贴板。 */
    val usesLsposed: Boolean
        get() = this == LSPOSED

    fun effective(privilegeMode: PrivilegeMode): ClipboardMonitoringMode =
        when (this) {
            FOLLOW_PRIVILEGE -> when (privilegeMode) {
                PrivilegeMode.ROOT -> ROOT_LOGS
                PrivilegeMode.SHIZUKU -> SHIZUKU_LOGS
            }
            else -> this
        }

    fun remappedForPrivilege(privilegeMode: PrivilegeMode): ClipboardMonitoringMode? {
        if (this == FOLLOW_PRIVILEGE || this == STANDARD || this == LSPOSED) return null
        val remapped = manualCaptureKind().toStoredMode(privilegeMode)
        return if (remapped == this) null else remapped
    }

    fun isManualOverride(): Boolean = this != FOLLOW_PRIVILEGE

    fun manualCaptureKind(): ClipboardMonitoringCaptureKind =
        if (usesHiddenApi) ClipboardMonitoringCaptureKind.HIDDEN_API
        else ClipboardMonitoringCaptureKind.LOGCAT

    fun manualPrivilegeMode(): PrivilegeMode =
        if (usesRoot) PrivilegeMode.ROOT else PrivilegeMode.SHIZUKU

    companion object {
        fun fromStorage(value: String?): ClipboardMonitoringMode {
            if (value.isNullOrBlank()) return FOLLOW_PRIVILEGE
            entries.firstOrNull { it.storageValue == value }?.let { return it }
            return when (value) {
                "logcat" -> SHIZUKU_LOGS
                "lsposed" -> SHIZUKU_HIDDEN_API
                else -> SHIZUKU_LOGS
            }
        }
    }
}

enum class ClipboardMonitoringCaptureKind {
    HIDDEN_API,
    LOGCAT,
    ;

    fun toStoredMode(privilegeMode: PrivilegeMode): ClipboardMonitoringMode =
        when (privilegeMode) {
            PrivilegeMode.ROOT -> when (this) {
                HIDDEN_API -> ClipboardMonitoringMode.ROOT_HIDDEN_API
                LOGCAT -> ClipboardMonitoringMode.ROOT_LOGS
            }
            PrivilegeMode.SHIZUKU -> when (this) {
                HIDDEN_API -> ClipboardMonitoringMode.SHIZUKU_HIDDEN_API
                LOGCAT -> ClipboardMonitoringMode.SHIZUKU_LOGS
            }
        }
}

fun AppSettings.effectiveClipboardMonitoringMode(): ClipboardMonitoringMode =
    clipboardBackgroundMonitoringMode.effective(privilegeMode)

/**
 * LSPosed 模式白名单的默认项：本应用自身。
 *
 * 放进去后系统会把它当成默认输入法放行（免焦点读剪贴板）；用户可以在设置里移除。
 */
const val CLIPBOARD_LSPOSED_SELF_PACKAGE = "com.slideindex.app"
