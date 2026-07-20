package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenu
import com.slideindex.app.search.ImageViewTargetResolver
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.filled.Image

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallPickSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    historyCount: Int,
    onBack: () -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPickOffsetChange: (Float) -> Unit,
    onPickTextSizeChange: (Float) -> Unit,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onOcrFallbackChange: (Boolean) -> Unit,
    onShareImageOcrHistoryEnabledChange: (Boolean) -> Unit,
    onDefaultImageViewerPackageChange: (String?) -> Unit,
    onOpenOcrModels: () -> Unit,
    onOpenShareImageOcrHistory: () -> Unit,
) {
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_pick_settings_title),
        onBack = onBack,
    ) {
        val context = LocalContext.current
        val imageViewerApps = remember { ImageViewTargetResolver.listTargets(context) }
        var showImageViewerDialog by remember { mutableStateOf(false) }
        SettingsCard {
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pick_offset),
                value = settings.floatBallPickOffsetDp,
                valueRange = 4f..48f,
                steps = 10,
                enabled = controlsEnabled,
                label = stringResource(R.string.float_ball_size_value, settings.floatBallPickOffsetDp),
                onValueChange = onPickOffsetChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pick_text_size),
                value = settings.floatBallPickTextSizeSp,
                valueRange = 12f..22f,
                steps = 9,
                enabled = controlsEnabled,
                label = stringResource(R.string.float_ball_text_size_value, settings.floatBallPickTextSizeSp),
                onValueChange = onPickTextSizeChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pick_bottom_transition),
                value = settings.floatBallPickBottomTransitionFraction,
                valueRange = 0.05f..0.22f,
                steps = 8,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallPickBottomTransitionFraction * 100).roundToInt(),
                ),
                onValueChange = onPickBottomTransitionChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pointer_slop),
                value = settings.floatBallPointerSlopDp,
                valueRange = 4f..32f,
                steps = 6,
                enabled = controlsEnabled,
                label = stringResource(R.string.float_ball_size_value, settings.floatBallPointerSlopDp),
                onValueChange = onPointerSlopChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pointer_speed),
                value = settings.floatBallPointerSpeedFraction,
                valueRange = FloatingPointerBounds.SENSITIVITY_MIN..FloatingPointerBounds.SENSITIVITY_MAX,
                steps = 10,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallPointerSpeedFraction * 100).roundToInt(),
                ),
                onValueChange = onPointerSpeedChange,
            )
            SettingSwitchRow(
                title = stringResource(R.string.float_ball_ocr_fallback),
                subtitle = stringResource(R.string.float_ball_ocr_fallback_desc),
                checked = settings.floatBallOcrFallbackEnabled,
                enabled = controlsEnabled,
                onCheckedChange = onOcrFallbackChange,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Download, contentDescription = label) },
                title = stringResource(R.string.float_ball_ocr_models),
                subtitle = ocrModelSelectionSubtitle(settings.floatBallOcrModelId),
                enabled = controlsEnabled,
                onClick = onOpenOcrModels,
            )
        }

        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.share_image_ocr_history_enabled),
                subtitle = stringResource(R.string.share_image_ocr_history_enabled_desc),
                checked = settings.shareImageOcrHistoryEnabled,
                enabled = controlsEnabled,
                onCheckedChange = onShareImageOcrHistoryEnabledChange,
            )
            ShareImageOcrHistoryEntryRow(
                historyCount = historyCount,
                enabled = controlsEnabled,
                onClick = onOpenShareImageOcrHistory,
            )

            SettingNavigationRow(
                icon = { label -> Icon(Icons.Default.Image, contentDescription = label) },
                title = "默认图片查看器",
                subtitle = settings.defaultImageViewerPackage?.let { pkg ->
                    imageViewerApps.find { it.packageName == pkg }?.label
                } ?: "每次都询问",
                enabled = controlsEnabled,
                onClick = { showImageViewerDialog = true },
            )

            DropdownMenu(
                expanded = showImageViewerDialog,
                onDismissRequest = { showImageViewerDialog = false }
            ) {
                DropdownMenuItem(
                    text = { Text("每次都询问") },
                    onClick = {
                        onDefaultImageViewerPackageChange(null)
                        showImageViewerDialog = false
                    }
                )
                imageViewerApps.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
                        leadingIcon = {
                            target.icon?.let { drawable ->
                                Image(
                                    bitmap = drawable.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                        onClick = {
                            onDefaultImageViewerPackageChange(target.packageName)
                            showImageViewerDialog = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ocrModelSelectionSubtitle(modelId: String): String {
    if (modelId.isBlank()) {
        return stringResource(R.string.ocr_model_status_not_installed)
    }
    return when (modelId) {
        "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese)
        "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng)
        "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny)
        "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small)
        "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium)
        else -> modelId
    }
}
