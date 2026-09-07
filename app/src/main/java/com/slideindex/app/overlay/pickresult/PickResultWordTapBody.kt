package com.slideindex.app.overlay.pickresult

import com.slideindex.app.ui.theme.LocalAppDarkTheme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shorter than system long-press for quicker word-split feedback. */
private const val WORD_SPLIT_LONG_PRESS_MS = 280L

/** FlowRow 分组 token 数，避免单块过大影响测量。 */
private const val WORD_TAP_ROW_CHUNK_SIZE = 40

/** 划选时接近上下边缘触发自动滚动的区域。 */
private val WORD_DRAG_EDGE_ZONE = 28.dp

/** 划选边缘自动滚动每步距离。 */
private val WORD_DRAG_EDGE_SCROLL_STEP = 14.dp

private data class WordTapChunk(
    val startIndex: Int,
    val tokens: List<String>,
)

private data class WordTapLine(
    val startIndex: Int,
    val endIndex: Int,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
)

private fun buildWordTapLines(
    chipBounds: Array<Rect?>,
    tokenCount: Int,
): List<WordTapLine> {
    val lines = mutableListOf<WordTapLine>()
    var lineStart = -1
    var lineEnd = -1
    var lineTop = Float.MAX_VALUE
    var lineBottom = Float.MIN_VALUE
    var lineLeft = Float.MAX_VALUE
    var lineRight = Float.MIN_VALUE

    for (i in 0 until tokenCount) {
        val rect = chipBounds.getOrNull(i) ?: continue
        if (lineStart == -1) {
            lineStart = i
            lineEnd = i
            lineTop = rect.top
            lineBottom = rect.bottom
            lineLeft = rect.left
            lineRight = rect.right
        } else {
            val prevRect = chipBounds[lineEnd] ?: continue
            val isNewLine = rect.left < prevRect.left || rect.top >= prevRect.bottom - 2f
            if (isNewLine) {
                lines.add(
                    WordTapLine(
                        startIndex = lineStart,
                        endIndex = lineEnd,
                        top = lineTop,
                        bottom = lineBottom,
                        left = lineLeft,
                        right = lineRight,
                    ),
                )
                lineStart = i
                lineEnd = i
                lineTop = rect.top
                lineBottom = rect.bottom
                lineLeft = rect.left
                lineRight = rect.right
            } else {
                lineEnd = i
                lineTop = min(lineTop, rect.top)
                lineBottom = max(lineBottom, rect.bottom)
                lineLeft = min(lineLeft, rect.left)
                lineRight = max(lineRight, rect.right)
            }
        }
    }
    if (lineStart != -1) {
        lines.add(
            WordTapLine(
                startIndex = lineStart,
                endIndex = lineEnd,
                top = lineTop,
                bottom = lineBottom,
                left = lineLeft,
                right = lineRight,
            ),
        )
    }
    return lines
}

private data class WordTapScrollMetrics(
    val scrollFraction: Float,
    val thumbFraction: Float,
    val scrollable: Boolean,
)

private fun computeWordTapScrollMetrics(
    scrollState: ScrollState,
    viewportHeightPx: Float,
): WordTapScrollMetrics {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0 || viewportHeightPx <= 0f) {
        return WordTapScrollMetrics(scrollFraction = 0f, thumbFraction = 1f, scrollable = false)
    }

    val totalHeight = maxScroll + viewportHeightPx
    val scrollFraction = (scrollState.value / maxScroll.toFloat()).coerceIn(0f, 1f)
    val thumbFraction = (viewportHeightPx / totalHeight).coerceIn(0.08f, 1f)
    return WordTapScrollMetrics(
        scrollFraction = scrollFraction,
        thumbFraction = thumbFraction,
        scrollable = true,
    )
}

private suspend fun scrollWordTapToFraction(scrollState: ScrollState, fraction: Float) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return
    val target = (fraction.coerceIn(0f, 1f) * maxScroll).roundToInt()
    scrollState.scrollTo(target)
}

