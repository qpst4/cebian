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
    onOpenOcrModels: () -> Unit,
    onOpenShareImageOcrHistory: () -> Unit,
) {
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_pick_settings_title),
        onBack = onBack,
    ) {
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
        }

        SettingsHintText(stringResource(R.string.share_image_ocr_hint))

        SettingsHintText(stringResource(R.string.float_ball_pick_anchor_hint))
        SettingsHintText(stringResource(R.string.float_ball_pointer_speed_hint))
        SettingsHintText(stringResource(R.string.float_ball_usage_hint))
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
