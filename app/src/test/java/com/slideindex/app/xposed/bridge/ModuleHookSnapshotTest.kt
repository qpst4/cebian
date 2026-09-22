package com.slideindex.app.xposed.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ModuleHookSnapshotTest {
  @Test
  fun jsonRoundTrip_preservesGroupsAndGeometry() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_SIDES or ModuleHookBridgeContract.GROUP_BOTTOM,
      interceptSystemBackGesture = true,
      navigationMode = 2,
      density = 2.75f,
      sides = listOf(
        ModuleHookSide(
          sideId = ModuleHookBridgeContract.SIDE_BOTTOM,
          widthDp = 20f,
          handles = listOf(ModuleHookHandle(topFraction = 0.1f, heightFraction = 0.4f, widthDp = 24f)),
        ),
      ),
      updatedAtMs = 1234L,
    )

    val parsed = ModuleHookSnapshot.parse(snapshot.toJson())!!

    assertEquals(snapshot.takeoverGroups, parsed.takeoverGroups)
    assertTrue(parsed.hasGroup(ModuleHookBridgeContract.GROUP_SIDES))
    assertTrue(parsed.hasGroup(ModuleHookBridgeContract.GROUP_BOTTOM))
    assertFalse(parsed.hasGroup(ModuleHookBridgeContract.GROUP_TOP))
    assertTrue(parsed.interceptSystemBackGesture)
    assertEquals(2, parsed.navigationMode)
    assertEquals(2.75f, parsed.density, 0.001f)
    assertEquals(1, parsed.sides.size)
    assertEquals(0.1f, parsed.sides[0].handles[0].topFraction, 0.001f)
    assertEquals(0.5f, parsed.sides[0].handles[0].bottomFraction, 0.001f)
  }

  @Test
  fun parse_rejectsGarbageAndBlank() {
    assertNull(ModuleHookSnapshot.parse(null))
    assertNull(ModuleHookSnapshot.parse(""))
    assertNull(ModuleHookSnapshot.parse("{not json"))
  }

  @Test
  fun parse_defaultsToDisabledWhenKeysMissing() {
    val parsed = ModuleHookSnapshot.parse("{}")!!
    assertEquals(0, parsed.takeoverGroups)
    assertTrue(parsed.sides.isEmpty())
    assertEquals(1f, parsed.density, 0.001f)
  }
}