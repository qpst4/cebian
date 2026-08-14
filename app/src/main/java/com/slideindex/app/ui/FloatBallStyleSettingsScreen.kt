@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallStyleSettingsScreen(
    settings: AppSettings,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleTypeChange: (FloatBallStyleType) -> Unit,
    onCustomImageUriChange: (String) -> Unit,
    onSlideshowUrisChange: (List<String>) -> Unit,
    onGifUriChange: (String) -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { onCustomImageUriChange(it.toString()) }
    }
    val slideshowPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSlideshowUrisChange(uris.map { it.toString() })
        }
    }
    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { onGifUriChange(it.toString()) }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_style_picker_title),
        subtitle = stringResource(R.string.float_ball_style_picker_summary),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "fb-style-type",
            selectableGroup = true,
            items = buildList {
                FloatBallStyleType.entries.forEach { style ->
                    add(
                        settingsCardScopeItem("style-${style.name}") {
                            SettingRadioRow(
                                title = floatBallStyleLabel(style),
                                selected = settings.floatBallStyleType == style,
                                enabled = enabled,
                                onClick = { if (enabled) onStyleTypeChange(style) },
                            )
                        },
                    )
                }
            },
        )

        item(key = "float-ball-style-extra") {
            when (settings.floatBallStyleType) {
                FloatBallStyleType.CUSTOM_IMAGE -> {
                    Column {
                        if (settings.floatBallCustomImageUri.isNotBlank()) {
                            MiuixHintText(stringResource(R.string.float_ball_style_image_selected))
                        } else {
                            MiuixHintText(stringResource(R.string.float_ball_style_custom_image_hint))
                        }
                        BasicComponent(
                            title = stringResource(R.string.float_ball_style_pick_image),
                            summary = stringResource(R.string.float_ball_style_custom_image),
                            enabled = enabled,
                            startAction = {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                            onClick = { imagePicker.launch("image/*") },
                        )
                    }
                }
                FloatBallStyleType.SLIDESHOW -> {
                    Column {
                        MiuixHintText(
                            pluralStringResource(
                                R.plurals.float_ball_style_slideshow_hint,
                                settings.floatBallSlideshowUris.size,
                                settings.floatBallSlideshowUris.size,
                            ),
                        )
                        BasicComponent(
                            title = stringResource(R.string.float_ball_style_pick_slideshow),
                            summary = stringResource(R.string.float_ball_style_slideshow),
                            enabled = enabled,
                            startAction = {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                            onClick = { slideshowPicker.launch(arrayOf("image/*")) },
                        )
                    }
                }
                FloatBallStyleType.GIF -> {
                    Column {
                        if (settings.floatBallGifUri.isBlank()) {
                            MiuixHintText(stringResource(R.string.float_ball_style_gif_hint))
                        }
                        BasicComponent(
                            title = stringResource(R.string.float_ball_style_pick_gif),
                            summary = stringResource(R.string.float_ball_style_gif),
                            enabled = enabled,
                            startAction = {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                            onClick = { gifPicker.launch("image/*") },
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun floatBallStyleLabel(style: FloatBallStyleType): String = when (style) {
    FloatBallStyleType.DEFAULT -> stringResource(R.string.float_ball_style_default)
    FloatBallStyleType.ANIMATED_PLANE -> stringResource(R.string.float_ball_style_animated_plane)
    FloatBallStyleType.ANIMATED_PULSE -> stringResource(R.string.float_ball_style_animated_pulse)
    FloatBallStyleType.ANIMATED_ORBIT -> stringResource(R.string.float_ball_style_animated_orbit)
    FloatBallStyleType.CUSTOM_IMAGE -> stringResource(R.string.float_ball_style_custom_image)
    FloatBallStyleType.SLIDESHOW -> stringResource(R.string.float_ball_style_slideshow)
    FloatBallStyleType.GIF -> stringResource(R.string.float_ball_style_gif)
}
