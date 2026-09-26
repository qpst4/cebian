package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.app.Application
import android.content.Context

internal object ClipboardMonitorProcess {
    /**
     * 剪贴板监听服务（[ClipboardMonitorForegroundService] / [ClipboardMonitorUserService]）由 `:overlay` 独占，
     * 因此"能启动监听"的进程是 `:overlay`，不再是默认进程。
     *
     * 主进程要做同步/重启时通过 [com.slideindex.app.overlay.OverlayStatePort] 下发命令。
     */
    fun isMonitorProcess(): Boolean = com.slideindex.app.util.AppProcess.isOverlay
}
