package com.slideindex.app.ui.picker

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.gesturepicker.filterGestureActions

enum class GestureActionCatalogScope {
    GesturePicker,
    QuickLauncher,
}

object GestureActionCatalog {
    fun build(
        scope: GestureActionCatalogScope,
        trigger: GestureTriggerType = GestureTriggerType.SHORT_SWIPE_IN,
        includePointerGestureActions: Boolean = false,
        includeCornerInnerZoneActions: Boolean = false,
    ): List<GestureAction> = when (scope) {
        GestureActionCatalogScope.GesturePicker -> buildGesturePickerActions(
            trigger = trigger,
            includePointerGestureActions = includePointerGestureActions,
            includeCornerInnerZoneActions = includeCornerInnerZoneActions,
        )
        GestureActionCatalogScope.QuickLauncher -> buildQuickLauncherActions()
    }

    fun filter(
        context: Context,
        actions: List<GestureAction>,
        query: String,
        pinNoneAtTop: Boolean = false,
        includeCornerInnerZoneActions: Boolean = false,
    ): List<GestureAction> {
        val sorted = filterGestureActions(context, actions, query)
        val pinnedTop = buildList {
            if (pinNoneAtTop) add(GestureAction.None)
            if (includeCornerInnerZoneActions) {
                add(GestureAction.CornerInnerPinWheel)
                add(GestureAction.CornerInnerCancel)
            }
        }
        if (pinnedTop.isEmpty()) return sorted
        val pinnedSet = pinnedTop.toSet()
        return pinnedTop.filter { it in sorted } + sorted.filter { it !in pinnedSet }
    }

    private fun buildGesturePickerActions(
        trigger: GestureTriggerType,
        includePointerGestureActions: Boolean,
        includeCornerInnerZoneActions: Boolean,
    ): List<GestureAction> = buildList {
        if (includeCornerInnerZoneActions) {
            add(GestureAction.CornerInnerPinWheel)
            add(GestureAction.CornerInnerCancel)
        }
        if (!includeCornerInnerZoneActions) {
            add(GestureAction.None)
        }
        add(GestureAction.OpenIndex)
        add(GestureAction.QuickLauncher())
        add(GestureAction.HoneycombLauncher)
        addAll(sharedCoreActions())
        if (includePointerGestureActions) {
            add(GestureAction.SimulatePointerSwipe())
            add(GestureAction.PointerGestureRecorder)
            add(GestureAction.PointerRealtimeGesture)
            add(GestureAction.OpenFloatingPointerRadialMenu)
        }
        add(GestureAction.SnoozeOverlays)
        if (trigger == GestureTriggerType.SHORT_SINGLE_TAP) add(GestureAction.ClickPassthrough)
    }

    private fun buildQuickLauncherActions(): List<GestureAction> = buildList {
        add(GestureAction.OpenIndex)
        addAll(sharedCoreActions())
        add(GestureAction.SnoozeOverlays)
    }

    private fun sharedCoreActions(): List<GestureAction> = listOf(
        GestureAction.TaskSwitcher,
        GestureAction.ShellCommandPanel,
        GestureAction.ExecuteShellCommand(),
        GestureAction.QuickToolsOverlay,
        GestureAction.WidgetPopupOverlay,
        GestureAction.StashPanel,
        GestureAction.ClipboardPanel,
        GestureAction.FloatingPointer,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.CloseCurrentApp,
        GestureAction.FreeWindowCurrentApp,
        GestureAction.Flashlight,
        GestureAction.ToggleDnd,
        GestureAction.ScreenRecord,
        GestureAction.ToggleWifi,
        GestureAction.ToggleMobileData,
        GestureAction.SwitchInputMethod,
        GestureAction.ToggleMute,
        GestureAction.MediaPlayPause,
        GestureAction.MediaPrevious,
        GestureAction.MediaNext,
        GestureAction.PreviousApp,
        GestureAction.OpenNotifications,
        GestureAction.OpenQuickSettings,
        GestureAction.LockScreen,
        GestureAction.LockScreenAndSilenceRing,
        GestureAction.LockScreenAndMuteAll,
        GestureAction.Screenshot,
        GestureAction.FullscreenScreenshotPick,
        GestureAction.RegionalScreenshotPick,
        GestureAction.SearchPanel,
        GestureAction.PowerMenu,
        GestureAction.KeepScreenOn,
        GestureAction.ScrollToTop,
        GestureAction.ScrollToBottom,
        GestureAction.AdjustVolume,
        GestureAction.AdjustBrightness,
        GestureAction.LaunchAssistant,
    )
}
