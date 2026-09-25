package com.slideindex.app.otp

/**
 * 验证码记录分类（对齐上游 XposedSmsCode 的四类记录开关）。
 *
 * 边栏目前只产生 [CODE] / [PLAIN_SMS] / [APP_NOTIFY] / [TEST] 四类；
 * 上游的「来电通知」分类需要通话通知抓取能力，边栏没有，暂不提供。
 */
enum class OtpRecordCategory(val storageKey: String) {
    CODE("code"),
    PLAIN_SMS("plain_sms"),
    APP_NOTIFY("app_notify"),
    TEST("test"),
    ;

    companion object {
        fun fromStorageKey(key: String?): OtpRecordCategory =
            entries.firstOrNull { it.storageKey == key } ?: CODE
    }
}

/** 每类记录条数上限（0 = 不记录该类）。 */
object OtpRecordLimits {
    const val DEFAULT = 20
    const val MIN = 0
    const val MAX = 200

    fun normalize(value: Int): Int = value.coerceIn(MIN, MAX)
}
