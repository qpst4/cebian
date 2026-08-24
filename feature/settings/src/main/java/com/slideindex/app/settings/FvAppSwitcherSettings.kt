package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.layout.FvAppSwitcherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvIconShape

enum class FvAppSwitcherAxis {
    VERTICAL,
    HORIZONTAL,
}

enum class FvAppSwitcherAxisMergeDirection {
    /** 采用另一轴已有配置，同步到两轴。 */
    USE_OTHER_AXIS,
    /** 用当前触发轴的配置覆盖另一轴，并同步到两轴。 */
    USE_CURRENT_AXIS,
}

data class FvAppSwitcherLinkFlags(
    val linkAppearanceAxes: Boolean = DEFAULT_LINK_APPEARANCE_AXES,
    val linkSlotAxes: Boolean = DEFAULT_LINK_SLOT_AXES,
) {
    companion object {
        const val DEFAULT_LINK_APPEARANCE_AXES = false
        const val DEFAULT_LINK_SLOT_AXES = true
    }
}

fun FvAppSwitcherAxis.other(): FvAppSwitcherAxis = when (this) {
    FvAppSwitcherAxis.VERTICAL -> FvAppSwitcherAxis.HORIZONTAL
    FvAppSwitcherAxis.HORIZONTAL -> FvAppSwitcherAxis.VERTICAL
}

fun FvAppSwitcherSide.toAxis(): FvAppSwitcherAxis = when (this) {
    FvAppSwitcherSide.TOP, FvAppSwitcherSide.BOTTOM -> FvAppSwitcherAxis.VERTICAL
    FvAppSwitcherSide.LEFT, FvAppSwitcherSide.RIGHT -> FvAppSwitcherAxis.HORIZONTAL
}

fun LauncherSettings.fvAppSwitcherFor(side: FvAppSwitcherSide): FvAppSwitcherSettings =
    fvAppSwitcherFor(side.toAxis())

fun LauncherSettings.fvAppSwitcherFor(axis: FvAppSwitcherAxis): FvAppSwitcherSettings {
    val vertical = fvAppSwitcherVertical
    val horizontal = fvAppSwitcherHorizontal
    val own = when (axis) {
        FvAppSwitcherAxis.VERTICAL -> vertical
        FvAppSwitcherAxis.HORIZONTAL -> horizontal
    }
    if (!fvAppSwitcherLinkAppearanceAxes && !fvAppSwitcherLinkSlotAxes) {
        return own
    }
    val appearanceSource = if (fvAppSwitcherLinkAppearanceAxes) vertical else own
    val slotSource = if (fvAppSwitcherLinkSlotAxes) vertical else own
    return own.withAppearanceFrom(appearanceSource).withSlotsFrom(slotSource)
}

fun AppSettings.fvAppSwitcherFor(side: FvAppSwitcherSide): FvAppSwitcherSettings =
    launcher.fvAppSwitcherFor(side)

