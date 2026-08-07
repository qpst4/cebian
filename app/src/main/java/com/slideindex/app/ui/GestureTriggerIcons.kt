package com.slideindex.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide

/**
 * 侧滑手势槽位图标：方向与旋转对齐 SideGesture `MySideGestureSettings`；
 * 长距离滑动用双箭头，短划/点击/长按用单箭头或专用图标。
 */
@Composable
fun GestureTriggerIcon(
    side: PanelSide,
    trigger: GestureTriggerType,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = gestureTriggerIconImageVector(trigger),
        contentDescription = contentDescription,
        tint = LocalContentColor.current,
        modifier = modifier.graphicsLayer {
            rotationZ = gestureTriggerIconRotationZ(side, trigger)
        },
    )
}

fun gestureTriggerIconImageVector(trigger: GestureTriggerType): ImageVector = when {
    trigger.isLongPress -> MaterialTouchIcons.LongPress
    trigger.isSingleTap -> MaterialTouchIcons.SingleTap
    trigger.isLongDistance -> ThinActionIcons.DoubleArrowRight
    else -> ThinActionIcons.ArrowRight
}

/** 与 SideGesture `TriggerDirection` × `Position` 旋转表一致；[PanelSide.TOP] 按内滑几何补全。 */
fun gestureTriggerIconRotationZ(side: PanelSide, trigger: GestureTriggerType): Float {
    if (trigger.isLongPress || trigger.isSingleTap) return 0f
    return when (side) {
        PanelSide.LEFT -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> 0f
            TriggerDirectionKind.UpRight -> -45f
            TriggerDirectionKind.DownRight -> 45f
            TriggerDirectionKind.Up -> -90f
            TriggerDirectionKind.Down -> 90f
            null -> 0f
        }
        PanelSide.RIGHT -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> 180f
            TriggerDirectionKind.UpRight -> -135f
            TriggerDirectionKind.DownRight -> 135f
            TriggerDirectionKind.Up -> -90f
            TriggerDirectionKind.Down -> 90f
            null -> 0f
        }
        PanelSide.BOTTOM -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> -90f
            TriggerDirectionKind.UpRight -> -135f
            TriggerDirectionKind.DownRight -> -45f
            TriggerDirectionKind.Up -> -180f
            TriggerDirectionKind.Down -> 0f
            null -> 0f
        }
        PanelSide.TOP -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> 90f
            TriggerDirectionKind.UpRight -> -135f
            TriggerDirectionKind.DownRight -> -45f
            TriggerDirectionKind.Up -> 180f
            TriggerDirectionKind.Down -> 0f
            null -> 0f
        }
    }
}

private enum class TriggerDirectionKind {
    In,
    UpRight,
    DownRight,
    Up,
    Down,
}

private fun GestureTriggerType.directionKind(): TriggerDirectionKind? = when (this) {
    GestureTriggerType.SHORT_SWIPE_IN, GestureTriggerType.LONG_SWIPE_IN -> TriggerDirectionKind.In
    GestureTriggerType.SHORT_SWIPE_UP_RIGHT, GestureTriggerType.LONG_SWIPE_UP_RIGHT -> TriggerDirectionKind.UpRight
    GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT -> TriggerDirectionKind.DownRight
    GestureTriggerType.SHORT_SWIPE_UP, GestureTriggerType.LONG_SWIPE_UP -> TriggerDirectionKind.Up
    GestureTriggerType.SHORT_SWIPE_DOWN, GestureTriggerType.LONG_SWIPE_DOWN -> TriggerDirectionKind.Down
    else -> null
}

fun FloatBallGestureType.toGestureTriggerType(): GestureTriggerType? = when (this) {
    FloatBallGestureType.SWIPE_UP_SHORT -> GestureTriggerType.SHORT_SWIPE_UP
    FloatBallGestureType.SWIPE_UP_LONG -> GestureTriggerType.LONG_SWIPE_UP
    FloatBallGestureType.SWIPE_DOWN_SHORT -> GestureTriggerType.SHORT_SWIPE_DOWN
    FloatBallGestureType.SWIPE_DOWN_LONG -> GestureTriggerType.LONG_SWIPE_DOWN
    FloatBallGestureType.SWIPE_SIDE_SHORT -> GestureTriggerType.SHORT_SWIPE_IN
    FloatBallGestureType.SWIPE_SIDE_LONG -> GestureTriggerType.LONG_SWIPE_IN
    FloatBallGestureType.SINGLE_TAP -> GestureTriggerType.SHORT_SINGLE_TAP
    FloatBallGestureType.LONG_PRESS -> GestureTriggerType.SHORT_LONG_PRESS
    FloatBallGestureType.DOUBLE_TAP -> null
}

fun AppSettings.floatBallGestureIconSide(): PanelSide {
    val ballSide = when (floatBallPositionMode) {
        FloatBallPositionMode.LEFT -> FloatBallSide.LEFT
        FloatBallPositionMode.RIGHT -> FloatBallSide.RIGHT
        FloatBallPositionMode.BOTH_EDGES -> floatBallActiveSide
        FloatBallPositionMode.CUSTOM ->
            if (floatBallCustomCenterXFraction >= 0.5f) FloatBallSide.RIGHT else FloatBallSide.LEFT
    }
    return when (ballSide) {
        FloatBallSide.LEFT -> PanelSide.LEFT
        FloatBallSide.RIGHT -> PanelSide.RIGHT
    }
}

@Composable
fun FloatBallGestureIcon(
    type: FloatBallGestureType,
    settings: AppSettings,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val trigger = type.toGestureTriggerType()
    if (trigger != null) {
        GestureTriggerIcon(
            side = settings.floatBallGestureIconSide(),
            trigger = trigger,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        Icon(
            imageVector = MaterialTouchIcons.DoubleTap,
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
            modifier = modifier,
        )
    }
}
