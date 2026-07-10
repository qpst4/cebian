package com.slideindex.app.xposed.hook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.otp.OtpCaptureDeduplicator
import com.slideindex.app.xposed.XposedLog

internal object SmsCaptureForwarder {
  private const val MODULE_PACKAGE = "com.slideindex.app"

  fun forward(context: Context, body: String, sender: String, slot: Int, tag: String) {
    if (body.isBlank()) return
    if (!OtpCaptureDeduplicator.tryConsumeSmsForward(sender, body)) {
      XposedLog.d(tag, "Skipping duplicate SMS forward")
      return
    }
    val broadcast = Intent(OtpAutoInputBroadcastContract.ACTION_SMS_CAPTURED).apply {
      setPackage(MODULE_PACKAGE)
      putExtra(OtpAutoInputBroadcastContract.EXTRA_SMS_BODY, body)
      putExtra(OtpAutoInputBroadcastContract.EXTRA_SMS_SENDER, sender)
      putExtra(OtpAutoInputBroadcastContract.EXTRA_SMS_SLOT, slot)
      addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
    }
    context.sendBroadcast(broadcast)
    XposedLog.i(tag, "Forwarded SMS to module (${body.length} chars)")
  }

  fun isSmsUri(uri: Uri?): Boolean {
    if (uri == null) return false
    val authority = uri.authority.orEmpty()
    if (
      authority == TelephonyProviderPackage ||
      authority == "sms" ||
      authority == "mms-sms"
    ) {
      return true
    }
    val text = uri.toString()
    return text.contains("content://sms") || text.contains("content://mms-sms")
  }

  fun readBody(values: android.content.ContentValues?): String? {
    values ?: return null
    return values.getAsString(Telephony.Sms.BODY)
      ?: values.getAsString("body")
  }

  fun readAddress(values: android.content.ContentValues?): String {
    values ?: return ""
    return values.getAsString(Telephony.Sms.ADDRESS)
      ?: values.getAsString("address")
      ?: ""
  }

  private const val TelephonyProviderPackage = "com.android.providers.telephony"
}