fun AppSettings.fvAppSwitcherFor(axis: FvAppSwitcherAxis): FvAppSwitcherSettings =
    launcher.fvAppSwitcherFor(axis)

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

    fun withAppearanceFrom(source: FvAppSwitcherSettings): FvAppSwitcherSettings = copy(
        circleCount = source.circleCount,
        iconSizeDp = source.iconSizeDp,
        iconShape = source.iconShape,
        baseRadiusDp = source.baseRadiusDp,
        layerGapDp = source.layerGapDp,
        endMarginDeg = source.endMarginDeg,
    )

    fun withSlotsFrom(source: FvAppSwitcherSettings): FvAppSwitcherSettings =
        copy(slots = source.slots)

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

        /** 旧版单套配置读取；等价于垂直轴。 */
        fun fromPreferences(prefs: Preferences): FvAppSwitcherSettings =
            fromPreferences(prefs, FvAppSwitcherAxis.VERTICAL)

        fun fromPreferences(prefs: Preferences, axis: FvAppSwitcherAxis): FvAppSwitcherSettings {
            val vertical = readAxis(prefs, FvAppSwitcherAxis.VERTICAL)
            if (axis == FvAppSwitcherAxis.VERTICAL) {
                return vertical
            }
            if (!hasHorizontalOverrides(prefs)) {
                return vertical
            }
            return readAxis(prefs, FvAppSwitcherAxis.HORIZONTAL)
        }

        fun linkFlagsFromPreferences(prefs: Preferences): FvAppSwitcherLinkFlags {
            val appearance = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_APPEARANCE_AXES]
            val slots = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_SLOT_AXES]
            if (appearance != null || slots != null) {
                return FvAppSwitcherLinkFlags(
                    linkAppearanceAxes = appearance ?: FvAppSwitcherLinkFlags.DEFAULT_LINK_APPEARANCE_AXES,
                    linkSlotAxes = slots ?: FvAppSwitcherLinkFlags.DEFAULT_LINK_SLOT_AXES,
                )
            }
            val legacyLinked = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_AXES] ?: true
            return if (legacyLinked) {
                FvAppSwitcherLinkFlags(linkAppearanceAxes = true, linkSlotAxes = true)
            } else {
                FvAppSwitcherLinkFlags(
                    linkAppearanceAxes = false,
                    linkSlotAxes = true,
                )
            }
        }

        internal fun writeAppearanceAxis(
            prefs: MutablePreferences,
            axis: FvAppSwitcherAxis,
            appearance: FvAppSwitcherSettings,
        ) {
            val current = readAxis(prefs, axis)
            writeAxis(prefs, axis, current.withAppearanceFrom(appearance))
        }

        internal fun writeSlotsAxis(
            prefs: MutablePreferences,
            axis: FvAppSwitcherAxis,
            slotsSource: FvAppSwitcherSettings,
        ) {
            val current = readAxis(prefs, axis)
            writeAxis(prefs, axis, current.withSlotsFrom(slotsSource))
        }

        internal fun writeAxis(
            prefs: MutablePreferences,
            axis: FvAppSwitcherAxis,
            settings: FvAppSwitcherSettings,
        ) {
            val keys = keysFor(axis)
            prefs[keys.circleCount] =
                settings.circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT)
            prefs[keys.iconSizeDp] =
                settings.iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)
            prefs[keys.iconShape] = settings.iconShape.name
            prefs[keys.baseRadiusDp] =
                settings.baseRadiusDp.coerceIn(MIN_BASE_RADIUS_DP, MAX_BASE_RADIUS_DP)
            prefs[keys.layerGapDp] =
                settings.layerGapDp.coerceIn(MIN_LAYER_GAP_DP, MAX_LAYER_GAP_DP)
            prefs[keys.endMarginDeg] =
                settings.endMarginDeg.coerceIn(MIN_END_MARGIN_DEG, MAX_END_MARGIN_DEG)
            prefs[keys.slots] = FvAppSwitcherSlotCodec.encodeAll(settings.slots)
        }

        private fun readAxis(prefs: Preferences, axis: FvAppSwitcherAxis): FvAppSwitcherSettings {
            val keys = keysFor(axis)
            val circleCount = prefs[keys.circleCount] ?: DEFAULT_CIRCLE_COUNT
            val iconSizeDp = prefs[keys.iconSizeDp] ?: DEFAULT_ICON_SIZE_DP
            val iconShape = FvIconShape.fromName(prefs[keys.iconShape])
            val baseRadiusDp = prefs[keys.baseRadiusDp] ?: DEFAULT_BASE_RADIUS_DP
            val layerGapDp = prefs[keys.layerGapDp] ?: DEFAULT_LAYER_GAP_DP
            val endMarginDeg = prefs[keys.endMarginDeg] ?: DEFAULT_END_MARGIN_DEG
            val slots = FvAppSwitcherSlotCodec.decodeAll(prefs[keys.slots] ?: emptySet())
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

        private fun hasHorizontalOverrides(prefs: Preferences): Boolean {
            if (prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_CIRCLE_COUNT] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_ICON_SIZE_DP] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_ICON_SHAPE] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_BASE_RADIUS_DP] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_LAYER_GAP_DP] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_END_MARGIN_DEG] != null) return true
            return prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_SLOTS]?.isNotEmpty() == true
        }

        private data class AxisKeys(
            val circleCount: Preferences.Key<Int>,
            val iconSizeDp: Preferences.Key<Float>,
            val iconShape: Preferences.Key<String>,
            val baseRadiusDp: Preferences.Key<Float>,
            val layerGapDp: Preferences.Key<Float>,
            val endMarginDeg: Preferences.Key<Float>,
            val slots: Preferences.Key<Set<String>>,
        )

        private fun keysFor(axis: FvAppSwitcherAxis): AxisKeys = when (axis) {
            FvAppSwitcherAxis.VERTICAL -> AxisKeys(
                circleCount = SettingsPreferenceKeys.FV_APP_SWITCHER_CIRCLE_COUNT,
                iconSizeDp = SettingsPreferenceKeys.FV_APP_SWITCHER_ICON_SIZE_DP,
                iconShape = SettingsPreferenceKeys.FV_APP_SWITCHER_ICON_SHAPE,
                baseRadiusDp = SettingsPreferenceKeys.FV_APP_SWITCHER_BASE_RADIUS_DP,
                layerGapDp = SettingsPreferenceKeys.FV_APP_SWITCHER_LAYER_GAP_DP,
                endMarginDeg = SettingsPreferenceKeys.FV_APP_SWITCHER_END_MARGIN_DEG,
                slots = SettingsPreferenceKeys.FV_APP_SWITCHER_SLOTS,
            )
            FvAppSwitcherAxis.HORIZONTAL -> AxisKeys(
                circleCount = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_CIRCLE_COUNT,
                iconSizeDp = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_ICON_SIZE_DP,
                iconShape = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_ICON_SHAPE,
                baseRadiusDp = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_BASE_RADIUS_DP,
                layerGapDp = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_LAYER_GAP_DP,
                endMarginDeg = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_END_MARGIN_DEG,
                slots = SettingsPreferenceKeys.FV_APP_SWITCHER_HORIZONTAL_SLOTS,
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
