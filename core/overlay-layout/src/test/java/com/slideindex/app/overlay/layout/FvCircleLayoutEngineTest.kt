package com.slideindex.app.overlay.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FvCircleLayoutEngineTest {
  private val density = 3f
  private val screenWidth = 1080f
  private val anchorX = 0f
  private val anchorY = 600f

  @Test
  fun slotCount_matchesFvCircleDefaults() {
    assertEquals(5, FvCircleLayoutEngine.slotCountForCircleCount(1))
    assertEquals(13, FvCircleLayoutEngine.slotCountForCircleCount(2))
    assertEquals(24, FvCircleLayoutEngine.slotCountForCircleCount(3))
    assertEquals(38, FvCircleLayoutEngine.slotCountForCircleCount(4))
  }

  @Test
  fun layout_twoCircles_hasThirteenSlots_openingRightFromLeftEdge() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = anchorX,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    assertEquals(13, layout.slots.size)
    assertTrue(layout.slots.all { it.centerX >= layout.anchorX })
    assertTrue(layout.slots.any { it.centerY < layout.anchorY })
    assertTrue(layout.slots.any { it.centerY > layout.anchorY })
  }

  @Test
  fun layout_rightSide_mirrorsX() {
    val left = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 40f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    val right = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.RIGHT,
      anchorX = screenWidth - 40f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    assertEquals(left.slots.size, right.slots.size)
    left.slots.zip(right.slots).forEach { (l, r) ->
      val leftOffset = l.centerX - left.anchorX
      val rightOffset = right.anchorX - r.centerX
      assertEquals(leftOffset, rightOffset, 0.5f)
      assertEquals(l.centerY, r.centerY, 0.5f)
    }
  }

  @Test
  fun slotIndexAt_hitsConfiguredSlotCenter() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 36f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    val target = layout.slots[3]
    val hit = FvCircleLayoutEngine.slotIndexAt(layout, target.centerX, target.centerY)
    assertEquals(3, hit)
  }

  @Test
  fun layout_rightSide_toolbarOnLeftScreenEdge() {
    val anchorX = screenWidth - 60f
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.RIGHT,
      anchorX = anchorX,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    val expectedX = 15f * density + 18f * density
    assertEquals(expectedX, layout.toolbarCenterX, 1f)
    assertTrue(layout.toolbarCenterX < layout.slots.minOf { it.centerX })
  }

  @Test
  fun layout_layerRadius_scalesWithDensity() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 60f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    val outerSlot = layout.slots[12]
    val dx = outerSlot.centerX - layout.anchorX
    val dy = outerSlot.centerY - layout.anchorY
    val dist = sqrt(dx * dx + dy * dy)
    assertEquals(138f * density, dist, 2f)
  }

  @Test
  fun slotIndexAt_hitsWithinEnlargedTouchTarget() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 36f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    val target = layout.slots[3]
    // 20dp offset from center should still be within hit radius (~25dp)
    val offsetPx = 20f * density
    val hit = FvCircleLayoutEngine.slotIndexAt(layout, target.centerX + offsetPx * 0.7f, target.centerY + offsetPx * 0.7f)
    assertEquals(3, hit)
  }

  @Test
  fun layout_leftSide_toolbarOnRightScreenEdge() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 60f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    val expectedX = screenWidth - 15f * density - 18f * density
    assertEquals(expectedX, layout.toolbarCenterX, 1f)
  }

  @Test
  fun slotIndexAt_innerDeadzone_returnsNoSlot() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 0f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    // 35dp away from anchor in the blank area inside the 1st ring (radius = 88dp)
    val blankAreaX = 35f * density
    val hit = FvCircleLayoutEngine.slotIndexAt(layout, blankAreaX, anchorY)
    assertEquals(-1, hit)
  }

  @Test
  fun slotIndexAt_interRingGap_returnsNoSlot() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 4,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 0f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    // Visual gap between ring 0 (outer 106dp) and ring 1 (inner 120dp)
    listOf(107f, 113f, 119f).forEach { gapDp ->
      val hit = FvCircleLayoutEngine.slotIndexAt(layout, gapDp * density, anchorY)
      assertEquals("Touch at ${gapDp}dp between ring 0 and ring 1 should be in dead zone", -1, hit)
    }

    listOf(157f, 163f, 169f).forEach { gapDp ->
      val hit = FvCircleLayoutEngine.slotIndexAt(layout, gapDp * density, anchorY)
      assertEquals("Touch at ${gapDp}dp between ring 1 and ring 2 should be in dead zone", -1, hit)
    }

    listOf(207f, 213f, 219f).forEach { gapDp ->
      val hit = FvCircleLayoutEngine.slotIndexAt(layout, gapDp * density, anchorY)
      assertEquals("Touch at ${gapDp}dp between ring 2 and ring 3 should be in dead zone", -1, hit)
    }
  }

  @Test
  fun slotIndexAt_beyondOuterRing_returnsNoSlot() {
    val layout = FvCircleLayoutEngine.layout(
      circleCount = 4,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 0f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
    )
    // Outer icon edge is at 256dp; blank area to the right of icons should not select slots.
    listOf(260f, 270f, 285f).forEach { beyondDp ->
      val hit = FvCircleLayoutEngine.slotIndexAt(layout, beyondDp * density, anchorY)
      assertEquals("Touch at ${beyondDp}dp beyond outer ring should not select", -1, hit)
    }
  }

  @Test
  fun layout_customParameters_adjustsItemSizeAndRadiiCorrectly() {
    val customLayout = FvCircleLayoutEngine.layout(
      circleCount = 2,
      side = FvAppSwitcherSide.LEFT,
      anchorX = 0f,
      anchorY = anchorY,
      screenWidth = screenWidth,
      density = density,
      iconSizeDp = 48f,
      iconShape = FvIconShape.CIRCLE,
      baseRadiusDp = 100f,
      layerGapDp = 60f,
      endMarginDeg = 10f,
    )
    assertEquals(13, customLayout.slots.size)
    assertEquals(48f * density, customLayout.itemSizePx, 0.01f)
    assertEquals(0.5f, customLayout.cornerRadiusRatio, 0.01f)
    val slot2 = customLayout.slots[2]
    val dist = kotlin.math.hypot(slot2.centerX - 0f, slot2.centerY - anchorY)
    assertEquals(100f * density, dist, 1f)
  }
}
