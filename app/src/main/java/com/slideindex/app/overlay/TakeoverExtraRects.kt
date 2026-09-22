package com.slideindex.app.overlay

import android.graphics.RectF
import android.graphics.Rect
import com.slideindex.app.overlay.corner.CornerAnchor
import com.slideindex.app.overlay.corner.CornerZoneLayout
import com.slideindex.app.overlay.corner.CornerZoneStrip
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleHookExtraRect

/**
 * 触钮之外、需要交给 system_server 模块在输入层接管的矩形。
 *
 * 只做「贴在系统手势带上的触钮类区域」：
 * - **悬浮球**（[ModuleHookBridgeContract.TARGET_FLOAT_BALL]）
 * - **双贴边模式的线条**（[ModuleHookBridgeContract.TARGET_FLOAT_LINE]）
 * - **边角轮盘**的两角竖条/横条（[ModuleHookBridgeContract.TARGET_CORNER_LEFT/RIGHT]）
 *
 * 归属开关按「矩形贴在哪条边」决定（方案 A）：
 * 贴左/右边 → [ModuleHookBridgeContract.GROUP_SIDES]，贴底边 → [ModuleHookBridgeContract.GROUP_BOTTOM]，
 * 贴顶边 → [ModuleHookBridgeContract.GROUP_TOP]；屏幕中部不贴边（自定义位置的球）则不下发。
 * 角轮盘位于屏幕底部左右两角 → [ModuleHookBridgeContract.GROUP_BOTTOM]。
 * 模块侧对该组有豁免：三键导航下底部触钮组整体停用，但角轮盘是明确的触钮矩形，
 * 不受该限制，否则三键导航用户永远用不了它。
 *
 * 矩形一律以**屏幕比例**下发，模块按实时窗口尺寸换算，旋转/折叠自动跟随。
 * 坐标精度不是安全边界：模块吞掉 DOWN 前会调用 app 的 `canAcceptTouchAt` 做命中复核，
 * 这里只要覆盖不到或算偏，最坏结果是「不接管」，不会出现吞掉却无人处理的死区。
 */
object TakeoverExtraRects {
  fun build(
    settings: AppSettings,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    isLandscape: Boolean,
  ): List<ModuleHookExtraRect> {
    if (screenWidthPx <= 0 || screenHeightPx <= 0 || density <= 0f) return emptyList()
    return buildList {
      addAll(floatBallRects(settings, screenWidthPx, screenHeightPx, density, isLandscape))
      addAll(cornerRects(settings, screenWidthPx, screenHeightPx, density, isLandscape))
    }
  }

  /**
   * 悬浮球与双贴边线条。
   *
   * 两者位置都随设置、键盘弹出和横屏变化（`keyboardAdjusted*`），因此每次下发都按当前状态重算；
   * 拖拽落位后 app 会重新下发一次，拖拽过程中不需要更新（那会儿会话已经锁在该目标上）。
   */
  private fun floatBallRects(
    settings: AppSettings,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    isLandscape: Boolean,
  ): List<ModuleHookExtraRect> {
    if (!settings.floatBallEnabled) return emptyList()
    if (FloatBallLayout.isKeyboardTriggerDisabled(settings, isLandscape)) return emptyList()

    val metrics = displayMetrics(screenWidthPx, screenHeightPx, density)
    val activeSide = FloatBallLayout.resolvedActiveSide(settings)
    val ballSizePx = FloatBallLayout.ballSizePx(settings, density)
    val (ballLeft, ballTop) = FloatBallLayout.keyboardAdjustedBallTopLeft(
      settings = settings,
      metrics = metrics,
      activeSide = activeSide,
      isLandscape = isLandscape,
      screenWidthPx = screenWidthPx,
      screenHeightPx = screenHeightPx,
    )
    val ballRect = Rect(ballLeft, ballTop, ballLeft + ballSizePx, ballTop + ballSizePx)

    return buildList {
      nearestEdgeGroup(ballRect, screenWidthPx, screenHeightPx, density)?.let { group ->
        add(ballRect.toExtraRect(ModuleHookBridgeContract.TARGET_FLOAT_BALL, group, strip = 0, screenWidthPx, screenHeightPx))
      }
      if (FloatBallLayout.shouldShowLine(settings)) {
        val inactiveSide = FloatBallSide.opposite(activeSide)
        val lineRect = FloatBallLayout.keyboardAdjustedLineStripBounds(
          settings = settings,
          metrics = metrics,
          side = inactiveSide,
          isLandscape = isLandscape,
          screenWidthPx = screenWidthPx,
          screenHeightPx = screenHeightPx,
        )
        val lineGroup = when (inactiveSide) {
          FloatBallSide.LEFT, FloatBallSide.RIGHT -> ModuleHookBridgeContract.GROUP_SIDES
        }
        add(lineRect.toExtraRect(ModuleHookBridgeContract.TARGET_FLOAT_LINE, lineGroup, strip = 0, screenWidthPx, screenHeightPx))
      }
    }
  }

