package com.slideindex.app.autofill

import android.content.Intent

object OtpAutoInputBroadcastContract {
    const val ACTION_AUTO_INPUT = "com.slideindex.app.action.OTP_AUTO_INPUT"
    const val ACTION_AUTO_INPUT_RESULT = "com.slideindex.app.action.OTP_AUTO_INPUT_RESULT"
    const val ACTION_SMS_CAPTURED = "com.slideindex.app.action.OTP_SMS_CAPTURED"

    const val EXTRA_CODE = "code"
    const val EXTRA_AUTO_ENTER = "auto_enter"
    const val EXTRA_INPUT_INTERVAL_MS = "input_interval_ms"
    const val EXTRA_ATTEMPT_ID = "attempt_id"
    const val EXTRA_SUCCESS = "success"
    const val EXTRA_STRATEGY = "strategy"
    const val EXTRA_REASON = "reason"
    const val EXTRA_WINDOW_PACKAGE = "window_package"
    const val EXTRA_SMS_BODY = "sms_body"
    const val EXTRA_SMS_SENDER = "sms_sender"
    const val EXTRA_SMS_SLOT = "sms_slot"
    const val EXTRA_ALLOW_SYSTEM_INJECT = "allow_system_inject"
    /** 剪贴板里已经有本次验证码（App 开了"提取后自动复制"），允许按键注入失败时改用 Ctrl+V。 */
    const val EXTRA_ALLOW_PASTE = "allow_paste"
    const val EXTRA_PROBE = "probe"

    const val RECEIVER_PRIORITY_SYSTEM = 2000
    const val RECEIVER_PRIORITY_ACCESSIBILITY = -500

    const val STRATEGY_SYSTEM_INJECT = "system_inject"

    object SystemInjectReason {
        const val OK = "ok"
        const val PROBE = "probe"
        const val UID_REJECTED = "uid_rejected"
        const val INJECT_DISABLED = "inject_disabled"
        const val RESULT_CODE_BLOCKED = "result_code_blocked"
        const val INVALID_REQUEST = "invalid_request"
        const val MANAGER_UNRESOLVED = "manager_unresolved"
        const val INJECT_METHOD_UNRESOLVED = "inject_method_unresolved"
        const val NO_KEY_EVENTS = "no_key_events"
        const val INJECT_EXCEPTION = "inject_exception"
        /** 按键注入失败后的第二层降级：Ctrl+V 粘贴。 */
        const val PASTE_FALLBACK = "paste_fallback"
    }

    data class Request(
        val code: String,
        val autoEnter: Boolean,
        val inputIntervalMs: Long,
        val attemptId: Long,
        val allowSystemInject: Boolean = true,
        val allowPaste: Boolean = false,
    )

    fun buildRequestIntent(request: Request): Intent =
        Intent(ACTION_AUTO_INPUT).apply {
            putExtra(EXTRA_CODE, request.code)
            putExtra(EXTRA_AUTO_ENTER, request.autoEnter)
            putExtra(EXTRA_INPUT_INTERVAL_MS, request.inputIntervalMs)
            putExtra(EXTRA_ATTEMPT_ID, request.attemptId)
            putExtra(EXTRA_ALLOW_SYSTEM_INJECT, request.allowSystemInject)
            putExtra(EXTRA_ALLOW_PASTE, request.allowPaste)
        }

    fun buildProbeIntent(attemptId: Long): Intent =
        Intent(ACTION_AUTO_INPUT).apply {
            putExtra(EXTRA_PROBE, true)
            putExtra(EXTRA_ATTEMPT_ID, attemptId)
            putExtra(EXTRA_ALLOW_SYSTEM_INJECT, true)
        }

    fun readRequest(intent: Intent): Request? {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return null
        if (code.isEmpty()) return null
        return Request(
            code = code,
            autoEnter = intent.getBooleanExtra(EXTRA_AUTO_ENTER, false),
            inputIntervalMs = intent.getLongExtra(EXTRA_INPUT_INTERVAL_MS, 0L),
            attemptId = intent.getLongExtra(EXTRA_ATTEMPT_ID, 0L),
            allowSystemInject = intent.getBooleanExtra(EXTRA_ALLOW_SYSTEM_INJECT, true),
            allowPaste = intent.getBooleanExtra(EXTRA_ALLOW_PASTE, false),
        )
    }

    fun buildResultIntent(attemptId: Long, success: Boolean, strategy: String, reason: String): Intent =
        Intent(ACTION_AUTO_INPUT_RESULT).apply {
            setPackage("com.slideindex.app")
            putExtra(EXTRA_ATTEMPT_ID, attemptId)
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_STRATEGY, strategy)
            putExtra(EXTRA_REASON, reason)
        }
}
