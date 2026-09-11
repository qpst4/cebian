package com.slideindex.app.shizuku

import android.annotation.SuppressLint
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.UserHandle
import android.system.Os
import android.util.Log
import java.io.FileInputStream
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 系统 Shortcut / LauncherApps 反射入口。
 *
 * **C Notice 群头像（通知 shortcutId）**
 * - 须在 Shizuku UserService（shell uid）内调用；binder 勿再包 [ShizukuBinderWrapper]。
 * - Android 16 / Flyme：有效路径为 `ILauncherApps.getShortcutIconFd(
 *   callingPackage = "com.android.shell", packageName, shortcutId, UserHandle)`。
 * - 勿传目标 App 包名作 callingPackage；勿依赖 ShortcutInfo.icon（TG 群常为 null）。
 * - 失败时由上层显示群名首字，勿 fallback 到 App 图标。
 *
 * **快捷方式面板 bulk 扫描**见 [getAllShortcutsViaSystemApi]（另一条业务线）。
 */
object ShortcutSystemApiFetcher {
    private const val TAG = "ShortcutSystemApiFetcher"
    private const val SHELL_CALLING_PACKAGE = "com.android.shell"
    private const val LAUNCHER_CALLING_PACKAGE_FALLBACK = "com.meizu.flyme.launcher"

    private const val MATCH_PINNED = 1
    private const val MATCH_DYNAMIC = 1 shl 1
    private const val MATCH_MANIFEST = 1 shl 2
    private const val MATCH_CACHED = 1 shl 3
    private const val MATCH_ALL = MATCH_PINNED or MATCH_DYNAMIC or MATCH_MANIFEST or MATCH_CACHED

    private var systemUidApplied = false

    fun readShortcutIconBytes(
        packageName: String,
        shortcutId: String,
        userId: Int,
    ): ByteArray? {
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        return readShortcutIconViaLauncherApps(packageName, shortcutId, userId)
    }

    private fun readShortcutIconViaLauncherApps(
        packageName: String,
        shortcutId: String,
        userId: Int,
    ): ByteArray? {
        val service = obtainLauncherAppsService() ?: return null
        val user = userHandleFor(userId)
        for (caller in shellCallingPackages()) {
            readIconFd(
                service,
                "getShortcutIconFd",
                arrayOf<Any?>(caller, packageName, shortcutId, user),
            )?.let { bytes ->
                    Log.d(TAG, "shortcut icon via ILauncherApps(pkg,user) caller=$caller $packageName/$shortcutId")
                    return bytes
                }
            readIconFd(
                service,
                "getShortcutIconFd",
                arrayOf<Any?>(caller, packageName, shortcutId, userId),
            )
                ?.let { bytes ->
                    Log.d(TAG, "shortcut icon via ILauncherApps(pkg,int) caller=$caller $packageName/$shortcutId")
                    return bytes
                }
        }
        Log.w(TAG, "ILauncherApps icon load failed: $packageName/$shortcutId user=$userId")
        return null
    }

