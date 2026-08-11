package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import kotlin.math.roundToInt

data class QuickLauncherDisplaySettings(
    val backgroundOpacityPercent: Int = DEFAULT_BACKGROUND_OPACITY_PERCENT,
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    val iconShape: Int = ICON_SHAPE_DEFAULT,
) {
    companion object {
        const val MIN_BACKGROUND_OPACITY_PERCENT = 0
        const val MAX_BACKGROUND_OPACITY_PERCENT = 100
        /** 对齐旧版 `225 × panelOpacity(0.95)` → alpha 213 ≈ 84% */
        const val DEFAULT_BACKGROUND_OPACITY_PERCENT = 84

        const val MIN_ICON_SIZE_DP = 28
        const val MAX_ICON_SIZE_DP = 48
        /** 对齐 overlay 硬编码 `host.dp(38f)` */
        const val DEFAULT_ICON_SIZE_DP = 38

        /** 不二次裁剪，保留系统绘制结果。 */
        const val ICON_SHAPE_DEFAULT = 0
        /** @deprecated 已移除；持久化值 1 会迁移为 [ICON_SHAPE_DEFAULT]。 */
        const val ICON_SHAPE_ROUNDED_LEGACY = 1
        const val ICON_SHAPE_CIRCLE = 2
        const val ICON_SHAPE_ADAPTIVE = 3

        fun coerceIconShape(shape: Int): Int = when (shape) {
            ICON_SHAPE_CIRCLE, ICON_SHAPE_ADAPTIVE -> shape
            else -> ICON_SHAPE_DEFAULT
        }

        fun legacyBackgroundOpacityPercent(panelOpacity: Float): Int {
            val alpha = (225f * panelOpacity).toInt().coerceIn(150, 225)
            return (alpha * 100f / 255f).roundToInt()
                .coerceIn(MIN_BACKGROUND_OPACITY_PERCENT, MAX_BACKGROUND_OPACITY_PERCENT)
        }

        fun backgroundAlphaArgb(backgroundOpacityPercent: Int): Int =
            (255f * backgroundOpacityPercent.coerceIn(
                MIN_BACKGROUND_OPACITY_PERCENT,
                MAX_BACKGROUND_OPACITY_PERCENT,
            ) / 100f).roundToInt().coerceIn(0, 255)

        fun fromPreferences(prefs: Preferences, panelOpacity: Float): QuickLauncherDisplaySettings {
            val storedOpacity = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_BACKGROUND_OPACITY_PERCENT]
            return QuickLauncherDisplaySettings(
                backgroundOpacityPercent = storedOpacity?.coerceIn(
                    MIN_BACKGROUND_OPACITY_PERCENT,
                    MAX_BACKGROUND_OPACITY_PERCENT,
                ) ?: legacyBackgroundOpacityPercent(panelOpacity),
                iconSizeDp = (prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SIZE_DP]
                    ?: DEFAULT_ICON_SIZE_DP)
                    .coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP),
                iconShape = coerceIconShape(
                    prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SHAPE] ?: ICON_SHAPE_DEFAULT,
                ),
            )
        }
    }
}
