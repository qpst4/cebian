package com.slideindex.app.segmentation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object JiebaWarmUp {
    private const val TAG = "JiebaWarmUp"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            runCatching {
                CppJiebaTokenizer.get(context).warmUp()
                Log.d(TAG, "cppjieba warm-up complete")
            }.onFailure { error ->
                Log.e(TAG, "cppjieba warm-up failed", error)
            }
        }
    }
}
