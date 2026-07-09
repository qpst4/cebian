package com.slideindex.app.settings

import com.slideindex.app.overlay.animation.GestureAnimationPosition

fun AppSettings.resolvedHapticStrength(): HapticStrength = HapticStrength.fromLevel(hapticStrengthLevel)

fun AppSettings.gestureHintStyle(): GestureHintStyle = GestureHintStyle.fromId(gestureHintStyleId)

fun GestureHintStyle.toAnimationType(): Int? = when (this) {
    GestureHintStyle.WAVE -> AnimationStyles.TYPE_WAVE
    GestureHintStyle.CAPSULE -> AnimationStyles.TYPE_CAPSULE
    GestureHintStyle.BUBBLE -> AnimationStyles.TYPE_BUBBLE
}

fun AppSettings.activeWaveStyle(): WaveStyle = animationStyles.waveStyle
fun AppSettings.activeCapsuleStyle(): CapsuleStyle = animationStyles.capsuleStyle
fun AppSettings.activeBubbleStyle(): BubbleStyle = animationStyles.bubbleStyle
fun AppSettings.activeAnimationStyle(): AnimationStyle = when (gestureHintStyle()) {
    GestureHintStyle.WAVE -> activeWaveStyle()
    GestureHintStyle.CAPSULE -> activeCapsuleStyle()
    GestureHintStyle.BUBBLE -> activeBubbleStyle()
}

fun animationIconInitialRotation(position: GestureAnimationPosition): Float = when (position) {
    GestureAnimationPosition.Left -> 0f
    GestureAnimationPosition.Right -> 180f
    GestureAnimationPosition.Bottom -> 270f
}
