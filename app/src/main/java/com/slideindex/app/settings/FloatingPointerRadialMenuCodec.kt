package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.gesture.PointerSwipeDirection
import com.slideindex.app.launcher.QuickLauncherItemCodec

object FloatingPointerRadialMenuCodec {
    const val SLOT_COUNT = 8
    private const val SEP = "\u001D"

    fun defaultSlots(): List<GestureAction> = listOf(
        GestureAction.SimulatePointerSwipe(PointerSwipeConfig(direction = PointerSwipeDirection.UP)),
        GestureAction.Screenshot,
        GestureAction.SimulatePointerSwipe(PointerSwipeConfig(direction = PointerSwipeDirection.RIGHT)),
        GestureAction.OpenQuickSettings,
        GestureAction.SimulatePointerSwipe(PointerSwipeConfig(direction = PointerSwipeDirection.DOWN)),
        GestureAction.None,
        GestureAction.SimulatePointerSwipe(PointerSwipeConfig(direction = PointerSwipeDirection.LEFT)),
        GestureAction.QuickLauncher,
    )

    fun decode(encoded: Set<String>): List<GestureAction> {
        if (encoded.isEmpty()) return defaultSlots()
        val byIndex = encoded.mapNotNull { entry ->
            val sep = entry.indexOf(SEP)
            if (sep <= 0) return@mapNotNull null
            val index = entry.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val payload = entry.substring(sep + 1)
            val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return@mapNotNull null
            index to sanitizeSlotAction(action)
        }.toMap()
        return List(SLOT_COUNT) { index ->
            byIndex[index] ?: defaultSlots().getOrElse(index) { GestureAction.None }
        }
    }

    fun encode(slots: List<GestureAction>): Set<String> =
        slots.take(SLOT_COUNT).mapIndexed { index, action ->
            "$index$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeSlotAction(action))}"
        }.toSet()

    private fun sanitizeSlotAction(action: GestureAction): GestureAction =
        if (action is GestureAction.FloatingPointer) GestureAction.None else action
}
