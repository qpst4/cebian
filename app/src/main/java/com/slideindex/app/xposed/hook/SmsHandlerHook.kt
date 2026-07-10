package com.slideindex.app.xposed.hook

import android.content.Intent
import android.provider.Telephony
import com.slideindex.app.xposed.XposedLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class SmsHandlerHook {
  fun install(classLoader: ClassLoader) {
    runCatching { hookSmsHandler(classLoader) }
      .onFailure { XposedLog.e(TAG, "SmsHandlerHook failed", it) }
  }

  private fun hookSmsHandler(classLoader: ClassLoader) {
    var hooked = false
    for (className in HANDLER_CLASSES) {
      if (hookDispatchIntent(classLoader, className)) {
        hooked = true
      }
    }
    if (!hooked) {
      XposedLog.w(TAG, "No dispatchIntent hook installed")
    }
  }

  private fun hookDispatchIntent(classLoader: ClassLoader, className: String): Boolean {
    val handlerClass = runCatching {
      XposedHelpers.findClass(className, classLoader)
    }.getOrNull() ?: return false
    val dispatchMethod = handlerClass.declaredMethods.firstOrNull { method ->
      if (method.name != DISPATCH_INTENT) return@firstOrNull false
      true
    } ?: return false
    XposedBridge.hookMethod(dispatchMethod, DispatchIntentHook())
    XposedLog.i(TAG, "SmsHandlerHook installed on $className")
    return true
  }

  private class DispatchIntentHook : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
      val intent = param.args[0] as? Intent ?: return
      if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
      val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
      val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
      if (body.isBlank()) return
      val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
      val slot = intent.getIntExtra("slot", intent.getIntExtra("phone", -1))
      val context = runCatching {
        XposedHelpers.getObjectField(param.thisObject, "mContext") as android.content.Context
      }.getOrNull() ?: return
      SmsCaptureForwarder.forward(context, body, sender, slot, TAG)
    }
  }

  companion object {
    private const val TAG = "SmsHandlerHook"
    private const val DISPATCH_INTENT = "dispatchIntent"
    private val HANDLER_CLASSES = listOf(
      "com.android.internal.telephony.InboundSmsHandler",
      "com.android.internal.telephony.gsm.GsmInboundSmsHandler",
      "com.android.internal.telephony.cdma.CdmaInboundSmsHandler",
    )
  }
}
