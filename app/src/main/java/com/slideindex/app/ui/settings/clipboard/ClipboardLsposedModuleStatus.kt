package com.slideindex.app.ui.settings.clipboard

import android.content.Context
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusProbe
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusStore
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract

/**
 * LSPosed 通道「模块此刻能不能真的干活」的判定。
 *
 * 只看两件事：模块回传的代码版本是否与当前 APK 一致、剪贴板白名单 hook 是否装上。
 * 不看 [ModuleBridgeStatusStore.Snapshot.active]——那个是手势接管是否生效，和剪贴板无关。
 */
object ClipboardLsposedModuleStatus {
    enum class Readiness {
        /** 模块代码与当前 APK 同版，且白名单 hook 已装上。 */
        Ready,

        /** 系统里跑的还是覆盖安装前的旧模块代码：重启手机后新代码才生效。 */
        StaleModuleCode,

        /** 模块在跑，但白名单 hook 没装上（系统内部实现改了等）。 */
        ClipboardHookMissing,

        /** 模块没回应：没启用 / 作用域没勾系统框架 / 装完没重启。 */
        NotReady,
    }

    private const val PROBE_THROTTLE_MS = 30_000L

    @Volatile
    private var lastProbeAtMs = 0L

    /** 先按本地缓存给一次结果，再按节流主动探一次模块。 */
    fun refresh(context: Context, onResult: (Readiness) -> Unit) {
        val appContext = context.applicationContext
        onResult(classify(ModuleBridgeStatusStore.read(appContext)))
        val now = System.currentTimeMillis()
        if (now - lastProbeAtMs < PROBE_THROTTLE_MS) return
        lastProbeAtMs = now
        ModuleBridgeStatusProbe.probe(appContext) { _, _ ->
            onResult(classify(ModuleBridgeStatusStore.read(appContext)))
        }
    }

    fun classify(snapshot: ModuleBridgeStatusStore.Snapshot): Readiness {
        if (snapshot.updatedAtMs <= 0L) return Readiness.NotReady
        val detail = snapshot.detail
        val moduleCode = intField(detail, ModuleHookBridgeContract.STATUS_DETAIL_CODE_PREFIX)
        // 旧模块不带 code 字段：能回应就说明模块活着，但那一定是覆盖安装前的代码。
        if (moduleCode == null) {
            return if (detail.isNotEmpty() || snapshot.state.isNotEmpty()) {
                Readiness.StaleModuleCode
            } else {
                Readiness.NotReady
            }
        }
        if (moduleCode != ModuleHookBridgeContract.MODULE_CODE_VERSION) {
            return Readiness.StaleModuleCode
        }
        val clipboardHook = field(detail, ModuleHookBridgeContract.STATUS_DETAIL_CLIPBOARD_PREFIX)
        return when (clipboardHook) {
            "ok" -> Readiness.Ready
            null -> Readiness.StaleModuleCode
            else -> Readiness.ClipboardHookMissing
        }
    }

    private fun field(detail: String, prefix: String): String? =
        detail.split(',')
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun intField(detail: String, prefix: String): Int? =
        field(detail, prefix)?.toIntOrNull()
}
