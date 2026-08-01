package com.slideindex.app.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/** Proactively binds [GestureToggleTileService] so SystemUI delivers tile clicks without cold-bind delay. */
object GestureToggleTileWarmup {
    fun requestListening(context: Context, reason: String) {
        val appContext = context.applicationContext
        val component = ComponentName(appContext, GestureToggleTileService::class.java)
        runCatching {
            TileService.requestListeningState(appContext, component)
            Log.i(GestureToggleTileService.LOG_TAG, "requestListeningState: reason=$reason")
        }.onFailure { error ->
            Log.w(GestureToggleTileService.LOG_TAG, "requestListeningState failed: reason=$reason", error)
        }
    }
}
