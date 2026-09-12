package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionSlotPickerTest {

    @Test
    fun overlayTap_excludesContinuousOnlyAndPointerMeta() {
        assertFalse(GestureAction.RegionalScreenshotPick.isEligibleForSlotPicker(SlotPickerKind.OverlayTap))
        assertFalse(GestureAction.FloatingPointer.isEligibleForSlotPicker(SlotPickerKind.OverlayTap))
        assertFalse(GestureAction.OpenFloatingPointerRadialMenu.isEligibleForSlotPicker(SlotPickerKind.OverlayTap))
        assertFalse(GestureAction.FingertipRing.isEligibleForSlotPicker(SlotPickerKind.OverlayTap))
        assertFalse(GestureAction.CornerInnerPinWheel.isEligibleForSlotPicker(SlotPickerKind.OverlayTap))
        assertTrue(GestureAction.Screenshot.isEligibleForSlotPicker(SlotPickerKind.OverlayTap))
    }

    @Test
    fun floatingPointerRadialLongPress_allowsOpenRadialMenu() {
        assertTrue(
            GestureAction.OpenFloatingPointerRadialMenu.isEligibleForSlotPicker(
                SlotPickerKind.FloatingPointerRadialLongPress,
            ),
        )
        assertFalse(
            GestureAction.RegionalScreenshotPick.isEligibleForSlotPicker(
                SlotPickerKind.FloatingPointerRadialLongPress,
            ),
        )
    }

    @Test
    fun floatingPointerRadialSlot_excludesOpenRadialMenu() {
        assertFalse(
            GestureAction.OpenFloatingPointerRadialMenu.isEligibleForSlotPicker(
                SlotPickerKind.FloatingPointerRadialSlot,
            ),
        )
        assertTrue(
            GestureAction.PointerGestureRecorder.isEligibleForSlotPicker(
                SlotPickerKind.FloatingPointerRadialSlot,
            ),
        )
    }

    @Test
    fun sanitizeForSlotPicker_mapsInvalidToNoneOrInnerCancel() {
        assertTrue(GestureAction.RegionalScreenshotPick.sanitizeForSlotPicker(SlotPickerKind.CornerWheel) is GestureAction.None)
        assertTrue(
            GestureAction.FloatingPointer.sanitizeForSlotPicker(SlotPickerKind.CornerInnerZone)
                is GestureAction.CornerInnerCancel,
        )
    }

    @Test
    fun actionPickerCatalogPolicy_edgeGesture_hasNoSlotKind() {
        assertEquals(null, ActionPickerCatalogPolicy.EdgeGesture.slotPickerKindOrNull())
    }

    @Test
    fun actionPickerCatalogPolicy_slot_exposesKind() {
        assertEquals(
            SlotPickerKind.CornerWheel,
            ActionPickerCatalogPolicy.Slot(SlotPickerKind.CornerWheel).slotPickerKindOrNull(),
        )
    }

    @Test
    fun fingertipRing_forbiddenInAllSlotPickerKinds() {
        for (kind in SlotPickerKind.entries) {
            assertFalse(GestureAction.FingertipRing.isEligibleForSlotPicker(kind))
        }
    }

    @Test
    fun cornerInnerZone_excludesFingertipRingOnSanitize() {
        assertTrue(
            GestureAction.FingertipRing.sanitizeForSlotPicker(SlotPickerKind.CornerInnerZone)
                is GestureAction.CornerInnerCancel,
        )
    }
}
