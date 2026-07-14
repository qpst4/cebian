package com.slideindex.app.gesture

data class TriggerHandle(
    val id: String,
    val topFraction: Float,
    val heightFraction: Float,
    val enabled: Boolean = true,
    val alignOppositeSide: Boolean = true,
    val alignOppositeDesign: Boolean = true,
    val shortSwipeDistanceDp: Float = DEFAULT_SHORT_SWIPE_DISTANCE_DP,
    val longSwipeDistanceDp: Float = DEFAULT_LONG_SWIPE_DISTANCE_DP,
    val edgeWidthDp: Float = DEFAULT_EDGE_WIDTH_DP,
    val design: TriggerHandleDesign = TriggerHandleDesign(),
    val rectanglePresetState: TriggerRectanglePresetState = TriggerRectanglePresetState.Empty,
) {
    val bottomFraction: Float get() = topFraction + heightFraction

    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_SHORT_SWIPE_DISTANCE_DP = 60f
        const val DEFAULT_LONG_SWIPE_DISTANCE_DP = 120f
        const val DEFAULT_EDGE_WIDTH_DP = 20f
        const val MIN_EDGE_WIDTH_DP = 12f
        const val MAX_EDGE_WIDTH_DP = 36f

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
        TriggerHandleDesignCodec.encode(handle.design),
        TriggerRectanglePresetStateCodec.encode(handle.rectanglePresetState),
        if (handle.alignOppositeDesign) "1" else "0",
        handle.edgeWidthDp.toString(),
    ).joinToString(SEP)

    fun decode(
        raw: String,
        defaultShortSwipeDistanceDp: Float = TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP,
        defaultLongSwipeDistanceDp: Float = TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP,
    ): TriggerHandle? {
        val parts = raw.split(SEP)
        if (parts.size !in 4..11) return null
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
            design = TriggerHandleDesignCodec.decode(parts.getOrNull(7)),
            rectanglePresetState = TriggerRectanglePresetStateCodec.decode(parts.getOrNull(8)),
            alignOppositeDesign = parts.getOrNull(9)?.let { it == "1" } ?: true,
            edgeWidthDp = parts.getOrNull(10)?.toFloatOrNull()?.takeIf { it > 0f } ?: 0f,
        ).let(TriggerRectanglePresetLogic::ensureMigrated)
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
