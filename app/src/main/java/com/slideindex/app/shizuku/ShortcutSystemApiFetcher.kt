package com.slideindex.app.shizuku

import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.system.Os
import android.util.Log

object ShortcutSystemApiFetcher {
    private const val TAG = "ShortcutSystemApiFetcher"

    private const val MATCH_PINNED = 1
    private const val MATCH_DYNAMIC = 1 shl 1
    private const val MATCH_MANIFEST = 1 shl 2
    private const val MATCH_CACHED = 1 shl 3
    private const val MATCH_ALL = MATCH_PINNED or MATCH_DYNAMIC or MATCH_MANIFEST or MATCH_CACHED

    private var systemUidApplied = false

    fun ensureSystemUid(): Boolean {
        if (systemUidApplied) return true
        val uid = Os.getuid()
        if (uid != Process.ROOT_UID && uid != Process.SYSTEM_UID) {
            Log.w(
                TAG,
                "ensureSystemUid skipped for uid=$uid (Shizuku shell cannot become system UID)",
            )
            return false
        }
        try {
            when (Os.getuid()) {
                Process.ROOT_UID -> {
                    Os.setgid(Process.SYSTEM_UID)
                    Os.setuid(Process.SYSTEM_UID)
                }
                else -> Os.seteuid(Process.SYSTEM_UID)
            }
            systemUidApplied = Os.geteuid() == Process.SYSTEM_UID
            if (!systemUidApplied) {
                Log.w(TAG, "Could not obtain system UID, euid=${Os.geteuid()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set system UID", e)
        }
        return systemUidApplied
    }

    fun getShortcutsViaSystemApi(packageName: String, userId: Int): List<ShortcutInfo> {
        if (!ensureSystemUid()) {
            return emptyList()
        }

        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager.getMethod("getService", String::class.java)
                .invoke(null, "shortcut") as IBinder
            val stubClass = Class.forName("android.content.pm.IShortcutService\$Stub")
            val shortcutService = stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                ?: return emptyList()

            return getShortcutInfoCompat(shortcutService, packageName, userId, MATCH_ALL)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get shortcuts via system API for $packageName", e)
            return emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getShortcutInfoCompat(
        shortcutService: Any,
        packageName: String,
        userId: Int,
        matchFlags: Int
    ): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val result = mutableListOf<ShortcutInfo>()
            
            // For older versions, we might need to call individual methods
            try {
                if (matchFlags and MATCH_PINNED != 0) {
                    val parceledList = shortcutService.javaClass.getMethod("getPinnedShortcuts", String::class.java, Int::class.javaPrimitiveType)
                        .invoke(shortcutService, packageName, userId)
                    result.addAll(getListFromParceledListSlice(parceledList))
                }
                if (matchFlags and MATCH_MANIFEST != 0) {
                    val parceledList = shortcutService.javaClass.getMethod("getManifestShortcuts", String::class.java, Int::class.javaPrimitiveType)
                        .invoke(shortcutService, packageName, userId)
                    result.addAll(getListFromParceledListSlice(parceledList))
                }
                if (matchFlags and MATCH_DYNAMIC != 0) {
                    val parceledList = shortcutService.javaClass.getMethod("getDynamicShortcuts", String::class.java, Int::class.javaPrimitiveType)
                        .invoke(shortcutService, packageName, userId)
                    result.addAll(getListFromParceledListSlice(parceledList))
                }
            } catch (e: Exception) {
                Log.w(TAG, "getShortcutInfoCompat pre-R failed", e)
            }
            return result
        } else {
            var flags = 0
            if (matchFlags and MATCH_PINNED != 0) flags = flags or (1 shl 1) // FLAG_MATCH_PINNED
            if (matchFlags and MATCH_MANIFEST != 0) flags = flags or (1 shl 3) // FLAG_MATCH_MANIFEST
            if (matchFlags and MATCH_DYNAMIC != 0) flags = flags or (1 shl 0) // FLAG_MATCH_DYNAMIC
            if (matchFlags and MATCH_CACHED != 0) flags = flags or (1 shl 4) // FLAG_MATCH_CACHED

            try {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R || Build.VERSION.SDK_INT >= 33) {
                    val parceledList = shortcutService.javaClass.getMethod(
                        "getShortcuts",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).invoke(shortcutService, packageName, flags, userId)
                    return getListFromParceledListSlice(parceledList)
                } else {
                    // S and S_V2 (API 31, 32) return AndroidFuture<ParceledListSlice>
                    val future = shortcutService.javaClass.getMethod(
                        "getShortcuts",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).invoke(shortcutService, packageName, flags, userId)
                    
                    val parceledList = future.javaClass.getMethod("get").invoke(future)
                    return getListFromParceledListSlice(parceledList)
                }
            } catch (e: Exception) {
                Log.w(TAG, "getShortcutInfoCompat post-R failed", e)
                return emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getListFromParceledListSlice(parceledList: Any?): List<ShortcutInfo> {
        if (parceledList == null) return emptyList()
        try {
            return parceledList.javaClass.getMethod("getList").invoke(parceledList) as? List<ShortcutInfo> ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to getList from ParceledListSlice", e)
            return emptyList()
        }
    }

    fun getAllShortcutsViaSystemApi(userId: Int): Map<String, List<ShortcutInfo>> {
        if (!ensureSystemUid()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, List<ShortcutInfo>>()
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val pmBinder = serviceManager.getMethod("getService", String::class.java).invoke(null, "package") as IBinder
            val pmStubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val pm = pmStubClass.getMethod("asInterface", IBinder::class.java).invoke(null, pmBinder)

            val packagesParceled = try {
                pm.javaClass.getMethod("getInstalledPackages", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(pm, 0L, userId)
            } catch (e: NoSuchMethodException) {
                pm.javaClass.getMethod("getInstalledPackages", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(pm, 0, userId)
            }
            
            val packages = packagesParceled?.javaClass?.getMethod("getList")?.invoke(packagesParceled) as? List<Any> ?: emptyList()

            val shortcutBinder = serviceManager.getMethod("getService", String::class.java).invoke(null, "shortcut") as IBinder
            val shortcutStubClass = Class.forName("android.content.pm.IShortcutService\$Stub")
            val shortcutService = shortcutStubClass.getMethod("asInterface", IBinder::class.java).invoke(null, shortcutBinder)
                ?: return emptyMap()

            for (pkg in packages) {
                try {
                    val packageName = pkg.javaClass.getField("packageName").get(pkg) as String
                    val shortcuts = getShortcutInfoCompat(shortcutService, packageName, userId, MATCH_ALL)
                    if (shortcuts.isNotEmpty()) {
                        result[packageName] = shortcuts
                    }
                } catch (e: Exception) {
                    // Ignore errors for individual packages
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get all shortcuts via system API", e)
        }
        return result
    }
}
