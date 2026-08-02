/**
 * Based on [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) (GPL-3.0).
 */
package com.slideindex.app.search.ral

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

internal val Context.hasShizukuPermission: Boolean
    get() {
        if (!Shizuku.pingBinder()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                checkCallingOrSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: IllegalStateException) {
            false
        }
    }

internal suspend fun requestShizukuPermission(): Boolean =
    suspendCoroutine { continuation ->
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            continuation.resume(false)
            return@suspendCoroutine
        }
        val code = Random(System.currentTimeMillis()).nextInt()
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                Shizuku.removeRequestPermissionResultListener(this)
            }
        }
        try {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(code)
        } catch (_: IllegalStateException) {
            continuation.resume(false)
        }
    }
