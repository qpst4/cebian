package com.slideindex.app.xposed.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import com.slideindex.app.xposed.XposedLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class SmsProviderHook {
  fun install(classLoader: ClassLoader) {
    runCatching { hookProviderMethods(classLoader) }
      .onFailure { XposedLog.e(TAG, "SmsProviderHook failed", it) }
  }

  private fun hookProviderMethods(classLoader: ClassLoader) {
    val providerClass = runCatching {
      XposedHelpers.findClass(TELEPHONY_PROVIDER_CLASS, classLoader)
    }.getOrNull()
    if (providerClass == null) {
      XposedLog.w(TAG, "TelephonyProvider class not found")
      return
    }
    var hooked = false
    for (methodName in PROVIDER_METHODS) {
      val methods = providerClass.declaredMethods.filter { it.name == methodName }
      if (methods.isEmpty()) continue
      for (method in methods) {
        XposedBridge.hookMethod(method, ProviderMethodHook(methodName))
        hooked = true
      }
    }
    if (hooked) {
      XposedLog.i(TAG, "SmsProviderHook installed on $TELEPHONY_PROVIDER_CLASS")
    } else {
      XposedLog.w(TAG, "No TelephonyProvider methods hooked")
    }
  }

  private class ProviderMethodHook(private val methodName: String) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
      val uri = param.args.firstOrNull() as? Uri ?: return
      if (!SmsCaptureForwarder.isSmsUri(uri)) return
      val provider = param.thisObject as? ContentProvider ?: return
      val context = provider.context ?: return
      when (methodName) {
        "insert" -> {
          val values = param.args.getOrNull(1) as? ContentValues ?: return
          forwardValues(context, values)
        }
        "bulkInsert" -> {
          val valuesArray = param.args.getOrNull(1) as? Array<*> ?: return
          for (item in valuesArray) {
            val values = item as? ContentValues ?: continue
            forwardValues(context, values)
          }
        }
        "update" -> {
          val values = param.args.getOrNull(1) as? ContentValues ?: return
          forwardValues(context, values)
        }
      }
    }

    private fun forwardValues(context: android.content.Context, values: ContentValues) {
      val body = SmsCaptureForwarder.readBody(values) ?: return
      val sender = SmsCaptureForwarder.readAddress(values)
      SmsCaptureForwarder.forward(context, body, sender, slot = -1, tag = TAG)
    }
  }

  companion object {
    private const val TAG = "SmsProviderHook"
    private const val TELEPHONY_PROVIDER_CLASS = "com.android.providers.telephony.TelephonyProvider"
    private val PROVIDER_METHODS = listOf("insert", "bulkInsert", "update")
  }
}
