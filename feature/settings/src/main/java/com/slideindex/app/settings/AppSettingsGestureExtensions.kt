package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SideGestureDefaults
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerHandlePairEntry
import com.slideindex.app.gesture.TriggerCollectionEntry
import com.slideindex.app.overlay.PanelSide
import kotlin.math.roundToInt

fun AppSettings.rulesForSide(side: PanelSide): List<GestureRule> =
    gestureRules.filter { it.enabled && it.side == side }.sortedByDescending { it.priority }

fun AppSettings.withSlotAction(
    side: PanelSide,
    trigger: GestureTriggerType,
    action: GestureAction,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val slotId = GestureRule.slotId(side, trigger, handleId)
    val existing = gestureRules.firstOrNull { it.id == slotId }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }
        } else {
            null
        }
    val others = gestureRules.filterNot { it.id == slotId || it.id == existing?.id }
    if (action.type == GestureActionType.NONE) {
        if (existing?.triggerMode == GestureTriggerMode.DEFAULT || existing?.triggerMode == null) {
            return copy(gestureRules = others)
        }
        return copy(
            gestureRules = others + GestureRule(
                id = slotId,
                side = side,
                trigger = trigger,
                action = GestureAction.None,
                triggerMode = existing.triggerMode,
                handleId = handleId,
            ),
        )
    }
    return copy(
        gestureRules = others + GestureRule(
            id = slotId,
            side = side,
            trigger = trigger,
            action = action,
            triggerMode = existing?.triggerMode ?: GestureTriggerMode.DEFAULT,
            handleId = handleId,
        ),
    )
}

fun AppSettings.withSlotTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    triggerMode: GestureTriggerMode,
    handleId: String = TriggerHandle.DEFAULT_ID,
): AppSettings {
    val slotId = GestureRule.slotId(side, trigger, handleId)
    val existing = gestureRules.firstOrNull { it.id == slotId }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }
        } else {
            null
        }
    val others = gestureRules.filterNot { it.id == slotId || it.id == existing?.id }
    val action = existing?.action ?: actionFor(side, trigger, handleId)
    if (triggerMode == GestureTriggerMode.DEFAULT &&
        (existing == null || existing.action.type == GestureActionType.NONE) &&
        action.type == GestureActionType.NONE
    ) {
        return copy(gestureRules = others)
    }
    if (triggerMode == GestureTriggerMode.DEFAULT && existing != null &&
        existing.action.type != GestureActionType.NONE
    ) {
        return copy(
            gestureRules = others + existing.copy(triggerMode = GestureTriggerMode.DEFAULT),
        )
    }
    if (triggerMode == GestureTriggerMode.DEFAULT && existing == null) {
        return copy(gestureRules = others)
    }
    return copy(
        gestureRules = others + GestureRule(
            id = slotId,
            side = side,
            trigger = trigger,
            action = action,
            triggerMode = triggerMode,
            handleId = handleId,
        ),
    )
}

fun AppSettings.shortcutGesturesConfiguredCount(): Int =
    gestureRules.count {
        it.enabled && it.action.type == GestureActionType.LAUNCH_APP &&
            it.trigger == GestureTriggerType.SHORT_SWIPE_IN
    }

fun AppSettings.effectiveRule(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureRule? {
    val newSlotId = GestureRule.slotId(side, trigger, handleId)
    val custom = gestureRules
        .filter { it.enabled && it.side == side && it.trigger == trigger && it.handleId == handleId }
        .maxByOrNull { it.priority }
        ?: gestureRules.firstOrNull {
            it.enabled &&
                it.side == side &&
                it.trigger == trigger &&
                it.id == newSlotId
        }
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull {
                it.enabled &&
                    it.side == side &&
                    it.trigger == trigger &&
                    it.id == GestureRule.legacySlotId(side, trigger)
            }
        } else {
            null
        }
    if (custom != null) return custom
    if (handleId != TriggerHandle.DEFAULT_ID) {
        return effectiveRule(side, trigger, TriggerHandle.DEFAULT_ID)
    }
    return SideGestureDefaults.rulesFor(side)
        .firstOrNull { it.trigger == trigger && it.action.isEffective() }
}

