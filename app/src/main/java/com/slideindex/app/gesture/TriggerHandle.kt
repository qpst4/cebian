package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

data class TriggerHandle(
    val id: String,
    val topFraction: Float,
    val heightFraction: Float,
    val enabled: Boolean = true,
    val alignOppositeSide: Boolean = true,
    val shortSwipeDistanceDp: Float = DEFAULT_SHORT_SWIPE_DISTANCE_DP,
    val longSwipeDistanceDp: Float = DEFAULT_LONG_SWIPE_DISTANCE_DP,
) {
    val bottomFraction: Float get() = topFraction + heightFraction

    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_SHORT_SWIPE_DISTANCE_DP = 60f
        const val DEFAULT_LONG_SWIPE_DISTANCE_DP = 120f

        fun default(topFraction: Float = 0.30f, heightFraction: Float = 0.38f): TriggerHandle =
            TriggerHandle(DEFAULT_ID, topFraction, heightFraction)

        fun newId(): String = java.util.UUID.randomUUID().toString().substring(0, 8)
    }
}

object TriggerHandleCodec {
    private const val SEP = "\u001E"

    fun encode(handle: TriggerHandle): String = listOf(
        handle.id,
        handle.topFraction.toString(),
        handle.heightFraction.toString(),
        if (handle.enabled) "1" else "0",
        if (handle.alignOppositeSide) "1" else "0",
        handle.shortSwipeDistanceDp.toString(),
        handle.longSwipeDistanceDp.toString(),
    ).joinToString(SEP)

    fun decode(
        raw: String,
        defaultShortSwipeDistanceDp: Float = TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP,
        defaultLongSwipeDistanceDp: Float = TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP,
    ): TriggerHandle? {
        val parts = raw.split(SEP)
        if (parts.size !in 4..7) return null
        val top = parts[1].toFloatOrNull() ?: return null
        val height = parts[2].toFloatOrNull() ?: return null
        val short = parts.getOrNull(5)?.toFloatOrNull() ?: defaultShortSwipeDistanceDp
        val long = parts.getOrNull(6)?.toFloatOrNull() ?: defaultLongSwipeDistanceDp
        return TriggerHandle(
            id = parts[0],
            topFraction = top,
            heightFraction = height,
            enabled = parts[3] == "1",
            alignOppositeSide = parts.getOrNull(4)?.let { it == "1" } ?: true,
            shortSwipeDistanceDp = short,
            longSwipeDistanceDp = long.coerceAtLeast(short + 16f),
        )
    }

    fun encodeAll(handles: List<TriggerHandle>): Set<String> = handles.map { encode(it) }.toSet()

    fun decodeAll(
        raw: Set<String>,
        defaultShortSwipeDistanceDp: Float = TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP,
        defaultLongSwipeDistanceDp: Float = TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP,
    ): List<TriggerHandle> =
        raw.mapNotNull { decode(it, defaultShortSwipeDistanceDp, defaultLongSwipeDistanceDp) }
            .ifEmpty { listOf(TriggerHandle.default()) }
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

data class TriggerHandlePairEntry(
    val index: Int,
    val handleId: String,
    val left: TriggerHandle,
    val right: TriggerHandle?,
)

data class TriggerCollectionEntry(
    val handleId: String,
    val left: TriggerHandle?,
    val right: TriggerHandle?,
)

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
