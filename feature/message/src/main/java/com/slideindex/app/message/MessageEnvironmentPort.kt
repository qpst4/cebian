package com.slideindex.app.message

import android.content.Context

interface MessageEnvironmentPort {
    fun isSystemDndEnabled(context: Context): Boolean

    /** 当前是否处于锁屏（含熄屏）状态；用于决定消息是否应在解锁后自动打开。 */
    fun isScreenLocked(context: Context): Boolean = false
}
