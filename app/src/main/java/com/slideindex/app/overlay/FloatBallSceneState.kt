package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.DisplayMetrics
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.roundToInt

/**
 * 悬浮球 Display 层集中状态：球心、十字、线条与各 layer 可见性。
 * [ballCenterPx] 为 null 时表示停靠布局，非 null 时为拖拽中的绝对球心（屏幕坐标 px）。
 */
internal class FloatBallSceneState(initialSettings: AppSettings) {
    val settingsState: MutableState<AppSettings> = mutableStateOf(initialSettings)
    val ballDragging: MutableState<Boolean> = mutableStateOf(false)
    val stripZonePreview: MutableState<Boolean> = mutableStateOf(false)
    val styleVisualGeneration: MutableState<Int> = mutableStateOf(0)
    /** 屏幕旋转等触发布局刷新时递增，驱动 Display 层按最新屏幕尺寸重绘。 */
    val screenLayoutGeneration: MutableState<Int> = mutableStateOf(0)
    val cursorVisible: MutableState<Boolean> = mutableStateOf(false)
    val cursorPaused: MutableState<Boolean> = mutableStateOf(false)
    val cursorAnchor: MutableState<Offset> = mutableStateOf(Offset.Zero)
    val selectionStart: MutableState<Offset?> = mutableStateOf(null)
    val selectionPreviewBounds: MutableState<Rect?> = mutableStateOf(null)
    /** null = 停靠；非 null = 拖拽球心（屏幕绝对坐标）。 */
    val ballCenterPx: MutableState<Offset?> = mutableStateOf(null)
    val chromeVisible: MutableState<Boolean> = mutableStateOf(true)
    val lineVisible: MutableState<Boolean> = mutableStateOf(false)
    val ballVisible: MutableState<Boolean> = mutableStateOf(true)
    /** 拖拽 GIF 快照期间隐藏 Compose 球体。 */
    val ballComposeVisible: MutableState<Boolean> = mutableStateOf(true)

    fun resolvedActiveSide(
        settings: AppSettings = settingsState.value,
        dragActiveSideOverride: FloatBallSide? = null,
    ): FloatBallSide = dragActiveSideOverride ?: FloatBallLayout.resolvedActiveSide(settings)

    /** 停靠球心（屏幕坐标）。 */
    fun dockBallCenter(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Offset {
        val (cx, cy) = FloatBallLayout.ballCenterPx(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
        )
        return Offset(cx, cy)
    }

    /** 当前球心：拖拽优先，否则停靠。 */
    fun resolveBallCenter(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Offset = ballCenterPx.value ?: dockBallCenter(
        settings,
        metrics,
        activeSide,
        screenWidthPx,
        screenHeightPx,
    )

    /** 球体窗口左上角，用于 Display 层定位（含半隐藏停靠）。 */
    fun ballWindowTopLeft(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        center: Offset,
        screenHeightPx: Int = metrics.heightPixels,
    ): Pair<Int, Int> {
        val ballSizePx = FloatBallLayout.ballSizePx(settings, metrics.density)
        return if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            (center.x - ballSizePx / 2f).roundToInt() to (center.y - ballSizePx / 2f).roundToInt()
        } else {
            FloatBallLayout.stripWindowOriginForBallCenter(
                settings = settings,
                metrics = metrics,
                activeSide = activeSide,
                ballCenterX = center.x,
                ballCenterY = center.y,
                screenHeightPx = screenHeightPx,
            )
        }
    }

    fun ballHitRect(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Rect {
        val center = resolveBallCenter(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
        val ballSizePx = FloatBallLayout.ballSizePx(settings, metrics.density)
        val (left, top) = ballWindowTopLeft(settings, metrics, activeSide, center, screenHeightPx)
        return Rect(left, top, left + ballSizePx, top + ballSizePx)
    }

    fun lineHitRect(
        settings: AppSettings,
        metrics: DisplayMetrics,
        inactiveSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Rect = FloatBallLayout.lineStripBounds(
        settings,
        metrics,
        inactiveSide,
        screenWidthPx,
        screenHeightPx,
    )

    /** 空闲态球体触摸窗 bounds（仅球区；线条由独立触摸窗覆盖）。 */
    fun ballTouchBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Rect = ballHitRect(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
}
