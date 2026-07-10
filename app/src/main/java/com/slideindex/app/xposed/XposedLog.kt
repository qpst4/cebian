package com.slideindex.app.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface

internal object XposedLog {
  private var xposed: XposedInterface? = null

  fun bind(xposed: XposedInterface) {
    this.xposed = xposed
  }

  fun i(tag: String, message: String) {
    Log.i(tag, message)
    xposed?.log(Log.INFO, tag, message)
  }

  fun w(tag: String, message: String) {
    Log.w(tag, message)
    xposed?.log(Log.WARN, tag, message)
  }

  fun d(tag: String, message: String) {
    Log.d(tag, message)
    xposed?.log(Log.DEBUG, tag, message)
  }

  fun e(tag: String, message: String, throwable: Throwable? = null) {
    if (throwable == null) {
      Log.e(tag, message)
      xposed?.log(Log.ERROR, tag, message)
    } else {
      Log.e(tag, message, throwable)
      xposed?.log(Log.ERROR, tag, message, throwable)
    }
  }
}
