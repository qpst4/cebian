package com.slideindex.app.xposed.hook

import com.slideindex.app.clipboard.ClipboardWhitelistContract
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface

class ClipboardWhitelistHook {
  @Volatile
  private var whitelist: Set<String> = emptySet()

  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    return runCatching { installInternal(xposed, classLoader) }
      .getOrElse {
        XposedLog.e(TAG, "ClipboardWhitelistHook failed", it)
        emptyList()
      }
  }

  private fun installInternal(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    val preferences = xposed.getRemotePreferences(ClipboardWhitelistContract.REMOTE_PREFS_NAME)
    whitelist = preferences.getStringSet(ClipboardWhitelistContract.KEY_WHITELIST, emptySet()).orEmpty()
    preferences.registerOnSharedPreferenceChangeListener { _, key ->
      if (key == ClipboardWhitelistContract.KEY_WHITELIST) {
        whitelist = preferences.getStringSet(ClipboardWhitelistContract.KEY_WHITELIST, emptySet()).orEmpty()
      }
    }

    val serviceClass = LibXposedReflect.findClassIfExists(
      "com.android.server.clipboard.ClipboardService",
      classLoader,
    ) ?: run {
      XposedLog.w(TAG, "ClipboardService not found")
      return emptyList()
    }
    val method = LibXposedReflect.findMethodExactIfExists(
      serviceClass,
      "isDefaultIme",
      Int::class.javaPrimitiveType,
      String::class.java,
    ) ?: run {
      XposedLog.w(TAG, "isDefaultIme not found")
      return emptyList()
    }
    val handle = xposed.hookMethod(
      method,
      object : LibXposedMethodHook() {
        override fun beforeHookedMethod(param: HookParam) {
          val packageName = param.arg(1) as? String ?: return
          if (whitelist.contains(packageName)) {
            param.result = true
            param.returnEarly = true
          }
        }
      },
      id = HOOK_ID,
    )
    XposedLog.i(TAG, "ClipboardWhitelistHook installed")
    return listOf(handle)
  }

  companion object {
    private const val TAG = "ClipboardWhitelist"
    private const val HOOK_ID = "clipboard_whitelist_is_default_ime"
  }
}
