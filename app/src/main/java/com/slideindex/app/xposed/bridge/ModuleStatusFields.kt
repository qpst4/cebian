package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock

/**
 * 模块状态串的字段解析 + 「模块代码是不是当前版本」判定。
 *
 * 状态串形如 `ready:hook=ok,proxy=ok,enable=ok,start=ok,receiver=ok,controller=ready:4,...,code=2,clip=ok`，
 * 其中 `code=` 是模块侧编译时的 [ModuleHookBridgeContract.MODULE_CODE_VERSION]。
 *
 * 模块在开机时被加载进 system_server，覆盖安装 APK 不会换掉里面正在跑的实例，
 * 所以"模块有响应"不等于"跑的是新代码"。判定有两条，任一命中即为
 * [CodeState.Stale]（设置页显示「需重启手机」）：
 *
 * 1. 模块回传的 `code=` 与当前 APK 不一致（旧模块甚至不带该字段）；
 * 2. 当前 APK 是在**本次开机之后**才覆盖安装的。
 *
 * 第 2 条不可省略：`code=` 只在人工 +1 时才变，普通更新（哪怕改了 `xposed/` 代码忘了 bump）
 * 都会被漏掉，而覆盖安装同时还会让 system_server 里那份实例丢掉与 App 的事件桥，
 * 因此"更新过 APK 就必须重启"是这两处设置页要如实告诉用户的事。
 */
object ModuleStatusFields {
    enum class CodeState {
        /** 模块回传的代码版本与当前 APK 一致。 */
        Current,

        /** 有响应但版本对不上（旧模块甚至不带该字段）：跑的是覆盖安装前的代码。 */
        Stale,

        /** 还没有任何响应。 */
        Unknown,
    }

    fun field(detail: String, prefix: String): String? =
        detail.split(',')
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun intField(detail: String, prefix: String): Int? = field(detail, prefix)?.toIntOrNull()

    /** 探针回传的 detail → 代码新旧（纯字符串判定，不含"本次开机后是否覆盖安装"）。 */
    fun codeStateOfDetail(detail: String?): CodeState {
        val value = detail?.takeIf { it.isNotBlank() && it != ModuleBridgeStatusProbe.NO_RESPONSE }
            ?: return CodeState.Unknown
        val code = intField(value, ModuleHookBridgeContract.STATUS_DETAIL_CODE_PREFIX)
            ?: return CodeState.Stale
        return if (code == ModuleHookBridgeContract.MODULE_CODE_VERSION) {
            CodeState.Current
        } else {
            CodeState.Stale
        }
    }

    /**
     * 探针回传的 detail → 代码新旧，含"本次开机后覆盖安装过 APK"判定。
     *
     * 模块完全没响应时保持 [CodeState.Unknown]：这种情况该引导用户启用模块 / 勾选作用域，
     * 不该一律说成"需重启"。
     */
    fun codeStateOfDetail(context: Context, detail: String?): CodeState =
        codeState(detail, apkInstalledAfterBoot(context))

    /** 状态缓存快照 → 代码新旧，含"本次开机后覆盖安装过 APK"判定。 */
    fun codeStateOf(context: Context, snapshot: ModuleBridgeStatusStore.Snapshot): CodeState {
        if (snapshot.updatedAtMs <= 0L) return CodeState.Unknown
        if (snapshot.detail.isBlank() && snapshot.state.isBlank()) return CodeState.Unknown
        return codeStateOfDetail(context, snapshot.detail)
    }

    /** 纯逻辑：字符串判定 + 覆盖安装判定。 */
    fun codeState(detail: String?, apkInstalledAfterBoot: Boolean): CodeState =
        when (codeStateOfDetail(detail)) {
            CodeState.Unknown -> CodeState.Unknown
            CodeState.Stale -> CodeState.Stale
            CodeState.Current -> if (apkInstalledAfterBoot) CodeState.Stale else CodeState.Current
        }

    /** 当前 APK 是否在本次开机之后才覆盖安装（含同码重装）。 */
    fun apkInstalledAfterBoot(context: Context): Boolean {
        val installedAtMs = moduleApkUpdatedAtMs(context) ?: return false
        return isInstalledAfterBoot(
            installedAtMs = installedAtMs,
            nowMs = System.currentTimeMillis(),
            uptimeMs = SystemClock.elapsedRealtime(),
        )
    }

    /**
     * 纯逻辑：APK 更新时刻晚于本次开机时刻即视为"系统里跑的还是旧实例"。
     *
     * 开机时刻用 `nowMs - uptimeMs` 推算（[SystemClock.elapsedRealtime] 含休眠），
     * 与 `PackageInfo.lastUpdateTime` 同源同钟，无需额外权限。
     */
    fun isInstalledAfterBoot(installedAtMs: Long, nowMs: Long, uptimeMs: Long): Boolean =
        installedAtMs > nowMs - uptimeMs

    private fun moduleApkUpdatedAtMs(context: Context): Long? = runCatching {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(appContext.packageName, 0)
        }
        info.lastUpdateTime.takeIf { it > 0L }
    }.getOrNull()
}
