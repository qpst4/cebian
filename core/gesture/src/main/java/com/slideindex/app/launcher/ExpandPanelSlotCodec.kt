package com.slideindex.app.launcher

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker

object ExpandPanelSlotCodec {
    const val SLOT_COUNT = 8
    private const val SLOT_SEP = ","

    fun encode(slots: List<GestureAction?>): String =
        List(SLOT_COUNT) { index -> slots.getOrNull(index) }
            .joinToString(SLOT_SEP) { action ->
                when {
                    action == null || action is GestureAction.None -> ""
                    else -> QuickLauncherItemCodec.encodeActionPayload(sanitizeAction(action))
                }
            }

    fun decode(raw: String?): List<GestureAction?> {
        val parts = raw.orEmpty().split(SLOT_SEP)
        return List(SLOT_COUNT) { index ->
            val part = parts.getOrNull(index).orEmpty()
            when {
                part.isBlank() -> null
                else -> QuickLauncherItemCodec.parseActionPayload(part)?.let(::sanitizeAction)
                    ?: GestureAction.LaunchApp(part)
            }
        }
    }

    private fun sanitizeAction(action: GestureAction): GestureAction =
        action.sanitizeForSlotPicker(SlotPickerKind.OverlayTap)
}
