package com.slideindex.app.ui.gesturepicker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.ui.gestureExecuteShellCommandPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.quickLauncherPanelLabel
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.TaskManagerUtil

private fun resolveAppDisplayName(context: Context, packageName: String): String {
    if (packageName.isBlank()) return packageName
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}

private fun launchAppActionLabel(context: Context, packageName: String): String =
    if (packageName.isBlank()) {
        context.getString(R.string.gesture_action_launch_app)
    } else {
        context.getString(
            R.string.gesture_action_launch_app_named,
            resolveAppDisplayName(context, packageName),
        )
    }

fun filterGestureActions(
    context: Context,
    actions: List<GestureAction>,
    query: String,
): List<GestureAction> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return actions
    return actions.filter { action ->
        val label = gestureActionLabelText(context, action)
        label.lowercase().contains(q) ||
            PinyinHelper.sortKey(label).contains(q) ||
            gestureActionDescriptionText(context, action)?.lowercase()?.contains(q) == true
    }
}

fun gestureActionDescriptionText(context: Context, action: GestureAction): String? =
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume_desc)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness_desc)
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> context.getString(R.string.gesture_action_toggle_auto_brightness_desc)
        GestureActionType.TOGGLE_AUTO_ROTATE -> context.getString(R.string.gesture_action_toggle_auto_rotate_desc)
        GestureActionType.FORCE_PORTRAIT -> context.getString(R.string.gesture_action_force_portrait_desc)
        GestureActionType.FORCE_LANDSCAPE -> context.getString(R.string.gesture_action_force_landscape_desc)
        GestureActionType.VOICE_SEARCH -> context.getString(R.string.gesture_action_voice_search_desc)
        GestureActionType.VOICE_ASSISTANT -> context.getString(R.string.gesture_action_voice_assistant_desc)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top_desc)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom_desc)
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_fullscreen_screenshot_pick_desc)
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_regional_screenshot_pick_desc)
        GestureActionType.SEARCH_PANEL -> context.getString(R.string.gesture_action_search_panel_desc)
        GestureActionType.VOLUME_PANEL -> context.getString(R.string.gesture_action_volume_panel_desc)
        GestureActionType.SCREEN_TRANSLATE -> context.getString(R.string.gesture_action_screen_translate_desc)
        GestureActionType.REMIND -> context.getString(R.string.gesture_action_remind_desc)
        GestureActionType.UNIVERSAL_COPY -> context.getString(R.string.gesture_action_universal_copy_desc)
        GestureActionType.FREEZER_PANEL -> context.getString(R.string.gesture_action_freezer_panel_desc)
        GestureActionType.REFREEZE -> context.getString(R.string.gesture_action_refreeze_desc)
        GestureActionType.CLIPBOARD_PICK -> context.getString(R.string.gesture_action_clipboard_pick_desc)
        GestureActionType.POINTER_REALTIME_GESTURE -> context.getString(R.string.gesture_action_pointer_realtime_gesture_desc)
        GestureActionType.TOGGLE_MUTE -> context.getString(R.string.gesture_action_toggle_mute_desc)
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> context.getString(R.string.gesture_action_lock_screen_and_silence_ring_desc)
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> context.getString(R.string.gesture_action_lock_screen_and_mute_all_desc)
        GestureActionType.SNOOZE_OVERLAYS -> context.getString(R.string.gesture_action_snooze_overlays_desc)
        GestureActionType.OPEN_INTERNET_PANEL -> context.getString(R.string.gesture_action_open_internet_panel_desc)
        GestureActionType.OPEN_VOLUME_PANEL -> context.getString(R.string.gesture_action_open_volume_panel_desc)
        GestureActionType.ONE_HANDED_MODE -> context.getString(R.string.gesture_action_one_handed_mode_desc)
        GestureActionType.CURRENT_APP_INFO -> context.getString(R.string.gesture_action_current_app_info_desc)
        GestureActionType.SIMULATE_KEY_EVENT -> context.getString(R.string.gesture_action_simulate_key_event_desc)
        else -> null
    }

fun launchShortcutDisplayLabel(action: GestureAction.LaunchShortcut): String =
    action.label.ifBlank {
        GestureShortcutPayload.decode(action.payloadKey)?.label.orEmpty()
    }

