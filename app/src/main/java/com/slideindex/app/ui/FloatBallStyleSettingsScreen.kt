@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.SettingsRadioGroup

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
        SettingsRadioGroup {
            FloatBallStyleType.entries.forEach { style ->
                SettingRadioRow(
                    title = floatBallStyleLabel(style),
                    selected = settings.floatBallStyleType == style,
                    enabled = enabled,
                    onClick = { if (enabled) onStyleTypeChange(style) },
                )
            }
        }

        when (settings.floatBallStyleType) {
            FloatBallStyleType.CUSTOM_IMAGE -> {
                if (settings.floatBallCustomImageUri.isNotBlank()) {
                    SettingsHintText(stringResource(R.string.float_ball_style_image_selected))
                } else {
                    SettingsHintText(stringResource(R.string.float_ball_style_custom_image_hint))
                }
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(
                        text = stringResource(R.string.float_ball_style_pick_image),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            FloatBallStyleType.SLIDESHOW -> {
                SettingsHintText(
                    pluralStringResource(
                        R.plurals.float_ball_style_slideshow_hint,
                        settings.floatBallSlideshowUris.size,
                        settings.floatBallSlideshowUris.size,
                    ),
                )
                Button(
                    onClick = { slideshowPicker.launch(arrayOf("image/*")) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(
                        text = stringResource(R.string.float_ball_style_pick_slideshow),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            FloatBallStyleType.GIF -> {
                if (settings.floatBallGifUri.isBlank()) {
                    SettingsHintText(stringResource(R.string.float_ball_style_gif_hint))
                }
                Button(
                    onClick = { gifPicker.launch("image/*") },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text(
                        text = stringResource(R.string.float_ball_style_pick_gif),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            else -> Unit
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
