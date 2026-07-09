package com.slideindex.app.message

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class MessageThemeSpec(
    val id: String,
    val style: MessageStyle,
    @StringRes val labelRes: Int,
    @DrawableRes val backgroundResId: Int,
    @DrawableRes val sideLeftResId: Int = 0,
    @DrawableRes val sideRightResId: Int = 0,
    @ColorInt val titleColorArgb: Int,
    @ColorInt val contentColorArgb: Int = MessageThemeColors.contentColor(titleColorArgb),
    val cornerRadiusDp: Float = 12f,
    val paddingHorizontalDp: Float = 12f,
    val paddingVerticalDp: Float = 10f,
    val backgroundTintArgb: Int? = null,
    val backgroundAlpha: Int = 255,
    val avatarTranslationYDp: Int = 0,
    val avatarStartPaddingDp: Float = 0f,
)
