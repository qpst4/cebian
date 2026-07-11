package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.slideindex.app.R

@Composable
fun AnimationStyleColorPickerDialog(
    initialColor: Int,
    onDismissRequest: () -> Unit,
    onColorPicked: (Int) -> Unit,
) {
    val colorController = rememberColorPickerController()
    var alpha by remember(initialColor) { mutableIntStateOf(android.graphics.Color.alpha(initialColor)) }
    var hexInput by remember(initialColor) { mutableStateOf(formatArgbHex(initialColor)) }
    var hexError by remember { mutableStateOf(false) }

    fun combinedColor(): Int {
        val rgb = colorController.selectedColor.value.toArgb() and 0x00FFFFFF
        return (alpha shl 24) or rgb
    }

    fun applyCombinedColor(argb: Int, fromUser: Boolean) {
        alpha = android.graphics.Color.alpha(argb)
        val opaqueRgb = (argb and 0x00FFFFFF) or 0xFF000000.toInt()
        colorController.selectByColor(Color(opaqueRgb), fromUser = fromUser)
        hexInput = formatArgbHex(argb)
        hexError = false
    }

    LaunchedEffect(initialColor) {
        applyCombinedColor(initialColor, fromUser = false)
    }

    fun resolvedColor(): Int? {
        parseHexColor(hexInput)?.let { return it }
        return if (hexInput.isBlank()) {
            combinedColor()
        } else {
            null
        }
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.animation_style_color_picker_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    controller = colorController,
                    onColorChanged = {
                        hexInput = formatArgbHex(combinedColor())
                        hexError = false
                    },
                )
                AlphaSliderRow(
                    alpha = alpha,
                    rgbArgb = colorController.selectedColor.value.toArgb() and 0x00FFFFFF,
                    onAlphaChange = { newAlpha ->
                        alpha = newAlpha
                        hexInput = formatArgbHex(combinedColor())
                        hexError = false
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorPreviewSwatch(
                        color = parseHexColor(hexInput)?.let { Color(it) }
                            ?: Color(combinedColor()),
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = sanitizeHexInput(raw)
                            val parsed = parseHexColor(hexInput)
                            if (parsed != null) {
                                applyCombinedColor(parsed, fromUser = true)
                            } else {
                                hexError = false
                            }
                        },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                        label = { Text(stringResource(R.string.animation_style_color_hex_label)) },
                        placeholder = { Text(stringResource(R.string.animation_style_color_hex_hint)) },
                        singleLine = true,
                        isError = hexError,
                        supportingText = if (hexError) {
                            { Text(stringResource(R.string.animation_style_color_hex_invalid)) }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = resolvedColor()
                    if (picked != null) {
                        onColorPicked(picked)
                        onDismissRequest()
                    } else {
                        hexError = true
                    }
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ColorPreviewSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clip(CircleShape),
    ) {
        CheckerboardBackground(modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlphaSliderRow(
    alpha: Int,
    rgbArgb: Int,
    onAlphaChange: (Int) -> Unit,
) {
    val opaqueColor = Color((0xFF000000L or rgbArgb.toLong()).toInt())
    val trackHeight = 4.dp
    val trackShape = RoundedCornerShape(trackHeight / 2)
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = Color.Transparent,
        inactiveTrackColor = Color.Transparent,
        disabledActiveTrackColor = Color.Transparent,
        disabledInactiveTrackColor = Color.Transparent,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.animation_style_color_alpha_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$alpha / 255",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = alpha.toFloat(),
            onValueChange = { onAlphaChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.fillMaxWidth(),
            colors = sliderColors,
            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(trackShape),
                    ) {
                        CheckerboardBackground(modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            opaqueColor.copy(alpha = 0f),
                                            opaqueColor,
                                        ),
                                    ),
                                ),
                        )
                    }
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.fillMaxWidth(),
                        colors = sliderColors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = trackHeight / 2,
                    )
                }
            },
        )
    }
}

@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellSize = 6.dp.toPx()
        val light = Color(0xFFE0E0E0)
        val dark = Color(0xFFBDBDBD)
        val cols = (size.width / cellSize).toInt() + 1
        val rows = (size.height / cellSize).toInt() + 1
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                drawRect(
                    color = if ((row + col) % 2 == 0) light else dark,
                    topLeft = Offset(col * cellSize, row * cellSize),
                    size = Size(cellSize, cellSize),
                )
            }
        }
    }
}

private fun formatArgbHex(argb: Int): String {
    val a = android.graphics.Color.alpha(argb)
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    return String.format(java.util.Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
}

private fun sanitizeHexInput(raw: String): String {
    val withoutHash = raw.trim().removePrefix("#")
    val hex = withoutHash.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
        .take(8)
        .uppercase()
    return if (hex.isEmpty()) "" else "#$hex"
}

private fun parseHexColor(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (hex.length !in 6..8) return null
    if (!hex.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) return null
    return when (hex.length) {
        6 -> {
            val rgb = hex.toLongOrNull(16) ?: return null
            (0xFF000000L or rgb).toInt()
        }
        8 -> hex.toLongOrNull(16)?.toInt()
        else -> null
    }
}
