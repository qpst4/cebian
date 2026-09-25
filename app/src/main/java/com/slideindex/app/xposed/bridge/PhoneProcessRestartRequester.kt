package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 请求电话与短信存储进程自我重启，让覆盖安装后的新模块代码立即生效。
 *
 * 为什么需要它：LSPosed 只在进程启动时注入模块代码，覆盖安装 APK 不会替换这些进程里
 * 已加载的实例；重启这两个进程即可让系统重新加载新代码（无需重启整机）。
 * 系统框架（system_server）无法用这种方式刷新，那部分仍需重启手机。
 */
object PhoneProcessRestartRequester {
    private const val TAG = "PhoneProcessRestart"

    fun request(context: Context) {
        runCatching {
            context.sendBroadcast(
                Intent(ModuleHookBridgeContract.ACTION_RESTART_PROCESS).apply {
                    putExtra(
                        ModuleHookBridgeContract.EXTRA_RESTART_CONFIRM,
                        ModuleHookBridgeContract.RESTART_CONFIRM_VALUE,
                    )
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                },
            )
            Log.i(TAG, "Restart request broadcast sent")
        }.onFailure { Log.w(TAG, "Restart request failed: ${it.message}") }
    }
}
