@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.drawFloatingPointer
import com.slideindex.app.overlay.drawQcRingPointer
import com.slideindex.app.overlay.rememberFloatingPointerDesignBitmap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
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

@Composable
fun PointerDesignPickerDialog(
    settings: AppSettings,
    selected: FloatingPointerDesign,
    onDismiss: () -> Unit,
    onSelect: (FloatingPointerDesign) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PointerDesignThumbnail(
                    design = selected,
                    settings = settings,
                    selected = true,
                )
                Text(stringResource(R.string.floating_pointer_design_section))
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(FloatingPointerDesign.entries, key = { it.id }) { design ->
                    PointerDesignDialogRow(
                        design = design,
                        settings = settings,
                        selected = design == selected,
                        onClick = { onSelect(design) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun PointerDesignDialogRow(
    design: FloatingPointerDesign,
    settings: AppSettings,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PointerDesignThumbnail(
            design = design,
            settings = settings,
            selected = selected,
        )
        Text(
            text = stringResource(design.labelResId),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
fun SettingsCardScope.PointerDesignPickerRow(
    design: FloatingPointerDesign,
    settings: AppSettings,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCardRow(key = design) { position ->
        Md3PickerListRow(
            segmentIndex = position.index,
            segmentCount = position.count,
            title = stringResource(design.labelResId),
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            leadingContent = {
                PointerDesignThumbnail(
                    design = design,
                    settings = settings,
                    selected = selected,
                )
            },
            trailingMode = PickerTrailingMode.Radio,
        )
    }
}
