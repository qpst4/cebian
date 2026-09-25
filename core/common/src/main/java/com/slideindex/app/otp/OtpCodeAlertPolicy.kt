package com.slideindex.app.otp

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

/**
 * 验证码提醒策略（纯逻辑，便于单测）。
 *
 * 对应上游 XposedSmsCode 的两个偏好：`pref_notification_retention_time`（秒，默认 5）
 * 与 `pref_auto_cancel_code_notification`。本实现把两者合并成一个"驻留时长"：
 * [RETENTION_NEVER_SECONDS] 表示不自动取消（通知保留到用户点击或划掉），
 * 其余值表示通知上屏若干秒后自动收回。
 */
object OtpCodeAlertPolicy {
    /** 不自动取消。 */
    const val RETENTION_NEVER_SECONDS = 0
    const val MIN_RETENTION_SECONDS = 0
    const val MAX_RETENTION_SECONDS = 600
    const val DEFAULT_RETENTION_SECONDS = 5

    fun normalizeRetentionSeconds(seconds: Int): Int =
        seconds.coerceIn(MIN_RETENTION_SECONDS, MAX_RETENTION_SECONDS)

    /** 换算 [android.app.Notification.Builder.setTimeoutAfter] 需要的毫秒；0 表示不设置超时。 */
    fun retentionMillis(seconds: Int): Long = normalizeRetentionSeconds(seconds) * 1000L
}
