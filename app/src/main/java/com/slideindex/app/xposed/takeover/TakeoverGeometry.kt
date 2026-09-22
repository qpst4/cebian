package com.slideindex.app.xposed.takeover

import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleHookSide
import com.slideindex.app.xposed.bridge.ModuleHookSnapshot

/** 屏幕坐标系下的接管矩形，附带所属边，便于回传给 app 路由。 */
data class TakeoverRect(
  val sideId: Int,
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val isEmpty: Boolean get() = right <= left || bottom <= top

  fun contains(x: Float, y: Float): Boolean =
    x >= left && x <= right && y >= top && y <= bottom
}

/**
 * 把 [ModuleHookSnapshot] 里的触钮几何换算成屏幕矩形。
 *
 * 规则与 app 侧 `GestureZoneLayout.interceptZoneRect` 对齐：
 * - LEFT/RIGHT：厚度用该边接管宽度（已含「拦截系统返回手势」加宽），纵向取各自触钮的比例区间；
 * - TOP/BOTTOM：厚度用各自触钮的边宽，横向取各自触钮的比例区间。
 */
object TakeoverGeometry {
  fun rectsForGroups(
    snapshot: ModuleHookSnapshot,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): List<TakeoverRect> {
    if (screenWidthPx <= 0 || screenHeightPx <= 0) return emptyList()
    return buildList {
      if (snapshot.hasGroup(ModuleHookBridgeContract.GROUP_TOP)) {
        snapshot.side(ModuleHookBridgeContract.SIDE_TOP)?.let { side ->
          addAll(rectsForSide(side, snapshot.density, screenWidthPx, screenHeightPx))
        }
      }
      if (snapshot.hasGroup(ModuleHookBridgeContract.GROUP_SIDES)) {
        snapshot.side(ModuleHookBridgeContract.SIDE_LEFT)?.let { side ->
          addAll(rectsForSide(side, snapshot.density, screenWidthPx, screenHeightPx))
        }
        snapshot.side(ModuleHookBridgeContract.SIDE_RIGHT)?.let { side ->
          addAll(rectsForSide(side, snapshot.density, screenWidthPx, screenHeightPx))
        }
      }
      if (snapshot.hasGroup(ModuleHookBridgeContract.GROUP_BOTTOM) &&
        isBottomGroupEffective(snapshot)
      ) {
        snapshot.side(ModuleHookBridgeContract.SIDE_BOTTOM)?.let { side ->
          addAll(rectsForSide(side, snapshot.density, screenWidthPx, screenHeightPx))
        }
      }
    }.filterNot { it.isEmpty }
  }

  /** 三键导航下底部是真实导航键区域，接管会吃掉返回/桌面键，因此自动停用。 */
  fun isBottomGroupEffective(snapshot: ModuleHookSnapshot): Boolean =
    snapshot.navigationMode == ModuleHookBridgeContract.NAVIGATION_MODE_GESTURAL

  /**
   * 点是否落在"两侧接管的触钮条"内。
   *
   * 返回手势由 SystemUI 的手势监听通道先行处理，输入层丢弃盖不住它，
   * 因此 SystemUI 侧需要按同一区域判断并放行（不在区域内才算系统返回手势）。
   */
  fun isInsideSidesRegion(
    snapshot: ModuleHookSnapshot,
    x: Float,
    y: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): Boolean {
    if (!snapshot.hasGroup(ModuleHookBridgeContract.GROUP_SIDES)) return false
    return rectsForGroups(snapshot, screenWidthPx, screenHeightPx).any { rect ->
      (rect.sideId == ModuleHookBridgeContract.SIDE_LEFT ||
        rect.sideId == ModuleHookBridgeContract.SIDE_RIGHT) && rect.contains(x, y)
    }
  }

  fun rectsForSide(
    side: ModuleHookSide,
    density: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): List<TakeoverRect> {
    if (side.handles.isEmpty()) return emptyList()
    val width = screenWidthPx.toFloat()
    val height = screenHeightPx.toFloat()
    val verticalEdge = side.sideId == ModuleHookBridgeContract.SIDE_LEFT ||
      side.sideId == ModuleHookBridgeContract.SIDE_RIGHT
    val spanLength = if (verticalEdge) height else width
    return side.handles.mapNotNull { handle ->
      val spanStart = handle.topFraction * spanLength
      val spanEnd = handle.bottomFraction * spanLength
      if (spanEnd <= spanStart) return@mapNotNull null
      val thickness = side.thicknessPx(handle, density)
      if (thickness <= 0f) return@mapNotNull null
      when (side.sideId) {
        ModuleHookBridgeContract.SIDE_LEFT -> TakeoverRect(
          sideId = side.sideId,
          left = 0f,
          top = spanStart,
          right = thickness.coerceAtMost(width),
          bottom = spanEnd,
        )
        ModuleHookBridgeContract.SIDE_RIGHT -> TakeoverRect(
          sideId = side.sideId,
          left = (width - thickness).coerceAtLeast(0f),
          top = spanStart,
          right = width,
          bottom = spanEnd,
        )
        ModuleHookBridgeContract.SIDE_TOP -> TakeoverRect(
          sideId = side.sideId,
          left = spanStart,
          top = 0f,
          right = spanEnd,
          bottom = thickness.coerceAtMost(height),
        )
        ModuleHookBridgeContract.SIDE_BOTTOM -> TakeoverRect(
          sideId = side.sideId,
          left = spanStart,
          top = (height - thickness).coerceAtLeast(0f),
          right = spanEnd,
          bottom = height,
        )
        else -> null
      }
    }
  }

  /**
   * 四边统一使用触钮自身的边宽（即"触钮条本身"）。
   *
   * 早期版本对 LEFT/RIGHT 复用了「拦截窗口宽度」（200/320dp）来对齐 app 的拦截矩形，
   * 结果两支触钮会在屏幕中部连成整条横带，把正常滑动也吞掉。
   */
  private fun ModuleHookSide.thicknessPx(
    handle: com.slideindex.app.xposed.bridge.ModuleHookHandle,
    density: Float,
  ): Float = handle.widthDp * density
}