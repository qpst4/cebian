package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AnimationStyleColorPickerDialog(
    initialColor: Int,
    onDismissRequest: () -> Unit,
    onColorPicked: (Int) -> Unit,
) {
    var currentColorArgb by remember(initialColor) { mutableIntStateOf(initialColor) }
    var hexInput by remember(initialColor) { mutableStateOf(formatArgbHex(initialColor)) }
    var hexError by remember { mutableStateOf(false) }

    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.animation_style_color_picker_title),
        onConfirm = {
            val parsed = parseHexColor(hexInput)
            val finalColor = if (hexInput.isBlank()) currentColorArgb else parsed
            if (finalColor != null) {
                onColorPicked(finalColor)
                onDismissRequest()
            } else {
                hexError = true
            }
        },
        dismissOnConfirm = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ColorPalette(
                color = Color(currentColorArgb),
                onColorChanged = { newColor ->
                    val argb = newColor.toArgb()
                    currentColorArgb = argb
                    hexInput = formatArgbHex(argb)
                    hexError = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                MiuixLabeledTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        hexInput = sanitizeHexInput(raw)
                        val parsed = parseHexColor(hexInput)
                        if (parsed != null) {
                            currentColorArgb = parsed
                            hexError = false
                        } else {
                            hexError = false
                        }
                    },
                    label = stringResource(R.string.animation_style_color_hex_label),
                )
                if (hexError) {
                    MiuixText(
                        text = stringResource(R.string.animation_style_color_hex_invalid),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
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
