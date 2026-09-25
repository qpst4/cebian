package com.slideindex.app.xposed.bridge

/**
 * 模块状态串的字段解析 + 「模块代码是不是当前版本」判定。
 *
 * 状态串形如 `ready:hook=ok,proxy=ok,enable=ok,start=ok,receiver=ok,controller=ready:4,...,code=2,clip=ok`，
 * 其中 `code=` 是模块侧编译时的 [ModuleHookBridgeContract.MODULE_CODE_VERSION]。
 *
 * 剪贴板页与系统手势接管页共用这份判定：模块在开机时被加载进 system_server，
 * 覆盖安装 APK 不会换掉里面正在跑的代码，所以"模块有响应"不等于"跑的是新代码"。
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

    /** 探针回传的 detail → 代码新旧。 */
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

    /** 状态缓存快照 → 代码新旧。 */
    fun codeStateOf(snapshot: ModuleBridgeStatusStore.Snapshot): CodeState {
        if (snapshot.updatedAtMs <= 0L) return CodeState.Unknown
        if (snapshot.detail.isBlank() && snapshot.state.isBlank()) return CodeState.Unknown
        return codeStateOfDetail(snapshot.detail)
    }
}
