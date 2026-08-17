package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import kotlin.math.min

/** FV CircleAppContainer 风格圆环启动器设置（仅圈数 + 槽位，配置在悬浮窗内完成）。 */
data class FvAppSwitcherSettings(
    val circleCount: Int = DEFAULT_CIRCLE_COUNT,
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

        fun slotCountForCircleCount(circleCount: Int): Int = when (circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT)) {
            1 -> 5
            2 -> 13
            3 -> 24
            else -> MAX_SLOTS
        }

        fun fromPreferences(prefs: Preferences): FvAppSwitcherSettings {
            val circleCount = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_CIRCLE_COUNT]
                ?: DEFAULT_CIRCLE_COUNT
            val slots = FvAppSwitcherSlotCodec.decodeAll(
                prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_SLOTS] ?: emptySet(),
            )
            return FvAppSwitcherSettings(
                circleCount = circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT),
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
