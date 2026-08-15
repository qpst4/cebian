package com.slideindex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.flow.first

/**
 * 导航动画完成后才返回 true，用于延迟组合重内容。
 *
 * 转场中 entry 生命周期为 STARTED；落定顶层后为 RESUMED。一旦为 true 不会变回 false。
 */
@Composable
fun rememberContentReady(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val ready = remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        if (ready.value) return@LaunchedEffect
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            withFrameNanos { }
            ready.value = true
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.currentStateFlow
            .first { it.isAtLeast(Lifecycle.State.RESUMED) }
        withFrameNanos { }
        ready.value = true
    }

    return ready.value
}
