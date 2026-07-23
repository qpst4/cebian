package com.slideindex.app.message

data class MessageDisplayPlan(
    val data: NotificationData,
    val showFloatIcon: Boolean,
    val showSideBubble: Boolean,
    val showDanmaku: Boolean,
    val sideTheme: MessageThemeSpec?,
    val danmakuTheme: MessageThemeSpec?,
    val settings: MessageSettings,
) {
    fun enabledStyles(): List<MessageStyle> = buildList {
        if (showFloatIcon) add(MessageStyle.FloatIcon)
        if (showSideBubble) add(MessageStyle.SideBubble)
        if (showDanmaku) add(MessageStyle.Danmaku)
    }
}
