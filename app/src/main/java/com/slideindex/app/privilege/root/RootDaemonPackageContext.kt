package com.slideindex.app.privilege.root

import android.content.Context
import android.util.Log

/**
 * Creates an application [Context] inside an [app_process] child started as shell/root.
 */
internal object RootDaemonPackageContext {
    private const val TAG = "RootTaskDaemon"

    fun create(packageName: String): Context? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val systemMain = activityThreadClass.getMethod("systemMain")
            val thread = systemMain.invoke(null)
            val getSystemContext = activityThreadClass.getMethod("getSystemContext")
            val systemContext = getSystemContext.invoke(thread) as Context
            systemContext.createPackageContext(
                packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrElse { error ->
            Log.e(TAG, "createPackageContext($packageName) failed", error)
            null
        }
    }
}
