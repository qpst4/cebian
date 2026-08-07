package com.slideindex.app.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/**
 * 推迟一帧再执行 [onClick]，让 [ArrowPreference] / [BasicComponent] 的按压态先绘制，
 * 避免与 Nav 转场同帧卸掉 Hub 列表行。
 */
@Composable
internal fun rememberDeferredNavigationClick(onClick: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(onClick) {
        {
            scope.launch {
                awaitFrame()
                onClick()
            }
        }
    }
}
