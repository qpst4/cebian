package com.slideindex.app.clipboard.monitor

/** Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT). */
class ClipboardMonitorConfig {
    var applicationId: String = ""

    /**
     * 自己写剪贴板后忽略事件的截止时间。
     *
     * 原先是单个 boolean：现在公开监听与特权通道会同时看到同一次写入，
     * 两条监听抢着消费它会让「自己写的」抑制失效，所以改成时间戳。
     */
    @Volatile
    private var ignoreEventsUntilMs: Long = 0L

    fun ignoreOwnClipboardWrite(windowMs: Long = DEFAULT_IGNORE_WINDOW_MS) {
        ignoreEventsUntilMs = System.currentTimeMillis() + windowMs
    }

    fun shouldIgnoreEvent(): Boolean = System.currentTimeMillis() < ignoreEventsUntilMs

    private companion object {
        const val DEFAULT_IGNORE_WINDOW_MS = 3_000L
    }
}
