package com.slideindex.app.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    fun longThreshold(view: View, settings: AppSettings) {
        pulse(view, settings, PulseKind.LONG_THRESHOLD)
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
        LONG_THRESHOLD,
        LETTER,
        APP,
        CONFIRM,
    }

    private fun pulse(view: View, settings: AppSettings, kind: PulseKind) {
        if (!settings.hapticEnabled) return
        val constant = feedbackConstant(kind, settings.resolvedHapticStrength())
        if (view.isAttachedToWindow) {
            view.performHapticFeedback(constant, FLAGS)
        } else {
            vibrateFallback(view.context, constant)
        }
    }

    private fun vibrateFallback(context: Context, feedbackConstant: Int) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val durationMs = when (feedbackConstant) {
            HapticFeedbackConstants.GESTURE_START,
            HapticFeedbackConstants.CONFIRM,
            -> 24L
            HapticFeedbackConstants.CONTEXT_CLICK -> 16L
            else -> 10L
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrator(context: Context): Vibrator? {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        return manager?.defaultVibrator
    }

    private fun feedbackConstant(kind: PulseKind, strength: HapticStrength): Int =
        when (strength) {
            HapticStrength.LIGHT -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.CLOCK_TICK
                PulseKind.LONG_THRESHOLD -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.LETTER -> HapticFeedbackConstants.CLOCK_TICK
                PulseKind.APP -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            HapticStrength.MEDIUM -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.GESTURE_START
                PulseKind.LONG_THRESHOLD -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.LETTER -> HapticFeedbackConstants.KEYBOARD_TAP
                PulseKind.APP -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            }
            HapticStrength.STRONG -> when (kind) {
                PulseKind.GESTURE -> HapticFeedbackConstants.GESTURE_START
                PulseKind.LONG_THRESHOLD -> HapticFeedbackConstants.CONFIRM
                PulseKind.LETTER -> HapticFeedbackConstants.CONTEXT_CLICK
                PulseKind.APP -> HapticFeedbackConstants.CONFIRM
                PulseKind.CONFIRM -> HapticFeedbackConstants.CONFIRM
            }
        }
}
