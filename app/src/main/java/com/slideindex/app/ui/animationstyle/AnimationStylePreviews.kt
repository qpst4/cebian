package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.GestureHintStyle

@Composable
fun AnimationStylePreview(style: GestureHintStyle, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AnimationStylePreviewStage {
            when (style) {
                GestureHintStyle.WAVE -> WaveStylePreview()
                GestureHintStyle.CAPSULE -> CapsuleStylePreview()
                GestureHintStyle.BUBBLE -> BubbleStylePreview()
            }
        }
    }
}

@Composable
private fun BoxScope.WaveStylePreview() {
    val colorScheme = MaterialTheme.colorScheme
    val iconPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = Path()
        val width = size.width
        val height = size.height
        val top = height * 0.02f
        val bottom = height * 0.98f
        val centerY = height / 2f
        val neckX = width * 0.06f
        val peakX = width * 0.4f

        path.moveTo(0f, 0f)
        path.lineTo(neckX, top)
        path.cubicTo(
            x1 = neckX,
            y1 = height * 0.22f,
            x2 = peakX,
            y2 = height * 0.26f,
            x3 = peakX,
            y3 = centerY,
        )
        path.cubicTo(
            x1 = peakX,
            y1 = height * 0.74f,
            x2 = neckX,
            y2 = height * 0.78f,
            x3 = neckX,
            y3 = bottom,
        )
        path.lineTo(0f, height)
        path.close()

        drawPath(path = path, color = colorScheme.primary)
    }

    Image(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 6.dp)
            .size(18.dp),
        painter = iconPainter,
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorScheme.onPrimary),
    )
}

@Composable
private fun CapsuleStylePreview() {
    val colorScheme = MaterialTheme.colorScheme
    val iconPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val thickness = 26.dp.toPx()
        val capsuleWidth = 48.dp.toPx()
        val cornerRadius = 13.dp.toPx()
        val startX = -capsuleWidth * 0.20f
        val top = size.height / 2f - thickness / 2f
        val center = Offset(startX + capsuleWidth / 2f, top + thickness / 2f)

        drawRoundRect(
            color = colorScheme.primary,
            topLeft = Offset(startX, top),
            size = Size(capsuleWidth, thickness),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )

        val iconSize = 15.dp.toPx()
        rotate(0f, pivot = center) {
            translate(left = center.x, top = center.y - iconSize / 2f) {
                drawPreviewIcon(
                    painter = iconPainter,
                    iconSize = iconSize,
                    tint = colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.BubbleStylePreview() {
    val colorScheme = MaterialTheme.colorScheme
    val iconPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val diameter = 34.dp.toPx()
        val radius = diameter / 2f
        val center = Offset(x = radius * 0.65f, y = size.height / 2f)

        drawCircle(
            color = colorScheme.primary,
            radius = radius,
            center = center,
        )
    }

    Image(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 4.dp)
            .size(18.dp),
        painter = iconPainter,
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorScheme.onPrimary),
    )
}

private fun DrawScope.drawPreviewIcon(
    painter: Painter,
    iconSize: Float,
    tint: Color,
) {
    with(painter) {
        draw(
            size = Size(iconSize, iconSize),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
