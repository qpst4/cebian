package com.slideindex.app.message

import com.slideindex.app.message.MessageStyle

/** Supplies themed assets for message reminder overlays. */
interface MessageThemePort {
    fun themeFor(style: MessageStyle, themeId: String): MessageThemeSpec
}
