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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.launch


@Composable
internal fun FloatingPointerDisplay(
    session: FloatingPointerSession,
    settings: AppSettings,
    visible: Boolean,
) {
    SlideIndexTheme(
        seedColor = Color(settings.themeColorArgb),
        dynamicColor = settings.dynamicColorEnabled,
    ) {
        val presence by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = FLOATING_POINTER_PRESENCE_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing,
            ),
            label = "floatingPointerPresence",
        )
        if (presence <= 0.001f && !visible) return@SlideIndexTheme

        val pointerX by session.pointerX
        val pointerY by session.pointerY
        val joystickX by session.joystickCenterX
        val joystickY by session.joystickCenterY
        val joystickActive by session.joystickActive
        val pointerVisible by session.pointerVisible
        val radialMenuActive by session.radialMenuActive
        val radialHighlightedSlot by session.radialHighlightedSlot
        val rippleGeneration by session.rippleGeneration
        val radialMenuAlwaysShown = settings.floatingPointerRadialMenuEnabled &&
            settings.floatingPointerRadialAlwaysVisible &&
            !session.awaitingPlacement
        val radialMenuTargetProgress = when {
            radialMenuActive -> 1f
            radialMenuAlwaysShown -> 1f
            else -> 0f
        }
        val radialMenuProgress by animateFloatAsState(
            targetValue = radialMenuTargetProgress,
            animationSpec = tween(
                durationMillis = FLOATING_POINTER_RADIAL_MENU_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing,
            ),
            label = "radialMenuProgress",
        )
        val showPointer = (!settings.floatingPointerHideWhenJoystickReleased || pointerVisible) &&
            !session.awaitingPlacement
        val pointerDesign = FloatingPointerDesign.fromId(settings.floatingPointerDesignId)
        val context = LocalContext.current
        val pointerBitmap = rememberFloatingPointerDesignBitmap(
            context = context,
            design = pointerDesign,
            sizePx = settings.floatingPointerPointerDiameterPx.roundToInt().coerceAtLeast(1),
        )
        val presenceScale = 0.72f + 0.28f * presence
        val rippleProgress = remember { Animatable(0f) }
        val pointerSizeScale = remember { Animatable(if (showPointer) 1f else 0f) }
        val pointerDrawAlpha = remember { Animatable(if (showPointer) 1f else 0f) }
        val pointerClickAnim = remember { Animatable(0f) }
        var animationTick by remember { mutableLongStateOf(0L) }

        LaunchedEffect(showPointer) {
            if (showPointer) {
                launch {
                    pointerDrawAlpha.snapTo(0f)
                    pointerDrawAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                launch {
                    pointerSizeScale.snapTo(0f)
                    pointerSizeScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.42f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            } else {
                launch {
                    pointerDrawAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearOutSlowInEasing,
                        ),
                    )
                }
                launch {
                    pointerSizeScale.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
        }

        val pointerClickGeneration = session.pointerClickGeneration.intValue
        LaunchedEffect(pointerClickGeneration, settings.floatingPointerRippleDurationMs) {
            if (pointerClickGeneration <= 0) return@LaunchedEffect
            val generation = pointerClickGeneration
            pointerClickAnim.snapTo(0f)
            pointerClickAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = floatingPointerClickAnimDurationMs(settings, session.density),
                    easing = QcPointerClickEasing,
                ),
            )
            if (session.pointerClickGeneration.intValue == generation) {
                pointerClickAnim.snapTo(0f)
            }
        }

        LaunchedEffect(rippleGeneration, settings.floatingPointerRippleDurationMs) {
            if (rippleGeneration <= 0) return@LaunchedEffect
            val generation = rippleGeneration
            rippleProgress.snapTo(0f)
            try {
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = settings.floatingPointerRippleDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            } finally {
                if (session.rippleGeneration.intValue == generation) {
                    session.rippleActive.value = false
                    rippleProgress.snapTo(0f)
                }
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { frameTime ->
                    val now = System.currentTimeMillis()
                    session.clearRippleIfExpired(now, settings.floatingPointerRippleDurationMs.toLong())
                    session.pruneExpiredTrailPoints(now)
                    if (session.hasActiveTrail(now) ||
                        rippleProgress.isRunning ||
                        rippleProgress.value > 0.001f ||
                        pointerSizeScale.isRunning ||
                        pointerDrawAlpha.isRunning ||
                        pointerClickAnim.isRunning ||
                        pointerClickAnim.value > 0.001f ||
                        pointerDrawAlpha.value > 0.001f && !showPointer ||
                        presence < 0.999f ||
                        radialMenuProgress < 0.999f
                    ) {
                        animationTick = frameTime
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val anchorX = if (!session.awaitingPlacement) joystickX else pointerX
                    val anchorY = if (!session.awaitingPlacement) joystickY else pointerY
                    val width = size.width.coerceAtLeast(1f)
                    val height = size.height.coerceAtLeast(1f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (anchorX / width).coerceIn(0f, 1f),
                        pivotFractionY = (anchorY / height).coerceIn(0f, 1f),
                    )
                    scaleX = presenceScale
                    scaleY = presenceScale
                    alpha = presence
                },
        ) {
            // Subscribe to per-frame invalidation while trail, ripple, or presence is animating.
            animationTick
            Canvas(Modifier.fillMaxSize()) {
                val now = System.currentTimeMillis()
                drawFloatingPointerTrail(session.trailPoints, settings, now)
                if (settings.floatingPointerClickVisualFeedbackEnabled && rippleProgress.value > 0.001f) {
                    val rippleSizePx = settings.floatingPointerRippleSizeDp * density
                    drawFloatingPointerRipple(
                        center = Offset(session.rippleCenterX.floatValue, session.rippleCenterY.floatValue),
                        progress = rippleProgress.value,
                        rippleColor = Color(settings.floatingPointerRippleColorArgb),
                        rippleSizePx = rippleSizePx,
                    )
                }
                if (showPointer || pointerDrawAlpha.value > 0.001f) {
                    drawFloatingPointer(
                        center = Offset(pointerX, pointerY),
                        settings = settings,
                        design = pointerDesign,
                        bitmap = pointerBitmap,
                        visibilityAlpha = pointerDrawAlpha.value,
                        sizeScale = pointerSizeScale.value,
                        clickProgress = if (settings.floatingPointerClickVisualFeedbackEnabled) {
                            pointerClickAnim.value
                        } else {
                            0f
                        },
                    )
                }
                if (!session.awaitingPlacement || joystickActive || radialMenuActive) {
                    drawQcJoystickDisc(
                        center = Offset(joystickX, joystickY),
                        radiusPx = session.joystickRadiusPx(),
                        innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
                        outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
                        gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
                        pressed = joystickActive && !radialMenuActive,
                    )
                }
                if (radialMenuProgress > 0.01f && settings.floatingPointerRadialMenuEnabled) {
                    val radialCenter = if (radialMenuActive) {
                        Offset(session.radialMenuCenterX, session.radialMenuCenterY)
                    } else {
                        Offset(joystickX, joystickY)
                    }
                    val highlightedSlot = if (radialMenuActive) radialHighlightedSlot else -1
                    val radialScale = 0.68f + 0.32f * radialMenuProgress
                    withTransform({
                        translate(radialCenter.x, radialCenter.y)
                        scale(radialScale, radialScale, pivot = Offset.Zero)
                        translate(-radialCenter.x, -radialCenter.y)
                    }) {
                        drawFloatingPointerRadialMenu(
                            center = radialCenter,
                            settings = settings,
                            slots = settings.floatingPointerRadialSlotActions,
                            highlightedSlot = highlightedSlot,
                            visibilityProgress = radialMenuProgress,
                        )
                    }
                }
            }
        }
    }
}