    private fun userHandleFor(userId: Int): UserHandle {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return runCatching {
                UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
                    .invoke(null, userId) as UserHandle
            }.getOrDefault(Process.myUserHandle())
        }
        return Process.myUserHandle()
    }

    private fun shellCallingPackages(): List<String> =
        listOf(SHELL_CALLING_PACKAGE, LAUNCHER_CALLING_PACKAGE_FALLBACK)

    private fun obtainLauncherAppsService(): Any? {
        return try {
            val binder = resolveSystemServiceBinder("launcherapps") ?: return null
            val stubClass = Class.forName("android.content.pm.ILauncherApps\$Stub")
            stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (e: Exception) {
            Log.w(TAG, "obtainLauncherAppsService failed", e)
            null
        }
    }

    private fun resolveSystemServiceBinder(serviceName: String): IBinder? {
        if (ensureSystemUid()) {
            val serviceManager = Class.forName("android.os.ServiceManager")
            return serviceManager.getMethod("getService", String::class.java)
                .invoke(null, serviceName) as? IBinder
        }
        val uid = Os.getuid()
        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) return null
        val raw = SystemServiceHelper.getSystemService(serviceName) ?: return null
        return if (uid == Process.SHELL_UID || uid == Process.ROOT_UID) {
            raw
        } else {
            ShizukuBinderWrapper(raw)
        }
    }

    private fun readIconFd(service: Any, method: String, args: Array<out Any?>): ByteArray? {
        return try {
            val parameterTypes = args.map { arg ->
                when (arg) {
                    is String -> String::class.java
                    is UserHandle -> UserHandle::class.java
                    is Int -> Int::class.javaPrimitiveType
                    else -> arg?.javaClass ?: Any::class.java
                }
            }.toTypedArray()
            val pfd = service.javaClass.getMethod(method, *parameterTypes)
                .invoke(service, *args) as? ParcelFileDescriptor
            pfd?.use { readParcelFileDescriptor(it) }
        } catch (_: NoSuchMethodException) {
            null
        } catch (e: Exception) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            Log.w(TAG, "readIconFd $method failed: ${cause.javaClass.simpleName}: ${cause.message}")
            null
        }
    }

    private fun readParcelFileDescriptor(pfd: ParcelFileDescriptor): ByteArray? =
        runCatching {
            FileInputStream(pfd.fileDescriptor).use { input ->
                input.readBytes().takeIf { it.isNotEmpty() }
            }
        }.onFailure { error ->
            Log.w(TAG, "readParcelFileDescriptor failed", error)
        }.getOrNull()

    private fun shortcutQueryFlags(matchFlags: Int): Int {
        var flags = 0
        if (matchFlags and MATCH_PINNED != 0) flags = flags or (1 shl 1)
        if (matchFlags and MATCH_MANIFEST != 0) flags = flags or (1 shl 3)
        if (matchFlags and MATCH_DYNAMIC != 0) flags = flags or (1 shl 0)
        if (matchFlags and MATCH_CACHED != 0) flags = flags or (1 shl 4)
        return flags
    }

    @Suppress("UNCHECKED_CAST")
    private fun getListFromParceledListSlice(parceledList: Any?): List<ShortcutInfo> {
        if (parceledList == null) return emptyList()
        return try {
            parceledList.javaClass.getMethod("getList").invoke(parceledList) as? List<ShortcutInfo> ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to getList from ParceledListSlice", e)
            emptyList()
        }
    }

    fun ensureSystemUid(): Boolean {
        if (systemUidApplied) return true
        val uid = Os.getuid()
        if (uid != Process.ROOT_UID && uid != Process.SYSTEM_UID) {
            return false
        }
        try {
            @Suppress("DEPRECATION")
            when (Os.getuid()) {
                Process.ROOT_UID -> {
                    Os.setgid(Process.SYSTEM_UID)
                    Os.setuid(Process.SYSTEM_UID)
                }
                else -> Os.seteuid(Process.SYSTEM_UID)
            }
            systemUidApplied = Os.geteuid() == Process.SYSTEM_UID
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set system UID", e)
        }
        return systemUidApplied
    }

    @SuppressLint("PrivateApi")
    @Suppress("UNCHECKED_CAST")
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

            val packages = packagesParceled?.javaClass?.getMethod("getList")?.invoke(packagesParceled) as? List<Any>
                ?: emptyList()

            val shortcutBinder = serviceManager.getMethod("getService", String::class.java)
                .invoke(null, "shortcut") as IBinder
            val shortcutStubClass = Class.forName("android.content.pm.IShortcutService\$Stub")
            val shortcutService = shortcutStubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, shortcutBinder) ?: return emptyMap()

            for (pkg in packages) {
                try {
                    val packageName = pkg.javaClass.getField("packageName").get(pkg) as String
                    val shortcuts = getShortcutInfoCompat(shortcutService, packageName, userId, MATCH_ALL)
                    if (shortcuts.isNotEmpty()) {
                        result[packageName] = shortcuts
                    }
                } catch (_: Exception) {
                    // Ignore errors for individual packages
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get all shortcuts via system API", e)
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun getShortcutInfoCompat(
        shortcutService: Any,
        packageName: String,
        userId: Int,
        matchFlags: Int,
    ): List<ShortcutInfo> {
        val flags = shortcutQueryFlags(matchFlags)
        for (caller in shellCallingPackages()) {
            invokeShortcutServiceGetShortcuts(shortcutService, caller, packageName, flags, userId)
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeShortcutServiceGetShortcuts(
        shortcutService: Any,
        callingPackage: String,
        targetPackage: String,
        flags: Int,
        userId: Int,
    ): List<ShortcutInfo>? {
        return try {
            val parceledList = shortcutService.javaClass.getMethod(
                "getShortcuts",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(shortcutService, callingPackage, targetPackage, flags, userId)
            getListFromParceledListSlice(parceledList)
        } catch (_: NoSuchMethodException) {
            null
        } catch (e: Exception) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            Log.w(TAG, "IShortcutService.getShortcuts failed: ${cause.javaClass.simpleName}: ${cause.message}")
            null
        }
    }
}
