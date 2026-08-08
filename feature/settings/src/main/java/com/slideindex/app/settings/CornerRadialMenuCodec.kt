package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

object CornerRadialMenuCodec {
    /** 三层槽位：内 3 / 中 5 / 外 7，共 15。 */
    const val SLOT_COUNT = 15
    const val LEGACY_SLOT_COUNT = 8
    val LAYER_SLOT_COUNTS = intArrayOf(3, 5, 7)

    private const val SEP = "\u001D"

    fun layerOf(globalIndex: Int): Int = when {
        globalIndex < 3 -> 0
        globalIndex < 8 -> 1
        globalIndex < SLOT_COUNT -> 2
        else -> -1
    }

    fun layerLocalIndex(globalIndex: Int): Int = when {
        globalIndex < 3 -> globalIndex
        globalIndex < 8 -> globalIndex - 3
        globalIndex < SLOT_COUNT -> globalIndex - 8
        else -> -1
    }

    fun layerStartIndex(layer: Int): Int = when (layer) {
        0 -> 0
        1 -> 3
        2 -> 8
        else -> 0
    }

    fun slotCountInLayer(layer: Int): Int = LAYER_SLOT_COUNTS.getOrElse(layer) { 0 }

    fun defaultLeftSlots(): List<GestureAction> = listOf(
        // layer 1 — 3
        GestureAction.OpenIndex,
        GestureAction.QuickLauncher(),
        GestureAction.Screenshot,
        // layer 2 — 5
        GestureAction.FreeWindowCurrentApp,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.LaunchAssistant,
        // layer 3 — 7
        GestureAction.OpenQuickSettings,
        GestureAction.TaskSwitcher,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
    )

    fun defaultRightSlots(): List<GestureAction> = listOf(
        GestureAction.FreeWindowCurrentApp,
        GestureAction.QuickLauncher(),
        GestureAction.OpenIndex,
        GestureAction.Screenshot,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.LaunchAssistant,
        GestureAction.OpenQuickSettings,
        GestureAction.TaskSwitcher,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
    )

    fun decode(encoded: Set<String>, defaults: List<GestureAction>): List<GestureAction> {
        if (encoded.isEmpty()) return normalizeSlots(defaults)
        val byIndex = encoded.mapNotNull { entry ->
            val sep = entry.indexOf(SEP)
            if (sep <= 0) return@mapNotNull null
            val index = entry.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val payload = entry.substring(sep + 1)
            val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return@mapNotNull null
            index to sanitizeSlotAction(action)
        }.toMap()
        val base = normalizeSlots(defaults)
        return List(SLOT_COUNT) { index ->
            byIndex[index] ?: base.getOrElse(index) { GestureAction.None }
        }
    }

    fun encode(slots: List<GestureAction>): Set<String> =
        normalizeSlots(slots).mapIndexed { index, action ->
            "$index$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeSlotAction(action))}"
        }.toSet()

    fun normalizeSlots(slots: List<GestureAction>): List<GestureAction> =
        List(SLOT_COUNT) { index -> slots.getOrElse(index) { GestureAction.None } }

    fun layerTitleResLayer(layer: Int): Int = layer + 1

    private fun sanitizeSlotAction(action: GestureAction): GestureAction = when (action) {
        is GestureAction.FloatingPointer,
        is GestureAction.OpenFloatingPointerRadialMenu,
        is GestureAction.CornerInnerCancel,
        is GestureAction.CornerInnerPinWheel,
        -> GestureAction.None
        else -> action
    }
}
