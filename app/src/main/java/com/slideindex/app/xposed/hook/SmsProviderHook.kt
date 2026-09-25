package com.slideindex.app.xposed.hook

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.ContentProvider
import android.content.ContentValues
import android.content.ContentUris
import android.net.Uri
import android.provider.Telephony
import com.slideindex.app.otp.SmsBlacklistMatcher
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import com.slideindex.app.xposed.hook.otp.SmsPolicyRuntime
import io.github.libxposed.api.XposedInterface

class SmsProviderHook {
  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    val handles = runCatching { hookProviderMethods(xposed, classLoader) }
      .getOrElse {
        XposedLog.e(TAG, "SmsProviderHook failed", it)
        emptyList()
      }
    SmsPolicyRuntime.markProviderHooked(handles.isNotEmpty())
    return handles
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

    /**
     * 入库后按策略执行「提取后删除 / 标记已读 / 黑名单删除」。
     *
     * 只能拿到 `insert` 返回的单条 Uri；`bulkInsert` 没有逐条 id，跳过（策略留待下次单条入库生效）。
     */
    override fun afterHookedMethod(param: HookParam) {
      if (methodName != METHOD_INSERT) return
      val uri = param.args.firstOrNull() as? Uri ?: return
      if (!SmsCaptureForwarder.isSmsUri(uri)) return
      val insertedUri = param.result as? Uri ?: return
      val rowId = runCatching { ContentUris.parseId(insertedUri) }.getOrNull() ?: return
      if (rowId < 0L) return
      val values = param.args.getOrNull(1) as? ContentValues ?: return
      val provider = param.thisObject as? ContentProvider ?: return
      val context = provider.context ?: return
      SmsPolicyRuntime.ensureRegistered(context)
      applyPolicy(context, insertedUri, values)
    }

    private fun applyPolicy(context: android.content.Context, uri: Uri, values: ContentValues) {
      val policy = SmsPolicyRuntime.policy()
      if (!policy.markAsReadEnabled && !policy.deleteSmsEnabled && !policy.blacklist.enabled) return
      val body = SmsCaptureForwarder.readBody(values) ?: return
      val sender = SmsCaptureForwarder.readAddress(values)
      val blacklistMatch = SmsBlacklistMatcher.match(policy.blacklist, sender, body)
      val deleteByBlacklist = blacklistMatch.matched && blacklistMatch.actionDelete
      // 「提取后删除 / 已读」要求真的抽出了验证码，避免误伤只含关键词的短信。
      val extracted = !SmsPolicyRuntime.extractVerificationCode(body).isNullOrBlank()
      val shouldDelete = deleteByBlacklist || (extracted && policy.deleteSmsEnabled)
      val shouldMarkRead = !shouldDelete && extracted && policy.markAsReadEnabled
      if (!shouldDelete && !shouldMarkRead) return
      runCatching {
        if (shouldDelete) {
          context.contentResolver.delete(uri, null, null)
          XposedLog.i(TAG, "policy delete applied: blacklist=$deleteByBlacklist")
        } else {
          val readValues = ContentValues().apply { put(Telephony.Sms.READ, 1) }
          context.contentResolver.update(uri, readValues, null, null)
          XposedLog.i(TAG, "policy mark-as-read applied")
        }
      }.onFailure { XposedLog.w(TAG, "policy apply failed: ${it.message}") }
    }

    private fun forwardValues(context: android.content.Context, values: ContentValues) {
      val body = SmsCaptureForwarder.readBody(values) ?: return
      val sender = SmsCaptureForwarder.readAddress(values)
      SmsCaptureForwarder.forward(context, body, sender, slot = -1, tag = TAG)
    }
  }

  companion object {
    private const val TAG = "SmsProviderHook"
    private const val METHOD_INSERT = "insert"
    private const val TELEPHONY_PROVIDER_CLASS = "com.android.providers.telephony.TelephonyProvider"
    private val PROVIDER_METHODS = listOf("insert", "bulkInsert", "update")
  }
}
