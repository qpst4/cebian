package com.slideindex.app.otp

/**
 * 自动填充失败原因里需要**跨模块共用**的几个字符串键。
 *
 * 这些键会写进记录（`OtpRecord.autoFillReason`）再由界面翻译成文案，
 * 所以谁写、谁读都要用同一份常量，别各写各的字面量。
 */
object OtpAutoFillReasons {
    /**
     * 抓码后一直没等到填充结果。
     *
     * 典型来源：跨进程的填充请求丢了（接收者是 :overlay 动态注册的，进程没起来 / 刚重启时
     * 广播直接丢），或者填充那一步的结果没能回写。由
     * [com.slideindex.app.otp.OtpRecordsRepository.PENDING_STALE_TIMEOUT_MS] 的超时兜底判定。
     */
    const val NO_RESULT = "no_result"

    /** 无障碍服务没连着，注入也没能填进去。 */
    const val A11Y_NOT_CONNECTED = "a11y_not_connected"
}
