package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File

/**
 * 模块侧（system_server）配置读取器。
 *
 * 读取顺序：app 导出的外部快照 → 本进程目录 → app 设备保护目录 → 保持“无配置”（即完全放行）。
 * 带 TTL 缓存，避免每条输入事件都读盘；读不到时反向请求 app 重新下发。
 *
 * 持久化由 app 负责（写外部私有目录不需要权限）；模块侧不再写盘——
 * 电话进程往 /data/system 写会恒 EACCES。
 */
class HookConfigReader(
  private val log: (String) -> Unit = {},
) {
  @Volatile
  private var cached: ModuleHookSnapshot? = null

  @Volatile
  private var lastLoadAtMs: Long = 0L

  @Volatile
  private var lastRequestAtMs: Long = 0L

  @Volatile
  private var requestedOnce: Boolean = false

  fun current(): ModuleHookSnapshot? {
    val now = SystemClock.elapsedRealtime()
    val cachedValue = cached
    if (cachedValue != null && now - lastLoadAtMs < ModuleHookBridgeContract.SNAPSHOT_TTL_MS) {
      return cachedValue
    }
    // 磁盘读不到时保留内存里那份。电话进程既写不了也读不到 /data/system/slideindex，又读不到 app 的
    // 设备保护目录，唯一来源就是广播下发；如果这里把 cached 清掉，策略会在 TTL 到期后凭空消失
    //（现象：「短信安全」的拦截 / 标记已读 / 提取后删除静默失效，状态回执里 config=fail）。
    return loadFromDisk() ?: cachedValue
  }

  fun applyBroadcast(json: String?) {
    val parsed = ModuleHookSnapshot.parse(json) ?: return
    cached = parsed
    lastLoadAtMs = SystemClock.elapsedRealtime()
    log("hook config applied: groups=${parsed.takeoverGroups}")
  }

  /**
   * 请求 app 重新下发配置（带节流）。
   *
   * 与 EdgeX 的差异：不因"磁盘已有旧配置"而跳过——模块在 app 之前启动时会错过
   * 启动广播，必须允许主动拉取，否则会一直停留在旧配置上。
   */
  fun requestSnapshotIfNeeded(context: Context) {
    if (requestedOnce && SystemClock.elapsedRealtime() - lastRequestAtMs <
      ModuleHookBridgeContract.SNAPSHOT_REQUEST_THROTTLE_MS
    ) {
      return
    }
    requestedOnce = true
    lastRequestAtMs = SystemClock.elapsedRealtime()
    runCatching {
      context.sendBroadcast(
        Intent(ModuleHookBridgeContract.ACTION_CONFIG_SNAPSHOT_REQUEST).apply {
          setPackage(ModuleHookBridgeContract.MODULE_PACKAGE)
          addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        },
      )
      log("hook config snapshot requested from app")
    }.onFailure { log("hook config snapshot request failed: ${it.message}") }
  }

  private fun loadFromDisk(): ModuleHookSnapshot? {
    // 读取顺序：app 导出的外部快照 → 本进程目录（system_server 可写）→ 设备保护目录。
    // 外部那份是冷启动恢复的主要来源：app 写它不需要权限，模块侧读得到。
    val fromExternal = runCatching {
      readText(File(ModuleHookBridgeContract.APP_EXTERNAL_SNAPSHOT_PATH))
    }.getOrNull()
    val fromSystem = if (fromExternal == null) {
      runCatching { readText(systemSnapshotFile()) }.getOrNull()
    } else {
      null
    }
    val fromApp = if (fromExternal == null && fromSystem == null) {
      runCatching { readText(File(ModuleHookBridgeContract.APP_SNAPSHOT_PATH)) }.getOrNull()
    } else {
      null
    }
    val parsed = ModuleHookSnapshot.parse(fromExternal ?: fromSystem ?: fromApp)
    lastLoadAtMs = SystemClock.elapsedRealtime()
    // 只在真的解析出配置时覆盖缓存，避免"读不到文件"把已下发的配置顶掉。
    if (parsed != null) cached = parsed
    return parsed
  }

  private fun readText(file: File): String? {
    if (!file.isFile || !file.canRead()) return null
    val text = file.readText()
    return text.ifBlank { null }
  }

  private fun systemSnapshotFile(): File =
    File(
      File(ModuleHookBridgeContract.SYSTEM_SNAPSHOT_DIR),
      ModuleHookBridgeContract.SNAPSHOT_FILE_NAME,
    )
}
