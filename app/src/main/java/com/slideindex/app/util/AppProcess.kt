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
 */
object AppProcess {
    const val OVERLAY_PROCESS_SUFFIX = ":overlay"
    const val ENGINE_PROCESS_SUFFIX = ":engine"

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
    val isMain: Boolean get() = !isOverlay && !isEngine

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
