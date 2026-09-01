package com.slideindex.app.settings

import com.slideindex.app.gesture.SwipePathRecognizer

object SwipeHoverDurationLimits {
    const val MIN_MS = SwipePathRecognizer.HOVER_DURATION_MIN_MS.toInt()
    const val MAX_MS = SwipePathRecognizer.HOVER_DURATION_MAX_MS.toInt()
    const val DEFAULT_MS = SwipePathRecognizer.DEFAULT_HOVER_DURATION_MS.toInt()
}
