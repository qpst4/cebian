package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.app.Application
import android.content.Context

internal object ClipboardMonitorProcess {
    /**
     * 剪贴板监听前台服务由独立进程 `:clipboard-monitor` 独占（此前跟着 `:overlay`）。
     *
     * 为什么要独立：这条服务最容易踩前台服务启动超时被杀，跟浮层同进程时一崩就带走
     * 悬浮球与手势。拆开后它自己崩只影响剪贴板记录。
     *
     * 其它进程要启停监听时，直接按组件名启停 [ClipboardMonitorForegroundService]，
     * 由它在自己进程里解析监听模式。
     */
    fun isMonitorProcess(): Boolean = com.slideindex.app.util.AppProcess.isClipboardMonitor
}
