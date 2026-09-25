package com.slideindex.app.receiver

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.slideindex.app.otp.OtpClipboardHelper

/**
 * 验证码通知点击后的复制动作（对应上游 `CopyCodeReceiver`）。
 *
 * 通知本体设置了 `setAutoCancel`，因此这里只需要把验证码写入剪贴板。
 */
class OtpCodeCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_OTP_COPY_CODE) return
        val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
        OtpClipboardHelper.copyCode(context, code)
    }

    companion object {
        const val ACTION_OTP_COPY_CODE = "com.slideindex.app.action.OTP_COPY_CODE"
        const val EXTRA_OTP_CODE = "otp_code"
    }
}
