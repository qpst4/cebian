package com.slideindex.app.overlay.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSwitcherLayoutEngineTest {
  private val density = 3f
  private val screenW = 1080f
  private val screenH = 2400f

  @Test
  fun leftSide_firstLayerStaysNearEdge() {
    val layout = AppSwitcherLayoutEngine.layout(
      itemCount = 6,
      side = AppSwitcherSide.LEFT,
      anchorRawY = screenH * 0.45f,
      screenWidth = screenW,
      screenHeight = screenH,
      itemSizeDp = 40f,
      spacingDp = 6f,
      density = density,
    )
    val innermost = layout.slots.minByOrNull { it.centerX } ?: return
    assertTrue(innermost.centerX < screenW * 0.25f)
    assertEquals(6, layout.slots.size)
  }

  @Test
  fun rightSide_mirrorsLeft() {
    val left = AppSwitcherLayoutEngine.layout(
      itemCount = 8,
      side = AppSwitcherSide.LEFT,
      anchorRawY = 1000f,
      screenWidth = screenW,
      screenHeight = screenH,
      itemSizeDp = 36f,
      spacingDp = 6f,
      density = density,
    )
    val right = AppSwitcherLayoutEngine.layout(
      itemCount = 8,
      side = AppSwitcherSide.RIGHT,
      anchorRawY = 1000f,
      screenWidth = screenW,
      screenHeight = screenH,
      itemSizeDp = 36f,
      spacingDp = 6f,
      density = density,
    )
    assertEquals(left.slots.size, right.slots.size)
    left.slots.zip(right.slots).forEach { (l, r) ->
      assertEquals(l.centerY, r.centerY, 0.5f)
      assertEquals(screenW - l.centerX, r.centerX, 2f)
    }
  }

  @Test
  fun slotIndexAt_picksNearest() {
    val layout = AppSwitcherLayoutEngine.layout(
      itemCount = 5,
      side = AppSwitcherSide.LEFT,
      anchorRawY = 900f,
      screenWidth = screenW,
      screenHeight = screenH,
      itemSizeDp = 40f,
      spacingDp = 6f,
      density = density,
    )
    val target = layout.slots[2]
    val hit = AppSwitcherLayoutEngine.slotIndexAt(layout, target.centerX, target.centerY)
    assertEquals(2, hit)
  }
}
