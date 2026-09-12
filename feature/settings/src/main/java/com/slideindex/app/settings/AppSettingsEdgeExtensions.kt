package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide

object FloatingPointerDesignIds {
    const val RING = "ring"
}

fun PanelSide.maxTriggerEdgeWidthDp(): Float = when (this) {
    PanelSide.TOP -> TriggerHandle.MAX_TOP_EDGE_WIDTH_DP
    else -> TriggerHandle.MAX_EDGE_WIDTH_DP
}

fun AppSettings.edgeTriggerWidthDp(side: PanelSide): Float = when (side) {
    PanelSide.LEFT -> leftEdgeTriggerWidthDp
    PanelSide.RIGHT -> rightEdgeTriggerWidthDp
    PanelSide.BOTTOM -> bottomEdgeTriggerWidthDp
    PanelSide.TOP -> topEdgeTriggerWidthDp
}

fun AppSettings.maxEdgeTriggerWidthDp(side: PanelSide): Float {
    val fromHandles = triggerHandles(side).maxOfOrNull { handle ->
        triggerHandleEdgeWidthDp(side, handle.id)
    }
    return fromHandles ?: edgeTriggerWidthDp(side)
}

fun AppSettings.triggerHandleEdgeWidthDp(side: PanelSide, handleId: String): Float {
    val handle = triggerHandle(side, handleId) ?: primaryTriggerHandle(side)
    val maxWidth = side.maxTriggerEdgeWidthDp()
    return handle.edgeWidthDp?.coerceIn(TriggerHandle.MIN_EDGE_WIDTH_DP, maxWidth)
        ?: edgeTriggerWidthDp(side)
}

fun AppSettings.withResolvedHandleEdgeWidths(): AppSettings {
    fun resolve(handles: List<TriggerHandle>, side: PanelSide): List<TriggerHandle> {
        val maxWidth = side.maxTriggerEdgeWidthDp()
        return handles.map { handle ->
            val width = handle.edgeWidthDp
            if (width != null) {
                handle.copy(
                    edgeWidthDp = width.coerceIn(
                        TriggerHandle.MIN_EDGE_WIDTH_DP,
                        maxWidth,
                    ),
                )
            } else {
                handle
            }
        }
    }
    return copy(
        edgeTrigger = edgeTrigger.copy(
            leftTriggerHandles = resolve(leftTriggerHandles, PanelSide.LEFT),
            rightTriggerHandles = resolve(rightTriggerHandles, PanelSide.RIGHT),
            bottomTriggerHandles = resolve(bottomTriggerHandles, PanelSide.BOTTOM),
            topTriggerHandles = resolve(topTriggerHandles, PanelSide.TOP),
            leftTriggerHandlesLandscape = resolve(leftTriggerHandlesLandscape, PanelSide.LEFT),
            rightTriggerHandlesLandscape = resolve(rightTriggerHandlesLandscape, PanelSide.RIGHT),
            bottomTriggerHandlesLandscape = resolve(bottomTriggerHandlesLandscape, PanelSide.BOTTOM),
            topTriggerHandlesLandscape = resolve(topTriggerHandlesLandscape, PanelSide.TOP),
        ),
    )
}

fun AppSettings.triggerTopFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).topFraction

fun AppSettings.triggerHeightFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).heightFraction

fun AppSettings.triggerBottomFraction(side: PanelSide): Float =
    primaryTriggerHandle(side).bottomFraction

fun AppSettings.interceptWindowWidthDp(side: PanelSide): Float {
    if (side.isVerticalEdge || !interceptSystemBackGesture) return maxEdgeTriggerWidthDp(side)
    val triggerWidth = maxEdgeTriggerWidthDp(side)
    val interceptWidth = if (limitMaxInterceptLength) 200f else 320f
    return maxOf(triggerWidth, interceptWidth)
}

internal fun Preferences.legacyTriggerEdgeWidthInherits(): Boolean =
    this[SettingsPreferenceKeys.TRIGGER_EDGE_WIDTH_NULLABLE_MIGRATED] != true
