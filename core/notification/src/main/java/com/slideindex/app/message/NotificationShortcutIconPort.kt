package com.slideindex.app.message

import android.graphics.Bitmap

/**
 * 通过系统级 Shortcut 服务解析通知 [shortcutId] 对应的会话/群头像。
 * 实现方通常依赖 Shizuku，在 LauncherApps 查询失败时使用。
 */
fun interface NotificationShortcutIconPort {
    fun loadShortcutIcon(packageName: String, shortcutId: String, userId: Int): Bitmap?
}
