package com.slideindex.app.ui.picker

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

internal const val PICKER_SUB_NAV_ANIMATION_MS = 400

internal fun <T> AnimatedContentTransitionScope<T>.pickerHorizontalSlideTransition(
    forward: Boolean,
): ContentTransform {
    return if (forward) {
        slideInHorizontally(animationSpec = tween(PICKER_SUB_NAV_ANIMATION_MS)) { width -> width } togetherWith
            slideOutHorizontally(animationSpec = tween(PICKER_SUB_NAV_ANIMATION_MS)) { width -> -width }
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(animationSpec = tween(PICKER_SUB_NAV_ANIMATION_MS)) { width -> -width / 3 },
            initialContentExit = slideOutHorizontally(animationSpec = tween(PICKER_SUB_NAV_ANIMATION_MS)) { width -> width },
            targetContentZIndex = -1f,
        )
    }
}

internal fun <T> AnimatedContentTransitionScope<T>.pickerHorizontalSlideTransitionByDepth(
    depth: (T) -> Int,
): ContentTransform {
    val forward = depth(targetState) > depth(initialState)
    return pickerHorizontalSlideTransition(forward)
}
