package com.slideindex.app.settings

import android.graphics.Color

sealed interface AnimationStyle

data class AnimationStyles(
    val type: Int = AnimationStyleDefaults.TYPE_WAVE,
    val json: String = "",
    val jsonMap: Map<Int, String> = emptyMap(),
) {
    companion object {
        const val TYPE_WAVE = AnimationStyleDefaults.TYPE_WAVE
        const val TYPE_CAPSULE = AnimationStyleDefaults.TYPE_CAPSULE
        const val TYPE_BUBBLE = AnimationStyleDefaults.TYPE_BUBBLE
    }

    fun payloadOf(targetType: Int): String =
        jsonMap[targetType].orEmpty().ifEmpty { if (targetType == type) json else "" }

    fun selectType(targetType: Int): AnimationStyles {
        val nextJsonMap = if (json.isNotEmpty() && jsonMap[type].isNullOrEmpty()) {
            jsonMap + (type to json)
        } else {
            jsonMap
        }
        return copy(
            type = targetType,
            json = nextJsonMap[targetType].orEmpty(),
            jsonMap = nextJsonMap,
        )
    }

    fun updateStyle(targetType: Int, payload: String): AnimationStyles =
        copy(
            type = targetType,
            json = payload,
            jsonMap = jsonMap + (targetType to payload),
        )

    val waveStyle: WaveStyle
        get() {
            val payload = payloadOf(TYPE_WAVE)
            if (payload.isEmpty()) return WaveStyle()
            return runCatching { AnimationStyleCodec.decode(payload, WaveStyle()) }.getOrDefault(WaveStyle())
        }
    val capsuleStyle: CapsuleStyle
        get() {
            val payload = payloadOf(TYPE_CAPSULE)
            if (payload.isEmpty()) return CapsuleStyle()
            return runCatching { AnimationStyleCodec.decode(payload, CapsuleStyle()) }.getOrDefault(CapsuleStyle())
        }
    val bubbleStyle: BubbleStyle
        get() {
            val payload = payloadOf(TYPE_BUBBLE)
            if (payload.isEmpty()) return BubbleStyle()
            return runCatching { AnimationStyleCodec.decode(payload, BubbleStyle()) }.getOrDefault(BubbleStyle())
        }

    val activeStyle: AnimationStyle
        get() = when (type) {
            TYPE_CAPSULE -> capsuleStyle
            TYPE_BUBBLE -> bubbleStyle
            else -> waveStyle
        }
}

data class WaveStyle(
    val backgroundColor: Int = AnimationStyleDefaults.Wave.backgroundColor,
    val strokeColor: Int = AnimationStyleDefaults.Wave.strokeColor,
    val strokeWidth: Int = AnimationStyleDefaults.Wave.strokeWidth,
    val width: Int = AnimationStyleDefaults.Wave.width,
    val bezierLengthHalfRatio: Float = AnimationStyleDefaults.Wave.bezierLengthHalfRatio,
    val safeBounds: Boolean = AnimationStyleDefaults.Wave.safeBounds,
    val transformEnabled: Boolean = AnimationStyleDefaults.Wave.transformEnabled,
    val iconColor: Int = AnimationStyleDefaults.Wave.iconColor,
    val iconScale: Float = AnimationStyleDefaults.Wave.iconScale,
    val iconType: Int = AnimationStyleDefaults.Wave.iconType,
    val stickySlideEnabled: Boolean = AnimationStyleDefaults.Wave.stickySlideEnabled,
    val stickySlidePx: Int = AnimationStyleDefaults.Wave.stickySlidePx,
) : AnimationStyle {
    companion object {
        const val ICON_TYPE_ARROW = 1
        const val ICON_TYPE_TRIANGLE = 2
        const val ICON_TYPE_ANGLE = 3
        const val ICON_TYPE_ARROW_NEW = 4
    }
}

data class CapsuleStyle(
    val backgroundColor: Int = AnimationStyleDefaults.Capsule.backgroundColor,
    val strokeColor: Int = AnimationStyleDefaults.Capsule.strokeColor,
    val strokeWidth: Int = AnimationStyleDefaults.Capsule.strokeWidth,
    val thickness: Int = AnimationStyleDefaults.Capsule.thickness,
    val maxLength: Int = AnimationStyleDefaults.Capsule.maxLength,
    val cornerRadius: Int = AnimationStyleDefaults.Capsule.cornerRadius,
    val iconColor: Int = AnimationStyleDefaults.Capsule.iconColor,
    val iconScale: Float = AnimationStyleDefaults.Capsule.iconScale,
    val iconType: Int = AnimationStyleDefaults.Capsule.iconType,
) : AnimationStyle

