package com.slideindex.app.xposed.takeover

import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleHookExtraRect
import com.slideindex.app.xposed.bridge.ModuleHookHandle
import com.slideindex.app.xposed.bridge.ModuleHookSide
import com.slideindex.app.xposed.bridge.ModuleHookSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverGeometryTest {
  @Test
  fun bottomGroup_producesStripAtScreenBottom() {
    val rects = TakeoverGeometry.rectsForGroups(
      snapshot = snapshot(groups = ModuleHookBridgeContract.GROUP_BOTTOM, navigationMode = 2),
      screenWidthPx = 1000,
      screenHeightPx = 2000,
    )

    assertEquals(1, rects.size)
    val rect = rects.first()
    assertEquals(ModuleHookBridgeContract.SIDE_BOTTOM, rect.sideId)
    assertEquals(0f, rect.left, 0.001f)
    assertEquals(500f, rect.right, 0.001f)
    assertEquals(1960f, rect.top, 0.001f)
    assertEquals(2000f, rect.bottom, 0.001f)
  }

  @Test
  fun bottomGroup_isIgnoredUnderThreeButtonNavigation() {
    val rects = TakeoverGeometry.rectsForGroups(
      snapshot = snapshot(groups = ModuleHookBridgeContract.GROUP_BOTTOM, navigationMode = 0),
      screenWidthPx = 1000,
      screenHeightPx = 2000,
    )

    assertTrue(rects.isEmpty())
  }

  @Test
  fun sidesGroup_usesTriggerStripWidthNotWidenedInterceptWidth() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_SIDES,
      density = 2f,
      sides = listOf(
        ModuleHookSide(
          sideId = ModuleHookBridgeContract.SIDE_LEFT,
          widthDp = 30f,
          handles = listOf(
            ModuleHookHandle(topFraction = 0.25f, heightFraction = 0.5f, widthDp = 20f),
          ),
        ),
      ),
    )

    val rects = TakeoverGeometry.rectsForGroups(snapshot, 1000, 2000)

    assertEquals(1, rects.size)
    val rect = rects.first()
    // 只接管触钮条本身（20dp * 2），不再复用 200/320dp 的「拦截窗口宽度」。
    assertEquals(40f, rect.right, 0.001f)
    assertEquals(500f, rect.top, 0.001f)
    assertEquals(1500f, rect.bottom, 0.001f)
  }

  @Test
  fun topGroup_usesHorizontalSpanAndHandleThickness() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_TOP,
      density = 2f,
      sides = listOf(
        ModuleHookSide(
          sideId = ModuleHookBridgeContract.SIDE_TOP,
          widthDp = 40f,
          handles = listOf(
            ModuleHookHandle(topFraction = 0.2f, heightFraction = 0.3f, widthDp = 18f),
          ),
        ),
      ),
    )

    val rects = TakeoverGeometry.rectsForGroups(snapshot, 1000, 2000)

    assertEquals(1, rects.size)
    val rect = rects.first()
    assertEquals(200f, rect.left, 0.001f)
    assertEquals(500f, rect.right, 0.001f)
    assertEquals(0f, rect.top, 0.001f)
    assertEquals(36f, rect.bottom, 0.001f)
  }

  @Test
  fun disabledGroups_produceNoRects() {
    val rects = TakeoverGeometry.rectsForGroups(
      snapshot = snapshot(groups = 0, navigationMode = 2),
      screenWidthPx = 1000,
      screenHeightPx = 2000,
    )

    assertTrue(rects.isEmpty())
  }

  @Test
  fun cornerExtraRect_isMergedAndWinsOverTriggerRect() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_BOTTOM,
      navigationMode = ModuleHookBridgeContract.NAVIGATION_MODE_GESTURAL,
      density = 2f,
      sides = listOf(
        ModuleHookSide(
          sideId = ModuleHookBridgeContract.SIDE_BOTTOM,
          widthDp = 20f,
          handles = listOf(
            ModuleHookHandle(topFraction = 0f, heightFraction = 0.5f, widthDp = 20f),
          ),
        ),
      ),
      extraRects = listOf(cornerLeftExtraRect()),
    )

    val rects = TakeoverGeometry.rectsForGroups(snapshot, 1000, 2000)

    // 扩展矩形排在触钮矩形之前：重叠处优先归角轮盘。
    assertEquals(2, rects.size)
    val corner = rects.first()
    assertEquals(ModuleHookBridgeContract.TARGET_CORNER_LEFT, corner.sideId)
    assertEquals(0f, corner.left, 0.001f)
    assertEquals(100f, corner.right, 0.001f)
    assertEquals(1800f, corner.top, 0.001f)
    assertEquals(2000f, corner.bottom, 0.001f)
    assertEquals(ModuleHookBridgeContract.SIDE_BOTTOM, rects[1].sideId)
  }

  @Test
  fun cornerExtraRect_staysEffectiveUnderThreeButtonNavigation() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_BOTTOM,
      navigationMode = 0,
      density = 2f,
      extraRects = listOf(cornerLeftExtraRect()),
    )

    val rects = TakeoverGeometry.rectsForGroups(snapshot, 1000, 2000)

    // 底部触钮组在三键导航下停用，但角轮盘豁免（它在底部两角，不压导航键）。
    assertEquals(1, rects.size)
    assertEquals(ModuleHookBridgeContract.TARGET_CORNER_LEFT, rects.first().sideId)
  }

  @Test
  fun extraRect_isDroppedWhenItsGroupIsOff() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_SIDES,
      navigationMode = ModuleHookBridgeContract.NAVIGATION_MODE_GESTURAL,
      density = 2f,
      extraRects = listOf(cornerLeftExtraRect()),
    )

    assertTrue(TakeoverGeometry.rectsForGroups(snapshot, 1000, 2000).isEmpty())
  }

  @Test
  fun extraRect_isDroppedWhenNoGroupIsEnabled() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = 0,
      navigationMode = ModuleHookBridgeContract.NAVIGATION_MODE_GESTURAL,
      density = 2f,
      extraRects = listOf(cornerLeftExtraRect()),
    )

    assertTrue(TakeoverGeometry.rectsForGroups(snapshot, 1000, 2000).isEmpty())
  }

  private fun cornerLeftExtraRect(): ModuleHookExtraRect = ModuleHookExtraRect(
    target = ModuleHookBridgeContract.TARGET_CORNER_LEFT,
    groups = ModuleHookBridgeContract.GROUP_BOTTOM,
    leftFraction = 0f,
    topFraction = 0.9f,
    rightFraction = 0.1f,
    bottomFraction = 1f,
    strip = ModuleHookBridgeContract.CORNER_STRIP_VERTICAL,
  )

  private fun snapshot(groups: Int, navigationMode: Int): ModuleHookSnapshot =
    ModuleHookSnapshot(
      takeoverGroups = groups,
      navigationMode = navigationMode,
      density = 2f,
      sides = listOf(
        ModuleHookSide(
          sideId = ModuleHookBridgeContract.SIDE_BOTTOM,
          widthDp = 20f,
          handles = listOf(
            ModuleHookHandle(topFraction = 0f, heightFraction = 0.5f, widthDp = 20f),
          ),
        ),
      ),
    )
}
