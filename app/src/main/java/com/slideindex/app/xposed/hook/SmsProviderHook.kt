package com.slideindex.app.xposed.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface

class SmsProviderHook {
  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    return runCatching { hookProviderMethods(xposed, classLoader) }
      .getOrElse {
        XposedLog.e(TAG, "SmsProviderHook failed", it)
        emptyList()
      }
  }

  private fun hookProviderMethods(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    val providerClass = LibXposedReflect.findClassIfExists(TELEPHONY_PROVIDER_CLASS, classLoader)
    if (providerClass == null) {
      XposedLog.w(TAG, "TelephonyProvider class not found")
      return emptyList()
    }
    val handles = mutableListOf<XposedInterface.HookHandle>()
    for (methodName in PROVIDER_METHODS) {
      val methods = providerClass.declaredMethods.filter { it.name == methodName }
      for (method in methods) {
        handles += xposed.hookMethod(
          method,
          ProviderMethodHook(methodName),
          id = "sms_provider_${methodName}_${method.parameterTypes.joinToString { it.simpleName }}",
        )
      }
    }
    if (handles.isNotEmpty()) {
      XposedLog.i(TAG, "SmsProviderHook installed on $TELEPHONY_PROVIDER_CLASS")
    } else {
      XposedLog.w(TAG, "No TelephonyProvider methods hooked")
    }
    return handles
  }

  private class ProviderMethodHook(private val methodName: String) : LibXposedMethodHook() {
    override fun beforeHookedMethod(param: HookParam) {
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
