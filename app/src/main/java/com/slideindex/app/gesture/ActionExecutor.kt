package com.slideindex.app.gesture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.executor.ActionExecutorLaunch
import com.slideindex.app.gesture.executor.ActionExecutorMediaSystem
import com.slideindex.app.gesture.executor.ActionExecutorOverlayPanels
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.PickResultFromHistoryCoordinator
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.clipboard.ClipboardFocusReader
import com.slideindex.app.overlay.HoneycombAppPickerOverlayWindow
import com.slideindex.app.overlay.appswitcher.AppSwitcherOverlayWindow
import com.slideindex.app.overlay.OhoQuickToolsOverlayWindow
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.util.AssistantLauncher
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.FlashlightHelper
import com.slideindex.app.util.InputMethodHelper
import com.slideindex.app.util.InputTapUtil
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.QuickToolsHelper
import com.slideindex.app.util.ScreenRecordHelper
import com.slideindex.app.util.SystemGestureActions
import com.slideindex.app.util.OverlaySnoozeController
import com.slideindex.app.util.VolumeControlHelper

class ActionExecutor(
    internal val context: Context,
    private val appRepository: AppRepository,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    overlayBrightness: OverlayBrightnessControl? = null,
    private val side: PanelSide? = null,
    onShellCommandsPersist: ((List<ShellCommand>) -> Unit)? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaSystem = ActionExecutorMediaSystem(context, overlayBrightness)
    private val overlayPanels = ActionExecutorOverlayPanels(context, onShellCommandsPersist)
    private val launchHelper = ActionExecutorLaunch(context, appRepository, mainHandler)

    fun beginContinuousAdjust(mode: ContinuousAdjustController.Mode, rawY: Float): Boolean =
        mediaSystem.beginContinuousAdjust(mode, rawY)

    fun updateContinuousAdjust(mode: ContinuousAdjustController.Mode, rawY: Float) {
        mediaSystem.updateContinuousAdjust(mode, rawY)
    }

    fun endContinuousAdjust() {
        mediaSystem.endContinuousAdjust()
    }

    fun applyAdjustOnce(
        mode: ContinuousAdjustController.Mode,
        anchorRawY: Float,
        targetRawY: Float,
    ): Float? = mediaSystem.applyAdjustOnce(mode, anchorRawY, targetRawY)

    fun readCurrentAdjustFraction(mode: ContinuousAdjustController.Mode): Float =
        mediaSystem.readCurrentAdjustFraction(mode)

    fun clearBrightnessPreview() {
        mediaSystem.clearBrightnessPreview()
    }

    fun adjustMode(): ContinuousAdjustController.Mode? = mediaSystem.adjustMode()

    fun adjustFraction(): Float = mediaSystem.adjustFraction()

    fun readRingerMode(): Int = mediaSystem.readRingerMode()

    fun cycleRingerMode(): Int? = mediaSystem.cycleRingerMode()

    fun readInterruptionFilter(): Int = mediaSystem.readInterruptionFilter()

    fun toggleDnd(): Int? = mediaSystem.toggleDnd()

    fun readAutoBrightnessEnabled(): Boolean = mediaSystem.readAutoBrightnessEnabled()

    fun toggleAutoBrightness(): Boolean? = mediaSystem.toggleAutoBrightness()

    fun readDarkModeEnabled(): Boolean = mediaSystem.readDarkModeEnabled()

    fun toggleDarkMode(): Boolean? = mediaSystem.toggleDarkMode()

    fun readVolumeFraction(stream: VolumeControlHelper.Stream): Float =
        mediaSystem.readVolumeFraction(stream)

    fun setVolumeFraction(stream: VolumeControlHelper.Stream, fraction: Float) {
        mediaSystem.setVolumeFraction(stream, fraction)
    }

    fun setBrightnessFraction(fraction: Float, previewOnly: Boolean = false) {
        mediaSystem.setBrightnessFraction(fraction, previewOnly)
    }

    fun execute(
        action: GestureAction,
        settings: AppSettings,
        longPressArmed: Boolean = false,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false,
        panelSide: PanelSide? = null,
    ): Boolean {
        val resolvedSide = panelSide ?: side
        return when (action) {
            GestureAction.OpenIndex,
            is GestureAction.QuickLauncher,
            GestureAction.TaskSwitcher,
            -> overlayPanels.showEdgeHostedPanel(action, anchorRawY, resolvedSide)
            GestureAction.ShellCommandPanel -> overlayPanels.openShellCommandPanelStandalone()
            is GestureAction.ExecuteShellCommand -> executeShellCommand(action)
            GestureAction.None, GestureAction.ClickPassthrough,
            GestureAction.CornerInnerCancel, GestureAction.CornerInnerPinWheel,
            -> false
            GestureAction.AdjustVolume -> overlayPanels.showEdgeHostedPanel(GestureAction.AdjustVolume, anchorRawY, resolvedSide)
            GestureAction.AdjustBrightness -> overlayPanels.showEdgeHostedPanel(GestureAction.AdjustBrightness, anchorRawY, resolvedSide)
            is GestureAction.SimulatePointerSwipe -> {
                val x = anchorRawX ?: return false
                val y = anchorRawY ?: return false
                if (FloatingPointerOverlayWindow.isVisible) {
                    FloatingPointerOverlayWindow.schedulePointerSwipe(x, y, action.config)
                } else {
                    InputTapUtil.dispatchPointerSwipeAsync(x, y, action.config)
                }
                true
            }
            GestureAction.PointerGestureRecorder,
            GestureAction.PointerRealtimeGesture,
            GestureAction.OpenFloatingPointerRadialMenu,
            -> false
            GestureAction.QuickToolsOverlay ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    OhoQuickToolsOverlayWindow.show(context, settings, resolvedSide, y)
                }
            GestureAction.HoneycombLauncher ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    val x = anchorRawX ?: (context.resources.displayMetrics.widthPixels / 2f)
                    HoneycombAppPickerOverlayWindow.show(
                        context = context,
                        settings = settings,
                        anchorRawX = x,
                        anchorRawY = y,
                        externalTracking = false,
                        onLaunch = { item, longPressArmed ->
                            launchQuickItem(item, settings, longPressArmed = longPressArmed, anchorRawY = y)
                        },
                    )
                }
            GestureAction.AppSwitcher ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    val x = anchorRawX ?: (context.resources.displayMetrics.widthPixels / 2f)
                    AppSwitcherOverlayWindow.show(
                        context = context,
                        settings = settings,
                        anchorRawX = x,
                        anchorRawY = y,
                        externalTracking = continueTouch,
                        onLaunch = { item, longPressArmed ->
                            launchQuickItem(item, settings, longPressArmed = longPressArmed, anchorRawY = y)
                        },
                    )
                }
            GestureAction.WidgetPopupOverlay ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    WidgetPopupOverlayWindow.show(context, settings, resolvedSide, y)
                }
            GestureAction.StashPanel -> FloatBallStashPanel.show(
                context = context,
                panelSide = resolvedSide,
            )
            GestureAction.ClipboardPanel -> FloatBallStashPanel.show(
                context = context,
                initialTab = StashPanelInitialTab.Clipboard,
                panelSide = resolvedSide,
            )
            GestureAction.ClipboardFloat -> {
                ClipboardFloatLifecycle.showExpanded(context)
                true
            }
            GestureAction.ClipboardPick -> {
                ClipboardFocusReader.read(context) { payload ->
                    PickResultFromHistoryCoordinator.openFromClipboardPayload(context, payload)
                }
                true
            }
            GestureAction.FloatingPointer -> {
                FloatingPointerOverlayWindow.toggle(
                    context,
                    settings,
                    anchorRawX,
                    anchorRawY,
                    continueTouch,
                )
                true
            }
            is GestureAction.LaunchApp -> launchHelper.launchApp(action.packageName, settings, longPressArmed)
            is GestureAction.LaunchShortcut -> {
                launchHelper.launchGestureShortcut(action, settings, longPressArmed)
                true
            }
            GestureAction.Back, GestureAction.Home, GestureAction.Recents ->
                SlideIndexAccessibilityService.perform(action)
            GestureAction.CloseCurrentApp -> {
                launchHelper.closeCurrentApp()
                true
            }
            GestureAction.FreeWindowCurrentApp -> {
                launchHelper.freeWindowForegroundApp(settings)
                true
            }
            GestureAction.Flashlight -> FlashlightHelper.toggle(context)
            GestureAction.ToggleDnd -> VolumeControlHelper.toggleDnd(context) != null
            GestureAction.ScreenRecord -> {
                ScreenRecordHelper.toggle(context)
                true
            }
            GestureAction.ToggleWifi -> QuickToolsHelper.toggleWifi(context) == true
            GestureAction.ToggleMobileData -> QuickToolsHelper.toggleMobileData(context) == true
            GestureAction.SwitchInputMethod -> InputMethodHelper.switchInputMethod(context)
            GestureAction.LaunchAssistant -> {
                AssistantLauncher.launchDefault(context)
                true
            }
            GestureAction.ToggleMute -> SystemGestureActions.toggleMute(context)
            GestureAction.LockScreenAndSilenceRing -> {
                SystemGestureActions.silenceRinger(context)
                SlideIndexAccessibilityService.perform(GestureAction.LockScreen)
            }
            GestureAction.LockScreenAndMuteAll -> {
                SystemGestureActions.muteAllVolumes(context)
                SlideIndexAccessibilityService.perform(GestureAction.LockScreen)
            }
            GestureAction.MediaPlayPause -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GestureAction.MediaPrevious -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            GestureAction.MediaNext -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            GestureAction.PreviousApp,
            GestureAction.OpenNotifications,
            GestureAction.OpenQuickSettings,
            GestureAction.LockScreen,
            GestureAction.Screenshot,
            GestureAction.PowerMenu,
            GestureAction.KeepScreenOn,
            GestureAction.ScrollToTop,
            GestureAction.ScrollToBottom,
            -> SlideIndexAccessibilityService.perform(action)
            GestureAction.FullscreenScreenshotPick ->
                SlideIndexAccessibilityService.pickFullscreen(
                    context,
                    settings.floatBallOcrFallbackEnabled,
                    settings.floatBallOcrModelId,
                )
            GestureAction.RegionalScreenshotPick -> {
                if (!continueTouch) return false
                com.slideindex.app.overlay.RegionalPickOverlay.show(
                    context = context,
                    appSettings = settings,
                    anchorRawX = anchorRawX,
                    anchorRawY = anchorRawY,
                    continueTouch = continueTouch,
                )
                true
            }
            GestureAction.SearchPanel ->
                overlayPanels.showSearchPanel(context, settings, resolvedSide)
            GestureAction.SnoozeOverlays -> {
                OverlaySnoozeController.snooze(context)
                true
            }
        }
    }

    fun launchQuickItem(
        item: QuickLauncherItem,
        settings: AppSettings,
        longPressArmed: Boolean = false,
        anchorRawY: Float? = null,
    ): Boolean = launchHelper.launchQuickItem(item, settings, longPressArmed, anchorRawY) { action, appSettings, armed, y ->
        execute(action, appSettings, armed, anchorRawX = null, anchorRawY = y)
    }

    fun switchToRecentTask(
        taskId: Int,
        rawIdentifier: String,
        topComponent: String,
        packageName: String,
        settings: AppSettings,
    ) = launchHelper.switchToRecentTask(taskId, rawIdentifier, topComponent, packageName, settings)

    fun dispatchClickPassthrough(rawX: Float, rawY: Float, onComplete: () -> Unit = {}) {
        val handler = clickPassthroughHandler
        if (handler != null) {
            handler(rawX, rawY, onComplete)
        } else {
            InputTapUtil.dispatchTap(rawX, rawY)
            onComplete()
        }
    }

    private fun executeShellCommand(action: GestureAction.ExecuteShellCommand): Boolean {
        val commandLine = action.command.trim()
        if (commandLine.isEmpty()) return false
        Thread {
            ShellCommandRunner.execute(
                context = context,
                command = ShellCommand(
                    label = "Gesture",
                    command = commandLine,
                ),
            )
        }.start()
        return true
    }

    internal companion object {
        const val TAG = "ActionExecutor"
    }
}
