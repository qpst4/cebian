package com.slideindex.app.overlay

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import com.slideindex.app.message.MessageBubbleTypography

internal fun applyDanmakuTitleText(
    textView: TextView,
    text: String,
    colorArgb: Int,
    titleSp: Float = MessageBubbleTypography.TITLE_SP,
) {
    textView.text = text
    textView.setTextColor(colorArgb)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp)
    textView.typeface = Typeface.DEFAULT
    textView.maxLines = 1
    textView.ellipsize = TextUtils.TruncateAt.END
    textView.includeFontPadding = false
}

internal fun applyDanmakuContentText(
    textView: TextView,
    title: String,
    rawContent: String,
    titleColorArgb: Int,
    contentSp: Float = MessageBubbleTypography.CONTENT_SP,
    maxLines: Int,
) {
    textView.text = resolveSideBubbleContent(title, rawContent)
    textView.setTextColor(titleColorArgb)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, contentSp)
    textView.typeface = Typeface.DEFAULT_BOLD
    textView.maxLines = maxLines.coerceIn(1, 3)
    textView.ellipsize = TextUtils.TruncateAt.END
    textView.includeFontPadding = false
}

internal fun bubbleFontSizeLevelToSp(
    level: Int,
    normalSp: Float,
    smallSp: Float,
    largeSp: Float,
): Float = when (com.slideindex.app.message.SideBubbleFontSize.coerce(level)) {
    com.slideindex.app.message.SideBubbleFontSize.SMALL -> smallSp
    com.slideindex.app.message.SideBubbleFontSize.LARGE -> largeSp
    else -> normalSp
}
