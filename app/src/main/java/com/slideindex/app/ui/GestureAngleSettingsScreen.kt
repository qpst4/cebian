package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.SwipeDirection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureAngleSettingsScreen(
    config: GestureAngleConfig,
    onBack: () -> Unit,
    onSave: (GestureAngleConfig) -> Unit,
) {
    var draft by remember { mutableStateOf(config.normalized()) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gesture_angle_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { draft = GestureAngleConfig.DEFAULT.normalized() },
                    ) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.gesture_angle_reset))
                    }
                    IconButton(
                        onClick = {
                            onSave(draft.normalized())
                            onBack()
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.gesture_angle_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        GestureAngleDiagram(
            config = draft,
            onConfigChange = { draft = it.normalized() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        )
    }
}

@Composable
fun GestureAngleEntryCard(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
        title = stringResource(R.string.gesture_angle_entry_title),
        subtitle = stringResource(R.string.gesture_angle_entry_desc),
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun GestureAngleDiagram(
    config: GestureAngleConfig,
    onConfigChange: (GestureAngleConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = remember(config) { config.normalized() }
    val boundaries = remember(normalized) { normalized.sectorBoundaryAngles() }
    val sectors = remember(normalized) { normalized.orderedSectorWidths() }
    val primary = MaterialTheme.colorScheme.primary
    val arcFill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    )
    val textMeasurer = rememberTextMeasurer()
    var activeBoundary by remember { mutableIntStateOf(-1) }
    val latestConfig by rememberUpdatedState(config)
    val latestBoundaries by rememberUpdatedState(boundaries)
    val latestOnConfigChange by rememberUpdatedState(onConfigChange)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val originX = 0f
        val originY = heightPx / 2f
        val radius = minOf(widthPx * 0.68f, heightPx * 0.46f)
        val handleRadius = 14f
        val geometry by rememberUpdatedState(Triple(originX, originY, radius))

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var draggingBoundary = -1
                    detectDragGestures(
                        onDragStart = { offset ->
                            val (ox, oy, r) = geometry
                            draggingBoundary = nearestBoundaryIndex(
                                position = offset,
                                boundaries = latestBoundaries,
                                originX = ox,
                                originY = oy,
                                radius = r,
                            )
                            activeBoundary = draggingBoundary
                        },
                        onDrag = { change, _ ->
                            if (draggingBoundary in 1..4) {
                                val (ox, oy, _) = geometry
                                val angle = offsetToAngle(change.position, ox, oy)
                                latestOnConfigChange(
                                    latestConfig.withMovedBoundary(draggingBoundary, angle),
                                )
                            }
                        },
                        onDragEnd = {
                            draggingBoundary = -1
                            activeBoundary = -1
                        },
                        onDragCancel = {
                            draggingBoundary = -1
                            activeBoundary = -1
                        },
                    )
                },
        ) {
            for (index in 0 until boundaries.lastIndex) {
                drawSectorWedge(
                    originX = originX,
                    originY = originY,
                    radius = radius,
                    upperBoundDeg = boundaries[index],
                    lowerBoundDeg = boundaries[index + 1],
                    color = arcFill,
                )
            }

            for (index in 1 until boundaries.lastIndex) {
                val boundary = angleToOffset(boundaries[index], originX, originY, radius)
                val isActive = index == activeBoundary
                drawLine(
                    color = primary.copy(alpha = if (isActive) 1f else 0.55f),
                    start = Offset(originX, originY),
                    end = boundary,
                    strokeWidth = if (isActive) 2.5f else 1.5f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = primary,
                    radius = if (isActive) handleRadius * 1.1f else handleRadius * 0.75f,
                    center = boundary,
                )
            }

            sectors.forEach { (direction, width) ->
                val centerAngle = normalized.sectorCenterAngles()
                    .first { it.first == direction }
                    .second
                val center = angleToOffset(centerAngle, originX, originY, radius)
                drawDirectionLabel(
                    direction = direction,
                    degrees = width,
                    anchor = center,
                    textMeasurer = textMeasurer,
                    style = labelStyle,
                    color = primary,
                )
            }
        }
    }
}

private fun angleToOffset(angleDeg: Float, originX: Float, originY: Float, radius: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        originX + radius * cos(rad).toFloat(),
        originY - radius * sin(rad).toFloat(),
    )
}

private fun offsetToAngle(offset: Offset, originX: Float, originY: Float): Float {
    val dx = offset.x - originX
    val dy = offset.y - originY
    return Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat()
}

private fun nearestBoundaryIndex(
    position: Offset,
    boundaries: List<Float>,
    originX: Float,
    originY: Float,
    radius: Float,
): Int {
    val angle = offsetToAngle(position, originX, originY)
    if (angle > 90f || angle < -90f) return -1
    val distFromOrigin = hypot(position.x - originX, position.y - originY)
    if (distFromOrigin < radius * 0.35f || distFromOrigin > radius * 1.25f) return -1

    var bestIndex = -1
    var bestDelta = Float.MAX_VALUE
    for (index in 1..4) {
        val delta = abs(angle - boundaries[index])
        if (delta < bestDelta) {
            bestDelta = delta
            bestIndex = index
        }
    }
    return if (bestDelta <= 24f) bestIndex else -1
}

private fun DrawScope.drawSectorWedge(
    originX: Float,
    originY: Float,
    radius: Float,
    upperBoundDeg: Float,
    lowerBoundDeg: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    drawArc(
        color = color,
        topLeft = Offset(originX - radius, originY - radius),
        size = Size(radius * 2f, radius * 2f),
        startAngle = -upperBoundDeg,
        sweepAngle = upperBoundDeg - lowerBoundDeg,
        useCenter = true,
    )
}

private fun DrawScope.drawDirectionLabel(
    direction: SwipeDirection,
    degrees: Float,
    anchor: Offset,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    val symbol = when (direction) {
        SwipeDirection.UP -> "↑"
        SwipeDirection.UP_RIGHT -> "↗"
        SwipeDirection.IN -> "→"
        SwipeDirection.DOWN_RIGHT -> "↘"
        SwipeDirection.DOWN -> "↓"
    }
    val label = "$symbol ${degrees.roundToInt()}"
    val layout = textMeasurer.measure(label, style)
    val offsetX = anchor.x + 18f
    val offsetY = anchor.y - layout.size.height / 2f
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(offsetX, offsetY),
        color = color,
    )
}
