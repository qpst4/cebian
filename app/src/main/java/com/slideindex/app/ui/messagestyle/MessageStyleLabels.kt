package com.slideindex.app.ui.messagestyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageThemeCatalog

@Composable
fun messageStyleLabel(style: MessageStyle): String = when (style) {
    MessageStyle.FloatIcon -> stringResource(R.string.message_style_float_icon)
    MessageStyle.SideBubble -> stringResource(R.string.message_style_side_bubble)
    MessageStyle.Danmaku -> stringResource(R.string.message_style_danmaku)
}

@Composable
fun messageThemeLabel(themeId: String): String {
    val theme = MessageThemeCatalog.findTheme(themeId)
    return if (theme != null) {
        stringResource(theme.labelRes)
    } else {
        themeId
    }
}

@Composable
fun messageStyleSummary(settings: MessageSettings): String {
    val parts = buildList {
        if (settings.floatIconEnabled) add(messageStyleLabel(MessageStyle.FloatIcon))
        if (settings.sideBubbleEnabled) add(messageStyleLabel(MessageStyle.SideBubble))
        if (settings.danmakuEnabled) add(messageStyleLabel(MessageStyle.Danmaku))
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.message_reminder_entry_disabled)
    } else {
        parts.joinToString(" + ")
    }
}
