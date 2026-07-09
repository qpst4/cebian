package com.slideindex.app.message

data class MessageSettings(
    val enabled: Boolean = false,
    val styleId: String = MessageStyle.DarkCard.id,
    val themeId: String = MessageThemeCatalog.defaultThemeIdFor(MessageStyle.DarkCard),
    val primaryStyleEnabled: Boolean = true,
    val danmakuEnabled: Boolean = true,
    val danmakuThemeId: String = MessageThemeCatalog.defaultThemeIdFor(MessageStyle.Danmaku),
    val floatIconOpacity: Float = 0.95f,
    val cardOpacity: Float = 0.95f,
    val sideBubbleOpacity: Float = 0.95f,
    val danmakuOpacity: Float = 0.9f,
    val cardMaxLines: Int = 2,
    val danmakuMaxLines: Int = 1,
    val sideMaxCount: Int = 3,
    val sideMaxWidthDp: Float = 200f,
    val sideMaxLines: Int = 2,
    val floatIconSizeDp: Float = 44f,
    val autoDismissSeconds: Int = 5,
    val hideInLandscape: Boolean = false,
    val portraitDanmaku: Boolean = true,
    val landscapeDanmaku: Boolean = true,
    val singleTapAction: MessageAction = MessageAction.Ignore,
    val swipeUpAction: MessageAction = MessageAction.Ignore,
    val swipeDownAction: MessageAction = MessageAction.Ignore,
    val swipeLeftAction: MessageAction = MessageAction.Ignore,
    val swipeRightAction: MessageAction = MessageAction.Ignore,
    val enabledPackages: Set<String> = emptySet(),
    val disabledPackages: Set<String> = emptySet(),
    val dndPackages: Set<String> = emptySet(),
    val suppressWhenSystemDnd: Boolean = false,
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

    fun passesAppFilter(data: NotificationData): Boolean =
        MessageAppFilterMatcher.passes(filterRuleFor(data.packageName), data.title, data.content)
}