  /**
   * 矩形贴在哪条系统手势带上（方案 A）。
   *
   * 系统手势带按边取 [SYSTEM_GESTURE_BAND_DP] 的保守近似；多边同时命中时取最近的那条，
   * 保证一个矩形只归一个开关（模块侧的生效判定与三键导航豁免都依赖这一点）。
   */
  private fun nearestEdgeGroup(
    rect: Rect,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
  ): Int? {
    val band = SYSTEM_GESTURE_BAND_DP * density
    val candidates = buildList {
      if (rect.left <= band) add(ModuleHookBridgeContract.GROUP_SIDES to rect.left)
      if (rect.right >= screenWidthPx - band) add(ModuleHookBridgeContract.GROUP_SIDES to screenWidthPx - rect.right)
      if (rect.bottom >= screenHeightPx - band) add(ModuleHookBridgeContract.GROUP_BOTTOM to screenHeightPx - rect.bottom)
      if (rect.top <= band) add(ModuleHookBridgeContract.GROUP_TOP to rect.top)
    }
    return candidates.minByOrNull { it.second }?.first
  }

  private fun cornerRects(
    settings: AppSettings,
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
    isLandscape: Boolean,
  ): List<ModuleHookExtraRect> {
    val corner = settings.cornerGestureSettings
    if (!corner.enabled || !corner.hasActiveTriggerZone()) return emptyList()
    if (isLandscape && corner.hideInLandscape) return emptyList()

    val layout = CornerZoneLayout().apply {
      update(screenWidthPx, screenHeightPx, density, corner)
    }
    return buildList {
      for (anchor in CORNER_ANCHORS) {
        val anchorEnabled = when (anchor) {
          CornerAnchor.LEFT -> corner.leftEnabled
          CornerAnchor.RIGHT -> corner.rightEnabled
        }
        if (!anchorEnabled) continue
        val target = anchor.extraRectTarget() ?: continue
        for (strip in CORNER_STRIPS) {
          val rect = layout.stripRect(anchor, strip) ?: continue
          if (rect.width() <= 0f || rect.height() <= 0f) continue
          add(rect.toExtraRect(target, strip, screenWidthPx, screenHeightPx))
        }
      }
    }
  }

  /** 角轮盘竖条/横条按距屏幕底边的远近取目标；两条带使用同一个 target。 */
  private fun CornerAnchor.extraRectTarget(): Int? = when (this) {
    CornerAnchor.LEFT -> ModuleHookBridgeContract.TARGET_CORNER_LEFT
    CornerAnchor.RIGHT -> ModuleHookBridgeContract.TARGET_CORNER_RIGHT
  }

  private fun RectF.toExtraRect(
    target: Int,
    strip: CornerZoneStrip,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): ModuleHookExtraRect = ModuleHookExtraRect(
    target = target,
    groups = ModuleHookBridgeContract.GROUP_BOTTOM,
    leftFraction = (left / screenWidthPx).coerceIn(0f, 1f),
    topFraction = (top / screenHeightPx).coerceIn(0f, 1f),
    rightFraction = (right / screenWidthPx).coerceIn(0f, 1f),
    bottomFraction = (bottom / screenHeightPx).coerceIn(0f, 1f),
    strip = strip.extraRectStrip(),
  )

  private fun Rect.toExtraRect(
    target: Int,
    groups: Int,
    strip: Int,
    screenWidthPx: Int,
    screenHeightPx: Int,
  ): ModuleHookExtraRect = ModuleHookExtraRect(
    target = target,
    groups = groups,
    leftFraction = (left.toFloat() / screenWidthPx).coerceIn(0f, 1f),
    topFraction = (top.toFloat() / screenHeightPx).coerceIn(0f, 1f),
    rightFraction = (right.toFloat() / screenWidthPx).coerceIn(0f, 1f),
    bottomFraction = (bottom.toFloat() / screenHeightPx).coerceIn(0f, 1f),
    strip = strip,
  )

  private fun displayMetrics(
    screenWidthPx: Int,
    screenHeightPx: Int,
    density: Float,
  ): android.util.DisplayMetrics = android.util.DisplayMetrics().apply {
    this.density = density
    densityDpi = (density * 160f).toInt()
    widthPixels = screenWidthPx
    heightPixels = screenHeightPx
  }

  private fun CornerZoneStrip.extraRectStrip(): Int = when (this) {
    CornerZoneStrip.VERTICAL -> ModuleHookBridgeContract.CORNER_STRIP_VERTICAL
    CornerZoneStrip.HORIZONTAL -> ModuleHookBridgeContract.CORNER_STRIP_HORIZONTAL
  }

  private val CORNER_ANCHORS = listOf(CornerAnchor.LEFT, CornerAnchor.RIGHT)
  private val CORNER_STRIPS = listOf(CornerZoneStrip.VERTICAL, CornerZoneStrip.HORIZONTAL)

  /** 系统手势带（返回/上滑/下拉）宽度的保守近似，用于判断矩形贴在哪条边。 */
  private const val SYSTEM_GESTURE_BAND_DP = 32f
}
