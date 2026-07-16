package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickResultWordTapBody(
    wordTokens: List<String>,
    selectedWordIndices: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    maxHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val chipBounds = remember { mutableStateMapOf<Int, Rect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gestureCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scrollState = rememberScrollState()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentSelectedIndices by rememberUpdatedState(selectedWordIndices)

    fun indexAt(pointerInGesture: Offset): Int? {
        val box = containerCoordinates ?: return null
        val gesture = gestureCoordinates ?: return null
        if (!box.isAttached || !gesture.isAttached) return null
        val pointerInBox = box.localPositionOf(gesture, pointerInGesture)
        return chipBounds.entries.firstOrNull { (_, rect) ->
            rect.contains(pointerInBox)
        }?.key
    }

    fun rangeIndices(anchor: Int, current: Int): Set<Int> {
        val start = min(anchor, current)
        val end = max(anchor, current)
        return (start..end).toSet()
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { containerCoordinates = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .onGloballyPositioned { gestureCoordinates = it }
                .pointerInput(wordTokens, touchSlop) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val startIndex = indexAt(down.position) ?: return@awaitEachGesture

                        val baseline = currentSelectedIndices
                        val selecting = startIndex !in baseline
                        var accumulated = Offset.Zero
                        var wordDragActive = false
                        var lastRangeIndex = startIndex

                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val delta = change.positionChange()
                            accumulated += delta

                            if (!wordDragActive && accumulated.getDistance() > touchSlop) {
                                wordDragActive = true
                            }

                            if (wordDragActive) {
                                val currentIndex = indexAt(change.position) ?: lastRangeIndex
                                lastRangeIndex = currentIndex
                                val range = rangeIndices(startIndex, currentIndex)
                                onSelectionChange(
                                    if (selecting) baseline + range else baseline - range,
                                )
                                change.consume()
                            }
                        }

                        if (!wordDragActive) {
                            onSelectionChange(
                                if (startIndex in baseline) baseline - startIndex else baseline + startIndex,
                            )
                        }
                    }
                }
                .verticalScroll(scrollState),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                wordTokens.forEachIndexed { index, token ->
                    val display = token.trim().ifEmpty { token }
                    val selected = index in selectedWordIndices
                    val isSingleChar = display.length == 1
                    val isDelimiter = PickResultWordTokenizer.isDelimiterToken(display)
                    val background = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else if (isDelimiter) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                    val borderColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                    Text(
                        text = display,
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val box = containerCoordinates
                                if (box != null && box.isAttached && coordinates.isAttached) {
                                    val topLeft = box.localPositionOf(coordinates, Offset.Zero)
                                    val bottomRight = box.localPositionOf(
                                        coordinates,
                                        Offset(
                                            coordinates.size.width.toFloat(),
                                            coordinates.size.height.toFloat(),
                                        ),
                                    )
                                    chipBounds[index] = Rect(topLeft, bottomRight)
                                }
                            }
                            .clip(RoundedCornerShape(6.dp))
                            .background(background)
                            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                            .padding(
                                horizontal = if (isSingleChar) 5.dp else 8.dp,
                                vertical = if (isSingleChar) 3.dp else 4.dp,
                            ),
                        fontSize = if (isDelimiter) 13.sp else 15.sp,
                        lineHeight = 20.sp,
                        color = if (isDelimiter) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
