package com.slideindex.app.xposed.bridge

import android.content.Context

/** 缓存模块状态探测结果，供设置页读取（与 EdgeX 的激活态存储同思路）。 */
object ModuleBridgeStatusStore {
  private const val PREFS_NAME = "module_bridge_status"
  private const val KEY_ACTIVE = "active"
  private const val KEY_STATE = "state"
  private const val KEY_DETAIL = "detail"
  private const val KEY_UPDATED_AT = "updated_at"

  fun write(context: Context, active: Boolean, state: String, detail: String) {
    prefs(context).edit()
      .putBoolean(KEY_ACTIVE, active)
      .putString(KEY_STATE, state)
      .putString(KEY_DETAIL, detail)
      .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
      .apply()
  }

  fun read(context: Context): Snapshot = Snapshot(
    active = prefs(context).getBoolean(KEY_ACTIVE, false),
    state = prefs(context).getString(KEY_STATE, "").orEmpty(),
    detail = prefs(context).getString(KEY_DETAIL, "").orEmpty(),
    updatedAtMs = prefs(context).getLong(KEY_UPDATED_AT, 0L),
  )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  data class Snapshot(
    val active: Boolean,
    /** 模块三态：`ready` / `armed` / `not-ready`；旧模块可能为空串。 */
    val state: String,
    val detail: String,
    val updatedAtMs: Long,
  )
}
