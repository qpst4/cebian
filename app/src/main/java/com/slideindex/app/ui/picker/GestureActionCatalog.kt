package com.slideindex.app.ui.picker

import android.content.Context
import androidx.annotation.StringRes
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.gesturepicker.filterGestureActions

enum class GestureActionCatalogScope {
    GesturePicker,
    QuickLauncher,
}

enum class GestureActionCategory(@StringRes val titleRes: Int) {
    Panel(R.string.action_category_panel),
    Navigation(R.string.action_category_navigation),
    System(R.string.action_category_system),
    Media(R.string.action_category_media),
    Capture(R.string.action_category_capture),
    Adjust(R.string.action_category_adjust),
    Pointer(R.string.action_category_pointer),
    Other(R.string.action_category_other),
}

data class GestureActionSection(
    val category: GestureActionCategory,
    val actions: List<GestureAction>,
)

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

    fun groupIntoSections(actions: List<GestureAction>): List<GestureActionSection> {
        if (actions.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<GestureActionCategory, MutableList<GestureAction>>()
        for (category in GestureActionCategory.entries) {
            buckets[category] = mutableListOf()
        }
        for (action in actions) {
            buckets.getValue(categoryOf(action)).add(action)
        }
        return buckets
            .mapNotNull { (category, list) ->
                if (list.isEmpty()) null else GestureActionSection(category, list)
            }
    }

    fun categoryOf(action: GestureAction): GestureActionCategory = when (action.type) {
        GestureActionType.NONE,
        GestureActionType.OPEN_INDEX,
        GestureActionType.QUICK_LAUNCHER,
        GestureActionType.HONEYCOMB_LAUNCHER,
        GestureActionType.APP_SWITCHER,
        GestureActionType.HOLOGRAPHIC_LAUNCHER,
        GestureActionType.TASK_SWITCHER,
        GestureActionType.SHELL_COMMAND_PANEL,
        GestureActionType.EXECUTE_SHELL_COMMAND,
        GestureActionType.QUICK_TOOLS_OVERLAY,
        GestureActionType.WIDGET_POPUP_OVERLAY,
        GestureActionType.OPEN_STASH_PANEL,
        GestureActionType.OPEN_CLIPBOARD_PANEL,
        GestureActionType.OPEN_CLIPBOARD_FLOAT,
        GestureActionType.CLIPBOARD_PICK,
        GestureActionType.FLOATING_POINTER,
        GestureActionType.SEARCH_PANEL,
        GestureActionType.VOLUME_PANEL,
        GestureActionType.SCREEN_TRANSLATE,
        GestureActionType.UNIVERSAL_COPY,
        GestureActionType.FREEZER_PANEL,
        GestureActionType.CORNER_INNER_PIN_WHEEL,
        GestureActionType.CORNER_INNER_CANCEL,
        -> GestureActionCategory.Panel

        GestureActionType.BACK,
        GestureActionType.HOME,
        GestureActionType.RECENTS,
        GestureActionType.CLOSE_CURRENT_APP,
        GestureActionType.FREE_WINDOW_CURRENT_APP,
        GestureActionType.PREVIOUS_APP,
        GestureActionType.SCROLL_TO_TOP,
        GestureActionType.SCROLL_TO_BOTTOM,
        -> GestureActionCategory.Navigation

        GestureActionType.FLASHLIGHT,
        GestureActionType.TOGGLE_DND,
        GestureActionType.SCREEN_RECORD,
        GestureActionType.TOGGLE_WIFI,
        GestureActionType.TOGGLE_MOBILE_DATA,
        GestureActionType.SWITCH_INPUT_METHOD,
        GestureActionType.TOGGLE_MUTE,
        GestureActionType.REFREEZE,
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        GestureActionType.OPEN_NOTIFICATIONS,
        GestureActionType.OPEN_QUICK_SETTINGS,
        GestureActionType.LOCK_SCREEN,
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING,
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL,
        GestureActionType.POWER_MENU,
        GestureActionType.KEEP_SCREEN_ON,
        GestureActionType.SNOOZE_OVERLAYS,
        GestureActionType.CLICK_PASSTHROUGH,
        -> GestureActionCategory.System

        GestureActionType.MEDIA_PLAY_PAUSE,
        GestureActionType.MEDIA_PREVIOUS,
        GestureActionType.MEDIA_NEXT,
        -> GestureActionCategory.Media

        GestureActionType.SCREENSHOT,
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK,
        GestureActionType.REGIONAL_SCREENSHOT_PICK,
        -> GestureActionCategory.Capture

        GestureActionType.ADJUST_VOLUME,
        GestureActionType.ADJUST_BRIGHTNESS,
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS,
        GestureActionType.LAUNCH_ASSISTANT,
        -> GestureActionCategory.Adjust

        GestureActionType.SIMULATE_POINTER_SWIPE,
        GestureActionType.POINTER_GESTURE_RECORDER,
        GestureActionType.POINTER_REALTIME_GESTURE,
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU,
        -> GestureActionCategory.Pointer

        else -> GestureActionCategory.Other
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
        add(GestureAction.AppSwitcher)
        add(GestureAction.HolographicLauncher)
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
        GestureAction.ClipboardFloat,
        GestureAction.ClipboardPick,
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
        GestureAction.VolumePanel,
        GestureAction.ScreenTranslate,
        GestureAction.UniversalCopy,
        GestureAction.FreezerPanel,
        GestureAction.Remind,
        GestureAction.Refreeze,
        GestureAction.PowerMenu,
        GestureAction.KeepScreenOn,
        GestureAction.ScrollToTop,
        GestureAction.ScrollToBottom,
        GestureAction.AdjustVolume,
        GestureAction.AdjustBrightness,
        GestureAction.ToggleAutoBrightness,
        GestureAction.LaunchAssistant,
    )
}
