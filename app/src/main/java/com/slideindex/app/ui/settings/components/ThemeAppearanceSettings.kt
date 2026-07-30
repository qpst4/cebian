@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.settings.ThemeSeedColors
import com.slideindex.app.ui.SettingIconContainer
import com.slideindex.app.ui.pickerSegmentedShapes
import com.slideindex.app.ui.settingsSegmentedColors
import com.slideindex.app.ui.theme.rememberSlideIndexColorScheme

@Composable
fun SettingsCardScope.ThemeAppearanceSettings(
    themeColorArgb: Int,
    dynamicColorEnabled: Boolean,
    paletteStyleId: Int,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (Int) -> Unit,
    onPaletteStyleChange: (ThemePaletteStyle) -> Unit,
) {
    val paletteStyle = ThemePaletteStyle.fromId(paletteStyleId)
    val previewScheme = rememberSlideIndexColorScheme(
        seedColor = Color(themeColorArgb),
        dynamicColor = dynamicColorEnabled,
        paletteStyle = paletteStyle,
    )
    val showManualSeedColors = !dynamicColorEnabled

    SettingsCardRow(key = "theme_appearance") { position ->
        SegmentedListItem(
            onClick = {},
            enabled = true,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = settingsSegmentedColors(),
            leadingContent = {
                SettingIconContainer {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = stringResource(R.string.theme_appearance_settings),
                    )
                }
            },
            content = {
                Text(
                    text = stringResource(R.string.theme_appearance_settings),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.theme_appearance_preview),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = previewScheme.surfaceContainerLow,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = previewScheme.surfaceContainerHighest,
                                ) {
                                    Box(
                                        modifier = Modifier.padding(10.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.theme_appearance_preview_card),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = previewScheme.onSurface,
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.size(width = 72.dp, height = 56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = previewScheme.primaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(R.string.theme_appearance_preview_accent),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = previewScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = if (dynamicColorEnabled) {
                                stringResource(R.string.theme_appearance_dynamic_hint)
                            } else {
                                stringResource(R.string.theme_appearance_manual_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.dynamic_color),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = dynamicColorEnabled,
                            onCheckedChange = onDynamicColorChange,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.theme_palette_style),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.theme_palette_style_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ThemePaletteStyle.entries.forEach { style ->
                                val selected = style == paletteStyle
                                FilterChip(
                                    selected = selected,
                                    onClick = { onPaletteStyleChange(style) },
                                    label = { Text(stringResource(paletteStyleLabelRes(style))) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                    }

                    if (showManualSeedColors) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = stringResource(R.string.theme_color),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ThemeSeedColors.presets.forEach { color ->
                                    val isSelected = color == themeColorArgb
                                    val swatchShape = if (isSelected) {
                                        MaterialShapes.Cookie9Sided.toShape()
                                    } else {
                                        CircleShape
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(swatchShape)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        2.dp,
                                                        MaterialTheme.colorScheme.primary,
                                                        swatchShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable { onThemeColorChange(color) },
                                        shape = swatchShape,
                                        color = Color(color),
                                    ) {}
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

private fun paletteStyleLabelRes(style: ThemePaletteStyle): Int = when (style) {
    ThemePaletteStyle.TONAL_SPOT -> R.string.theme_palette_tonal_spot
    ThemePaletteStyle.VIBRANT -> R.string.theme_palette_vibrant
    ThemePaletteStyle.EXPRESSIVE -> R.string.theme_palette_expressive
    ThemePaletteStyle.MONOCHROME -> R.string.theme_palette_monochrome
    ThemePaletteStyle.NEUTRAL -> R.string.theme_palette_neutral
    ThemePaletteStyle.FIDELITY -> R.string.theme_palette_fidelity
    ThemePaletteStyle.CONTENT -> R.string.theme_palette_content
    ThemePaletteStyle.RAINBOW -> R.string.theme_palette_rainbow
    ThemePaletteStyle.FRUIT_SALAD -> R.string.theme_palette_fruit_salad
}
