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
        imageVector = gestureTriggerIconImageVector(side, trigger),
        contentDescription = contentDescription,
        tint = LocalContentColor.current,
        modifier = modifier.graphicsLayer {
            rotationZ = gestureTriggerIconRotationZ(side, trigger)
        },
    )
}

fun gestureTriggerIconImageVector(trigger: GestureTriggerType): ImageVector =
    gestureTriggerIconImageVector(PanelSide.LEFT, trigger)

fun gestureTriggerIconImageVector(side: PanelSide, trigger: GestureTriggerType): ImageVector = when (trigger) {
    GestureTriggerType.SHORT_SWIPE_IN_UP -> when (side) {
        PanelSide.LEFT, PanelSide.BOTTOM -> ThinActionIcons.CornerArrowUpRight
        PanelSide.RIGHT, PanelSide.TOP -> ThinActionIcons.CornerArrowDownRight
    }
    GestureTriggerType.SHORT_SWIPE_IN_DOWN -> when (side) {
        PanelSide.LEFT, PanelSide.BOTTOM -> ThinActionIcons.CornerArrowDownRight
        PanelSide.RIGHT, PanelSide.TOP -> ThinActionIcons.CornerArrowUpRight
    }
    GestureTriggerType.LONG_SWIPE_IN_UP -> when (side) {
        PanelSide.LEFT, PanelSide.BOTTOM -> ThinActionIcons.DoubleCornerArrowUpRight
        PanelSide.RIGHT, PanelSide.TOP -> ThinActionIcons.DoubleCornerArrowDownRight
    }
    GestureTriggerType.LONG_SWIPE_IN_DOWN -> when (side) {
        PanelSide.LEFT, PanelSide.BOTTOM -> ThinActionIcons.DoubleCornerArrowDownRight
        PanelSide.RIGHT, PanelSide.TOP -> ThinActionIcons.DoubleCornerArrowUpRight
    }
    GestureTriggerType.SHORT_SWIPE_IN_AND_BACK -> ThinActionIcons.SwipeReturn
    else -> when {
        trigger.isLongPress -> MaterialTouchIcons.LongPress
        trigger.isSingleTap -> MaterialTouchIcons.SingleTap
        trigger.isLongDistance -> ThinActionIcons.DoubleArrowRight
        else -> ThinActionIcons.ArrowRight
    }
}

/** 与 SideGesture `TriggerDirection` × `Position` 旋转表一致；[PanelSide.TOP] 按内滑几何补全。 */
fun gestureTriggerIconRotationZ(side: PanelSide, trigger: GestureTriggerType): Float {
    if (trigger.isLongPress || trigger.isSingleTap) return 0f
    return when (side) {
        PanelSide.LEFT -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> 0f
            TriggerDirectionKind.InReturn -> 0f
            TriggerDirectionKind.UpRight -> -45f
            TriggerDirectionKind.DownRight -> 45f
            TriggerDirectionKind.Up -> -90f
            TriggerDirectionKind.Down -> 90f
            TriggerDirectionKind.InUp -> 0f
            TriggerDirectionKind.InDown -> 0f
            null -> 0f
        }
        PanelSide.RIGHT -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> 180f
            TriggerDirectionKind.InReturn -> 180f
            TriggerDirectionKind.UpRight -> -135f
            TriggerDirectionKind.DownRight -> 135f
            TriggerDirectionKind.Up -> -90f
            TriggerDirectionKind.Down -> 90f
            TriggerDirectionKind.InUp -> 180f
            TriggerDirectionKind.InDown -> 180f
            null -> 0f
        }
        PanelSide.BOTTOM -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> -90f
            TriggerDirectionKind.InReturn -> -90f
            TriggerDirectionKind.UpRight -> -135f
            TriggerDirectionKind.DownRight -> -45f
            TriggerDirectionKind.Up -> -180f
            TriggerDirectionKind.Down -> 0f
            TriggerDirectionKind.InUp -> -90f
            TriggerDirectionKind.InDown -> -90f
            null -> 0f
        }
        PanelSide.TOP -> when (trigger.directionKind()) {
            TriggerDirectionKind.In -> 90f
            TriggerDirectionKind.InReturn -> 90f
            TriggerDirectionKind.UpRight -> -135f
            TriggerDirectionKind.DownRight -> -45f
            TriggerDirectionKind.Up -> 180f
            TriggerDirectionKind.Down -> 0f
            TriggerDirectionKind.InUp -> 90f
            TriggerDirectionKind.InDown -> 90f
            null -> 0f
        }
    }
}

private enum class TriggerDirectionKind {
    In,
    InReturn,
    UpRight,
    DownRight,
    Up,
    Down,
    InUp,
    InDown,
}

private fun GestureTriggerType.directionKind(): TriggerDirectionKind? = when (this) {
    GestureTriggerType.SHORT_SWIPE_IN, GestureTriggerType.LONG_SWIPE_IN,
    GestureTriggerType.SHORT_SWIPE_IN_HOVER,
    -> TriggerDirectionKind.In
    GestureTriggerType.SHORT_SWIPE_IN_AND_BACK -> TriggerDirectionKind.InReturn
    GestureTriggerType.SHORT_SWIPE_UP_RIGHT, GestureTriggerType.LONG_SWIPE_UP_RIGHT,
    GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER,
    -> TriggerDirectionKind.UpRight
    GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT,
    GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT_HOVER,
    -> TriggerDirectionKind.DownRight
    GestureTriggerType.SHORT_SWIPE_UP, GestureTriggerType.LONG_SWIPE_UP,
    GestureTriggerType.SHORT_SWIPE_UP_HOVER,
    -> TriggerDirectionKind.Up
    GestureTriggerType.SHORT_SWIPE_DOWN, GestureTriggerType.LONG_SWIPE_DOWN,
    GestureTriggerType.SHORT_SWIPE_DOWN_HOVER,
    -> TriggerDirectionKind.Down
    GestureTriggerType.SHORT_SWIPE_IN_UP, GestureTriggerType.LONG_SWIPE_IN_UP -> TriggerDirectionKind.InUp
    GestureTriggerType.SHORT_SWIPE_IN_DOWN, GestureTriggerType.LONG_SWIPE_IN_DOWN -> TriggerDirectionKind.InDown
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
    FloatBallGestureType.DOUBLE_TAP,
    FloatBallGestureType.SWIPE_SIDE_RETURN,
    FloatBallGestureType.SWIPE_UP_RETURN,
    FloatBallGestureType.SWIPE_DOWN_RETURN,
    -> null
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
    } else if (type.isReturnGesture) {
        val rotation = when (type) {
            FloatBallGestureType.SWIPE_SIDE_RETURN ->
                if (settings.floatBallGestureIconSide() == PanelSide.RIGHT) 180f else 0f
            FloatBallGestureType.SWIPE_UP_RETURN -> -90f
            FloatBallGestureType.SWIPE_DOWN_RETURN -> 90f
            else -> 0f
        }
        Icon(
            imageVector = ThinActionIcons.SwipeReturn,
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
            modifier = modifier.graphicsLayer {
                rotationZ = rotation
            },
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
