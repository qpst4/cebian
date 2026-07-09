package com.slideindex.app.message

data class MessageDisplayPlan(
    val data: NotificationData,
    val primaryStyle: MessageStyle?,
    val cardTheme: MessageThemeSpec?,
    val showDanmaku: Boolean,
    val danmakuTheme: MessageThemeSpec?,
    val settings: MessageSettings,
)
