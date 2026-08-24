package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.core.net.toUri
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.settings.FloatingPointerTrailType
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.overlay.FloatBallStyleAssetStore
import com.slideindex.app.settings.FloatBallStyleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.feedback.UserMessageBus
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QuickLauncherFolderDraft(
    val panelId: String,
    val name: String = "",
    val items: List<QuickLauncherItem> = emptyList(),
)

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _folderDraft = MutableStateFlow<QuickLauncherFolderDraft?>(null)
    val folderDraft: StateFlow<QuickLauncherFolderDraft?> = _folderDraft.asStateFlow()

    fun initFolderDraft(panelId: String) {
        val current = _folderDraft.value
        if (current == null || current.panelId != panelId) {
            _folderDraft.value = QuickLauncherFolderDraft(panelId)
        }
    }

    fun updateFolderDraftName(name: String) {
        _folderDraft.value = _folderDraft.value?.copy(name = name)
    }

    fun toggleFolderDraftItem(item: QuickLauncherItem, added: Boolean) {
        val draft = _folderDraft.value ?: return
        val items = if (added) {
            draft.items.filterNot { it.type == item.type && it.payload == item.payload }
        } else {
            draft.items + item
        }
        _folderDraft.value = draft.copy(items = items)
    }

    fun addFolderDraftItem(item: QuickLauncherItem) {
        val draft = _folderDraft.value ?: return
        if (draft.items.none { it.type == item.type && it.payload == item.payload }) {
            _folderDraft.value = draft.copy(items = draft.items + item)
        }
    }

    fun clearFolderDraft() {
        _folderDraft.value = null
    }

    fun commitFolderDraft(name: String) {
        val draft = _folderDraft.value ?: return
        addQuickLauncherPanelItem(
            draft.panelId,
            QuickLauncherItem.folder(name, draft.items),
        )
        _folderDraft.value = null
    }

    fun setQuickLauncherPanels(panels: List<QuickLauncherPanel>) = launchSettingsWrite {
        settingsRepository.setQuickLauncherPanels(
            QuickLauncherPanelDefaults.effectivePanels(panels),
        )
    }

    fun setQuickLauncherDisplaySettings(settings: com.slideindex.app.settings.QuickLauncherDisplaySettings) =
        launchSettingsWrite {
            settingsRepository.setQuickLauncherDisplaySettings(settings)
        }

    fun setQuickLauncherItems(items: List<QuickLauncherItem>) = launchSettingsWrite {
        settingsRepository.setQuickLauncherItems(items)
    }

    fun setHoneycombLauncherItems(items: List<QuickLauncherItem>) = launchSettingsWrite {
        settingsRepository.setHoneycombLauncherItems(items)
    }

    fun setHoneycombDisplaySettings(settings: com.slideindex.app.settings.HoneycombDisplaySettings) =
        launchSettingsWrite {
            settingsRepository.setHoneycombDisplaySettings(settings)
        }

    fun toggleQuickLauncherPanelItem(panelId: String, item: QuickLauncherItem, added: Boolean) = launchSettingsWrite {
        val currentPanels = QuickLauncherPanelDefaults.effectivePanels(settingsRepository.readSnapshot().quickLauncherPanels)
        val updated = currentPanels.map { panel ->
            if (panel.id == panelId) {
                val nextItems = if (added) {
                    when (item.type) {
                        QuickLauncherItemType.APP -> panel.items.filterNot { it.type == QuickLauncherItemType.APP && it.payload == item.payload }
                        QuickLauncherItemType.SHORTCUT -> {
                            val key = QuickLauncherItemCodec.shortcutItemKey(item)
                            panel.items.filterNot { it.type == QuickLauncherItemType.SHORTCUT && QuickLauncherItemCodec.shortcutItemKey(it) == key }
                        }
                        QuickLauncherItemType.ACTION -> {
                            val actionKey = QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(QuickLauncherItemCodec::actionKey)
                            panel.items.filterNot { it.type == QuickLauncherItemType.ACTION && QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) == actionKey }
                        }
                        QuickLauncherItemType.WIDGET -> panel.items.filterNot { it.type == QuickLauncherItemType.WIDGET && it.payload == item.payload }
                        QuickLauncherItemType.FOLDER -> panel.items.filterNot { it.type == QuickLauncherItemType.FOLDER && it.payload == item.payload && it.label == item.label }
                    }
                } else {
                    panel.items + item
                }
                panel.copy(items = nextItems)
            } else {
                panel
            }
        }
        settingsRepository.setQuickLauncherPanels(QuickLauncherPanelDefaults.effectivePanels(updated))
    }

    fun addQuickLauncherPanelItem(panelId: String, item: QuickLauncherItem) =
        toggleQuickLauncherPanelItem(panelId, item, added = false)

    fun toggleHoneycombItem(item: QuickLauncherItem, added: Boolean) = launchSettingsWrite {
        val current = settingsRepository.readSnapshot().honeycombLauncher.honeycombRuntimeItems()
        val next = if (added) {
            when (item.type) {
                QuickLauncherItemType.APP -> current.filterNot { it.type == QuickLauncherItemType.APP && it.payload == item.payload }
                QuickLauncherItemType.SHORTCUT -> {
                    val key = QuickLauncherItemCodec.shortcutItemKey(item) ?: return@launchSettingsWrite Result.success(Unit)
                    current.filterNot { it.type == QuickLauncherItemType.SHORTCUT && QuickLauncherItemCodec.shortcutItemKey(it) == key }
                }
                QuickLauncherItemType.ACTION -> {
                    val actionKey = QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(QuickLauncherItemCodec::actionKey) ?: return@launchSettingsWrite Result.success(Unit)
                    current.filterNot { it.type == QuickLauncherItemType.ACTION && QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) == actionKey }
                }
                else -> return@launchSettingsWrite Result.success(Unit)
            }
        } else {
            current + item
        }
        settingsRepository.setHoneycombLauncherItems(next.honeycombRuntimeItems())
    }

    fun addHoneycombItem(item: QuickLauncherItem) =
        toggleHoneycombItem(item, added = false)

    fun setQuickLauncherColumnsPerPage(value: Int) = launchSettingsWrite {
        settingsRepository.setQuickLauncherColumnsPerPage(value)
    }

    fun setQuickLauncherRowsPerPage(value: Int) = launchSettingsWrite {
        settingsRepository.setQuickLauncherRowsPerPage(value)
    }

    fun setShellCommands(items: List<ShellCommand>) = launchSettingsWrite {
        settingsRepository.setShellCommands(items)
    }

    fun setActivityShortcuts(items: List<com.slideindex.app.activity.ActivityShortcut>) = launchSettingsWrite {
        settingsRepository.setActivityShortcuts(items)
    }

    fun setWidgetPanelPages(pages: List<WidgetPanelPage>) = launchSettingsWrite {
        settingsRepository.setWidgetPanelPages(pages)
    }

    fun setWidgetPanelBlurEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setWidgetPanelBlurEnabled(enabled)
    }

    fun setWidgetPanelBlurRadiusDp(radiusDp: Int) = launchSettingsWrite {
        settingsRepository.setWidgetPanelBlurRadiusDp(radiusDp)
    }

    fun setWidgetPanelWidthFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setWidgetPanelWidthFraction(fraction)
    }

    fun setFloatingPointerSensitivityFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerSensitivityFraction(fraction)
    }

    fun setFloatingPointerPointerDiameterPx(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerPointerDiameterPx(size)
    }

    fun setFloatingPointerRingThicknessPx(thickness: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRingThicknessPx(thickness)
    }

    fun setFloatingPointerDotDiameterPx(diameter: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDotDiameterPx(diameter)
    }

    fun setFloatingPointerRingColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRingColor(color)
    }

    fun setFloatingPointerFillColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerFillColor(color)
    }

    fun setFloatingPointerDotColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDotColor(color)
    }

    fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickVisualFeedbackEnabled(enabled)
    }

    fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickHapticEnabled(enabled)
    }

    fun setFloatingPointerRippleColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleColor(color)
    }

    fun setFloatingPointerRippleSizeDp(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleSizeDp(size)
    }

    fun setFloatingPointerRippleDurationMs(duration: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRippleDurationMs(duration)
    }

    fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailType(type)
    }

    fun setFloatingPointerTrailDurationMs(duration: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailDurationMs(duration)
    }

    fun setFloatingPointerTrailColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerTrailColor(color)
    }

    fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideWhenJoystickReleased(enabled)
    }

    fun setFloatingPointerDesignId(designId: String) = launchSettingsWrite {
        settingsRepository.setFloatingPointerDesignId(designId)
    }

    fun resetFloatingPointerVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerVisualDefaults()
    }

    fun setFloatingPointerJoystickDiameterPx(size: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickDiameterPx(size)
    }

    fun setFloatingPointerJoystickInnerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickInnerColor(color)
    }

    fun setFloatingPointerJoystickOuterColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickOuterColor(color)
    }

    fun setFloatingPointerJoystickGradientRadiusFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickGradientRadiusFraction(fraction)
    }

    fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideOnOutsideClick(enabled)
    }

    fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideOnQuickSwipe(enabled)
    }

    fun setFloatingPointerHideWhenIdle(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHideWhenIdle(enabled)
    }

    fun setFloatingPointerReleaseClickAndDismiss(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerReleaseClickAndDismiss(enabled)
    }

    fun setFloatingPointerHoverEnterSelect(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerHoverEnterSelect(enabled)
    }

    fun setFloatingPointerClickDistanceThresholdDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerClickDistanceThresholdDp(value)
    }

    fun setFloatingPointerIdleHideDelayMs(delayMs: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerIdleHideDelayMs(delayMs)
    }

    fun setFloatingPointerJoystickLongPressAction(action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatingPointerJoystickLongPressAction(action)
    }

    fun resetFloatingPointerJoystickVisualDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickVisualDefaults()
    }

    fun resetFloatingPointerJoystickBehaviorDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerJoystickBehaviorDefaults()
    }

    fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialAlwaysVisible(enabled)
    }

    fun setFloatingPointerRadialLongPressMs(ms: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialLongPressMs(ms)
    }

    fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialSlotAction(index, action)
    }

    fun setFloatingPointerRadialOuterDiameterPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialOuterDiameterPx(value)
    }

    fun setFloatingPointerRadialInnerDiameterPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialInnerDiameterPx(value)
    }

    fun setFloatingPointerRadialOuterColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialOuterColor(color)
    }

    fun setFloatingPointerRadialInnerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialInnerColor(color)
    }

    fun setFloatingPointerRadialDividerThicknessPx(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialDividerThicknessPx(value)
    }

    fun setFloatingPointerRadialDividerColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialDividerColor(color)
    }

    fun setFloatingPointerRadialIconSizeFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialIconSizeFraction(value)
    }

    fun setFloatingPointerRadialIconColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerRadialIconColor(color)
    }

    fun resetFloatingPointerRadialDesignDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerRadialDesignDefaults()
    }

    fun setFloatingPointerEdgeThresholdDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeThresholdDp(value)
    }

    fun setFloatingPointerEdgePreviewSensitivity(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewSensitivity(value)
    }

    fun setFloatingPointerEdgePreviewGlowSize(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewGlowSize(value)
    }

    fun setFloatingPointerEdgePreviewShowIcon(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgePreviewShowIcon(enabled)
    }

    fun setFloatingPointerEdgeVisualColor(color: Int) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeVisualColor(color)
    }

    fun setFloatingPointerEdgeBarEnabled(side: FloatingPointerEdgeSide, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeBarEnabled(side, enabled)
    }

    fun setFloatingPointerEdgeBarSlotAction(
        side: FloatingPointerEdgeSide,
        slotIndex: Int,
        action: GestureAction,
    ) = launchSettingsWrite {
        settingsRepository.setFloatingPointerEdgeBarSlotAction(side, slotIndex, action)
    }

    fun addFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide) = launchSettingsWrite {
        settingsRepository.addFloatingPointerEdgeBarSlot(side)
    }

    fun removeFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide, slotIndex: Int) = launchSettingsWrite {
        settingsRepository.removeFloatingPointerEdgeBarSlot(side, slotIndex)
    }

    fun resetFloatingPointerEdgeDefaults() = launchSettingsWrite {
        settingsRepository.resetFloatingPointerEdgeDefaults()
    }

    fun setFloatBallEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallEnabled(enabled)
    }

    fun setFloatBallSizeDp(sizeDp: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallSizeDp = sizeDp.coerceIn(36f, 72f)))
        },
        block = { settingsRepository.setFloatBallSizeDp(sizeDp) },
    )

    fun setFloatBallPickCrossArmDp(armDp: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickCrossArmDp(armDp)
    }

    fun setFloatBallOpacity(opacity: Float) = launchOptimisticSettingsWrite(
        optimisticUpdate = { settings ->
            settings.copy(floatBall = settings.floatBall.copy(floatBallOpacity = opacity.coerceIn(0f, 1f)))
        },
        block = { settingsRepository.setFloatBallOpacity(opacity) },
    )

    fun setDefaultImageViewerPackage(packageName: String?) = launchSettingsWrite {
        settingsRepository.setDefaultImageViewerPackage(packageName)
    }

    fun setFloatBallOcrFallbackEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallOcrFallbackEnabled(enabled)
    }

    fun setFloatBallPointerSpeedFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSpeedFraction(fraction)
    }

    fun setFloatBallPointerSpeedVerticalFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSpeedVerticalFraction(fraction)
    }

    fun setFloatBallPositionMode(mode: FloatBallPositionMode) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionMode(mode)
    }

    fun setFloatBallPositionYFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPositionYFraction(fraction)
    }

    fun setFloatBallVisibleFraction(fraction: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallVisibleFraction(fraction)
    }

    fun setFloatBallLineHeightFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineHeightFraction(value)
    }

    fun setFloatBallLineWidthFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineWidthFraction(value)
    }

    fun setFloatBallLineOpacity(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallLineOpacity(value)
    }

    fun setFloatBallGestureAction(type: FloatBallGestureType, action: GestureAction) = launchSettingsWrite {
        settingsRepository.setFloatBallGestureAction(type, action)
    }

    fun setFloatBallStyleType(type: FloatBallStyleType) = launchSettingsWrite {
        settingsRepository.setFloatBallStyleType(type)
    }

    fun setFloatBallCustomImageUri(uri: String) = launchSettingsWrite {
        val stored = withContext(Dispatchers.IO) {
            FloatBallStyleAssetStore.importCustomImage(appContext, uri.toUri())
        } ?: uri
        settingsRepository.setFloatBallCustomImageUri(stored)
    }

    fun setFloatBallSlideshowUris(uris: List<String>) = launchSettingsWrite {
        val stored = withContext(Dispatchers.IO) {
            FloatBallStyleAssetStore.importSlideshow(appContext, uris.map { it.toUri() })
        }.ifEmpty { uris }
        settingsRepository.setFloatBallSlideshowUris(stored)
    }

    fun setFloatBallGifUri(uri: String) = launchSettingsWrite {
        val stored = withContext(Dispatchers.IO) {
            FloatBallStyleAssetStore.importGif(appContext, uri.toUri())
        } ?: uri
        settingsRepository.setFloatBallGifUri(stored)
    }

    fun setFloatBallPointerSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPointerSlopDp(value)
    }

    fun setFloatBallDownSwipeShortPercent(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallDownSwipeShortPercent(value)
    }

    fun setFloatBallSideSwipeShortPercent(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallSideSwipeShortPercent(value)
    }

    fun setFloatBallUpSwipeShortPercent(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallUpSwipeShortPercent(value)
    }

    fun setFloatBallPickOffsetDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickOffsetDp(value)
    }

    fun setFloatBallPickTextSizeSp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickTextSizeSp(value)
    }

    fun setFloatBallPickBottomTransitionFraction(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallPickBottomTransitionFraction(value)
    }

    fun setFloatBallPickTextFirstPanel(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallPickTextFirstPanel(enabled)
    }

    fun setFloatBallPickPanelEnterAnimationMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallPickPanelEnterAnimationMs(value)
    }

    fun setFloatBallPickPanelExitAnimationMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallPickPanelExitAnimationMs(value)
    }

    fun setShareImageOcrHistoryEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setShareImageOcrHistoryEnabled(enabled)
    }

    fun setFloatBallHoverPauseDelayMs(value: Int) = launchSettingsWrite {
        settingsRepository.setFloatBallHoverPauseDelayMs(value)
    }

    fun setFloatBallRegionalCancelSlopDp(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallRegionalCancelSlopDp(value)
    }
}
