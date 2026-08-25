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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PickPanelSlideAnimationDefaults
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.viewmodel.FloatBallPickSettingsViewModel
import com.slideindex.app.ui.viewmodel.ImageViewerDropdownOption
import com.slideindex.app.ui.viewmodel.ImageViewerOptionsState
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallPickSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    historyCount: Int,
    imageViewerOptions: ImageViewerOptionsState,
    onBack: () -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPickOffsetChange: (Float) -> Unit,
    onPickCrossArmChange: (Float) -> Unit,
    onPickTextSizeChange: (Float) -> Unit,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPickTextFirstPanelChange: (Boolean) -> Unit,
    onPickPanelEnterAnimationMsChange: (Int) -> Unit,
    onPickPanelExitAnimationMsChange: (Int) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onHoverPauseDelayMsChange: (Int) -> Unit = {},
    onRegionalCancelSlopDpChange: (Float) -> Unit = {},
    onOcrFallbackChange: (Boolean) -> Unit,
    onShareImageOcrHistoryEnabledChange: (Boolean) -> Unit,
    onDefaultImageViewerPackageChange: (String?) -> Unit,
    onOpenOcrModels: () -> Unit,
    onOpenShareImageOcrHistory: () -> Unit,
) {
    val controlsEnabled = settings.floatBallEnabled && accessibilityGranted
    val askEveryTimeLabel = FloatBallPickSettingsViewModel.ASK_EVERY_TIME_LABEL
    val readyOptions = (imageViewerOptions as? ImageViewerOptionsState.Ready)?.options
    val imageViewerItems = remember(readyOptions) {
        readyOptions?.map { option -> option.toDropdownItem() }
            ?: listOf(DropdownItem(text = askEveryTimeLabel))
    }
    val selectedImageViewerIndex = remember(settings.defaultImageViewerPackage, readyOptions) {
        settings.defaultImageViewerPackage?.let { pkg ->
            readyOptions?.indexOfFirst { it.packageName == pkg }
                ?.takeIf { it >= 0 }
        } ?: 0
    }
    val imageViewerSubtitle = when (imageViewerOptions) {
        ImageViewerOptionsState.Loading -> "加载中…"
        is ImageViewerOptionsState.Ready ->
            imageViewerItems
                .getOrNull(selectedImageViewerIndex.coerceIn(0, imageViewerItems.lastIndex))
                ?.text
                ?: askEveryTimeLabel
    }
    val recognitionImageSectionTitle =
        stringResource(R.string.float_ball_pick_section_recognition_image)
    val panelSectionTitle = stringResource(R.string.float_ball_pick_section_panel)
    val pickOperationSectionTitle = stringResource(R.string.float_ball_pick_section_operation)
    val advancedPickSectionTitle = stringResource(R.string.float_ball_pick_section_advanced)
    val advancedPickExpanded = remember { androidx.compose.runtime.mutableStateOf(false) }

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_pick_settings_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "recognition-image-section",
            title = recognitionImageSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-recognition-image",
            items = buildList {
                add(
                    settingsCardScopeItem("ocr-fallback") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_ocr_fallback),
                            subtitle = stringResource(R.string.float_ball_ocr_fallback_desc),
                            checked = settings.floatBallOcrFallbackEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onOcrFallbackChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("ocr-models") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Default.Download, contentDescription = label) },
                            title = stringResource(R.string.float_ball_ocr_models),
                            subtitle = ocrModelSelectionSubtitle(settings.floatBallOcrModelId),
                            enabled = controlsEnabled,
                            onClick = onOpenOcrModels,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("history-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.share_image_ocr_history_enabled),
                            subtitle = stringResource(R.string.share_image_ocr_history_enabled_desc),
                            checked = settings.shareImageOcrHistoryEnabled,
                            enabled = controlsEnabled,
                            onCheckedChange = onShareImageOcrHistoryEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("history-entry") {
                        ShareImageOcrHistoryEntryRow(
                            historyCount = historyCount,
                            enabled = controlsEnabled,
                            onClick = onOpenShareImageOcrHistory,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("image-viewer") {
                        SettingSpinnerRow(
                            title = "默认图片查看器",
                            subtitle = imageViewerSubtitle,
                            dialogButtonText = stringResource(R.string.cancel),
                            items = imageViewerItems,
                            selectedIndex = selectedImageViewerIndex,
                            enabled = controlsEnabled && imageViewerOptions is ImageViewerOptionsState.Ready,
                            icon = { label -> Icon(Icons.Default.Image, contentDescription = label) },
                            onSelectedIndexChange = { index ->
                                val option = readyOptions?.getOrNull(index) ?: return@SettingSpinnerRow
                                onDefaultImageViewerPackageChange(option.packageName)
                            },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "panel-section",
            title = panelSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-panel",
            items = buildList {
                add(
                    settingsCardScopeItem("text-first-panel") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_pick_text_first_panel),
                            subtitle = stringResource(R.string.float_ball_pick_text_first_panel_desc),
                            checked = settings.floatBallPickTextFirstPanel,
                            enabled = controlsEnabled,
                            onCheckedChange = onPickTextFirstPanelChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("text-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_pick_text_size),
                            value = settings.floatBallPickTextSizeSp,
                            valueRange = 12f..22f,
                            steps = 9,
                            enabled = controlsEnabled,
                            label = stringResource(R.string.float_ball_text_size_value, settings.floatBallPickTextSizeSp),
                            onValueChange = onPickTextSizeChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("enter-animation") {
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
                    },
                )
                add(
                    settingsCardScopeItem("exit-animation") {
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
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "operation-section",
            title = pickOperationSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-operation",
            items = buildList {
                add(
                    settingsCardScopeItem("cross-arm") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_pick_cross_arm),
                            value = settings.floatBallPickCrossArmDp,
                            valueRange = 4f..16f,
                            steps = 23,
                            enabled = controlsEnabled,
                            label = stringResource(
                                R.string.float_ball_pick_cross_arm_value,
                                settings.floatBallPickCrossArmDp,
                            ),
                            onValueChange = onPickCrossArmChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("offset") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_pick_offset),
                            value = settings.floatBallPickOffsetDp,
                            valueRange = 4f..48f,
                            steps = 10,
                            enabled = controlsEnabled,
                            label = stringResource(R.string.float_ball_size_value, settings.floatBallPickOffsetDp),
                            onValueChange = onPickOffsetChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("advanced-pick") {
                        SettingExpandableSwitchRow(
                            title = advancedPickSectionTitle,
                            checked = advancedPickExpanded.value,
                            enabled = controlsEnabled,
                            onCheckedChange = { advancedPickExpanded.value = it },
                        ) {
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
                                title = stringResource(R.string.float_ball_hover_pause_delay),
                                value = settings.floatBallHoverPauseDelayMs.toFloat(),
                                valueRange = 200f..1000f,
                                steps = 15,
                                enabled = controlsEnabled,
                                label = stringResource(
                                    R.string.float_ball_pick_panel_animation_ms_value,
                                    settings.floatBallHoverPauseDelayMs,
                                ),
                                onValueChange = { onHoverPauseDelayMsChange(it.roundToInt()) },
                            )
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_regional_cancel_slop),
                                value = settings.floatBallRegionalCancelSlopDp,
                                valueRange = 3f..30f,
                                steps = 26,
                                enabled = controlsEnabled,
                                label = stringResource(R.string.float_ball_size_value, settings.floatBallRegionalCancelSlopDp),
                                onValueChange = onRegionalCancelSlopDpChange,
                            )
                        }
                    },
                )
            },
        )
    }
}

private fun ImageViewerDropdownOption.toDropdownItem(): DropdownItem =
    DropdownItem(
        text = label,
        icon = { modifier ->
            iconBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = modifier.size(24.dp),
                )
            }
        },
    )

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
