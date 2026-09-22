package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File

/**
 * 模块侧（system_server）配置读取器。
 *
 * 读取顺序：本进程持久化文件 → app 设备保护目录快照 → 保持“无配置”（即完全放行）。
 * 带 TTL 缓存，避免每条输入事件都读盘；读不到时反向请求 app 重新下发。
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
    return loadFromDisk()
  }

  fun applyBroadcast(json: String?) {
    val parsed = ModuleHookSnapshot.parse(json) ?: return
    cached = parsed
    lastLoadAtMs = SystemClock.elapsedRealtime()
    persistForRestart(json)
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
    val fromSystem = runCatching { readText(systemSnapshotFile()) }.getOrNull()
    val fromApp = if (fromSystem == null) {
      runCatching { readText(File(ModuleHookBridgeContract.APP_SNAPSHOT_PATH)) }.getOrNull()
    } else {
      null
    }
    val parsed = ModuleHookSnapshot.parse(fromSystem ?: fromApp)
    lastLoadAtMs = SystemClock.elapsedRealtime()
    cached = parsed
    return parsed
  }

  private fun readText(file: File): String? {
    if (!file.isFile || !file.canRead()) return null
    val text = file.readText()
    return text.ifBlank { null }
  }

  private fun persistForRestart(json: String?) {
    if (json.isNullOrBlank()) return
    runCatching {
      val dir = File(ModuleHookBridgeContract.SYSTEM_SNAPSHOT_DIR)
      dir.mkdirs()
      val file = File(dir, ModuleHookBridgeContract.SNAPSHOT_FILE_NAME)
      val temp = File(dir, "${file.name}.tmp")
      temp.writeText(json)
      if (!temp.renameTo(file)) {
        temp.copyTo(file, overwrite = true)
        temp.delete()
      }
    }.onFailure { log("hook config persist failed: ${it.message}") }
  }

  private fun systemSnapshotFile(): File =
    File(
      File(ModuleHookBridgeContract.SYSTEM_SNAPSHOT_DIR),
      ModuleHookBridgeContract.SNAPSHOT_FILE_NAME,
    )
}