package com.slideindex.app.overlay

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.HapticHelper
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

internal class FloatingPointerSession(
    val density: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    private val settingsSource: () -> AppSettings,
) {
    val tapSlopPx = 10f * density

    fun joystickDiameterPx(): Float = settingsSource().floatingPointerJoystickDiameterPx
    fun joystickRadiusPx(): Float = joystickDiameterPx() / 2f

    val pointerX = mutableFloatStateOf(screenWidth / 2f)
    val pointerY = mutableFloatStateOf(screenHeight / 2f)
    val joystickCenterX = mutableFloatStateOf(0f)
    val joystickCenterY = mutableFloatStateOf(0f)
    val joystickActive = mutableStateOf(false)
    /** When hide-on-release is enabled, stays true after tap; false only after drag release. */
    val pointerVisible = mutableStateOf(true)
    val trailPoints = mutableStateListOf<FloatingPointerTrailPoint>()
    val rippleActive = mutableStateOf(false)
    val rippleCenterX = mutableFloatStateOf(0f)
    val rippleCenterY = mutableFloatStateOf(0f)
    val rippleStartTimeMs = mutableStateOf(0L)
    val rippleGeneration = mutableIntStateOf(0)
    val pointerClickGeneration = mutableIntStateOf(0)
    val radialMenuActive = mutableStateOf(false)
    val radialHighlightedSlot = mutableIntStateOf(-1)
    var radialMenuCenterX = 0f
    var radialMenuCenterY = 0f

    fun openRadialMenu(centerX: Float, centerY: Float) {
        radialMenuCenterX = centerX
        radialMenuCenterY = centerY
        joystickCenterX.floatValue = centerX
        joystickCenterY.floatValue = centerY
        radialMenuActive.value = true
        radialHighlightedSlot.intValue = -1
    }

    fun closeRadialMenu() {
        radialMenuActive.value = false
        radialHighlightedSlot.intValue = -1
    }

    fun updateRadialHighlight(fingerX: Float, fingerY: Float, settings: AppSettings): Boolean {
        if (!radialMenuActive.value) return false
        val inner = settings.floatingPointerRadialInnerDiameterPx / 2f
        val outer = settings.floatingPointerRadialOuterDiameterPx / 2f
        val previous = radialHighlightedSlot.intValue
        val newSlot = FloatingPointerRadialMenu.sectorIndexAt(
            centerX = radialMenuCenterX,
            centerY = radialMenuCenterY,
            fingerX = fingerX,
            fingerY = fingerY,
            innerRadius = inner,
            outerRadius = outer,
        ) ?: -1
        radialHighlightedSlot.intValue = newSlot
        return newSlot >= 0 && newSlot != previous
    }

    /** True until the first touch places joystick and pointer near the finger. */
    var awaitingPlacement = false

    /** Joystick center when the gesture started; the joystick area is anchored here. */
    private var gestureCenterX = 0f
    private var gestureCenterY = 0f
    private var gestureAreaLeft = 0f
    private var gestureAreaTop = 0f
    private var gestureAreaWidth = 0f
    private var gestureAreaHeight = 0f
    private var dragFingerAnchorX = 0f
    private var dragFingerAnchorY = 0f
    private var dragPointerAnchorX = 0f
    private var dragPointerAnchorY = 0f

    fun triggerRipple(x: Float, y: Float) {
        rippleCenterX.floatValue = x
        rippleCenterY.floatValue = y
        rippleStartTimeMs.value = System.currentTimeMillis()
        rippleActive.value = true
        rippleGeneration.intValue++
    }

    fun triggerPointerClick() {
        pointerClickGeneration.intValue++
    }

    fun clearRippleIfExpired(nowMs: Long, durationMs: Long = FLOATING_POINTER_RIPPLE_DURATION_MS) {
        if (!rippleActive.value) return
        if (nowMs - rippleStartTimeMs.value >= durationMs) {
            rippleActive.value = false
        }
    }

    fun placeJoystickDefault() {
        val margin = 32f * density
        val radius = joystickRadiusPx()
        joystickCenterX.floatValue = margin + radius
        joystickCenterY.floatValue = screenHeight - margin - radius
    }

    fun placeAtTouch(rawX: Float, rawY: Float, settings: AppSettings) {
        val center = clampJoystickCenter(rawX, rawY, settings)
        joystickCenterX.floatValue = center.x
        joystickCenterY.floatValue = center.y
        gestureCenterX = center.x
        gestureCenterY = center.y
        establishGestureArea(settings)
        val pointer = FloatingPointerBounds.pointerForFingerInArea(
            fingerX = rawX,
            fingerY = rawY,
            areaLeft = gestureAreaLeft,
            areaTop = gestureAreaTop,
            areaWidth = gestureAreaWidth,
            areaHeight = gestureAreaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        pointerX.floatValue = pointer.x
        pointerY.floatValue = pointer.y
        awaitingPlacement = false
        armDrag(rawX, rawY)
    }

    /** Arms a new drag without moving the pointer or re-anchoring the joystick area. */
    fun beginGesture(rawX: Float, rawY: Float, settings: AppSettings) {
        if (awaitingPlacement || !hasEstablishedGestureArea()) {
            placeAtTouch(rawX, rawY, settings)
        } else {
            armDrag(rawX, rawY)
        }
    }

    private fun armDrag(fingerX: Float, fingerY: Float) {
        dragFingerAnchorX = fingerX
        dragFingerAnchorY = fingerY
        dragPointerAnchorX = pointerX.floatValue
        dragPointerAnchorY = pointerY.floatValue
    }

    fun hasEstablishedGestureArea(): Boolean =
        gestureAreaWidth > 0f && gestureAreaHeight > 0f

    private fun establishGestureArea(settings: AppSettings) {
        val (areaWidth, areaHeight) = FloatingPointerBounds.effectiveJoystickAreaSize(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        gestureAreaWidth = areaWidth
        gestureAreaHeight = areaHeight
        gestureAreaLeft = gestureCenterX - areaWidth / 2f
        gestureAreaTop = gestureCenterY - areaHeight / 2f
    }

    fun refreshGestureArea(settings: AppSettings) {
        if (!hasEstablishedGestureArea()) return
        establishGestureArea(settings)
    }

    private fun clampJoystickCenter(rawX: Float, rawY: Float, settings: AppSettings): Offset {
        val (areaWidth, areaHeight) = FloatingPointerBounds.effectiveJoystickAreaSize(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        return FloatingPointerBounds.clampJoystickCenter(
            rawX = rawX,
            rawY = rawY,
            joystickRadiusPx = joystickRadiusPx(),
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
        )
    }

    fun applyLayoutSettings(settings: AppSettings) {
        val next = FloatingPointerBounds.clamp(
            position = Offset(pointerX.floatValue, pointerY.floatValue),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        pointerX.floatValue = next.x
        pointerY.floatValue = next.y
        refreshGestureArea(settings)
    }

    fun clearTrail() {
        trailPoints.clear()
    }

    fun pruneExpiredTrailPoints(nowMs: Long = System.currentTimeMillis()) {
        val maxAge = settingsSource().floatingPointerTrailDurationMs.coerceAtLeast(50)
        val cutoff = nowMs - maxAge
        while (trailPoints.isNotEmpty() && trailPoints.first().timeMs < cutoff) {
            if (trailPoints.size <= 2) break
            trailPoints.removeAt(0)
        }
    }

    fun hasActiveTrail(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (trailPoints.size < 2) return false
        val maxAge = settingsSource().floatingPointerTrailDurationMs.coerceAtLeast(50)
        return trailPoints.count { nowMs - it.timeMs <= maxAge } >= 2
    }

    fun isNearPointer(rawX: Float, rawY: Float): Boolean {
        val hitRadius = settingsSource().floatingPointerPointerDiameterPx / 2f + tapSlopPx
        return kotlin.math.hypot(
            (rawX - pointerX.floatValue).toDouble(),
            (rawY - pointerY.floatValue).toDouble(),
        ) <= hitRadius
    }

    private fun recordTrail(x: Float, y: Float) {
        val settings = settingsSource()
        val trailType = com.slideindex.app.settings.FloatingPointerTrailType.fromId(
            settings.floatingPointerTrailTypeId,
        )
        FloatingPointerTrailSampler.recordPoint(
            points = trailPoints,
            x = x,
            y = y,
            nowMs = System.currentTimeMillis(),
            type = trailType,
            density = density,
        )
        val maxAge = settings.floatingPointerTrailDurationMs.coerceAtLeast(50)
        val cutoff = System.currentTimeMillis() - maxAge
        while (trailPoints.isNotEmpty() && trailPoints.first().timeMs < cutoff) {
            if (trailPoints.size <= 2) break
            trailPoints.removeAt(0)
        }
    }

    fun applyPointerFromTouch(rawX: Float, rawY: Float, @Suppress("UNUSED_PARAMETER") settings: AppSettings) {
        if (gestureAreaWidth <= 0f || gestureAreaHeight <= 0f) return
        val next = FloatingPointerBounds.pointerForFingerDeltaInArea(
            deltaX = rawX - dragFingerAnchorX,
            deltaY = rawY - dragFingerAnchorY,
            areaWidth = gestureAreaWidth,
            areaHeight = gestureAreaHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pointerAnchorX = dragPointerAnchorX,
            pointerAnchorY = dragPointerAnchorY,
        )
        pointerX.floatValue = next.x
        pointerY.floatValue = next.y
        recordTrail(next.x, next.y)
    }
}
