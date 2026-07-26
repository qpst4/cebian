package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.gesture.GestureAngle
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.overlay.PanelSide

internal object GestureAnglesCodec {
    fun read(prefs: Preferences, legacyConfig: GestureAngleConfig): GestureAngles {
        val left = readAngle(prefs, PanelSide.LEFT) ?: GestureAngle.fromLegacyConfig(legacyConfig)
        val right = readAngle(prefs, PanelSide.RIGHT) ?: GestureAngle.fromLegacyConfig(legacyConfig)
        val bottom = readAngle(prefs, PanelSide.BOTTOM) ?: GestureAngle.DEFAULT_BOTTOM
        return GestureAngles(left = left, right = right, bottom = bottom)
    }

    fun writeAngles(prefs: MutablePreferences, angles: GestureAngles) {
        writeAngle(prefs, PanelSide.LEFT, angles.left)
        writeAngle(prefs, PanelSide.RIGHT, angles.right)
        writeAngle(prefs, PanelSide.BOTTOM, angles.bottom)
    }

    private fun readAngle(prefs: Preferences, side: PanelSide): GestureAngle? {
        val p1 = prefs[key(side, 1)] ?: return null
        val p2 = prefs[key(side, 2)] ?: return null
        val p3 = prefs[key(side, 3)] ?: return null
        val p4 = prefs[key(side, 4)] ?: return null
        return GestureAngle(p1, p2, p3, p4)
    }

    private fun writeAngle(prefs: MutablePreferences, side: PanelSide, angle: GestureAngle) {
        prefs[key(side, 1)] = angle.p1
        prefs[key(side, 2)] = angle.p2
        prefs[key(side, 3)] = angle.p3
        prefs[key(side, 4)] = angle.p4
    }

    private fun key(side: PanelSide, index: Int) = when (side) {
        PanelSide.LEFT -> when (index) {
            1 -> SettingsPreferenceKeys.GESTURE_ANGLE_LEFT_P1
            2 -> SettingsPreferenceKeys.GESTURE_ANGLE_LEFT_P2
            3 -> SettingsPreferenceKeys.GESTURE_ANGLE_LEFT_P3
            4 -> SettingsPreferenceKeys.GESTURE_ANGLE_LEFT_P4
            else -> error("bad index")
        }
        PanelSide.RIGHT -> when (index) {
            1 -> SettingsPreferenceKeys.GESTURE_ANGLE_RIGHT_P1
            2 -> SettingsPreferenceKeys.GESTURE_ANGLE_RIGHT_P2
            3 -> SettingsPreferenceKeys.GESTURE_ANGLE_RIGHT_P3
            4 -> SettingsPreferenceKeys.GESTURE_ANGLE_RIGHT_P4
            else -> error("bad index")
        }
        PanelSide.BOTTOM -> when (index) {
            1 -> SettingsPreferenceKeys.GESTURE_ANGLE_BOTTOM_P1
            2 -> SettingsPreferenceKeys.GESTURE_ANGLE_BOTTOM_P2
            3 -> SettingsPreferenceKeys.GESTURE_ANGLE_BOTTOM_P3
            4 -> SettingsPreferenceKeys.GESTURE_ANGLE_BOTTOM_P4
            else -> error("bad index")
        }
    }
}
