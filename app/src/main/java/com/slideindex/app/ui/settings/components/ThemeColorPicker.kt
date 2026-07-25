@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.pickerSegmentedShapes
import com.slideindex.app.ui.settingsSegmentedColors

@Composable
fun SettingsCardScope.ThemeColorPicker(
    selected: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit,
) {
    val colors = listOf(
        0xFF6750A4.toInt(),
        0xFF0061A4.toInt(),
        0xFF386A20.toInt(),
        0xFF984061.toInt(),
        0xFF7D5260.toInt(),
        0xFF006874.toInt(),
    )
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        segment(key = "theme_color") { position ->
            SegmentedListItem(
                onClick = {},
                enabled = enabled,
                shapes = pickerSegmentedShapes(position.index, position.count),
                colors = settingsSegmentedColors(),
                content = {
                    Text(
                        stringResource(R.string.theme_color),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colors.forEach { color ->
                            val isSelected = color == selected
                            val swatchShape = if (isSelected) {
                                MaterialShapes.Cookie9Sided.toShape()
                            } else {
                                CircleShape
                            }
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(swatchShape)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, swatchShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable(enabled = enabled) { onColorSelected(color) },
                                shape = swatchShape,
                                color = Color(color),
                            ) {}
                        }
                    }
                },
            )
        }
    }
}
