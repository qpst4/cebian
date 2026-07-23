package com.slideindex.app.message

import com.slideindex.app.R

object MessageThemeCatalog {

    private val sideThemes = WeipopSideThemes.themes()

    private val danmakuThemes = listOf(
        danmakuTheme(
            id = MessageThemeIds.DEFAULT_DANMAKU_THEME_ID,
            labelRes = R.string.message_theme_default_barrage,
            backgroundResId = R.drawable.bg_message_danmaku_light,
            titleColor = "#474747",
        ),
        danmakuTheme(
            id = "danmaku_dark",
            labelRes = R.string.message_theme_dark_barrage,
            backgroundResId = R.drawable.bg_message_danmaku_dark,
            titleColor = "#F5F5F5",
        ),
        danmakuTheme(
            id = "danmaku_mint",
            labelRes = R.string.message_theme_danmaku_mint,
            backgroundResId = R.drawable.bg_message_danmaku_mint,
            titleColor = "#134E4A",
        ),
        danmakuTheme(
            id = "danmaku_coral",
            labelRes = R.string.message_theme_danmaku_coral,
            backgroundResId = R.drawable.bg_message_danmaku_coral,
            titleColor = "#FFFFFF",
        ),
        danmakuTheme(
            id = "danmaku_sky",
            labelRes = R.string.message_theme_danmaku_sky,
            backgroundResId = R.drawable.bg_message_danmaku_sky,
            titleColor = "#FFFFFF",
        ),
        danmakuTheme(
            id = "danmaku_outline",
            labelRes = R.string.message_theme_danmaku_outline,
            backgroundResId = R.drawable.bg_message_danmaku_outline,
            titleColor = "#FFFFFF",
        ),
    )

    fun findTheme(themeId: String): MessageThemeSpec? {
        val normalizedId = normalizeThemeId(themeId)
        return (sideThemes + danmakuThemes).firstOrNull { it.id == normalizedId }
    }

    fun normalizeThemeId(themeId: String): String = MessageThemeIds.normalizeThemeId(themeId)

    fun themeFor(style: MessageStyle, themeId: String): MessageThemeSpec {
        val normalizedId = normalizeThemeId(themeId)
        return themesFor(style).firstOrNull { it.id == normalizedId }
            ?: themesFor(style).first()
    }

    fun themesFor(style: MessageStyle): List<MessageThemeSpec> = when (style) {
        MessageStyle.Danmaku -> danmakuThemes
        MessageStyle.SideBubble -> sideThemes
        else -> sideThemes
    }

    fun defaultThemeIdFor(style: MessageStyle): String = MessageThemeIds.defaultThemeIdFor(style)

    private fun danmakuTheme(
        id: String,
        labelRes: Int,
        backgroundResId: Int,
        titleColor: String = "#474747",
        backgroundTintArgb: Int? = null,
        backgroundAlpha: Int = 255,
        cornerRadiusDp: Float = 20f,
    ): MessageThemeSpec {
        val titleArgb = MessageThemeColors.parseHex(titleColor)
        return MessageThemeSpec(
            id = id,
            style = MessageStyle.Danmaku,
            labelRes = labelRes,
            backgroundResId = backgroundResId,
            titleColorArgb = titleArgb,
            contentColorArgb = MessageThemeColors.contentColor(titleArgb),
            cornerRadiusDp = cornerRadiusDp,
            paddingHorizontalDp = 10f,
            paddingVerticalDp = 6f,
            backgroundTintArgb = backgroundTintArgb,
            backgroundAlpha = backgroundAlpha,
        )
    }
}
