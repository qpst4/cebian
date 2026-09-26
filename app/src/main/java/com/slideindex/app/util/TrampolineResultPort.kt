package com.slideindex.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Trampoline（中转 Activity）结果的跨进程回传通道。
 *
 * 多进程后"发起方在 :overlay、Activity 在默认进程"这类组合很常见，而 trampoline 原来靠
 * 进程内静态变量传回调：另一个进程读到的永远是空副本 → 回调不触发、标记卡死
 * （Shell 面板"只能触发一次"就是这个原因）。
 *
 * 现在：发起方按 token 注册回调 → Intent 带 token → Activity 结束时把结果广播回来，
 * 由发起进程收到后调用本地回调。发起方与宿主同进程/跨进程走同一条路径。
 */
object TrampolineResultPort {
    private const val TAG = "TrampolineResultPort"
    private const val ACTION_RESULT = "com.slideindex.app.action.TRAMPOLINE_RESULT"

    const val EXTRA_TOKEN = "trampoline_token"
    const val EXTRA_CANCELLED = "trampoline_cancelled"

    private val pending = ConcurrentHashMap<String, (Bundle) -> Unit>()

    @Volatile
    private var receiverRegistered = false

    fun register(context: Context, token: String, onResult: (Bundle) -> Unit) {
        ensureReceiver(context)
        pending[token] = onResult
    }

    fun clearToken(token: String) {
        pending.remove(token)
    }

    /** Activity 侧调用：把结果送回发起进程。 */
    fun deliver(context: Context, token: String, payload: Bundle) {
        val appContext = context.applicationContext
        val intent = Intent(ACTION_RESULT).apply {
            setPackage(appContext.packageName)
            putExtra(EXTRA_TOKEN, token)
            putExtras(payload)
        }
        runCatching { appContext.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "deliver($token) failed", it) }
    }

    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_RESULT) return
                val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
                val callback = pending.remove(token) ?: return
                val extras = intent.extras ?: Bundle()
                runCatching { callback(extras) }
                    .onFailure { Log.w(TAG, "onResult($token) failed", it) }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(ACTION_RESULT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }.onFailure { Log.w(TAG, "registerReceiver failed", it) }
    }
}
