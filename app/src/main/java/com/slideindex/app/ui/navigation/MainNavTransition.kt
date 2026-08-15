package com.slideindex.app.ui.navigation

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastRoundToInt
import top.yukonga.miuix.kmp.nav.runtime.NavProgrammaticEasing
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

private val mainNavMotion = NavMotion(
    programmatic = NavSettleSpec.Tween(
        durationMillis = MainNavTransitionDurationMs,
        easing = NavProgrammaticEasing,
    ),
)

/**
 * 与旧 Navigation3 400ms 水平 slide 对齐的 miuix-nav 转场：几何同 [NavTransitions.MiuixDefault]，
 * programmatic 时长与底栏显隐动画一致。
 */
internal fun mainAppNavTransition(swipeDismiss: NavSwipeDirection): NavTransition =
    navGraphicsTransition(
        opaqueDepth = 1f,
        dismissDirection = swipeDismiss,
        motion = mainNavMotion,
    ) { scope ->
        val width = scope.layoutSize.width.toFloat()
        val d = scope.relativeDepth
        val rtl = scope.layoutDirection == LayoutDirection.Rtl
        if (d <= 0f) {
            translationX = ((if (rtl) -1f else 1f) * (-d).coerceIn(0f, 1f) * width).fastRoundToInt().toFloat()
        } else {
            val cover = d.coerceIn(0f, 1f)
            translationX = (if (rtl) 1f else -1f) * cover * width * 0.25f
            alpha = 1f - 0.1f * cover
        }
    }
