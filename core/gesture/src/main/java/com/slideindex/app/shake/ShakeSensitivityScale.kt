package com.slideindex.app.shake

object ShakeSensitivityScale {
    const val UI_MIN = 1f
    const val UI_MAX = 20f
    const val UI_STEPS = 18

    /** ~same trigger difficulty as legacy default UI 3.0 on the 1–10 scale. */
    const val DEFAULT_UI = 14f

    const val LEGACY_UI_MIN = 1f
    const val LEGACY_UI_MAX = 10f
    const val LEGACY_DEFAULT_UI = 3f

    private const val THRESHOLD_MIN = 2f
    private const val THRESHOLD_MAX = 20f

    /** MIUIX Custom Key Points：range 均分 4 段（5 个刻度，含两端）。 */
    val UI_KEY_POINTS: List<Float> = run {
        val span = UI_MAX - UI_MIN
        List(5) { index -> UI_MIN + span * index / 4f }
    }

    fun clampUi(value: Float): Float = value.coerceIn(UI_MIN, UI_MAX)

    fun effectiveThreshold(uiValue: Float): Float {
        val ui = clampUi(uiValue)
        return THRESHOLD_MAX - (ui - UI_MIN) * (THRESHOLD_MAX - THRESHOLD_MIN) / (UI_MAX - UI_MIN)
    }

    /** Preserves gyro threshold when upgrading stored UI values from the 1–10 scale. */
    fun migrateUiFromV2(legacyUi: Float): Float {
        val clamped = legacyUi.coerceIn(LEGACY_UI_MIN, LEGACY_UI_MAX)
        return clampUi(1f + (9f + clamped) * (UI_MAX - UI_MIN) / 18f)
    }
}
