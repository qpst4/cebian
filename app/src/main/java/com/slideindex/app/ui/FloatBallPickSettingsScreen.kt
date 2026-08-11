package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.search.ImageViewTargetResolver
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PickPanelSlideAnimationDefaults
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallPickSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    historyCount: Int,
    onBack: () -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPickOffsetChange: (Float) -> Unit,
    onPickTextSizeChange: (Float) -> Unit,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPickTextFirstPanelChange: (Boolean) -> Unit,
    onPickPanelEnterAnimationMsChange: (Int) -> Unit,
    onPickPanelExitAnimationMsChange: (Int) -> Unit,
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
        val askEveryTimeLabel = "每次都询问"
        val imageViewerItems = remember(imageViewerApps, askEveryTimeLabel) {
            buildList {
                add(DropdownItem(text = askEveryTimeLabel))
                imageViewerApps.forEach { target ->
                    add(
                        DropdownItem(
                            text = target.label,
                            icon = { modifier ->
                                target.icon?.let { drawable ->
                                    Image(
                                        bitmap = drawable.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = modifier.size(24.dp),
                                    )
                                }
                            },
                        ),
                    )
                }
            }
        }
        val selectedImageViewerIndex = remember(settings.defaultImageViewerPackage, imageViewerApps) {
            settings.defaultImageViewerPackage?.let { pkg ->
                imageViewerApps.indexOfFirst { it.packageName == pkg }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            } ?: 0
        }
        val imageViewerSubtitle = imageViewerItems
            .getOrNull(selectedImageViewerIndex.coerceIn(0, imageViewerItems.lastIndex))
            ?.text
            ?: askEveryTimeLabel

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
            SettingSwitchRow(
                title = stringResource(R.string.float_ball_pick_text_first_panel),
                subtitle = stringResource(R.string.float_ball_pick_text_first_panel_desc),
                checked = settings.floatBallPickTextFirstPanel,
                enabled = controlsEnabled,
                onCheckedChange = onPickTextFirstPanelChange,
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pick_panel_enter_animation),
                value = settings.floatBallPickPanelEnterAnimationMs.toFloat(),
                valueRange = PickPanelSlideAnimationDefaults.MIN_MS.toFloat()
                    ..PickPanelSlideAnimationDefaults.MAX_MS.toFloat(),
                steps = (PickPanelSlideAnimationDefaults.MAX_MS - PickPanelSlideAnimationDefaults.MIN_MS) / 10,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.float_ball_pick_panel_animation_ms_value,
                    settings.floatBallPickPanelEnterAnimationMs,
                ),
                onValueChange = { onPickPanelEnterAnimationMsChange(it.roundToInt()) },
            )
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pick_panel_exit_animation),
                value = settings.floatBallPickPanelExitAnimationMs.toFloat(),
                valueRange = PickPanelSlideAnimationDefaults.MIN_MS.toFloat()
                    ..PickPanelSlideAnimationDefaults.MAX_MS.toFloat(),
                steps = (PickPanelSlideAnimationDefaults.MAX_MS - PickPanelSlideAnimationDefaults.MIN_MS) / 10,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.float_ball_pick_panel_animation_ms_value,
                    settings.floatBallPickPanelExitAnimationMs,
                ),
                onValueChange = { onPickPanelExitAnimationMsChange(it.roundToInt()) },
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
            SettingsSliderRow(
                title = stringResource(R.string.float_ball_pointer_speed_vertical),
                value = settings.floatBallPointerSpeedVerticalFraction,
                valueRange = FloatingPointerBounds.SENSITIVITY_MIN..FloatingPointerBounds.SENSITIVITY_MAX,
                steps = 10,
                enabled = controlsEnabled,
                label = stringResource(
                    R.string.floating_pointer_percent_value,
                    (settings.floatBallPointerSpeedVerticalFraction * 100).roundToInt(),
                ),
                onValueChange = onPointerSpeedVerticalChange,
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
            SettingSpinnerRow(
                title = "默认图片查看器",
                subtitle = imageViewerSubtitle,
                dialogButtonText = stringResource(R.string.cancel),
                items = imageViewerItems,
                selectedIndex = selectedImageViewerIndex,
                enabled = controlsEnabled,
                icon = { label -> Icon(Icons.Default.Image, contentDescription = label) },
                onSelectedIndexChange = { index ->
                    if (index == 0) {
                        onDefaultImageViewerPackageChange(null)
                    } else {
                        imageViewerApps.getOrNull(index - 1)?.let {
                            onDefaultImageViewerPackageChange(it.packageName)
                        }
                    }
                },
            )
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
