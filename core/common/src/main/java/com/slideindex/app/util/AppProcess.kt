package com.slideindex.app.util

import android.app.Application

/**
 * 多进程架构下的进程身份。
 *
 * 目标架构：
 * - `:overlay`：无障碍服务 + 全部浮层/面板渲染 + 模块桥 + 系统级监听（自给自足，不依赖主进程）
 * - `:engine`：OCR / 翻译 / 公式 / 分词等引擎（OOM 隔离）
 * - 默认进程：设置 UI 与其它重 UI
 *
 * 注意：**静态状态在每个进程各有一份**，跨进程共享必须走显式的接口/快照通道，
 * 不要直接读另一个进程的 object 字段。本类只负责身份判定。
 *
 * 放在 `core:common` 是为了让 core 层模块（如 core:ocr）也能做进程判定。
 */
object AppProcess {
    const val OVERLAY_PROCESS_SUFFIX = ":overlay"
    const val ENGINE_PROCESS_SUFFIX = ":engine"
    /**
     * 剪贴板监听前台服务独占进程（`com.slideindex.app:clipboard`）。
     *
     * 单独拆出来是为了**隔离它自己的崩溃面**：这条服务历史上最容易踩
     * `ForegroundServiceDidNotStartInTimeException`（进程被系统干掉），
     * 之前和浮层同进程时一崩就带走悬浮球与全部手势。
     *
     * 名字刻意**不带连字符**：真机（MEIZU 21 / Android 16）实测，
     * 一旦 `android:process` 值里出现 `-`（如 `:clipboard-monitor`），安装时
     * Flyme 的 PackageParser 会抛 `ParsedServiceImpl cannot be cast to java.lang.String`
     * 直接拒绝安装。`:clipmonitor`、`:clipboard` 这类无连字符名字都正常。
     */
    const val CLIPBOARD_MONITOR_PROCESS_SUFFIX = ":clipboard"

    @Volatile
    private var cachedName: String? = null

    /** 形如 `com.slideindex.app:overlay`；取不到时返回空串。 */
    fun name(): String {
        cachedName?.let { return it }
        val detected = detect()
        cachedName = detected
        return detected
    }

    val isOverlay: Boolean get() = name().endsWith(OVERLAY_PROCESS_SUFFIX)
    val isEngine: Boolean get() = name().endsWith(ENGINE_PROCESS_SUFFIX)
    val isClipboardMonitor: Boolean get() = name().endsWith(CLIPBOARD_MONITOR_PROCESS_SUFFIX)

    /** 默认进程的进程名里没有 ':'（不依赖 BuildConfig，core 层也能用）。 */
    val isMain: Boolean get() = !name().contains(':')

    /**
     * 其它进程（例如 Shizuku 用户服务 `:task_manager_v36`）。
     * 这类进程既不是主进程也不是 overlay/engine，初始化要按最小集合处理。
     */
    val isOther: Boolean get() = !isMain && !isOverlay && !isEngine && !isClipboardMonitor

    private fun detect(): String {
        runCatching { Application.getProcessName() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // 兜底：/proc/self/cmdline（进程名以 \0 结尾）
        return runCatching {
            java.io.File("/proc/self/cmdline").readBytes()
                .takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        }.getOrDefault("")
    }
}
