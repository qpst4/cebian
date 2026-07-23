package com.slideindex.app.message

object MessageThemeIds {
    const val DEFAULT_SIDE_THEME_ID = "side_weipop_round_white"
    const val DEFAULT_DANMAKU_THEME_ID = "danmaku"

    private val legacyThemeIds = mapOf(
        "default_dark" to DEFAULT_SIDE_THEME_ID,
        "light_card" to DEFAULT_SIDE_THEME_ID,
        "default_danmaku" to DEFAULT_DANMAKU_THEME_ID,
        "default_side" to DEFAULT_SIDE_THEME_ID,
        "light_side" to DEFAULT_SIDE_THEME_ID,
        "card_book" to DEFAULT_SIDE_THEME_ID,
        "card_light" to DEFAULT_SIDE_THEME_ID,
        "miui" to DEFAULT_SIDE_THEME_ID,
        "card_black" to DEFAULT_SIDE_THEME_ID,
        "card_pure_black" to DEFAULT_SIDE_THEME_ID,
        "cool_black" to DEFAULT_SIDE_THEME_ID,
        "card_dark" to DEFAULT_SIDE_THEME_ID,
        "card_round_white" to DEFAULT_SIDE_THEME_ID,
        "green_mountains" to DEFAULT_SIDE_THEME_ID,
        "pure_text" to DEFAULT_SIDE_THEME_ID,
        "summer_watermelon" to DEFAULT_SIDE_THEME_ID,
        "summer_water" to DEFAULT_SIDE_THEME_ID,
        "card_luzi" to DEFAULT_SIDE_THEME_ID,
        "card_cat" to DEFAULT_SIDE_THEME_ID,
        "card_dialog_box" to DEFAULT_SIDE_THEME_ID,
        "card_frost" to DEFAULT_SIDE_THEME_ID,
        "card_slate" to DEFAULT_SIDE_THEME_ID,
        "card_soft_white" to DEFAULT_SIDE_THEME_ID,
        "card_soft_black" to DEFAULT_SIDE_THEME_ID,
        "card_glass" to DEFAULT_SIDE_THEME_ID,
        "card_ocean" to DEFAULT_SIDE_THEME_ID,
        "card_warm" to DEFAULT_SIDE_THEME_ID,
        "side_message" to DEFAULT_SIDE_THEME_ID,
        "side_message_black" to "side_weipop_round_black",
        "side_message_book" to DEFAULT_SIDE_THEME_ID,
        "side_message_luzi" to DEFAULT_SIDE_THEME_ID,
        "side_message_cat" to DEFAULT_SIDE_THEME_ID,
        "side_message_dialog_box" to DEFAULT_SIDE_THEME_ID,
        "danmaku_book" to DEFAULT_DANMAKU_THEME_ID,
        "danmaku_luzi" to DEFAULT_DANMAKU_THEME_ID,
        "danmaku_cat" to DEFAULT_DANMAKU_THEME_ID,
        "danmaku_dialog_box" to DEFAULT_DANMAKU_THEME_ID,
        "danmaku_round_light" to DEFAULT_DANMAKU_THEME_ID,
        "danmaku_round_dark" to "danmaku_dark",
    )

    fun normalizeThemeId(themeId: String): String = legacyThemeIds[themeId] ?: themeId

    fun defaultThemeIdFor(style: MessageStyle): String = when (style) {
        MessageStyle.Danmaku -> DEFAULT_DANMAKU_THEME_ID
        MessageStyle.SideBubble -> DEFAULT_SIDE_THEME_ID
        else -> DEFAULT_SIDE_THEME_ID
    }
}
