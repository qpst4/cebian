package com.slideindex.app.message

import com.slideindex.app.message.MessageStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageThemePort @Inject constructor() : MessageThemePort {
    override fun themeFor(style: MessageStyle, themeId: String): MessageThemeSpec =
        MessageThemeCatalog.themeFor(style, themeId)
}
