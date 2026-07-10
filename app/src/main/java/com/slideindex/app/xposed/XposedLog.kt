package com.slideindex.app.xposed

import android.util.Log
import de.robv.android.xposed.XposedBridge

internal object XposedLog {
  fun i(tag: String, message: String) {
    Log.i(tag, message)
    runCatching { XposedBridge.log("[$tag] $message") }
  }

  fun w(tag: String, message: String) {
    Log.w(tag, message)
    runCatching { XposedBridge.log("[$tag] WARN: $message") }
  }

  fun d(tag: String, message: String) {
    Log.d(tag, message)
    runCatching { XposedBridge.log("[$tag] DEBUG: $message") }
  }

  fun e(tag: String, message: String, throwable: Throwable? = null) {
    if (throwable == null) {
      Log.e(tag, message)
      runCatching { XposedBridge.log("[$tag] ERROR: $message") }
    } else {
      Log.e(tag, message, throwable)
      runCatching { XposedBridge.log("[$tag] ERROR: $message\n${Log.getStackTraceString(throwable)}") }
    }
  }
}
