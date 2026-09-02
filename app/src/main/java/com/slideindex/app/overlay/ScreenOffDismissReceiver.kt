package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * 在单一 [Context] 上注册 [Intent.ACTION_SCREEN_OFF]，保证 unregister 与 register 配对。
 */
class ScreenOffDismissReceiver(
    private val onScreenOff: () -> Unit,
) {
    private companion object {
        private const val TAG = "ScreenOffDismissReceiver"
    }
    private var receiver: BroadcastReceiver? = null
    private var registeredHost: Context? = null

    fun register(context: Context) {
        unregister()
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
            }
        }
        receiver = r
        registeredHost = context
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(r, filter)
            }
        }.onFailure { error ->
            Log.w(TAG, "register screen-off receiver failed", error)
            receiver = null
            registeredHost = null
        }
    }

    fun unregister() {
        val r = receiver ?: return
        registeredHost?.let { host -> runCatching { host.unregisterReceiver(r) } }
        receiver = null
        registeredHost = null
    }
}