private fun scrollWordTapForDragEdge(
    scrollState: ScrollState,
    gestureScope: CoroutineScope,
    pointerYInGesture: Float,
    viewportHeightPx: Float,
    edgeZonePx: Float,
    scrollStepPx: Float,
) {
    if (viewportHeightPx <= 0f || edgeZonePx <= 0f) return

    when {
        pointerYInGesture >= viewportHeightPx - edgeZonePx -> {
            if (!scrollState.canScrollForward) return
            val beyond = (pointerYInGesture - (viewportHeightPx - edgeZonePx)).coerceAtLeast(0f)
            val step = scrollStepPx * (1f + beyond / edgeZonePx)
            gestureScope.launch {
                scrollState.scroll { scrollBy(step) }
            }
        }
        pointerYInGesture <= edgeZonePx -> {
            if (!scrollState.canScrollBackward) return
            val beyond = (edgeZonePx - pointerYInGesture).coerceAtLeast(0f)
            val step = scrollStepPx * (1f + beyond / edgeZonePx)
            gestureScope.launch {
                scrollState.scroll { scrollBy(-step) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickResultWordTapBody(
    wordTokens: List<String>,
    selectedWordIndices: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    onWordLongPress: (Int) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 15f,
    fillAvailableHeight: Boolean = false,
) {
    val bodyTextSize = textSizeSp.sp
    val delimiterTextSize = (textSizeSp * 13f / 15f).sp
    val bodyLineHeight = (textSizeSp * 20f / 15f).sp
    val chunks = remember(wordTokens) {
        wordTokens.chunked(WORD_TAP_ROW_CHUNK_SIZE).mapIndexed { chunkIndex, tokens ->
            WordTapChunk(
                startIndex = chunkIndex * WORD_TAP_ROW_CHUNK_SIZE,
                tokens = tokens,
            )
        }
    }
    val chipBounds = remember(wordTokens) { arrayOfNulls<Rect>(wordTokens.size) }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gestureCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val edgeZonePx = with(density) { WORD_DRAG_EDGE_ZONE.toPx() }
    val edgeScrollStepPx = with(density) { WORD_DRAG_EDGE_SCROLL_STEP.toPx() }
    val gestureScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val scrollMetrics by remember {
        derivedStateOf { computeWordTapScrollMetrics(scrollState, viewportHeightPx) }
    }
    val currentSelectedIndices by rememberUpdatedState(selectedWordIndices)
    val currentOnWordLongPress by rememberUpdatedState(onWordLongPress)

    fun recordChipBounds(index: Int, coordinates: LayoutCoordinates) {
        val box = containerCoordinates ?: return
        if (!box.isAttached || !coordinates.isAttached) return
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

    fun pointerInGestureSpace(pointerInContainer: Offset): Offset? {
        val container = containerCoordinates ?: return null
        val gesture = gestureCoordinates ?: return null
        if (!container.isAttached || !gesture.isAttached) return null
        return gesture.localPositionOf(container, pointerInContainer)
    }

    fun indexAt(pointerInGesture: Offset, allowLineProjection: Boolean = false): Int? {
        val box = containerCoordinates ?: return null
        val gesture = gestureCoordinates ?: return null
        if (!box.isAttached || !gesture.isAttached) return null
        val pointerInBox = box.localPositionOf(gesture, pointerInGesture)
        var bestIndex: Int? = null
        var bestArea = Float.MAX_VALUE
        for (index in wordTokens.indices) {
            val rect = chipBounds[index] ?: continue
            if (!rect.contains(pointerInBox)) continue
            val area = rect.width * rect.height
            if (area < bestArea) {
                bestArea = area
                bestIndex = index
            }
        }
        if (bestIndex != null || !allowLineProjection) return bestIndex

        val lines = buildWordTapLines(chipBounds, wordTokens.size)
        if (lines.isEmpty()) return null

        if (pointerInBox.y < lines.first().top) {
            return lines.first().startIndex
        }
        if (pointerInBox.y > lines.last().bottom) {
            return lines.last().endIndex
        }

        val targetLine = if (lines.size == 1) {
            lines.first()
        } else {
            var matched: WordTapLine? = null
            for (i in lines.indices) {
                val line = lines[i]
                val prevBottom = if (i > 0) lines[i - 1].bottom else line.top
                val nextTop = if (i < lines.lastIndex) lines[i + 1].top else line.bottom
                val topBoundary = (line.top + prevBottom) / 2f
                val bottomBoundary = (line.bottom + nextTop) / 2f
                if (pointerInBox.y in topBoundary..bottomBoundary) {
                    matched = line
                    break
                }
            }
            matched ?: lines.minByOrNull { line ->
                when {
                    pointerInBox.y < line.top -> line.top - pointerInBox.y
                    pointerInBox.y > line.bottom -> pointerInBox.y - line.bottom
                    else -> 0f
                }
            } ?: lines.last()
        }

        if (pointerInBox.x >= targetLine.right) {
            return targetLine.endIndex
        }
        if (pointerInBox.x <= targetLine.left) {
            return targetLine.startIndex
        }

        var closestIndex = targetLine.startIndex
        var minDistance = Float.MAX_VALUE
        for (idx in targetLine.startIndex..targetLine.endIndex) {
            val rect = chipBounds[idx] ?: continue
            val dist = when {
                pointerInBox.x < rect.left -> rect.left - pointerInBox.x
                pointerInBox.x > rect.right -> pointerInBox.x - rect.right
                else -> 0f
            }
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = idx
            }
        }
        return closestIndex
    }

    fun indexAtInContainer(pointerInContainer: Offset, allowLineProjection: Boolean = false): Int? {
        val pointerInGesture = pointerInGestureSpace(pointerInContainer) ?: return null
        return indexAt(pointerInGesture, allowLineProjection)
    }

    fun rangeIndices(anchor: Int, current: Int): Set<Int> {
        val start = min(anchor, current)
        val end = max(anchor, current)
        return (start..end).toSet()
    }

    Box(
        modifier = modifier.then(
            if (fillAvailableHeight) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.heightIn(max = maxHeight)
            },
        )
            .onGloballyPositioned { containerCoordinates = it }
            .pointerInput(wordTokens, touchSlop) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val startIndex = indexAtInContainer(down.position, allowLineProjection = false) ?: return@awaitEachGesture

                    val baseline = currentSelectedIndices
                    val selecting = startIndex !in baseline
                    val scrollAtDown = scrollState.value
                    var accumulated = Offset.Zero
                    var wordDragArmed = false
                    var longPressTriggered = false
                    var scrollGestureStarted = false
                    var lastRangeIndex = startIndex

                    val longPressJob = gestureScope.launch {
                        delay(WORD_SPLIT_LONG_PRESS_MS)
                        if (!wordDragArmed && !scrollGestureStarted &&
                            accumulated.getDistance() < touchSlop
                        ) {
                            longPressTriggered = true
                            currentOnWordLongPress(startIndex)
                        }
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            if (scrollState.value != scrollAtDown) {
                                scrollGestureStarted = true
                                longPressJob.cancel()
                            }

                            val delta = change.positionChange()
                            accumulated += delta

                            if (!wordDragArmed && !longPressTriggered) {
                                val horizontalIntent =
                                    abs(accumulated.x) > abs(accumulated.y) &&
                                        abs(accumulated.x) > touchSlop
                                val verticalIntent =
                                    abs(accumulated.y) > abs(accumulated.x) &&
                                        abs(accumulated.y) > touchSlop
                                when {
                                    horizontalIntent -> {
                                        wordDragArmed = true
                                        longPressJob.cancel()
                                    }
                                    verticalIntent -> {
                                        scrollGestureStarted = true
                                        longPressJob.cancel()
                                        return@awaitEachGesture
                                    }
                                }
                            }

                            if (wordDragArmed) {
                                val pointerInGesture =
                                    pointerInGestureSpace(change.position) ?: continue
                                scrollWordTapForDragEdge(
                                    scrollState = scrollState,
                                    gestureScope = gestureScope,
                                    pointerYInGesture = pointerInGesture.y,
                                    viewportHeightPx = viewportHeightPx,
                                    edgeZonePx = edgeZonePx,
                                    scrollStepPx = edgeScrollStepPx,
                                )
                                val currentIndex =
                                    indexAtInContainer(change.position, allowLineProjection = true) ?: lastRangeIndex
                                lastRangeIndex = currentIndex
                                val range = rangeIndices(startIndex, currentIndex)
                                onSelectionChange(
                                    if (selecting) baseline + range else baseline - range,
                                )
                                change.consume()
                            }
                        }
                    } finally {
                        longPressJob.cancel()
                    }

                    val didScroll = scrollState.value != scrollAtDown
                    val isTap = accumulated.getDistance() < touchSlop
                    if (!wordDragArmed && !longPressTriggered && !scrollGestureStarted && !didScroll && isTap) {
                        onSelectionChange(
                            if (startIndex in baseline) baseline - startIndex else baseline + startIndex,
                        )
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fillAvailableHeight) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.heightIn(max = maxHeight)
                    },
                )
                .verticalScroll(scrollState)
                .padding(
                    start = 2.dp,
                    top = 2.dp,
                    end = 2.dp,
                    bottom = PickResultWordTapBottomContentPadding + 2.dp,
                )
                .onGloballyPositioned {
                    gestureCoordinates = it
                    viewportHeightPx = it.size.height.toFloat()
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            chunks.forEach { chunk ->
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    chunk.tokens.forEachIndexed { localIndex, token ->
                        val index = chunk.startIndex + localIndex
                        WordTapTokenChip(
                            token = token,
                            selected = index in selectedWordIndices,
                            bodyTextSize = bodyTextSize,
                            delimiterTextSize = delimiterTextSize,
                            bodyLineHeight = bodyLineHeight,
                            onPositioned = { coordinates -> recordChipBounds(index, coordinates) },
                        )
                    }
                }
            }
        }

        if (scrollMetrics.scrollable) {
            PickResultWordTapScrollbar(
                metrics = scrollMetrics,
                trackHeight = maxHeight,
                onDragToFraction = { fraction ->
                    gestureScope.launch {
                        scrollWordTapToFraction(scrollState, fraction)
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 8.dp),
            )
        }
    }
}

@Composable
private fun PickResultWordTapScrollbar(
    metrics: WordTapScrollMetrics,
    trackHeight: Dp,
    onDragToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val trackTouchWidth = 12.dp
    val thumbWidth = 5.dp

    BoxWithConstraints(
        modifier = modifier
            .width(trackTouchWidth)
            .height(trackHeight)
            .pointerInput(Unit) {
                fun fractionAt(y: Float): Float =
                    (y / size.height.toFloat()).coerceIn(0f, 1f)

                detectVerticalDragGestures(
                    onDragStart = { offset -> onDragToFraction(fractionAt(offset.y)) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        onDragToFraction(fractionAt(change.position.y))
                    },
                )
            },
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        if (trackHeightPx <= 0f || !constraints.hasBoundedHeight) return@BoxWithConstraints

        val thumbHeightPx = (trackHeightPx * metrics.thumbFraction).coerceAtLeast(1f)
        val maxThumbOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = metrics.scrollFraction * maxThumbOffsetPx
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .width(thumbWidth)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(RoundedCornerShape(thumbWidth / 2))
                .background(thumbColor),
        )
    }
}

