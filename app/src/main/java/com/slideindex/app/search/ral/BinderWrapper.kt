package com.slideindex.app.search.ral

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

private const val PER_USER_RANGE = 100_000

internal interface BinderWrapper {
    suspend fun Context.getUidAndPackage(): Pair<Int, String?>
    suspend fun wrapBinder(binder: IBinder): IBinder
}

internal interface ShizukuBinderWrapperHost : BinderWrapper {
    override suspend fun Context.getUidAndPackage(): Pair<Int, String?> {
        val uid = try {
            Shizuku.getUid()
        } catch (_: IllegalStateException) {
            Process.SHELL_UID
        }
        val userId = uid / PER_USER_RANGE
        return userId to packageManager.getPackagesForUid(uid)?.firstOrNull()
    }

    override suspend fun wrapBinder(binder: IBinder): IBinder = ShizukuBinderWrapper(binder)
}
