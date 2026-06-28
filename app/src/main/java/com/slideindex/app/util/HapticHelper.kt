package com.slideindex.app.util

import android.view.HapticFeedbackConstants
import android.view.View
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HapticStrength
import com.slideindex.app.settings.resolvedHapticStrength

object HapticHelper {
    private const val FLAGS = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING

    fun gestureStart(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.GESTURE)
    }

    fun letterTick(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.LETTER)
    }

    fun appTick(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.APP)
    }

    fun confirmLaunch(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.CONFIRM)
    }

    fun preview(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.CONFIRM)
    }

    private enum class PulseKind {
        GESTURE,
        LETTER,
        APP,
        CONFIRM,
    }

    private fun pulse(view: View, settings: AppSettings, kind: PulseKind) {
        if (!settings.hapticEnabled) return
        view.performHapticFeedback(feedbackConstant(kind, settings.resolvedHapticStrength()), FLAGS)
    }

    private fun feedbackConstant(kind: PulseKind, strength: HapticStrength): Int =
        when (strength) {
            HapticStrength.LIGHT -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.CLOCK_TICK
                PulseKind.LETTER -> HapticFeedbackConstants.CLOCK_TICK
                PulseKind.APP -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            HapticStrength.MEDIUM -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.GESTURE_START
                PulseKind.LETTER -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.APP -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            }
            HapticStrength.STRONG -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.GESTURE_START
                PulseKind.LETTER -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.APP -> HapticFeedbackConstants.CONFIRM
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            }
        }
}
