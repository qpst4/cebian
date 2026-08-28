package com.slideindex.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.overlay.PanelSide

internal fun panelSideSwipeOutlinedIcon(side: PanelSide): ImageVector = when (side) {
    PanelSide.LEFT -> ThinActionIcons.Back
    PanelSide.RIGHT -> ThinActionIcons.ArrowRight
    PanelSide.TOP -> ThinActionIcons.ArrowUp
    PanelSide.BOTTOM -> ThinActionIcons.ArrowDown
}

internal fun pointerSwipeDirectionOutlinedIcon(
    direction: com.slideindex.app.gesture.PointerSwipeDirection,
): ImageVector = when (direction) {
    com.slideindex.app.gesture.PointerSwipeDirection.LEFT -> ThinActionIcons.Back
    com.slideindex.app.gesture.PointerSwipeDirection.RIGHT -> ThinActionIcons.ArrowRight
    com.slideindex.app.gesture.PointerSwipeDirection.UP -> ThinActionIcons.ArrowUp
    com.slideindex.app.gesture.PointerSwipeDirection.DOWN -> ThinActionIcons.ArrowDown
}

/** 动作图标（outlined / 浮窗 raster）统一走自绘 [ThinActionIcons]。 */
@Suppress("DEPRECATION")
internal fun gestureActionTypeOutlinedIcon(type: GestureActionType): ImageVector = when (type) {
    GestureActionType.NONE -> ThinActionIcons.Block
    GestureActionType.OPEN_INDEX -> ThinActionIcons.SortByAlpha
    GestureActionType.QUICK_LAUNCHER -> ThinActionIcons.Apps
    GestureActionType.HONEYCOMB_LAUNCHER -> ThinActionIcons.Hive
    GestureActionType.APP_SWITCHER -> ThinActionIcons.Apps
    GestureActionType.HOLOGRAPHIC_LAUNCHER -> ThinActionIcons.Globe
    GestureActionType.TASK_SWITCHER -> ThinActionIcons.ViewCarousel
    GestureActionType.SHELL_COMMAND_PANEL -> ThinActionIcons.Code
    GestureActionType.EXECUTE_SHELL_COMMAND -> ThinActionIcons.PlayCircle
    GestureActionType.QUICK_TOOLS_OVERLAY -> ThinActionIcons.QuickTools
    GestureActionType.WIDGET_POPUP_OVERLAY -> ThinActionIcons.Widgets
    GestureActionType.OPEN_STASH_PANEL -> ThinActionIcons.Inventory
    GestureActionType.OPEN_CLIPBOARD_PANEL -> ThinActionIcons.ContentPaste
    GestureActionType.OPEN_CLIPBOARD_FLOAT -> ThinActionIcons.ContentPaste
    GestureActionType.CLIPBOARD_PICK -> ThinActionIcons.TextFields
    GestureActionType.FLOATING_POINTER -> ThinActionIcons.MyLocation
    GestureActionType.SIMULATE_POINTER_SWIPE -> ThinActionIcons.TouchApp
    GestureActionType.BACK -> ThinActionIcons.Back
    GestureActionType.HOME -> ThinActionIcons.Home
    GestureActionType.RECENTS -> ThinActionIcons.Recents
    GestureActionType.CLOSE_CURRENT_APP -> ThinActionIcons.Close
    GestureActionType.FREE_WINDOW_CURRENT_APP -> ThinActionIcons.FreeWindow
    GestureActionType.CLICK_PASSTHROUGH -> ThinActionIcons.ClickPassthrough
    GestureActionType.FLASHLIGHT -> ThinActionIcons.Flashlight
    GestureActionType.ADJUST_VOLUME -> ThinActionIcons.VolumeUp
    GestureActionType.ADJUST_BRIGHTNESS -> ThinActionIcons.Brightness
    GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> ThinActionIcons.BrightnessAuto
    GestureActionType.LAUNCH_ASSISTANT -> ThinActionIcons.Assistant
    GestureActionType.TOGGLE_MUTE -> ThinActionIcons.VolumeOff
    GestureActionType.MEDIA_PLAY_PAUSE -> ThinActionIcons.PlayPause
    GestureActionType.MEDIA_PREVIOUS -> ThinActionIcons.SkipPrevious
    GestureActionType.MEDIA_NEXT -> ThinActionIcons.SkipNext
    GestureActionType.PREVIOUS_APP -> ThinActionIcons.Restore
    GestureActionType.OPEN_NOTIFICATIONS -> ThinActionIcons.Notifications
    GestureActionType.OPEN_QUICK_SETTINGS -> ThinActionIcons.QuickSettings
    GestureActionType.LOCK_SCREEN -> ThinActionIcons.Lock
    GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> ThinActionIcons.LockSilenceRing
    GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> ThinActionIcons.LockMuteAll
    GestureActionType.SCREENSHOT -> ThinActionIcons.Screenshot
    GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> ThinActionIcons.TextFields
    GestureActionType.REGIONAL_SCREENSHOT_PICK -> ThinActionIcons.ScreenshotRegion
    GestureActionType.SEARCH_PANEL -> ThinActionIcons.Search
    GestureActionType.VOLUME_PANEL -> ThinActionIcons.VolumeUp
    GestureActionType.SCREEN_TRANSLATE -> ThinActionIcons.TextFields
    GestureActionType.REMIND,
    GestureActionType.REMIND_1M, GestureActionType.REMIND_3M, GestureActionType.REMIND_5M,
    GestureActionType.REMIND_10M, GestureActionType.REMIND_15M -> ThinActionIcons.Alarm
    GestureActionType.UNIVERSAL_COPY -> ThinActionIcons.ContentPaste
    GestureActionType.FREEZER_PANEL -> ThinActionIcons.Fridge
    GestureActionType.REFREEZE -> ThinActionIcons.Snowflake
    GestureActionType.POWER_MENU -> ThinActionIcons.Power
    GestureActionType.KEEP_SCREEN_ON -> ThinActionIcons.KeepScreenOn
    GestureActionType.SCROLL_TO_TOP -> ThinActionIcons.ScrollToTop
    GestureActionType.SCROLL_TO_BOTTOM -> ThinActionIcons.ScrollToBottom
    GestureActionType.TOGGLE_DND -> ThinActionIcons.DoNotDisturb
    GestureActionType.SCREEN_RECORD -> ThinActionIcons.ScreenRecord
    GestureActionType.TOGGLE_WIFI -> ThinActionIcons.Wifi
    GestureActionType.TOGGLE_MOBILE_DATA -> ThinActionIcons.Cellular
    GestureActionType.SWITCH_INPUT_METHOD -> ThinActionIcons.Keyboard
    GestureActionType.POINTER_GESTURE_RECORDER -> ThinActionIcons.Gesture
    GestureActionType.POINTER_REALTIME_GESTURE -> ThinActionIcons.Gesture
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> ThinActionIcons.MenuOpen
    GestureActionType.LAUNCH_APP -> ThinActionIcons.Apps
    GestureActionType.LAUNCH_SHORTCUT -> ThinActionIcons.Shortcut
    GestureActionType.CORNER_INNER_CANCEL -> ThinActionIcons.Close
    GestureActionType.CORNER_INNER_PIN_WHEEL -> ThinActionIcons.TouchApp
    GestureActionType.SNOOZE_OVERLAYS -> ThinActionIcons.VisibilityOff
}

