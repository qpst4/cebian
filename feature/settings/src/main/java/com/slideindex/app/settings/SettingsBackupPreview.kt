package com.slideindex.app.settings

data class SettingsBackupPreview(
    val formatVersion: Int,
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val totalPreferencesCount: Int,
    val domains: Set<SettingsDomain>,
    val hasOtpRecords: Boolean,
    val hasNotificationHistory: Boolean,
)

enum class SettingsDomain {
    EDGE_GESTURES,
    SHAKE_GESTURES,
    MESSAGE_DANMAKU,
    OTP_AUTO_INPUT,
    FLOATING_POINTER,
    WIDGET_PANEL,
    QUICK_LAUNCHER,
    FREE_WINDOW,
    GENERAL,
}

fun mapPreferenceKeyToDomain(key: String): SettingsDomain {
    return when {
        key.startsWith("edge_") || key.startsWith("left_") || key.startsWith("right_") || key.startsWith("trigger_") || key.startsWith("gesture_") || key == "intercept_system_back_gesture" || key == "limit_max_intercept_length" || key == "short_swipe_distance_dp" || key == "long_swipe_distance_dp" || key == "animation_styles" || key == "index_height_fraction" || key == "apps_per_row" || key == "panel_opacity" -> SettingsDomain.EDGE_GESTURES
        key.startsWith("shake_") || key.startsWith("hold_shake_") || key.startsWith("lock_screen_shake_") ||
            key.startsWith("independent_app_shake_") || key.startsWith("face_down_") -> SettingsDomain.SHAKE_GESTURES
        key.startsWith("message_") -> SettingsDomain.MESSAGE_DANMAKU
        key.startsWith("otp_") -> SettingsDomain.OTP_AUTO_INPUT
        key.startsWith("floating_pointer_") -> SettingsDomain.FLOATING_POINTER
        key.startsWith("widget_panel_") -> SettingsDomain.WIDGET_PANEL
        key.startsWith("quick_launcher") -> SettingsDomain.QUICK_LAUNCHER
        key.startsWith("free_window_") || key.startsWith("app_launch_policy") -> SettingsDomain.FREE_WINDOW
        else -> SettingsDomain.GENERAL
    }
}
