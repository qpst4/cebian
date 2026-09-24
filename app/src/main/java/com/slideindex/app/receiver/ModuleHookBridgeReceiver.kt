package com.slideindex.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusStore
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleHookConfigWriter
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收 system_server 模块发来的请求/回执。
 *
 * - 请求配置快照：把当前接管开关与触钮几何重新落盘并广播；
 * - 状态回执：写入本地缓存供设置页「检测模块状态」读取。
 */
class ModuleHookBridgeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      ModuleHookBridgeContract.ACTION_CONFIG_SNAPSHOT_REQUEST -> publishSnapshot(context)
      ModuleHookBridgeContract.ACTION_MODULE_STATUS_RESPONSE -> {
        ModuleBridgeStatusStore.write(
          context = context,
          active = intent.getBooleanExtra(ModuleHookBridgeContract.EXTRA_STATUS_ACTIVE, false),
          state = intent.getStringExtra(ModuleHookBridgeContract.EXTRA_STATUS_STATE).orEmpty(),
          detail = intent.getStringExtra(ModuleHookBridgeContract.EXTRA_STATUS_DETAIL).orEmpty(),
        )
      }
      else -> Unit
    }
  }

  private fun publishSnapshot(context: Context) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
      try {
        val deps = EntryPointAccessors.fromApplication(
          context.applicationContext,
          AppGraphEntryPoint::class.java,
        ).dependencies()
        ModuleHookConfigWriter.publish(context.applicationContext, deps.settingsRepository.readSnapshot())
      } finally {
        pendingResult.finish()
      }
    }
  }
}