data class BubbleStyle(
    val backgroundColor: Int = AnimationStyleDefaults.Bubble.backgroundColor,
    val strokeColor: Int = AnimationStyleDefaults.Bubble.strokeColor,
    val strokeWidth: Int = AnimationStyleDefaults.Bubble.strokeWidth,
    val diameter: Int = AnimationStyleDefaults.Bubble.diameter,
    val maxOffset: Int = AnimationStyleDefaults.Bubble.maxOffset,
    val iconColor: Int = AnimationStyleDefaults.Bubble.iconColor,
    val iconScale: Float = AnimationStyleDefaults.Bubble.iconScale,
    val iconType: Int = AnimationStyleDefaults.Bubble.iconType,
) : AnimationStyle

fun pxFromDp(density: Float, dp: Float): Int = (dp * density).toInt()

object AnimationStyleDefaults {
    const val TYPE_WAVE = 1
    const val TYPE_CAPSULE = 2
    const val TYPE_BUBBLE = 3

    fun waveDefaults(density: Float) = WaveStyle(
        width = pxFromDp(density, 40f),
        stickySlidePx = pxFromDp(density, 36f),
    )

    fun capsuleDefaults(density: Float) = CapsuleStyle(
        thickness = pxFromDp(density, 36f),
        maxLength = pxFromDp(density, 72f),
        cornerRadius = pxFromDp(density, 18f),
    )

    fun bubbleDefaults(density: Float) = BubbleStyle(
        diameter = pxFromDp(density, 44f),
        maxOffset = pxFromDp(density, 72f),
    )

    object Wave {
        val backgroundColor = Color.BLACK
        val strokeColor = Color.TRANSPARENT
        const val strokeWidth = 0
        var width: Int = 120
        const val bezierLengthHalfRatio = 2.5f
        const val safeBounds = true
        const val transformEnabled = true
        val iconColor = Color.argb(200, 255, 255, 255)
        const val iconScale = 0.6f
        const val iconType = WaveStyle.ICON_TYPE_ARROW
        const val stickySlideEnabled = false
        var stickySlidePx: Int = 108
    }

    object Capsule {
        val backgroundColor = Color.argb(220, 18, 18, 18)
        val strokeColor = Color.TRANSPARENT
        const val strokeWidth = 0
        var thickness: Int = 108
        var maxLength: Int = 216
        var cornerRadius: Int = 54
        val iconColor = Color.argb(220, 255, 255, 255)
        const val iconScale = 0.52f
        const val iconType = WaveStyle.ICON_TYPE_ARROW
    }

    object Bubble {
        val backgroundColor = Color.argb(220, 22, 22, 22)
        val strokeColor = Color.argb(36, 255, 255, 255)
        const val strokeWidth = 0
        var diameter: Int = 132
        var maxOffset: Int = 216
        val iconColor = Color.argb(232, 255, 255, 255)
        const val iconScale = 0.52f
        const val iconType = WaveStyle.ICON_TYPE_ARROW
    }
}

object AnimationStyleLimits {
    fun minStrokeWidthPx(density: Float) = 0
    fun maxStrokeWidthPx(density: Float) = pxFromDp(density, 5f)
    fun minWaveWidthPx(density: Float) = pxFromDp(density, 20f)
    fun maxWaveWidthPx(density: Float) = pxFromDp(density, 80f)
    const val MIN_BEZIER_LENGTH = 1.8f
    const val MAX_BEZIER_LENGTH = 4.0f
    const val MIN_ICON_SCALE = 0f
    const val MAX_ICON_SCALE = 1f
    fun minCapsuleThicknessPx(density: Float) = pxFromDp(density, 20f)
    fun maxCapsuleThicknessPx(density: Float) = pxFromDp(density, 56f)
    fun minCapsuleLengthPx(density: Float) = pxFromDp(density, 40f)
    fun maxCapsuleLengthPx(density: Float) = pxFromDp(density, 120f)
    fun minCapsuleCornerRadiusPx(density: Float) = pxFromDp(density, 8f)
    fun maxCapsuleCornerRadiusPx(density: Float) = pxFromDp(density, 32f)
    fun minBubbleDiameterPx(density: Float) = pxFromDp(density, 28f)
    fun maxBubbleDiameterPx(density: Float) = pxFromDp(density, 72f)
    fun minBubbleOffsetPx(density: Float) = pxFromDp(density, 20f)
    fun maxBubbleOffsetPx(density: Float) = pxFromDp(density, 120f)
}
