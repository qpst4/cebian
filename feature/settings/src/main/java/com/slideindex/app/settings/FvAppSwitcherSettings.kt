package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvIconShape
import kotlin.math.min

/** FV CircleAppContainer 风格圆环启动器设置（圈数、外观尺寸、半径与槽位）。 */
data class FvAppSwitcherSettings(
    val circleCount: Int = DEFAULT_CIRCLE_COUNT,
    val iconSizeDp: Float = DEFAULT_ICON_SIZE_DP,
    val iconShape: FvIconShape = FvIconShape.ROUNDED_RECT,
    val baseRadiusDp: Float = DEFAULT_BASE_RADIUS_DP,
    val layerGapDp: Float = DEFAULT_LAYER_GAP_DP,
    val endMarginDeg: Float = DEFAULT_END_MARGIN_DEG,
    val slots: Map<Int, QuickLauncherItem> = emptyMap(),
) {
    fun slotCount(): Int = slotCountForCircleCount(circleCount)

    fun itemAt(index: Int): QuickLauncherItem? = slots[index]

    fun configuredCount(): Int = slots.values.count { it.payload.isNotBlank() }

    companion object {
        const val MIN_CIRCLE_COUNT = 1
        const val MAX_CIRCLE_COUNT = 4
        const val DEFAULT_CIRCLE_COUNT = 2
        const val MAX_SLOTS = 38

        const val MIN_ICON_SIZE_DP = 24f
        const val MAX_ICON_SIZE_DP = 56f
        const val DEFAULT_ICON_SIZE_DP = FvCircleLayoutEngine.ICON_SIZE_DP

        const val MIN_BASE_RADIUS_DP = 60f
        const val MAX_BASE_RADIUS_DP = 130f
        const val DEFAULT_BASE_RADIUS_DP = FvCircleLayoutEngine.DEFAULT_BASE_RADIUS_DP

        const val MIN_LAYER_GAP_DP = 35f
        const val MAX_LAYER_GAP_DP = 75f
        const val DEFAULT_LAYER_GAP_DP = FvCircleLayoutEngine.DEFAULT_LAYER_GAP_DP

        const val MIN_END_MARGIN_DEG = 0f
        const val MAX_END_MARGIN_DEG = 30f
        const val DEFAULT_END_MARGIN_DEG = FvCircleLayoutEngine.DEFAULT_END_MARGIN_DEG

        fun slotCountForCircleCount(circleCount: Int): Int = when (circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT)) {
            1 -> 5
            2 -> 13
            3 -> 24
            else -> MAX_SLOTS
        }

        fun fromPreferences(prefs: Preferences): FvAppSwitcherSettings {
            val circleCount = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_CIRCLE_COUNT]
                ?: DEFAULT_CIRCLE_COUNT
            val iconSizeDp = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_ICON_SIZE_DP]
                ?: DEFAULT_ICON_SIZE_DP
            val iconShape = FvIconShape.fromName(prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_ICON_SHAPE])
            val baseRadiusDp = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_BASE_RADIUS_DP]
                ?: DEFAULT_BASE_RADIUS_DP
            val layerGapDp = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LAYER_GAP_DP]
                ?: DEFAULT_LAYER_GAP_DP
            val endMarginDeg = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_END_MARGIN_DEG]
                ?: DEFAULT_END_MARGIN_DEG
            val slots = FvAppSwitcherSlotCodec.decodeAll(
                prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_SLOTS] ?: emptySet(),
            )
            return FvAppSwitcherSettings(
                circleCount = circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT),
                iconSizeDp = iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP),
                iconShape = iconShape,
                baseRadiusDp = baseRadiusDp.coerceIn(MIN_BASE_RADIUS_DP, MAX_BASE_RADIUS_DP),
                layerGapDp = layerGapDp.coerceIn(MIN_LAYER_GAP_DP, MAX_LAYER_GAP_DP),
                endMarginDeg = endMarginDeg.coerceIn(MIN_END_MARGIN_DEG, MAX_END_MARGIN_DEG),
                slots = slots,
            )
        }
    }
}

object FvAppSwitcherSlotCodec {
    private const val INDEX_SEP = "\u001D"

    fun encode(index: Int, item: QuickLauncherItem): String =
        "$index$INDEX_SEP${QuickLauncherItemCodec.encode(item)}"

    fun decode(raw: String): Pair<Int, QuickLauncherItem>? {
        val sep = raw.indexOf(INDEX_SEP)
        if (sep <= 0) return null
        val index = raw.substring(0, sep).toIntOrNull() ?: return null
        val item = QuickLauncherItemCodec.decode(raw.substring(sep + 1)) ?: return null
        return index to item
    }

    fun encodeAll(slots: Map<Int, QuickLauncherItem>): Set<String> =
        slots.filterValues { it.payload.isNotBlank() }
            .map { (index, item) -> encode(index, item) }
            .toSet()

    fun decodeAll(raw: Set<String>): Map<Int, QuickLauncherItem> =
        raw.mapNotNull { decode(it) }
            .filter { (_, item) -> item.payload.isNotBlank() }
            .toMap()
}
