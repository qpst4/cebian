package com.slideindex.app.overlay

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.QuickLauncherIconResolver
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal data class HoneycombPickerState(
    val items: List<QuickLauncherItem>,
    val appsByPackage: Map<String, AppInfo>,
    val settings: AppSettings,
    val anchorX: Float,
    val anchorY: Float,
    val externalTracking: Boolean,
    val pointerX: Float,
    val pointerY: Float,
    val selectedIndex: Int,
    val onSelectionChanged: (Int) -> Unit,
    val onLaunch: (QuickLauncherItem) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun HoneycombPickerPanel(
    state: HoneycombPickerState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSizePx = with(density) { 52.dp.toPx() }
    val pitchPx = iconSizePx * 1.08f
    val centerScale = 1.12f
    val edgeScale = 0.86f
    val effectRadius = iconSizePx * 2.8f

    val layoutPoints = remember(state.items.size, pitchPx) {
        HoneycombGeometry.compactPoints(state.items.size, pitchPx)
    }

    var selectedIndex by remember(state.items, state.selectedIndex) {
        mutableIntStateOf(state.selectedIndex)
    }

    val iconCache = remember(state.items, state.appsByPackage) {
        state.items.map { item ->
            QuickLauncherIconResolver.iconBitmap(item, state.appsByPackage, context = context)
        }
    }

    fun updateSelection(pointerX: Float, pointerY: Float) {
        if (layoutPoints.isEmpty()) return
        val localX = pointerX - state.anchorX
        val localY = pointerY - state.anchorY
        val hit = HoneycombGeometry.hitScaled(
            centers = layoutPoints,
            x = localX,
            y = localY,
            iconSize = iconSizePx,
            effectCenterX = localX,
            effectCenterY = localY,
            effectRadius = effectRadius,
            centerScale = centerScale,
            edgeScale = edgeScale,
        )
        if (hit != selectedIndex) {
            selectedIndex = hit
            state.onSelectionChanged(hit)
        }
    }

    LaunchedEffect(state.pointerX, state.pointerY, state.externalTracking) {
        if (state.externalTracking) {
            updateSelection(state.pointerX, state.pointerY)
        }
    }

    val touchModifier = if (state.externalTracking) {
        Modifier
    } else {
        Modifier.pointerInput(state.items, state.anchorX, state.anchorY) {
            detectTapGestures(
                onTap = { offset -> updateSelection(offset.x, offset.y) },
                onPress = { offset ->
                    updateSelection(offset.x, offset.y)
                    tryAwaitRelease()
                    val item = state.items.getOrNull(selectedIndex) ?: return@detectTapGestures
                    state.onLaunch(item)
                },
            )
        }.pointerInput(state.items, state.anchorX, state.anchorY) {
            detectDragGestures(
                onDragStart = { offset -> updateSelection(offset.x, offset.y) },
                onDrag = { change, _ ->
                    change.consume()
                    updateSelection(change.position.x, change.position.y)
                },
                onDragEnd = {
                    val item = state.items.getOrNull(selectedIndex) ?: return@detectDragGestures
                    state.onLaunch(item)
                },
                onDragCancel = { state.onDismiss() },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().then(touchModifier)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.42f))
            if (layoutPoints.isEmpty()) return@Canvas

            layoutPoints.forEachIndexed { index, point ->
                val item = state.items.getOrNull(index) ?: return@forEachIndexed
                val centerX = state.anchorX + point.x
                val centerY = state.anchorY + point.y
                val distanceToPointer = hypot(centerX - state.pointerX, centerY - state.pointerY)
                val scale = HoneycombGeometry.smoothScale(
                    distance = distanceToPointer,
                    radius = effectRadius,
                    centerScale = centerScale,
                    edgeScale = edgeScale,
                )
                val isSelected = index == selectedIndex
                val cellScale = if (isSelected) scale * 1.08f else scale
                val radius = iconSizePx * cellScale * 0.52f
                drawHoneycombCell(
                    center = Offset(centerX, centerY),
                    radius = radius,
                    selected = isSelected,
                    icon = iconCache.getOrNull(index),
                )
            }
        }
    }
}

private fun DrawScope.drawHoneycombCell(
    center: Offset,
    radius: Float,
    selected: Boolean,
    icon: Bitmap?,
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        for (vertex in 0 until 6) {
            val angle = (Math.PI / 3.0 * vertex - Math.PI / 6.0).toFloat()
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            if (vertex == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    clipPath(path) {
        drawPath(path, Color(0xFF2A2A2E))
        icon?.let { bitmap ->
            val iconRadius = radius * 0.72f
            val dstSize = iconRadius * 2f
            val left = center.x - iconRadius
            val top = center.y - iconRadius
            drawContext.canvas.nativeCanvas.apply {
                val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                val dst = android.graphics.RectF(left, top, left + dstSize, top + dstSize)
                drawBitmap(bitmap, src, dst, null)
            }
        }
    }
    drawPath(
        path = path,
        color = if (selected) Color(0xFF8AB4FF) else Color.White.copy(alpha = 0.22f),
        style = Stroke(width = if (selected) 3f else 1.5f),
    )
}
