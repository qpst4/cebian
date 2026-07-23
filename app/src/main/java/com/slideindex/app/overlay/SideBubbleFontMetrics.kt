package com.slideindex.app.overlay

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.slideindex.app.message.SideBubbleFontSize

data class SideBubbleFontMetrics(
    val titleSize: TextUnit,
    val titleLineHeight: TextUnit,
    val contentSize: TextUnit,
    val contentLineHeight: TextUnit,
)

fun sideBubbleFontMetrics(level: Int): SideBubbleFontMetrics =
    when (SideBubbleFontSize.coerce(level)) {
        SideBubbleFontSize.SMALL -> SideBubbleFontMetrics(
            titleSize = 8.sp,
            titleLineHeight = 9.sp,
            contentSize = 10.sp,
            contentLineHeight = 12.sp,
        )
        SideBubbleFontSize.LARGE -> SideBubbleFontMetrics(
            titleSize = 12.sp,
            titleLineHeight = 13.sp,
            contentSize = 14.sp,
            contentLineHeight = 16.sp,
        )
        else -> SideBubbleFontMetrics(
            titleSize = 10.sp,
            titleLineHeight = 11.sp,
            contentSize = 12.sp,
            contentLineHeight = 14.sp,
        )
    }