fun gestureActionLabelText(context: Context, action: GestureAction): String = when (action) {
    is GestureAction.LaunchApp -> launchAppActionLabel(context, action.packageName)
    is GestureAction.LaunchShortcut -> {
        val shortcutLabel = launchShortcutDisplayLabel(action)
        if (shortcutLabel.isBlank()) {
            context.getString(R.string.gesture_action_launch_shortcut)
        } else {
            context.getString(R.string.gesture_action_launch_shortcut_named, shortcutLabel)
        }
    }
    is GestureAction.ExecuteShellCommand -> {
        if (action.command.isBlank()) {
            context.getString(R.string.gesture_action_execute_shell_command)
        } else {
            context.getString(
                R.string.gesture_action_execute_shell_command_named,
                gestureExecuteShellCommandPreview(action.command),
            )
        }
    }
    is GestureAction.SimulateKeyEvent -> {
        val name = if (action.keyName.isNotBlank()) action.keyName else com.slideindex.app.gesture.KeyEventPresets.getDisplayName(action.keyCode)
        context.getString(R.string.gesture_action_simulate_key_event_named, name)
    }
    else -> when (action.type) {
        GestureActionType.NONE -> context.getString(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> context.getString(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> context.getString(R.string.gesture_action_quick_launcher)
        GestureActionType.HONEYCOMB_LAUNCHER -> context.getString(R.string.gesture_action_honeycomb_launcher)
        GestureActionType.APP_SWITCHER -> context.getString(R.string.gesture_action_app_switcher)
        GestureActionType.HOLOGRAPHIC_LAUNCHER -> context.getString(R.string.gesture_action_holographic_launcher)
        GestureActionType.TASK_SWITCHER -> context.getString(R.string.gesture_action_task_switcher)
        GestureActionType.BACK -> context.getString(R.string.gesture_action_back)
        GestureActionType.HOME -> context.getString(R.string.gesture_action_home)
        GestureActionType.RECENTS -> context.getString(R.string.gesture_action_recents)
        GestureActionType.CLOSE_CURRENT_APP -> context.getString(R.string.gesture_action_close_current_app)
        GestureActionType.FREE_WINDOW_CURRENT_APP -> context.getString(R.string.gesture_action_free_window_current_app)
        GestureActionType.CLICK_PASSTHROUGH -> context.getString(R.string.gesture_action_click_passthrough)
        GestureActionType.FLASHLIGHT -> context.getString(R.string.gesture_action_flashlight)
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness)
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> context.getString(R.string.gesture_action_toggle_auto_brightness)
        GestureActionType.LAUNCH_ASSISTANT -> context.getString(R.string.gesture_action_launch_assistant)
        GestureActionType.VOICE_SEARCH -> context.getString(R.string.gesture_action_voice_search)
        GestureActionType.VOICE_ASSISTANT -> context.getString(R.string.gesture_action_voice_assistant)
        GestureActionType.TOGGLE_AUTO_ROTATE -> context.getString(R.string.gesture_action_toggle_auto_rotate)
        GestureActionType.FORCE_PORTRAIT -> context.getString(R.string.gesture_action_force_portrait)
        GestureActionType.FORCE_LANDSCAPE -> context.getString(R.string.gesture_action_force_landscape)
        GestureActionType.TOGGLE_MUTE -> context.getString(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> context.getString(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> context.getString(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> context.getString(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> context.getString(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> context.getString(R.string.gesture_action_open_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> context.getString(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> context.getString(R.string.gesture_action_lock_screen)
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> context.getString(R.string.gesture_action_lock_screen_and_silence_ring)
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> context.getString(R.string.gesture_action_lock_screen_and_mute_all)
        GestureActionType.SCREENSHOT -> context.getString(R.string.gesture_action_screenshot)
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_fullscreen_screenshot_pick)
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_regional_screenshot_pick)
        GestureActionType.SEARCH_PANEL -> context.getString(R.string.gesture_action_search_panel)
        GestureActionType.VOLUME_PANEL -> context.getString(R.string.gesture_action_volume_panel)
        GestureActionType.SCREEN_TRANSLATE -> context.getString(R.string.gesture_action_screen_translate)
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        -> context.getString(R.string.gesture_action_remind)
        GestureActionType.UNIVERSAL_COPY -> context.getString(R.string.gesture_action_universal_copy)
        GestureActionType.FREEZER_PANEL -> context.getString(R.string.gesture_action_freezer_panel)
        GestureActionType.REFREEZE -> context.getString(R.string.gesture_action_refreeze)
        GestureActionType.POWER_MENU -> context.getString(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> context.getString(R.string.gesture_action_keep_screen_on)
        GestureActionType.SNOOZE_OVERLAYS -> context.getString(R.string.gesture_action_snooze_overlays)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> context.getString(R.string.gesture_action_shell_command_panel)
        GestureActionType.EXECUTE_SHELL_COMMAND -> context.getString(R.string.gesture_action_execute_shell_command)
        GestureActionType.QUICK_TOOLS_OVERLAY -> context.getString(R.string.gesture_action_quick_tools_overlay)
        GestureActionType.WIDGET_POPUP_OVERLAY -> context.getString(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.OPEN_STASH_PANEL -> context.getString(R.string.gesture_action_stash_panel)
        GestureActionType.OPEN_CLIPBOARD_PANEL -> context.getString(R.string.gesture_action_clipboard_panel)
        GestureActionType.OPEN_CLIPBOARD_FLOAT -> context.getString(R.string.gesture_action_clipboard_float)
        GestureActionType.CLIPBOARD_PICK -> context.getString(R.string.gesture_action_clipboard_pick)
        GestureActionType.FLOATING_POINTER -> context.getString(R.string.gesture_action_floating_pointer)
        GestureActionType.SIMULATE_POINTER_SWIPE -> context.getString(R.string.gesture_action_pointer_swipe)
        GestureActionType.POINTER_GESTURE_RECORDER -> context.getString(R.string.gesture_action_pointer_gesture_recorder)
        GestureActionType.POINTER_REALTIME_GESTURE -> context.getString(R.string.gesture_action_pointer_realtime_gesture)
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> context.getString(R.string.gesture_action_open_floating_pointer_radial_menu)
        GestureActionType.TOGGLE_DND -> context.getString(R.string.gesture_action_toggle_dnd)
        GestureActionType.SCREEN_RECORD -> context.getString(R.string.gesture_action_screen_record)
        GestureActionType.TOGGLE_WIFI -> context.getString(R.string.gesture_action_toggle_wifi)
        GestureActionType.TOGGLE_MOBILE_DATA -> context.getString(R.string.gesture_action_toggle_mobile_data)
        GestureActionType.SWITCH_INPUT_METHOD -> context.getString(R.string.gesture_action_switch_input_method)
        GestureActionType.OPEN_INTERNET_PANEL -> context.getString(R.string.gesture_action_open_internet_panel)
        GestureActionType.OPEN_VOLUME_PANEL -> context.getString(R.string.gesture_action_open_volume_panel)
        GestureActionType.ONE_HANDED_MODE -> context.getString(R.string.gesture_action_one_handed_mode)
        GestureActionType.CURRENT_APP_INFO -> context.getString(R.string.gesture_action_current_app_info)
        GestureActionType.SIMULATE_KEY_EVENT -> context.getString(R.string.gesture_action_simulate_key_event)
        GestureActionType.CORNER_INNER_CANCEL -> context.getString(R.string.gesture_action_corner_inner_cancel)
        GestureActionType.CORNER_INNER_PIN_WHEEL -> context.getString(R.string.gesture_action_corner_inner_pin_wheel)
        GestureActionType.LAUNCH_APP -> context.getString(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> context.getString(R.string.gesture_action_launch_shortcut)
    }
}

fun gestureActionSortKey(context: Context, action: GestureAction): String =
    PinyinHelper.sortKey(gestureActionLabelText(context, action))

@Composable
fun gestureActionLabel(action: GestureAction, settings: AppSettings? = null): String {
    val appRepository = rememberAppRepository()
    return when (action) {
    is GestureAction.LaunchApp -> {
        if (action.packageName.isBlank()) {
            stringResource(R.string.gesture_action_launch_app)
        } else {
            val appLabel = appRepository.ensureAppInfo(action.packageName)?.label
                ?: resolveAppDisplayName(LocalContext.current, action.packageName)
            stringResource(R.string.gesture_action_launch_app_named, appLabel)
        }
    }
    is GestureAction.LaunchShortcut -> {
        val label = launchShortcutDisplayLabel(action)
        if (label.isBlank()) {
            stringResource(R.string.gesture_action_launch_shortcut)
        } else {
            label
        }
    }
    is GestureAction.QuickLauncher -> {
        if (action.panelId.isBlank() || settings == null) {
            stringResource(R.string.gesture_action_quick_launcher)
        } else {
            stringResource(
                R.string.gesture_action_quick_launcher_named,
                quickLauncherPanelLabel(settings, action.panelId),
            )
        }
    }
    is GestureAction.ExecuteShellCommand -> {
        if (action.command.isBlank()) {
            stringResource(R.string.gesture_action_execute_shell_command)
        } else {
            stringResource(
                R.string.gesture_action_execute_shell_command_named,
                gestureExecuteShellCommandPreview(action.command),
            )
        }
    }
    is GestureAction.SimulateKeyEvent -> {
        if (action.keyName.isBlank() || (action.keyCode == 82 && action.keyName == "KEYCODE_MENU")) {
            stringResource(R.string.gesture_action_simulate_key_event)
        } else {
            val name = if (action.keyName.isNotBlank()) action.keyName else com.slideindex.app.gesture.KeyEventPresets.getDisplayName(action.keyCode)
            stringResource(R.string.gesture_action_simulate_key_event_named, name)
        }
    }
    else -> when (action.type) {
        GestureActionType.NONE -> stringResource(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> stringResource(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> stringResource(R.string.gesture_action_quick_launcher)
        GestureActionType.HONEYCOMB_LAUNCHER -> stringResource(R.string.gesture_action_honeycomb_launcher)
        GestureActionType.APP_SWITCHER -> stringResource(R.string.gesture_action_app_switcher)
        GestureActionType.HOLOGRAPHIC_LAUNCHER -> stringResource(R.string.gesture_action_holographic_launcher)
        GestureActionType.TASK_SWITCHER -> stringResource(R.string.gesture_action_task_switcher)
        GestureActionType.BACK -> stringResource(R.string.gesture_action_back)
        GestureActionType.HOME -> stringResource(R.string.gesture_action_home)
        GestureActionType.RECENTS -> stringResource(R.string.gesture_action_recents)
        GestureActionType.CLOSE_CURRENT_APP -> stringResource(R.string.gesture_action_close_current_app)
        GestureActionType.FREE_WINDOW_CURRENT_APP -> stringResource(R.string.gesture_action_free_window_current_app)
        GestureActionType.CLICK_PASSTHROUGH -> stringResource(R.string.gesture_action_click_passthrough)
        GestureActionType.FLASHLIGHT -> stringResource(R.string.gesture_action_flashlight)
        GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume)
        GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness)
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> stringResource(R.string.gesture_action_toggle_auto_brightness)
        GestureActionType.LAUNCH_ASSISTANT -> stringResource(R.string.gesture_action_launch_assistant)
        GestureActionType.VOICE_SEARCH -> stringResource(R.string.gesture_action_voice_search)
        GestureActionType.VOICE_ASSISTANT -> stringResource(R.string.gesture_action_voice_assistant)
        GestureActionType.TOGGLE_AUTO_ROTATE -> stringResource(R.string.gesture_action_toggle_auto_rotate)
        GestureActionType.FORCE_PORTRAIT -> stringResource(R.string.gesture_action_force_portrait)
        GestureActionType.FORCE_LANDSCAPE -> stringResource(R.string.gesture_action_force_landscape)
        GestureActionType.TOGGLE_MUTE -> stringResource(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> stringResource(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> stringResource(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> stringResource(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> stringResource(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> stringResource(R.string.gesture_action_open_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> stringResource(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> stringResource(R.string.gesture_action_lock_screen)
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> stringResource(R.string.gesture_action_lock_screen_and_silence_ring)
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> stringResource(R.string.gesture_action_lock_screen_and_mute_all)
        GestureActionType.SCREENSHOT -> stringResource(R.string.gesture_action_screenshot)
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_fullscreen_screenshot_pick)
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_regional_screenshot_pick)
        GestureActionType.SEARCH_PANEL -> stringResource(R.string.gesture_action_search_panel)
        GestureActionType.VOLUME_PANEL -> stringResource(R.string.gesture_action_volume_panel)
        GestureActionType.SCREEN_TRANSLATE -> stringResource(R.string.gesture_action_screen_translate)
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        -> stringResource(R.string.gesture_action_remind)
        GestureActionType.UNIVERSAL_COPY -> stringResource(R.string.gesture_action_universal_copy)
        GestureActionType.FREEZER_PANEL -> stringResource(R.string.gesture_action_freezer_panel)
        GestureActionType.REFREEZE -> stringResource(R.string.gesture_action_refreeze)
        GestureActionType.POWER_MENU -> stringResource(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> stringResource(R.string.gesture_action_keep_screen_on)
        GestureActionType.SNOOZE_OVERLAYS -> stringResource(R.string.gesture_action_snooze_overlays)
        GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> stringResource(R.string.gesture_action_shell_command_panel)
        GestureActionType.EXECUTE_SHELL_COMMAND -> stringResource(R.string.gesture_action_execute_shell_command)
        GestureActionType.QUICK_TOOLS_OVERLAY -> stringResource(R.string.gesture_action_quick_tools_overlay)
        GestureActionType.WIDGET_POPUP_OVERLAY -> stringResource(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.OPEN_STASH_PANEL -> stringResource(R.string.gesture_action_stash_panel)
        GestureActionType.OPEN_CLIPBOARD_PANEL -> stringResource(R.string.gesture_action_clipboard_panel)
        GestureActionType.OPEN_CLIPBOARD_FLOAT -> stringResource(R.string.gesture_action_clipboard_float)
        GestureActionType.CLIPBOARD_PICK -> stringResource(R.string.gesture_action_clipboard_pick)
        GestureActionType.FLOATING_POINTER -> stringResource(R.string.gesture_action_floating_pointer)
        GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe)
        GestureActionType.POINTER_GESTURE_RECORDER -> stringResource(R.string.gesture_action_pointer_gesture_recorder)
        GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_pointer_realtime_gesture)
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> stringResource(R.string.gesture_action_open_floating_pointer_radial_menu)
        GestureActionType.TOGGLE_DND -> stringResource(R.string.gesture_action_toggle_dnd)
        GestureActionType.SCREEN_RECORD -> stringResource(R.string.gesture_action_screen_record)
        GestureActionType.TOGGLE_WIFI -> stringResource(R.string.gesture_action_toggle_wifi)
        GestureActionType.TOGGLE_MOBILE_DATA -> stringResource(R.string.gesture_action_toggle_mobile_data)
        GestureActionType.SWITCH_INPUT_METHOD -> stringResource(R.string.gesture_action_switch_input_method)
        GestureActionType.OPEN_INTERNET_PANEL -> stringResource(R.string.gesture_action_open_internet_panel)
        GestureActionType.OPEN_VOLUME_PANEL -> stringResource(R.string.gesture_action_open_volume_panel)
        GestureActionType.ONE_HANDED_MODE -> stringResource(R.string.gesture_action_one_handed_mode)
        GestureActionType.CURRENT_APP_INFO -> stringResource(R.string.gesture_action_current_app_info)
        GestureActionType.SIMULATE_KEY_EVENT -> stringResource(R.string.gesture_action_simulate_key_event)
        GestureActionType.CORNER_INNER_CANCEL -> stringResource(R.string.gesture_action_corner_inner_cancel)
        GestureActionType.CORNER_INNER_PIN_WHEEL -> stringResource(R.string.gesture_action_corner_inner_pin_wheel)
        GestureActionType.LAUNCH_APP -> stringResource(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> stringResource(R.string.gesture_action_launch_shortcut)
    }
    }
}

@Composable
fun gestureActionSettingSubtitle(action: GestureAction): String {
    return when (action) {
        is GestureAction.ExecuteShellCommand -> {
            if (action.command.isBlank()) {
                stringResource(R.string.gesture_action_execute_shell_command)
            } else {
                stringResource(
                    R.string.gesture_action_execute_shell_command_named,
                    gestureExecuteShellCommandPreview(action.command, maxLength = 28),
                )
            }
        }
        is GestureAction.SimulateKeyEvent -> {
            val name = if (action.keyName.isNotBlank()) action.keyName else com.slideindex.app.gesture.KeyEventPresets.getDisplayName(action.keyCode)
            stringResource(R.string.gesture_action_simulate_key_event_named, name)
        }
        else -> gestureActionLabel(action)
    }
}

@Composable
fun gestureActionDescription(action: GestureAction): String? = when (action.type) {
    GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume_desc)
    GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness_desc)
    GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> stringResource(R.string.gesture_action_toggle_auto_brightness_desc)
    GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top_desc)
    GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom_desc)
    GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_fullscreen_screenshot_pick_desc)
    GestureActionType.REGIONAL_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_regional_screenshot_pick_desc)
    GestureActionType.SEARCH_PANEL -> stringResource(R.string.gesture_action_search_panel_desc)
    GestureActionType.VOLUME_PANEL -> stringResource(R.string.gesture_action_volume_panel_desc)
    GestureActionType.SCREEN_TRANSLATE -> stringResource(R.string.gesture_action_screen_translate_desc)
    GestureActionType.UNIVERSAL_COPY -> stringResource(R.string.gesture_action_universal_copy_desc)
    GestureActionType.FREEZER_PANEL -> stringResource(R.string.gesture_action_freezer_panel_desc)
    GestureActionType.REFREEZE -> stringResource(R.string.gesture_action_refreeze_desc)
    GestureActionType.CLIPBOARD_PICK -> stringResource(R.string.gesture_action_clipboard_pick_desc)
    GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe_desc)
    GestureActionType.POINTER_GESTURE_RECORDER -> stringResource(R.string.gesture_action_pointer_gesture_recorder_desc)
    GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_pointer_realtime_gesture_desc)
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> stringResource(R.string.gesture_action_open_floating_pointer_radial_menu_desc)
    GestureActionType.TOGGLE_MUTE -> stringResource(R.string.gesture_action_toggle_mute_desc)
    GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> stringResource(R.string.gesture_action_lock_screen_and_silence_ring_desc)
    GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> stringResource(R.string.gesture_action_lock_screen_and_mute_all_desc)
    GestureActionType.OPEN_INTERNET_PANEL -> stringResource(R.string.gesture_action_open_internet_panel_desc)
    GestureActionType.OPEN_VOLUME_PANEL -> stringResource(R.string.gesture_action_open_volume_panel_desc)
    GestureActionType.ONE_HANDED_MODE -> stringResource(R.string.gesture_action_one_handed_mode_desc)
    GestureActionType.CURRENT_APP_INFO -> stringResource(R.string.gesture_action_current_app_info_desc)
    GestureActionType.SIMULATE_KEY_EVENT -> stringResource(R.string.gesture_action_simulate_key_event_desc)
    GestureActionType.CORNER_INNER_CANCEL -> stringResource(R.string.gesture_action_corner_inner_cancel_desc)
    GestureActionType.CORNER_INNER_PIN_WHEEL -> stringResource(R.string.gesture_action_corner_inner_pin_wheel_desc)
    GestureActionType.SNOOZE_OVERLAYS -> stringResource(R.string.gesture_action_snooze_overlays_desc)
    else -> null
}

fun gestureActionMinSdk(action: GestureAction): Int? = when (action.type) {
    GestureActionType.POINTER_REALTIME_GESTURE -> 36
    else -> null
}

fun isGestureActionEnabledOnDevice(action: GestureAction): Boolean {
    val minSdk = gestureActionMinSdk(action) ?: return true
    return Build.VERSION.SDK_INT >= minSdk
}

@Composable
fun gestureActionRequirementHint(action: GestureAction): String? = when (action.type) {
    GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_require_min_sdk_36)
    else -> null
}

@Composable
fun gestureActionPermissionHint(action: GestureAction, context: Context): String? =
    gestureActionPermissionHintText(context, action)

fun gestureActionPermissionHintText(context: Context, action: GestureAction): String? =
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_adjust_volume_permission)
        }
        GestureActionType.ADJUST_BRIGHTNESS -> {
            if (PermissionHelper.canWriteSettings(context)) return null
            context.getString(R.string.gesture_action_adjust_brightness_permission)
        }
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> {
            if (PermissionHelper.canWriteSettings(context)) return null
            context.getString(R.string.gesture_action_adjust_brightness_permission)
        }
        GestureActionType.TOGGLE_MUTE -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING,
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL,
        -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_DND -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_WIFI, GestureActionType.TOGGLE_MOBILE_DATA,
        GestureActionType.EXECUTE_SHELL_COMMAND,
        -> {
            if (TaskManagerUtil.hasPermission()) return null
            context.getString(R.string.gesture_action_toggle_shell_permission)
        }
        GestureActionType.FLASHLIGHT -> {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            context.getString(R.string.gesture_action_flashlight_permission)
        }
        GestureActionType.SCREEN_RECORD -> {
            if (PermissionHelper.canDrawOverlays(context)) return null
            context.getString(R.string.gesture_action_screen_record_permission)
        }
        GestureActionType.LOCK_SCREEN, GestureActionType.SCREENSHOT -> null
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_fullscreen_screenshot_pick_permission)
        }
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_regional_screenshot_pick_permission)
        }
        GestureActionType.SCROLL_TO_TOP, GestureActionType.SCROLL_TO_BOTTOM -> null
        GestureActionType.QUICK_TOOLS_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_quick_tools_overlay_permission)
        }
        GestureActionType.HOLOGRAPHIC_LAUNCHER -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_quick_tools_overlay_permission)
        }
        GestureActionType.WIDGET_POPUP_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_widget_popup_overlay_permission)
        }
        GestureActionType.OPEN_STASH_PANEL -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_stash_panel_permission)
        }
        GestureActionType.OPEN_CLIPBOARD_PANEL -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_clipboard_panel_permission)
        }
        GestureActionType.OPEN_CLIPBOARD_FLOAT -> {
            if (PermissionHelper.canDrawOverlays(context) &&
                PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
            ) return null
            context.getString(R.string.gesture_action_clipboard_float_permission)
        }
        GestureActionType.CLIPBOARD_PICK -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_clipboard_pick_permission)
        }
        GestureActionType.FLOATING_POINTER -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_floating_pointer_permission)
        }
        GestureActionType.SIMULATE_POINTER_SWIPE -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_pointer_swipe_permission)
        }
        GestureActionType.POINTER_GESTURE_RECORDER,
        GestureActionType.POINTER_REALTIME_GESTURE,
        -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_pointer_gesture_record_permission)
        }
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_open_floating_pointer_radial_menu_permission)
        }
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        -> {
            if (PermissionHelper.canDrawOverlays(context)) return null
            context.getString(R.string.gesture_action_remind_permission)
        }
        else -> null
    }

fun requestPermissionForAdjustAction(context: Context, action: GestureAction) {
    when (action) {
        GestureAction.AdjustVolume, GestureAction.ToggleMute, GestureAction.ToggleDnd,
        GestureAction.LockScreenAndSilenceRing, GestureAction.LockScreenAndMuteAll,
        ->
            PermissionHelper.requestNotificationPolicyAccess(context)
        GestureAction.AdjustBrightness,
        GestureAction.ToggleAutoBrightness,
        ->
            PermissionHelper.requestWriteSettingsAccess(context)
        GestureAction.Flashlight -> {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
        GestureAction.QuickToolsOverlay -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.HolographicLauncher -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.WidgetPopupOverlay -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.StashPanel -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardPanel -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardFloat -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            } else if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardPick -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.FloatingPointer -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        is GestureAction.SimulatePointerSwipe -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.PointerGestureRecorder,
        GestureAction.PointerRealtimeGesture,
        -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.OpenFloatingPointerRadialMenu -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ScreenRecord -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            }
        }
        GestureAction.FullscreenScreenshotPick -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.RegionalScreenshotPick -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.SearchPanel -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.Remind -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            }
        }
        else -> Unit
    }
}
