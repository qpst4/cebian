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
    resolveClipboardMonitoringMode(
        channel = clipboardMonitoringChannel,
        capture = clipboardMonitoringCapture,
        privilegeMode = privilegeMode,
    )

/** 提权通道：决定「谁有权限去听」。 */
enum class ClipboardMonitoringChannel(val storageValue: String) {
    /** 跟随全局特权设置（Shizuku / Root）。 */
    FOLLOW_PRIVILEGE("follow_privilege"),
    SHIZUKU("shizuku"),
    ROOT("root"),
    /** LSPosed 模块白名单：把应用伪装成默认输入法，免焦点直接读。 */
    LSPOSED("lsposed"),
    /** 免提权的公开 API。 */
    STANDARD("standard"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardMonitoringChannel =
            entries.firstOrNull { it.storageValue == value } ?: FOLLOW_PRIVILEGE
    }
}

/** 采集方式：决定「用什么手段拿到变更事件」。仅对 Shizuku / Root 通道有意义。 */
enum class ClipboardMonitoringCapture(val storageValue: String) {
    /** 注册系统隐藏剪贴板监听。 */
    HIDDEN_API("hidden_api"),
    /** 读取系统 ClipboardService 日志。 */
    LOGCAT("logcat"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardMonitoringCapture =
            entries.firstOrNull { it.storageValue == value } ?: LOGCAT
    }
}

fun channelOf(mode: ClipboardMonitoringMode): ClipboardMonitoringChannel = when (mode) {
    ClipboardMonitoringMode.FOLLOW_PRIVILEGE -> ClipboardMonitoringChannel.FOLLOW_PRIVILEGE
    ClipboardMonitoringMode.SHIZUKU_LOGS,
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API -> ClipboardMonitoringChannel.SHIZUKU
    ClipboardMonitoringMode.ROOT_LOGS,
    ClipboardMonitoringMode.ROOT_HIDDEN_API -> ClipboardMonitoringChannel.ROOT
    ClipboardMonitoringMode.LSPOSED -> ClipboardMonitoringChannel.LSPOSED
    ClipboardMonitoringMode.STANDARD -> ClipboardMonitoringChannel.STANDARD
}

fun captureOf(mode: ClipboardMonitoringMode): ClipboardMonitoringCapture = when (mode) {
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API,
    ClipboardMonitoringMode.ROOT_HIDDEN_API -> ClipboardMonitoringCapture.HIDDEN_API
    // 跟随特权模式的旧语义是「日志」；LSPosed / 标准没有采集方式之分，取默认值即可。
    else -> ClipboardMonitoringCapture.LOGCAT
}

/** 通道 + 采集方式 → 内部使用的具体监听模式。 */
fun resolveClipboardMonitoringMode(
    channel: ClipboardMonitoringChannel,
    capture: ClipboardMonitoringCapture,
    privilegeMode: PrivilegeMode,
): ClipboardMonitoringMode = when (channel) {
    ClipboardMonitoringChannel.STANDARD -> ClipboardMonitoringMode.STANDARD
    ClipboardMonitoringChannel.LSPOSED -> ClipboardMonitoringMode.LSPOSED
    ClipboardMonitoringChannel.ROOT -> when (capture) {
        ClipboardMonitoringCapture.HIDDEN_API -> ClipboardMonitoringMode.ROOT_HIDDEN_API
        ClipboardMonitoringCapture.LOGCAT -> ClipboardMonitoringMode.ROOT_LOGS
    }
    ClipboardMonitoringChannel.SHIZUKU -> when (capture) {
        ClipboardMonitoringCapture.HIDDEN_API -> ClipboardMonitoringMode.SHIZUKU_HIDDEN_API
        ClipboardMonitoringCapture.LOGCAT -> ClipboardMonitoringMode.SHIZUKU_LOGS
    }
    ClipboardMonitoringChannel.FOLLOW_PRIVILEGE -> when (privilegeMode) {
        PrivilegeMode.ROOT -> when (capture) {
            ClipboardMonitoringCapture.HIDDEN_API -> ClipboardMonitoringMode.ROOT_HIDDEN_API
            ClipboardMonitoringCapture.LOGCAT -> ClipboardMonitoringMode.ROOT_LOGS
        }
        PrivilegeMode.SHIZUKU -> when (capture) {
            ClipboardMonitoringCapture.HIDDEN_API -> ClipboardMonitoringMode.SHIZUKU_HIDDEN_API
            ClipboardMonitoringCapture.LOGCAT -> ClipboardMonitoringMode.SHIZUKU_LOGS
        }
    }
}

/**
 * LSPosed 模式白名单的默认项：本应用自身。
 *
 * 放进去后系统会把它当成默认输入法放行（免焦点读剪贴板）；用户可以在设置里移除。
 */
const val CLIPBOARD_LSPOSED_SELF_PACKAGE = "com.slideindex.app"
