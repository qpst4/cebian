package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings

data class TriggerHandle(
    val id: String,
    val topFraction: Float,
    val heightFraction: Float,
    val enabled: Boolean = true,
) {
    val bottomFraction: Float get() = topFraction + heightFraction

    companion object {
        const val DEFAULT_ID = "default"
        const val MAX_HANDLES = 3

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
    ).joinToString(SEP)

    fun decode(raw: String): TriggerHandle? {
        val parts = raw.split(SEP)
        if (parts.size != 4) return null
        val top = parts[1].toFloatOrNull() ?: return null
        val height = parts[2].toFloatOrNull() ?: return null
        return TriggerHandle(
            id = parts[0],
            topFraction = top,
            heightFraction = height,
            enabled = parts[3] == "1",
        )
    }

    fun encodeAll(handles: List<TriggerHandle>): Set<String> = handles.map { encode(it) }.toSet()

    fun decodeAll(raw: Set<String>): List<TriggerHandle> =
        raw.mapNotNull { decode(it) }.ifEmpty { listOf(TriggerHandle.default()) }
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
): AppSettings {
    val normalized = handles.take(TriggerHandle.MAX_HANDLES).ifEmpty { listOf(TriggerHandle.default()) }
    return when (side) {
        PanelSide.LEFT -> copy(leftTriggerHandles = normalized)
        PanelSide.RIGHT -> copy(rightTriggerHandles = normalized)
    }
}

fun AppSettings.withUpdatedTriggerHandle(
    side: PanelSide,
    handleId: String,
    topFraction: Float,
    heightFraction: Float,
): AppSettings {
    val updated = allTriggerHandles(side).map { handle ->
        if (handle.id == handleId) {
            handle.copy(topFraction = topFraction, heightFraction = heightFraction)
        } else {
            handle
        }
    }
    return withTriggerHandles(side, updated)
}

fun AppSettings.withAddedTriggerHandlePair(): AppSettings {
    if (leftTriggerHandles.size >= TriggerHandle.MAX_HANDLES) return this
    val pairId = TriggerHandle.newId()
    val leftNew = suggestNextTriggerHandle(leftTriggerHandles).copy(id = pairId)
    val rightNew = if (alignHandlesEnabled) {
        leftNew
    } else {
        suggestNextTriggerHandle(rightTriggerHandles).copy(id = pairId)
    }
    return copy(
        leftTriggerHandles = leftTriggerHandles + leftNew,
        rightTriggerHandles = rightTriggerHandles + rightNew,
    )
}

fun AppSettings.withRemovedTriggerHandlePair(handleId: String): AppSettings {
    if (leftTriggerHandles.size <= 1) return this
    val newLeft = leftTriggerHandles.filterNot { it.id == handleId }
        .ifEmpty { listOf(TriggerHandle.default()) }
    val newRight = rightTriggerHandles.filterNot { it.id == handleId }
        .ifEmpty { listOf(TriggerHandle.default()) }
    return copy(
        leftTriggerHandles = newLeft,
        rightTriggerHandles = newRight,
        gestureRules = gestureRules.filterNot { it.handleId == handleId },
    )
}

data class TriggerHandlePairEntry(
    val index: Int,
    val handleId: String,
    val left: TriggerHandle,
    val right: TriggerHandle?,
)

fun AppSettings.sideTriggerPairs(): List<TriggerHandlePairEntry> =
    leftTriggerHandles.mapIndexed { index, left ->
        val right = rightTriggerHandles.getOrNull(index)?.takeIf { it.id == left.id }
            ?: rightTriggerHandles.firstOrNull { it.id == left.id }
        TriggerHandlePairEntry(index = index, handleId = left.id, left = left, right = right)
    }

private fun suggestNextTriggerHandle(existing: List<TriggerHandle>): TriggerHandle {
    val occupied = existing.map { it.topFraction to it.bottomFraction }
    val candidates = listOf(0.08f to 0.24f, 0.55f to 0.83f, 0.30f to 0.68f)
    val (top, bottom) = candidates.firstOrNull { (top, bottom) ->
        occupied.none { (otherTop, otherBottom) ->
            top < otherBottom && bottom > otherTop
        }
    } ?: (0.55f to 0.83f)
    return TriggerHandle(
        id = TriggerHandle.newId(),
        topFraction = top,
        heightFraction = (bottom - top).coerceAtLeast(0.15f),
    )
}
