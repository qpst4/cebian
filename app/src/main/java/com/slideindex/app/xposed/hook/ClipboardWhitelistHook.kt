package com.slideindex.app.xposed.hook

/*
 * Portions derived from Clipboard Whitelist (https://github.com/Tehcneko/ClipboardWhitelist)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.bridge.HookConfigReader
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface

/**
 * 剪贴板白名单：hook `ClipboardService.isDefaultIme(int, String)`。
 *
 * 命中白名单的包会被系统当成「默认输入法」，于是后台也能读剪贴板——
 * 这是"不抢焦点"的路径：省掉 16×16 焦点探针，也不再和别的剪贴板应用抢焦点。
 *
 * 白名单走现有快照通道下发（[HookConfigReader]，TTL 缓存），不依赖 LibXposed remote prefs。
 */
class ClipboardWhitelistHook {
  private val configReader = HookConfigReader { message -> XposedLog.w(TAG, message) }

  @Volatile
  private var cachedWhitelist: Set<String> = emptySet()

  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> =
    runCatching { installInternal(xposed, classLoader) }
      .getOrElse {
        XposedLog.e(TAG, "ClipboardWhitelistHook failed", it)
        installStatus = STATUS_FAILED
        emptyList()
      }

  private fun installInternal(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    val serviceClass = LibXposedReflect.findClassIfExists(
      "com.android.server.clipboard.ClipboardService",
      classLoader,
    ) ?: run {
      XposedLog.w(TAG, "ClipboardService not found")
      installStatus = STATUS_MISSING_SERVICE
      return emptyList()
    }
    val method = LibXposedReflect.findMethodExactIfExists(
      serviceClass,
      "isDefaultIme",
      Int::class.javaPrimitiveType,
      String::class.java,
    ) ?: run {
      // 系统版本改了内部实现时走到这里：记录一条状态，避免"装上了但没生效"的黑盒。
      XposedLog.w(TAG, "ClipboardService.isDefaultIme(int, String) not found")
      installStatus = STATUS_MISSING_METHOD
      return emptyList()
    }
    val handle = xposed.hookMethod(
      method,
      object : LibXposedMethodHook() {
        override fun beforeHookedMethod(param: HookParam) {
          val packageName = param.arg(1) as? String ?: return
          if (currentWhitelist().contains(packageName)) {
            param.result = true
            param.returnEarly = true
          }
        }
      },
      id = HOOK_ID,
    )
    XposedLog.i(TAG, "ClipboardWhitelistHook installed")
    installStatus = STATUS_OK
    return listOf(handle)
  }

  /** 白名单命中判断在 system_server 的热路径上：读不到新快照时沿用上一次的缓存。 */
  private fun currentWhitelist(): Set<String> {
    val snapshot = configReader.current() ?: return cachedWhitelist
    val fresh = snapshot.clipboardWhitelist.toSet()
    if (fresh != cachedWhitelist) cachedWhitelist = fresh
    return fresh
  }

  companion object {
    private const val TAG = "ClipboardWhitelist"
    private const val HOOK_ID = "clipboard_whitelist_is_default_ime"

    const val STATUS_OK = "ok"
    const val STATUS_MISSING_SERVICE = "missing-service"
    const val STATUS_MISSING_METHOD = "missing-method"
    const val STATUS_FAILED = "failed"

    /**
     * 最近一次安装结果，随模块状态串回传给 app（system_server 单进程，volatile 读足够）。
     *
     * 默认 [STATUS_FAILED]：app 侧看到它就知道"白名单 hook 此刻没生效"，不会误报正常。
     */
    @Volatile
    var installStatus: String = STATUS_FAILED
      private set
  }
}
