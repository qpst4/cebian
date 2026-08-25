package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * 与 miuix-nav [top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler] 共用
 * [androidx.navigationevent] 分发链；勿在 NavDisplay 子页使用 [androidx.activity.compose.BackHandler]，
 * 否则会吞掉系统预测性返回的 progress 事件。
 */
@Composable
fun MiuixNavigationBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