@Suppress("DEPRECATION")
internal fun gestureActionTypeThinIcon(type: GestureActionType): ImageVector = when (type) {
    GestureActionType.NONE -> ThinActionIcons.Block
    GestureActionType.OPEN_INDEX -> ThinActionIcons.SortByAlpha
    GestureActionType.QUICK_LAUNCHER -> ThinActionIcons.Apps
    GestureActionType.HONEYCOMB_LAUNCHER -> ThinActionIcons.Hive
    GestureActionType.APP_SWITCHER -> ThinActionIcons.Apps
    GestureActionType.HOLOGRAPHIC_LAUNCHER -> ThinActionIcons.Globe
    GestureActionType.TASK_SWITCHER -> ThinActionIcons.ViewCarousel
    GestureActionType.SHELL_COMMAND_PANEL -> ThinActionIcons.Code
    GestureActionType.EXECUTE_SHELL_COMMAND -> ThinActionIcons.Code
    GestureActionType.QUICK_TOOLS_OVERLAY -> ThinActionIcons.QuickTools
    GestureActionType.WIDGET_POPUP_OVERLAY -> ThinActionIcons.Widgets
    GestureActionType.OPEN_STASH_PANEL -> ThinActionIcons.Inventory
    GestureActionType.OPEN_CLIPBOARD_PANEL -> ThinActionIcons.ContentPaste
    GestureActionType.OPEN_CLIPBOARD_FLOAT -> ThinActionIcons.ContentPaste
    GestureActionType.CLIPBOARD_PICK -> ThinActionIcons.TextFields
    GestureActionType.FLOATING_POINTER -> ThinActionIcons.MyLocation
    GestureActionType.SIMULATE_POINTER_SWIPE -> ThinActionIcons.TouchApp
    GestureActionType.BACK -> ThinActionIcons.Back
    GestureActionType.HOME -> ThinActionIcons.Home
    GestureActionType.RECENTS -> ThinActionIcons.Recents
    GestureActionType.CLOSE_CURRENT_APP -> ThinActionIcons.Close
    GestureActionType.FREE_WINDOW_CURRENT_APP -> ThinActionIcons.FreeWindow
    GestureActionType.CLICK_PASSTHROUGH -> ThinActionIcons.ClickPassthrough
    GestureActionType.FLASHLIGHT -> ThinActionIcons.Flashlight
    GestureActionType.ADJUST_VOLUME -> ThinActionIcons.VolumeUp
    GestureActionType.ADJUST_BRIGHTNESS -> ThinActionIcons.Brightness
    GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> ThinActionIcons.BrightnessAuto
    GestureActionType.LAUNCH_ASSISTANT -> ThinActionIcons.Assistant
    GestureActionType.TOGGLE_MUTE -> ThinActionIcons.VolumeOff
    GestureActionType.MEDIA_PLAY_PAUSE -> ThinActionIcons.PlayPause
    GestureActionType.MEDIA_PREVIOUS -> ThinActionIcons.SkipPrevious
    GestureActionType.MEDIA_NEXT -> ThinActionIcons.SkipNext
    GestureActionType.PREVIOUS_APP -> ThinActionIcons.Restore
    GestureActionType.OPEN_NOTIFICATIONS -> ThinActionIcons.Notifications
    GestureActionType.OPEN_QUICK_SETTINGS -> ThinActionIcons.QuickSettings
    GestureActionType.LOCK_SCREEN -> ThinActionIcons.Lock
    GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> ThinActionIcons.LockSilenceRing
    GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> ThinActionIcons.LockMuteAll
    GestureActionType.SCREENSHOT -> ThinActionIcons.Screenshot
    GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> ThinActionIcons.TextFields
    GestureActionType.REGIONAL_SCREENSHOT_PICK -> ThinActionIcons.ScreenshotRegion
    GestureActionType.SEARCH_PANEL -> ThinActionIcons.Search
    GestureActionType.VOLUME_PANEL -> ThinActionIcons.VolumeUp
    GestureActionType.SCREEN_TRANSLATE -> ThinActionIcons.TextFields
    GestureActionType.REMIND,
    GestureActionType.REMIND_1M, GestureActionType.REMIND_3M, GestureActionType.REMIND_5M,
    GestureActionType.REMIND_10M, GestureActionType.REMIND_15M -> ThinActionIcons.Alarm
    GestureActionType.UNIVERSAL_COPY -> ThinActionIcons.ContentPaste
    GestureActionType.FREEZER_PANEL -> ThinActionIcons.Fridge
    GestureActionType.REFREEZE -> ThinActionIcons.Snowflake
    GestureActionType.POWER_MENU -> ThinActionIcons.Power
    GestureActionType.KEEP_SCREEN_ON -> ThinActionIcons.KeepScreenOn
    GestureActionType.SCROLL_TO_TOP -> ThinActionIcons.ScrollToTop
    GestureActionType.SCROLL_TO_BOTTOM -> ThinActionIcons.ScrollToBottom
    GestureActionType.TOGGLE_DND -> ThinActionIcons.DoNotDisturb
    GestureActionType.SCREEN_RECORD -> ThinActionIcons.ScreenRecord
    GestureActionType.TOGGLE_WIFI -> ThinActionIcons.Wifi
    GestureActionType.TOGGLE_MOBILE_DATA -> ThinActionIcons.Cellular
    GestureActionType.SWITCH_INPUT_METHOD -> ThinActionIcons.Keyboard
    GestureActionType.POINTER_GESTURE_RECORDER -> ThinActionIcons.Gesture
    GestureActionType.POINTER_REALTIME_GESTURE -> ThinActionIcons.Gesture
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> ThinActionIcons.MenuOpen
    GestureActionType.LAUNCH_APP -> ThinActionIcons.Apps
    GestureActionType.LAUNCH_SHORTCUT -> ThinActionIcons.Shortcut
    GestureActionType.CORNER_INNER_CANCEL -> ThinActionIcons.Close
    GestureActionType.CORNER_INNER_PIN_WHEEL -> ThinActionIcons.TouchApp
    GestureActionType.SNOOZE_OVERLAYS -> ThinActionIcons.VisibilityOff
}
