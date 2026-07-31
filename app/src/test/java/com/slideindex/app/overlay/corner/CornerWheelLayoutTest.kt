package com.slideindex.app.overlay.corner

import com.slideindex.app.settings.CornerGestureSettings
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class CornerWheelLayoutTest {
    private val settings = CornerGestureSettings()
    private val density = 3f
    private val anchorX = 0f
    private val anchorY = 2400f

    @Test
    fun activeLayerCount_progressiveFromActivation_startsAtInnerLayer() {
        val activationDist = 420f
        val activation = fingerAtRadius(activationDist)
        assertEquals(
            1,
            CornerWheelLayout.activeLayerCount(
                anchor = CornerAnchor.LEFT,
                anchorX = anchorX,
                anchorY = anchorY,
                fingerX = activation.first,
                fingerY = activation.second,
                settings = settings,
                density = density,
                progressive = true,
                activationRadDist = activationDist,
            ),
        )
    }

    @Test
    fun activeLayerCount_progressiveFromActivation_expandsWithOutwardSwipe() {
        val innerR = CornerWheelLayout.layerRadiusPx(settings, density, 0)
        val middleR = CornerWheelLayout.layerRadiusPx(settings, density, 1)
        val outerR = CornerWheelLayout.layerRadiusPx(settings, density, 2)
        val activationDist = middleR + 80f
        val bandInnerToMiddle = middleR - innerR
        val bandMiddleToOuter = outerR - middleR

        val atActivation = fingerAtRadius(activationDist)
        assertEquals(
            1,
            layerCountAt(atActivation, activationDist),
        )

        val revealMiddle = fingerAtRadius(activationDist + bandInnerToMiddle * 0.6f)
        assertEquals(1, layerCountAt(revealMiddle, activationDist))

        val revealOuter = fingerAtRadius(activationDist + bandInnerToMiddle + bandMiddleToOuter * 0.6f)
        assertEquals(2, layerCountAt(revealOuter, activationDist))

        val fullOuter = fingerAtRadius(activationDist + bandInnerToMiddle + bandMiddleToOuter + 120f)
        assertEquals(3, layerCountAt(fullOuter, activationDist))
    }

    private fun fingerAtRadius(radius: Float): Pair<Float, Float> {
        val radians = Math.toRadians(315.0)
        return (
            anchorX + (cos(radians) * radius).toFloat() to
                anchorY + (sin(radians) * radius).toFloat()
            )
    }

    private fun layerCountAt(finger: Pair<Float, Float>, activationDist: Float): Int =
        CornerWheelLayout.activeLayerCount(
            anchor = CornerAnchor.LEFT,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = finger.first,
            fingerY = finger.second,
            settings = settings,
            density = density,
            progressive = true,
            activationRadDist = activationDist,
        )
}
