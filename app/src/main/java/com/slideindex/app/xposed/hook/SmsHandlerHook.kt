package com.slideindex.app.xposed.hook

import android.content.Intent
import android.provider.Telephony
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface

class SmsHandlerHook {
  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    return runCatching { hookSmsHandler(xposed, classLoader) }
      .getOrElse {
        XposedLog.e(TAG, "SmsHandlerHook failed", it)
        emptyList()
      }
  }

  private fun hookSmsHandler(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    val handles = mutableListOf<XposedInterface.HookHandle>()
    for (className in HANDLER_CLASSES) {
      hookDispatchIntent(xposed, classLoader, className)?.let(handles::add)
    }
    if (handles.isEmpty()) {
      XposedLog.w(TAG, "No dispatchIntent hook installed")
    }
    return handles
  }

  private fun hookDispatchIntent(
    xposed: XposedInterface,
    classLoader: ClassLoader,
    className: String,
  ): XposedInterface.HookHandle? {
    val handlerClass = LibXposedReflect.findClassIfExists(className, classLoader) ?: return null
    val dispatchMethod = handlerClass.declaredMethods.firstOrNull { method ->
      method.name == DISPATCH_INTENT
    } ?: return null
    val handle = xposed.hookMethod(
      dispatchMethod,
      object : LibXposedMethodHook() {
        override fun beforeHookedMethod(param: HookParam) {
          val intent = param.args[0] as? Intent ?: return
          if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
          val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
          val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
          if (body.isBlank()) return
          val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
          val slot = intent.getIntExtra("slot", intent.getIntExtra("phone", -1))
          val context = runCatching {
            LibXposedReflect.getObjectField(param.thisObject!!, "mContext") as android.content.Context
          }.getOrNull() ?: return
          SmsCaptureForwarder.forward(context, body, sender, slot, TAG)
        }
      },
      id = "sms_handler_$className",
    )
    XposedLog.i(TAG, "SmsHandlerHook installed on $className")
    return handle
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
