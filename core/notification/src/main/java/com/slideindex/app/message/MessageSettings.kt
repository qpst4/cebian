package com.slideindex.app.message

data class MessageSettings(
    val enabled: Boolean = false,
    @Deprecated("Use per-style enabled flags")
    val styleId: String = MessageStyle.SideBubble.id,
    @Deprecated("Use per-style enabled flags")
    val primaryStyleEnabled: Boolean = true,
    val floatIconEnabled: Boolean = false,
    val sideBubbleEnabled: Boolean = true,
    val danmakuEnabled: Boolean = true,
    val cNoticeEnabled: Boolean = false,
    @Deprecated("Use sideThemeId")
    val themeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
    val sideThemeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
    val danmakuThemeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.Danmaku),
    val floatIconOpacity: Float = 0.95f,
    val sideBubbleOpacity: Float = 0.7f,
    val danmakuOpacity: Float = 0.7f,
    val cNoticeOpacity: Float = 1.0f,
    val cNoticeDimmedOpacity: Float = 0.4f,
    val cNoticeGhostOpacity: Float = 0.2f,
    val danmakuMaxLines: Int = 1,
    val sideMaxCount: Int = 3,
    val sideMaxWidthDp: Float = 168f,
    val sideMaxLines: Int = 2,
    val floatIconSizeDp: Float = 44f,
    val cNoticeIconSizeDp: Float = 44f,
    val cNoticeMaxCount: Int = 5,
    /** 悬浮球样式自动关闭时间，0 表示不自动关闭。 */
    val floatIconAutoDismissSeconds: Int = 5,
    /** 侧边气泡样式自动关闭时间，0 表示不自动关闭。 */
    val sideBubbleAutoDismissSeconds: Int = 5,
    /** C Notice 样式自动关闭时间，0 表示不自动关闭。 */
    val cNoticeAutoDismissSeconds: Int = 0,
    val cNoticeHorizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
    val cNoticeYFraction: Float = 0.5f,
    /** 贴边列表距屏幕左右边缘的距离（dp）。 */
    val cNoticeEdgeMarginDp: Float = 8f,
    val cNoticeDimDelayMs: Int = 300,
    val cNoticeGhostDelayMs: Int = 2500,
    val cNoticeDefaultCollapsed: Boolean = false,
    /** 新消息到达时，在对应悬浮球旁短暂显示内容横幅。 */
    val cNoticePeekBannerEnabled: Boolean = false,
    /** 横屏时是否显示贴边通知列表（与「横屏隐藏悬浮球与侧边」独立）。 */
    val cNoticeLandscapeEnabled: Boolean = true,
    val hideInLandscape: Boolean = false,
    val portraitDanmaku: Boolean = true,
    val landscapeDanmaku: Boolean = true,
    val sideBubbleHorizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
    val sideBubbleVerticalAnchor: SideBubbleVerticalAnchor = SideBubbleVerticalAnchor.Middle,
    val sideBubbleYFraction: Float = 0.5f,
    val floatIconCorner: MessageOverlayCorner = MessageOverlayCorner.BottomEnd,
    val floatIconYFraction: Float = MessagePlacementFractions.DEFAULT_BOTTOM_Y,
    val sideBubbleFontSizeLevel: Int = SideBubbleFontSize.NORMAL,
    val danmakuSpeedLevel: Int = DanmakuSpeed.NORMAL,
    val singleTapAction: MessageAction = MessageAction.Ignore,
    val swipeUpAction: MessageAction = MessageAction.Ignore,
    val swipeDownAction: MessageAction = MessageAction.Ignore,
    val swipeLeftAction: MessageAction = MessageAction.Ignore,
    val swipeRightAction: MessageAction = MessageAction.Ignore,
    val longPressAction: MessageAction = MessageAction.Ignore,
    val enabledPackages: Set<String> = emptySet(),
    val disabledPackages: Set<String> = emptySet(),
    val dndPackages: Set<String> = emptySet(),
    val suppressWhenSystemDnd: Boolean = false,
    val interceptNotifications: Boolean = false,
    val appFilterRules: Map<String, MessageAppFilterRule> = emptyMap(),
    /** 解锁屏幕后自动打开锁屏期间到达的最后一条消息。 */
    val openLastMessageOnUnlock: Boolean = false,
    /** 解锁后无需询问即可打开消息的应用。 */
    val openLastMessageAlwaysPackages: Set<String> = emptySet(),
    /** 解锁确认卡片自动消失时间，0 表示不自动消失。 */
    val unlockConfirmationAutoDismissSeconds: Int = 3,
) {
    @Suppress("DEPRECATION")
    val style: MessageStyle get() = MessageStyle.fromId(styleId)

    fun isPackageAllowed(packageName: String): Boolean {
        if (packageName in disabledPackages) return false
        if (enabledPackages.isEmpty()) return true
        return packageName in enabledPackages
    }

    fun filterRuleFor(packageName: String): MessageAppFilterRule =
        appFilterRules[packageName] ?: MessageAppFilterRule.default(packageName)

    fun passesAppFilter(packageName: String, title: String, content: String): Boolean =
        MessageAppFilterMatcher.passes(filterRuleFor(packageName), title, content)

    fun hasAnyStyleEnabled(): Boolean =
        floatIconEnabled || sideBubbleEnabled || danmakuEnabled || cNoticeEnabled

    companion object {
        const val C_NOTICE_MAX_RETAINED_CONVERSATIONS = 20
    }
}
