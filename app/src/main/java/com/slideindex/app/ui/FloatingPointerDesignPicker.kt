@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.drawFloatingPointer
import com.slideindex.app.overlay.drawQcRingPointer
import com.slideindex.app.overlay.rememberFloatingPointerDesignBitmap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import kotlin.math.roundToInt

private val PointerDesignThumbnailContainerSize = 56.dp
private val PointerDesignThumbnailPointerSize = 44.dp

@Composable
fun PointerDesignThumbnail(
    design: FloatingPointerDesign,
    settings: AppSettings,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewDiameterPx = with(density) { PointerDesignThumbnailPointerSize.toPx() }
    val bitmap = rememberFloatingPointerDesignBitmap(
        context = context,
        design = design,
        sizePx = previewDiameterPx.roundToInt().coerceAtLeast(1),
    )
    val containerShape = if (selected) {
        MaterialShapes.Cookie9Sided.toShape()
    } else {
        MaterialTheme.shapes.small
    }

    Surface(
        modifier = modifier.size(PointerDesignThumbnailContainerSize),
        shape = containerShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            if (design.isRing) {
                drawQcRingPointer(
                    center = center,
                    diameterPx = previewDiameterPx.coerceAtMost(size.minDimension - 4f),
                    ringThicknessPx = previewDiameterPx * 0.14f,
                    dotDiameterPx = previewDiameterPx * 0.18f,
                    ringColor = Color(settings.floatingPointerRingColorArgb),
                    fillColor = Color(settings.floatingPointerFillColorArgb),
                    dotColor = Color(settings.floatingPointerDotColorArgb),
                )
            } else {
                drawFloatingPointer(
                    center = center,
                    settings = settings,
                    design = design,
                    bitmap = bitmap,
                    sizePx = previewDiameterPx.coerceAtMost(size.minDimension - 4f),
                )
            }
        }
    }
}
