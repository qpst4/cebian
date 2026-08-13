package com.slideindex.app.ui.trigger

/** 触钮设置页是否处于横屏独立布局编辑态（跨子页面导航共享）。 */
object TriggerSettingsLandscapeSession {
    var active: Boolean = false

    /** null = 跟随系统方向；非 null = 用户手动指定展示竖/横屏触钮。 */
    var manualLandscapeDisplay: Boolean? = null

    fun displayLandscape(systemLandscape: Boolean): Boolean =
        manualLandscapeDisplay ?: systemLandscape

    fun releaseForExit() {
        active = false
        manualLandscapeDisplay = null
    }

    fun setDisplayLandscape(landscape: Boolean) {
        manualLandscapeDisplay = landscape
        updateActive(landscape)
    }

    fun updateActive(landscape: Boolean) {
        if (active == landscape) return
        active = landscape
    }
}
