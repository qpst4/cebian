package com.slideindex.app.ui.trigger

import android.app.Activity
import android.content.pm.ActivityInfo

/** 触钮设置页是否处于横屏独立布局编辑态（跨子页面导航共享）。 */
object TriggerSettingsLandscapeSession {
    var active: Boolean = false

    /** null = 跟随系统方向；非 null = 用户手动指定展示竖/横屏触钮。 */
    var manualLandscapeDisplay: Boolean? = null

    fun displayLandscape(systemLandscape: Boolean): Boolean =
        manualLandscapeDisplay ?: systemLandscape

    fun lockLandscapeOrientation(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun releaseToPortraitOrientation(activity: Activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun releaseForExit(activity: Activity) {
        active = false
        manualLandscapeDisplay = null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun setDisplayLandscape(landscape: Boolean, activity: Activity?) {
        manualLandscapeDisplay = landscape
        setActive(landscape, activity)
    }

    fun setActive(landscape: Boolean, activity: Activity?) {
        if (active == landscape) return
        val wasActive = active
        active = landscape
        if (activity == null) return
        when {
            landscape -> lockLandscapeOrientation(activity)
            wasActive -> releaseToPortraitOrientation(activity)
        }
    }
}
