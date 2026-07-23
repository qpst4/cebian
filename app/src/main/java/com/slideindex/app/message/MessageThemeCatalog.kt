package com.slideindex.app.message

import com.slideindex.app.R

object MessageThemeCatalog {

    private val sideThemes = WeipopSideThemes.themes()

    fun findTheme(themeId: String): MessageThemeSpec? {
        val normalizedId = normalizeThemeId(themeId)
        return sideThemes.firstOrNull { it.id == normalizedId }
    }

    fun normalizeThemeId(themeId: String): String = MessageThemeIds.normalizeThemeId(themeId)

    fun themeFor(style: MessageStyle, themeId: String): MessageThemeSpec {
        val normalizedId = normalizeThemeId(themeId)
        val sideTheme = sideThemes.firstOrNull { it.id == normalizedId } ?: sideThemes.first()
        return when (style) {
            MessageStyle.Danmaku -> sideTheme.toDanmakuPresentation()
            MessageStyle.SideBubble -> sideTheme
            else -> sideTheme
        }
    }

    fun themesFor(style: MessageStyle): List<MessageThemeSpec> = when (style) {
        MessageStyle.Danmaku,
        MessageStyle.SideBubble,
        -> sideThemes
        else -> sideThemes
    }

    fun defaultThemeIdFor(style: MessageStyle): String = MessageThemeIds.defaultThemeIdFor(style)
}
