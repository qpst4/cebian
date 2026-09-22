package com.slideindex.app.overlay

import android.graphics.RectF
import com.slideindex.app.overlay.corner.CornerAnchor
import com.slideindex.app.overlay.corner.CornerZoneLayout
import com.slideindex.app.overlay.corner.CornerZoneStrip
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.bridge.ModuleHookExtraRect

/**
 * 触钮之外、需要交给 system_server 模块在输入层接管的矩形。
 *
 * 只做「贴在系统手势带上的触钮类区域」：当前是**边角轮盘**的竖条与横条
 * （悬浮球与双贴边线条在下一步加入，见 `TARGET_FLOAT_BALL` / `TARGET_FLOAT_LINE`）。
 *
 * 归属开关按「矩形贴在哪条边」决定（方案 A）：
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
    return cornerRects(settings, screenWidthPx, screenHeightPx, density, isLandscape)
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

  private fun CornerZoneStrip.extraRectStrip(): Int = when (this) {
    CornerZoneStrip.VERTICAL -> ModuleHookBridgeContract.CORNER_STRIP_VERTICAL
    CornerZoneStrip.HORIZONTAL -> ModuleHookBridgeContract.CORNER_STRIP_HORIZONTAL
  }

  private val CORNER_ANCHORS = listOf(CornerAnchor.LEFT, CornerAnchor.RIGHT)
  private val CORNER_STRIPS = listOf(CornerZoneStrip.VERTICAL, CornerZoneStrip.HORIZONTAL)
}