fun AppSettings.actionFor(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureAction {
    return effectiveRule(side, trigger, handleId)?.action ?: GestureAction.None
}

fun AppSettings.slotTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode {
    val newSlotId = GestureRule.slotId(side, trigger, handleId)
    return gestureRules.firstOrNull { it.id == newSlotId }?.triggerMode
        ?: if (handleId == TriggerHandle.DEFAULT_ID) {
            gestureRules.firstOrNull { it.id == GestureRule.legacySlotId(side, trigger) }?.triggerMode
        } else {
            null
        }
        ?: GestureTriggerMode.DEFAULT
}

fun AppSettings.resolvedTriggerMode(
    side: PanelSide,
    trigger: GestureTriggerType,
    handleId: String = TriggerHandle.DEFAULT_ID,
): GestureTriggerMode {
    val customMode = slotTriggerMode(side, trigger, handleId)
    if (customMode != GestureTriggerMode.DEFAULT) return customMode
    val ruleMode = effectiveRule(side, trigger, handleId)?.triggerMode
    if (ruleMode != null && ruleMode != GestureTriggerMode.DEFAULT) return ruleMode
    return defaultTriggerModeFor(side)
}

fun AppSettings.defaultTriggerModeFor(side: PanelSide): GestureTriggerMode =
    when (side) {
        PanelSide.LEFT -> leftDefaultTriggerMode
        PanelSide.RIGHT -> rightDefaultTriggerMode
    }

fun AppSettings.triggerHandles(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandles
    PanelSide.RIGHT -> rightTriggerHandles
}.filter { it.enabled }

fun AppSettings.allTriggerHandles(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandles
    PanelSide.RIGHT -> rightTriggerHandles
}

fun AppSettings.primaryTriggerHandle(side: PanelSide): TriggerHandle =
    triggerHandles(side).firstOrNull() ?: TriggerHandle.default()

fun AppSettings.triggerHandle(side: PanelSide, handleId: String): TriggerHandle? =
    allTriggerHandles(side).firstOrNull { it.id == handleId }

fun AppSettings.withTriggerHandles(
    side: PanelSide,
    handles: List<TriggerHandle>,
): AppSettings = withSideTriggerHandles(side, handles, allowEmpty = false)

private fun AppSettings.withSideTriggerHandles(
    side: PanelSide,
    handles: List<TriggerHandle>,
    allowEmpty: Boolean,
): AppSettings {
    val resolved = if (handles.isEmpty() && !allowEmpty) {
        listOf(TriggerHandle.default())
    } else {
        handles
    }
    return when (side) {
        PanelSide.LEFT -> copy(leftTriggerHandles = resolved)
        PanelSide.RIGHT -> copy(rightTriggerHandles = resolved)
    }
}

fun AppSettings.withUpdatedTriggerHandleEdgeWidth(
    side: PanelSide,
    handleId: String,
    edgeWidthDp: Float,
): AppSettings {
    val width = edgeWidthDp.coerceIn(
        TriggerHandle.MIN_EDGE_WIDTH_DP,
        TriggerHandle.MAX_EDGE_WIDTH_DP,
    )
    var updated = withSideTriggerHandleEdgeWidth(side, handleId, width)
    val sourceHandle = updated.triggerHandle(side, handleId)
    if (sourceHandle?.alignOppositeSide != false) {
        val otherSide = side.opposite()
        if (updated.triggerHandle(otherSide, handleId) != null) {
            updated = updated.withSideTriggerHandleEdgeWidth(otherSide, handleId, width)
        }
    }
    return updated
}

private fun AppSettings.withSideTriggerHandleEdgeWidth(
    side: PanelSide,
    handleId: String,
    width: Float,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(edgeWidthDp = width)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withUpdatedTriggerHandleDistances(
    side: PanelSide,
    handleId: String,
    shortSwipeDistanceDp: Float? = null,
    longSwipeDistanceDp: Float? = null,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            val short = shortSwipeDistanceDp?.roundToInt()?.toFloat()
                ?.coerceIn(0f, 160f) ?: handle.shortSwipeDistanceDp
            val longMin = if (short <= 0f) 16f else short + 16f
            var long = longSwipeDistanceDp?.roundToInt()?.toFloat()
                ?.coerceIn(longMin, 240f) ?: handle.longSwipeDistanceDp
            if (long < longMin) {
                long = longMin.coerceAtMost(240f)
            }
            handle.copy(
                shortSwipeDistanceDp = short,
                longSwipeDistanceDp = long,
            )
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withUpdatedTriggerHandle(
    side: PanelSide,
    handleId: String,
    topFraction: Float,
    heightFraction: Float,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(topFraction = topFraction, heightFraction = heightFraction)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withTriggerAlignOppositeDesign(
    handleId: String,
    alignOppositeDesign: Boolean,
): AppSettings {
    fun mapSide(side: PanelSide): List<TriggerHandle> =
        allTriggerHandles(side).map { handle ->
            if (handle.id == handleId) {
                handle.copy(alignOppositeDesign = alignOppositeDesign)
            } else {
                handle
            }
        }
    return copy(
        leftTriggerHandles = mapSide(PanelSide.LEFT),
        rightTriggerHandles = mapSide(PanelSide.RIGHT),
    )
}

fun AppSettings.withTriggerAlignOppositeSide(
    handleId: String,
    alignOppositeSide: Boolean,
): AppSettings {
    fun mapSide(side: PanelSide): List<TriggerHandle> =
        allTriggerHandles(side).map { handle ->
            if (handle.id == handleId) {
                handle.copy(alignOppositeSide = alignOppositeSide)
            } else {
                handle
            }
        }
    return copy(
        leftTriggerHandles = mapSide(PanelSide.LEFT),
        rightTriggerHandles = mapSide(PanelSide.RIGHT),
    )
}

fun AppSettings.withAddedTriggerHandlePair(): AppSettings {
    val pairId = TriggerHandle.newId()
    val leftNew = suggestNextTriggerHandle(leftTriggerHandles).copy(id = pairId)
    val rightNew = if (leftNew.alignOppositeSide) {
        leftNew
    } else {
        suggestNextTriggerHandle(rightTriggerHandles).copy(id = pairId)
    }
    return copy(
        leftTriggerHandles = leftTriggerHandles + leftNew,
        rightTriggerHandles = rightTriggerHandles + rightNew,
    )
}

fun AppSettings.withRemovedTriggerHandle(side: PanelSide, handleId: String): AppSettings {
    if (triggerHandle(side, handleId) == null) return this
    val updated = withSideTriggerHandles(
        side = side,
        handles = allTriggerHandles(side).filterNot { it.id == handleId },
        allowEmpty = true,
    )
    return updated.copy(
        gestureRules = gestureRules.filterNot { rule ->
            rule.handleId == handleId && rule.side == side
        },
    )
}

fun AppSettings.sideTriggerPairs(): List<TriggerHandlePairEntry> =
    leftTriggerHandles.mapIndexed { index, left ->
        val right = rightTriggerHandles.getOrNull(index)?.takeIf { it.id == left.id }
            ?: rightTriggerHandles.firstOrNull { it.id == left.id }
        TriggerHandlePairEntry(index = index, handleId = left.id, left = left, right = right)
    }

fun AppSettings.triggerCollectionEntries(): List<TriggerCollectionEntry> {
    val orderedIds = buildList {
        leftTriggerHandles.forEach { if (it.id !in this) add(it.id) }
        rightTriggerHandles.forEach { if (it.id !in this) add(it.id) }
    }
    return orderedIds.map { handleId ->
        TriggerCollectionEntry(
            handleId = handleId,
            left = leftTriggerHandles.firstOrNull { it.id == handleId },
            right = rightTriggerHandles.firstOrNull { it.id == handleId },
        )
    }
}

fun AppSettings.withReplacedTriggerHandle(
    side: PanelSide,
    handleId: String,
    handle: TriggerHandle,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { existing ->
        if (!matched && existing.id == handleId) {
            matched = true
            handle
        } else {
            existing
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withSyncedTriggerHandle(
    sourceSide: PanelSide,
    handleId: String,
    handle: TriggerHandle,
): AppSettings {
    var updated = withReplacedTriggerHandle(sourceSide, handleId, handle)
    if (handle.alignOppositeSide != false) {
        val otherSide = sourceSide.opposite()
        if (updated.triggerHandle(otherSide, handleId) != null) {
            updated = updated.withReplacedTriggerHandle(otherSide, handleId, handle)
        }
    }
    return updated
}

fun AppSettings.withUpdatedTriggerHandleDesign(
    side: PanelSide,
    handleId: String,
    design: TriggerHandleDesign,
): AppSettings {
    var matched = false
    val updated = allTriggerHandles(side).map { handle ->
        if (!matched && handle.id == handleId) {
            matched = true
            handle.copy(design = design)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withSyncedTriggerHandleDesignState(
    sourceSide: PanelSide,
    handleId: String,
    sourceHandle: TriggerHandle,
): AppSettings {
    var updated = withReplacedTriggerHandle(sourceSide, handleId, sourceHandle)
    if (sourceHandle.alignOppositeDesign == false) return updated
    val otherSide = sourceSide.opposite()
    val other = updated.triggerHandle(otherSide, handleId) ?: return updated
    return updated.withReplacedTriggerHandle(
        otherSide,
        handleId,
        other.copy(
            design = sourceHandle.design,
            rectanglePresetState = sourceHandle.rectanglePresetState,
        ),
    )
}

fun AppSettings.withSyncedTriggerHandleDesign(
    sourceSide: PanelSide,
    handleId: String,
    design: TriggerHandleDesign,
): AppSettings {
    var updated = withUpdatedTriggerHandleDesign(sourceSide, handleId, design)
    val sourceHandle = updated.triggerHandle(sourceSide, handleId)
    if (sourceHandle?.alignOppositeDesign != false) {
        val otherSide = sourceSide.opposite()
        if (updated.triggerHandle(otherSide, handleId) != null) {
            updated = updated.withUpdatedTriggerHandleDesign(otherSide, handleId, design)
        }
    }
    return updated
}

private fun suggestNextTriggerHandle(existing: List<TriggerHandle>): TriggerHandle {
    val occupied = existing.map { it.topFraction to it.bottomFraction }
    val minHeight = 0.15f
    val candidates = buildList {
        add(0.08f to 0.24f)
        add(0.55f to 0.83f)
        add(0.30f to 0.68f)
        val slotCount = (existing.size + 2).coerceAtMost(16)
        val step = 0.85f / slotCount
        for (i in 0 until slotCount) {
            val top = 0.05f + i * step
            add(top to (top + step * 0.85f).coerceAtMost(0.95f))
        }
    }
    val (top, bottom) = candidates.firstOrNull { (top, bottom) ->
        (bottom - top) >= minHeight &&
            occupied.none { (otherTop, otherBottom) ->
                top < otherBottom && bottom > otherTop
            }
    } ?: run {
        val top = (0.05f + existing.size * 0.1f).coerceAtMost(0.95f - minHeight)
        top to (top + minHeight)
    }
    return TriggerHandle(
        id = TriggerHandle.newId(),
        topFraction = top,
        heightFraction = (bottom - top).coerceAtLeast(minHeight),
        shortSwipeDistanceDp = existing.lastOrNull()?.shortSwipeDistanceDp
            ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP,
        longSwipeDistanceDp = existing.lastOrNull()?.longSwipeDistanceDp
            ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP,
    )
}
