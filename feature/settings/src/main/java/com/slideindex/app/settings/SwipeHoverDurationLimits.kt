package com.slideindex.app.settings

import com.slideindex.app.gesture.SwipePathRecognizer

object SwipeHoverDurationLimits {
    const val MIN_MS = SwipePathRecognizer.HOVER_DURATION_MIN_MS.toInt()
    const val MAX_MS = SwipePathRecognizer.HOVER_DURATION_MAX_MS.toInt()
    const val DEFAULT_MS = SwipePathRecognizer.DEFAULT_HOVER_DURATION_MS.toInt()

    /** MIUIX Custom Key Points：范围均分 4 段（5 个刻度，含两端）。 */
    val UI_KEY_POINTS: List<Float> = List(5) { index ->
        MIN_MS + (MAX_MS - MIN_MS) * index / 4f
    }
}
