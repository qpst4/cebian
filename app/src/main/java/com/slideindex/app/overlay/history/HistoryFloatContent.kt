@file:OptIn(ExperimentalFoundationApi::class)

package com.slideindex.app.overlay.history

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 剪贴板历史边缘把手：仅作为入口，点击/左滑打开 [com.slideindex.app.overlay.FloatBallStashPanel]。
 */
@Composable
fun HistoryFloatContent(
    handleVisible: Boolean,
    handleWidth: Int,
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
) {
    OverlayAwareModuleTheme {
        if (handleVisible) {
            HistoryFloatHandle(
                onOpenPanel = onOpenPanel,
                onMoveHandle = onMoveHandle,
                handleWidth = handleWidth,
            )
        }
    }
}

@Composable
private fun HistoryFloatHandle(
    onOpenPanel: () -> Unit,
    onMoveHandle: (Float) -> Unit,
    handleWidth: Int = 32,
) {
    var active by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val handleWidthAnim by animateDpAsState(
        targetValue = if (active) (handleWidth + 16).dp else handleWidth.dp,
        label = "handleWidth",
    )
    val handleHeight by animateDpAsState(
        targetValue = if (active) 108.dp else 96.dp,
        label = "handleHeight",
    )
    val handleAlpha by animateFloatAsState(
        targetValue = if (active) 0.22f else 0.09f,
        label = "handleAlpha",
    )
    val scheme = MiuixTheme.colorScheme

    Box(
        modifier = Modifier
            .width(handleWidthAnim)
            .height(handleHeight)
            .pointerInput(Unit) {
                var totalX = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        active = true
                    },
                    onDragEnd = {
                        if (totalX < -20f) {
                            onOpenPanel()
                        }
                        scope.launch {
                            delay(500)
                            active = false
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    onMoveHandle(dragAmount.y)
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = {
                    active = true
                    scope.launch {
                        delay(500)
                        active = false
                    }
                    onOpenPanel()
                },
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                .width(handleWidthAnim)
                .height(handleHeight),
            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
            color = scheme.surface.copy(alpha = handleAlpha),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                scheme.onSurface.copy(alpha = if (active) 0.24f else 0.10f),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.onSurface.copy(alpha = if (active) 0.035f else 0.015f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 4.dp, height = 24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scheme.onSurface.copy(alpha = if (active) 0.42f else 0.22f)),
                )
            }
        }
    }
}