@Composable
private fun WordTapTokenChip(
    token: String,
    selected: Boolean,
    bodyTextSize: androidx.compose.ui.unit.TextUnit,
    delimiterTextSize: androidx.compose.ui.unit.TextUnit,
    bodyLineHeight: androidx.compose.ui.unit.TextUnit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    val display = token.trim().ifEmpty { token }
    val isSingleChar = display.length == 1
    val isDelimiter = PickResultWordTokenizer.isDelimiterToken(display)
    val isDark = LocalAppDarkTheme.current
    val background = if (selected) {
        if (isDark) androidx.compose.ui.graphics.Color(0xFF322F4C) else androidx.compose.ui.graphics.Color(0xFFF0EDFF)
    } else {
        if (isDark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else androidx.compose.ui.graphics.Color.White
    }
    val borderColor = if (selected) {
        if (isDark) androidx.compose.ui.graphics.Color(0xFF9BA8E6) else androidx.compose.ui.graphics.Color(0xFF8C7AE6)
    } else {
        if (isDark) androidx.compose.ui.graphics.Color(0xFF4A4A4C) else androidx.compose.ui.graphics.Color(0xFFF1F2F6)
    }
    val textColor = if (selected) {
        if (isDark) androidx.compose.ui.graphics.Color(0xFF9BA8E6) else androidx.compose.ui.graphics.Color(0xFF8C7AE6)
    } else {
        if (isDark) androidx.compose.ui.graphics.Color(0xFFD1D1D6) else androidx.compose.ui.graphics.Color(0xFF2F3542)
    }
    Text(
        text = display,
        modifier = Modifier
            .onGloballyPositioned(onPositioned)
            .shadow(elevation = if (selected) 0.dp else 1.dp, shape = RoundedCornerShape(8.dp), clip = false)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(
                horizontal = if (isSingleChar) 8.dp else 12.dp,
                vertical = if (isSingleChar) 6.dp else 8.dp,
            ),
        fontSize = if (isDelimiter) delimiterTextSize else bodyTextSize,
        lineHeight = bodyLineHeight,
        color = textColor,
    )
}
