package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings

/** Executes shake-bound gesture actions outside the accessibility fast path. */
interface ShakeActionPort {
    fun execute(
        action: GestureAction,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
    ): Boolean
}
