package com.slideindex.app.overlay

import androidx.annotation.DrawableRes
import com.slideindex.app.R
import com.slideindex.app.settings.FloatBallStyleType

internal object FloatBallBuiltinAnimCatalog {

    @DrawableRes
    fun animatedDrawableRes(type: FloatBallStyleType): Int? = when (type) {
        FloatBallStyleType.ANIMATED_PLANE -> R.drawable.avd_float_ball_plane
        FloatBallStyleType.ANIMATED_PULSE -> R.drawable.avd_float_ball_pulse
        FloatBallStyleType.ANIMATED_ORBIT -> R.drawable.avd_float_ball_orbit
        else -> null
    }

    fun isBuiltinAnimated(type: FloatBallStyleType): Boolean = animatedDrawableRes(type) != null
}
