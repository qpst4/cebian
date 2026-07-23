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
    @Deprecated("Use sideThemeId")
    val themeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
    val sideThemeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.SideBubble),
    val danmakuThemeId: String = MessageThemeIds.defaultThemeIdFor(MessageStyle.Danmaku),
    val floatIconOpacity: Float = 0.95f,
    val sideBubbleOpacity: Float = 0.95f,
    val danmakuOpacity: Float = 0.9f,
    val danmakuMaxLines: Int = 1,
    val sideMaxCount: Int = 3,
    val sideMaxWidthDp: Float = 168f,
    val sideMaxLines: Int = 2,
    val floatIconSizeDp: Float = 44f,
    val autoDismissSeconds: Int = 5,
    val hideInLandscape: Boolean = false,
    val portraitDanmaku: Boolean = true,
    val landscapeDanmaku: Boolean = true,
    val sideBubbleHorizontalEdge: SideBubbleHorizontalEdge = SideBubbleHorizontalEdge.Right,
    val sideBubbleVerticalAnchor: SideBubbleVerticalAnchor = SideBubbleVerticalAnchor.Middle,
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
) {
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
        floatIconEnabled || sideBubbleEnabled || danmakuEnabled
}
