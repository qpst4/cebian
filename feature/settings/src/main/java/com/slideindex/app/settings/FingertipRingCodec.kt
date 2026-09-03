package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

object FingertipRingCodec {
    const val MIN_SLOT_COUNT = 3
    const val MAX_SLOT_COUNT = 8
    const val DEFAULT_SLOT_COUNT = 6
    const val DEFAULT_ORBIT_RADIUS_PX = 320f
    const val MIN_ORBIT_RADIUS_PX = 80f
    const val MAX_ORBIT_RADIUS_PX = 550f
    const val DEFAULT_ICON_SIZE_PX = 140f
    const val MIN_ICON_SIZE_PX = 90f
    const val MAX_ICON_SIZE_PX = 360f
    private const val SEP = "\u001D"

    fun effectiveOrbitRadiusPx(value: Float): Float =
        value.coerceIn(MIN_ORBIT_RADIUS_PX, MAX_ORBIT_RADIUS_PX)

    fun effectiveIconSizePx(value: Float): Float =
        value.coerceIn(MIN_ICON_SIZE_PX, MAX_ICON_SIZE_PX)

    /** 图标圆形底半径，随图标大小等比缩放。 */
    fun iconBackgroundRadiusPx(iconSizePx: Float): Float = iconSizePx * 0.58f

    fun effectiveSlotCount(requested: Int): Int =
        requested.coerceIn(MIN_SLOT_COUNT, MAX_SLOT_COUNT)

    fun defaultSlots(): List<GestureAction> = listOf(
        GestureAction.Screenshot,
        GestureAction.OpenQuickSettings,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.QuickLauncher(),
        GestureAction.None,
        GestureAction.None,
    )

    fun activeSlots(settings: FingertipRingSettings): List<GestureAction> {
        val count = effectiveSlotCount(settings.slotCount)
        return List(count) { index ->
            settings.slotActions.getOrElse(index) { defaultSlots().getOrElse(index) { GestureAction.None } }
        }
    }

    fun decode(encoded: Set<String>, slotCount: Int): List<GestureAction> {
        val count = effectiveSlotCount(slotCount)
        if (encoded.isEmpty()) return defaultSlots().take(count)
        val byIndex = encoded.mapNotNull { entry ->
            val sep = entry.indexOf(SEP)
            if (sep <= 0) return@mapNotNull null
            val index = entry.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val payload = entry.substring(sep + 1)
            val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return@mapNotNull null
            index to sanitizeSlotAction(action)
        }.toMap()
        return List(count) { index ->
            byIndex[index] ?: defaultSlots().getOrElse(index) { GestureAction.None }
        }
    }

    fun encode(slots: List<GestureAction>, slotCount: Int): Set<String> {
        val count = effectiveSlotCount(slotCount)
        return slots.take(count).mapIndexed { index, action ->
            "$index$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeSlotAction(action))}"
        }.toSet()
    }

    private fun sanitizeSlotAction(action: GestureAction): GestureAction = when (action) {
        is GestureAction.FingertipRing,
        is GestureAction.FloatingPointer,
        is GestureAction.OpenFloatingPointerRadialMenu,
        -> GestureAction.None
        else -> action
    }
}
