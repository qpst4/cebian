package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    var hexInput by remember(initialColor) { mutableStateOf(formatArgbHex(initialColor)) }
    var hexError by remember { mutableStateOf(false) }

    LaunchedEffect(initialColor) {
        colorController.selectByColor(Color(initialColor), fromUser = false)
        hexInput = formatArgbHex(initialColor)
        hexError = false
    }

    fun resolvedColor(): Int? {
        parseHexColor(hexInput)?.let { return it }
        return if (hexInput.isBlank()) {
            colorController.selectedColor.value.toArgb()
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
                        hexInput = formatArgbHex(colorController.selectedColor.value.toArgb())
                        hexError = false
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clip(CircleShape)
                            .background(
                                parseHexColor(hexInput)?.let { Color(it) }
                                    ?: colorController.selectedColor.value,
                            ),
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = sanitizeHexInput(raw)
                            val parsed = parseHexColor(hexInput)
                            if (parsed != null) {
                                hexError = false
                                colorController.selectByColor(Color(parsed), fromUser = true)
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

private fun formatArgbHex(argb: Int): String {
    val a = android.graphics.Color.alpha(argb)
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    return if (a == 255) {
        String.format("#%02X%02X%02X", r, g, b)
    } else {
        String.format("#%02X%02X%02X%02X", a, r, g, b)
    }
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
