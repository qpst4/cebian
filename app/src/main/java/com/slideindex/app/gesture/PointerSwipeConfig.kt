package com.slideindex.app.gesture

enum class PointerSwipeDirection(val id: Int) {
    LEFT(0),
    UP(1),
    RIGHT(2),
    DOWN(3),
    ;

    companion object {
        fun fromId(id: Int): PointerSwipeDirection =
            entries.firstOrNull { it.id == id } ?: LEFT
    }
}

enum class PointerSwipeDistance(val id: Int) {
    SHORT(0),
    MEDIUM(1),
    LONG(2),
    ;

    fun distancePx(density: Float = 1f): Float = when (this) {
        SHORT -> 150f * density
        MEDIUM -> 280f * density
        LONG -> 420f * density
    }

    companion object {
        fun fromId(id: Int): PointerSwipeDistance =
            entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

data class PointerSwipeConfig(
    val direction: PointerSwipeDirection = PointerSwipeDirection.LEFT,
    val distance: PointerSwipeDistance = PointerSwipeDistance.MEDIUM,
    val durationMs: Int = 80,
    val pointerCount: Int = 1,
) {
    fun delta(density: Float = 1f): Pair<Float, Float> {
        val amount = distance.distancePx(density)
        return when (direction) {
            PointerSwipeDirection.LEFT -> -amount to 0f
            PointerSwipeDirection.RIGHT -> amount to 0f
            PointerSwipeDirection.UP -> 0f to -amount
            PointerSwipeDirection.DOWN -> 0f to amount
        }
    }

    companion object {
        val DEFAULT = PointerSwipeConfig()
    }
}

object PointerSwipeConfigCodec {
    private const val SEP = "|"

    fun encode(config: PointerSwipeConfig): String =
        listOf(
            config.direction.id,
            config.distance.id,
            config.durationMs.coerceIn(20, 800),
            config.pointerCount.coerceIn(1, 5),
        ).joinToString(SEP)

    fun decode(payload: String): PointerSwipeConfig {
        if (payload.isBlank()) return PointerSwipeConfig.DEFAULT
        val parts = payload.split(SEP)
        return PointerSwipeConfig(
            direction = PointerSwipeDirection.fromId(parts.getOrNull(0)?.toIntOrNull() ?: 0),
            distance = PointerSwipeDistance.fromId(parts.getOrNull(1)?.toIntOrNull() ?: 1),
            durationMs = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(20, 800) ?: 80,
            pointerCount = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 5) ?: 1,
        )
    }
}
