package com.slideindex.app.privilege.root

import android.os.IBinder
import android.util.Log

/** Optional ServiceManager path; on many ROMs only RPC works for the app process. */
internal object RootBinderPublish {
    private const val TAG = "RootBinderPublish"

    fun serviceName(packageName: String): String {
        val hash = packageName.hashCode().toUInt().toString(16)
        return "slideindex.$hash.taskmgr"
    }

    // 反射访问 ServiceManager.addService：root 侧发布 binder 的必要手段，失败已有 runCatching 兜底。
    @android.annotation.SuppressLint("PrivateApi")
    fun publish(name: String, binder: IBinder): Boolean {
        return runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val added = invokeAddService(sm, name, binder)
            if (added) {
                Log.i(TAG, "published name=$name")
            }
            added
        }.onFailure { error ->
            Log.w(TAG, "publish skipped name=$name: ${error.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    fun lookup(name: String): IBinder? {
        return runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("getService", String::class.java).invoke(null, name) as? IBinder
        }.getOrNull()?.takeIf { it.pingBinder() }
    }

    private fun invokeAddService(smClass: Class<*>, name: String, binder: IBinder): Boolean {
        val candidates = listOf(
            arrayOf(String::class.java, IBinder::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            arrayOf(String::class.java, IBinder::class.java, Boolean::class.javaPrimitiveType),
            arrayOf(String::class.java, IBinder::class.java),
        )
        for (params in candidates) {
            val method = runCatching { smClass.getMethod("addService", *params) }.getOrNull()
                ?: continue
            val args: Array<Any?> = when (params.size) {
                4 -> arrayOf(name, binder, false, 0)
                3 -> arrayOf(name, binder, false)
                2 -> arrayOf(name, binder)
                else -> continue
            }
            val result = method.invoke(null, *args)
            if (result is Boolean) return result
            return true
        }
        throw NoSuchMethodException("ServiceManager.addService")
    }
}

