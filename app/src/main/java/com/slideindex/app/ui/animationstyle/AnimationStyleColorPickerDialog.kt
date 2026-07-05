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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    LaunchedEffect(initialColor) {
        colorController.selectByColor(Color(initialColor), fromUser = false)
    }
    val hexColor by remember(colorController) {
        derivedStateOf {
            val nativeColor = colorController.selectedColor.value.toArgb()
            val a = String.format("%02X", android.graphics.Color.alpha(nativeColor))
            val r = String.format("%02X", android.graphics.Color.red(nativeColor))
            val g = String.format("%02X", android.graphics.Color.green(nativeColor))
            val b = String.format("%02X", android.graphics.Color.blue(nativeColor))
            "$a$r$g$b"
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
                    onColorChanged = {},
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clip(CircleShape)
                            .background(colorController.selectedColor.value),
                    )
                    Text(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .width(120.dp),
                        text = "#$hexColor",
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 18.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onColorPicked(colorController.selectedColor.value.toArgb())
                    onDismissRequest()
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
