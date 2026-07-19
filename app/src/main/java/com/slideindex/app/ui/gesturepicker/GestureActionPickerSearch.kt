package com.slideindex.app.ui.gesturepicker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureShortcutPayload
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
    val sorted = actions.sortedBy { gestureActionSortKey(context, it) }
    val q = query.trim().lowercase()
    if (q.isEmpty()) return sorted
    return sorted.filter { action ->
        val label = gestureActionLabelText(context, action)
        label.lowercase().contains(q) ||
            PinyinHelper.sortKey(label).contains(q) ||
            gestureActionDescriptionText(context, action)?.lowercase()?.contains(q) == true
    }
}

private fun gestureActionDescriptionText(context: Context, action: GestureAction): String? =
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume_desc)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness_desc)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top_desc)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom_desc)
        GestureActionType.POINTER_REALTIME_GESTURE -> context.getString(R.string.gesture_action_pointer_realtime_gesture_desc)
        else -> null
    }

fun gestureActionLabelText(context: Context, action: GestureAction): String = when (action) {
    is GestureAction.LaunchApp -> launchAppActionLabel(context, action.packageName)
    is GestureAction.LaunchShortcut -> {
        val shortcutLabel = action.label.ifBlank {
            GestureShortcutPayload.decode(action.payloadKey)?.label.orEmpty()
        }
        if (shortcutLabel.isBlank()) {
            context.getString(R.string.gesture_action_launch_shortcut)
        } else {
            context.getString(R.string.gesture_action_launch_shortcut_named, shortcutLabel)
        }
    }
    else -> when (action.type) {
        GestureActionType.NONE -> context.getString(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> context.getString(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> context.getString(R.string.gesture_action_quick_launcher)
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
        GestureActionType.LAUNCH_ASSISTANT -> context.getString(R.string.gesture_action_launch_assistant)
        GestureActionType.TOGGLE_MUTE -> context.getString(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> context.getString(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> context.getString(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> context.getString(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> context.getString(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> context.getString(R.string.gesture_action_open_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> context.getString(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> context.getString(R.string.gesture_action_lock_screen)
        GestureActionType.SCREENSHOT -> context.getString(R.string.gesture_action_screenshot)
        GestureActionType.POWER_MENU -> context.getString(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> context.getString(R.string.gesture_action_keep_screen_on)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> context.getString(R.string.gesture_action_shell_command_panel)
        GestureActionType.QUICK_TOOLS_OVERLAY -> context.getString(R.string.gesture_action_quick_tools_overlay)
        GestureActionType.WIDGET_POPUP_OVERLAY -> context.getString(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.OPEN_STASH_PANEL -> context.getString(R.string.gesture_action_stash_panel)
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
        GestureActionType.LAUNCH_APP -> context.getString(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> context.getString(R.string.gesture_action_launch_shortcut)
    }
}

fun gestureActionSortKey(context: Context, action: GestureAction): String =
    PinyinHelper.sortKey(gestureActionLabelText(context, action))

@Composable
fun gestureActionLabel(action: GestureAction): String {
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
        val label = action.label.ifBlank {
            GestureShortcutPayload.decode(action.payloadKey)?.label.orEmpty()
        }
        if (label.isBlank()) {
            stringResource(R.string.gesture_action_launch_shortcut)
        } else {
            stringResource(R.string.gesture_action_launch_shortcut_named, label)
        }
    }
    is GestureAction.SimulatePointerSwipe -> stringResource(R.string.gesture_action_pointer_swipe)
    else -> when (action.type) {
        GestureActionType.NONE -> stringResource(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> stringResource(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> stringResource(R.string.gesture_action_quick_launcher)
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
        GestureActionType.LAUNCH_ASSISTANT -> stringResource(R.string.gesture_action_launch_assistant)
        GestureActionType.TOGGLE_MUTE -> stringResource(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> stringResource(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> stringResource(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> stringResource(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> stringResource(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> stringResource(R.string.gesture_action_open_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> stringResource(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> stringResource(R.string.gesture_action_lock_screen)
        GestureActionType.SCREENSHOT -> stringResource(R.string.gesture_action_screenshot)
        GestureActionType.POWER_MENU -> stringResource(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> stringResource(R.string.gesture_action_keep_screen_on)
        GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> stringResource(R.string.gesture_action_shell_command_panel)
        GestureActionType.QUICK_TOOLS_OVERLAY -> stringResource(R.string.gesture_action_quick_tools_overlay)
        GestureActionType.WIDGET_POPUP_OVERLAY -> stringResource(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.OPEN_STASH_PANEL -> stringResource(R.string.gesture_action_stash_panel)
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
        GestureActionType.LAUNCH_APP -> stringResource(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> stringResource(R.string.gesture_action_launch_shortcut)
    }
    }
}

@Composable
fun gestureActionDescription(action: GestureAction): String? = when (action.type) {
    GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume_desc)
    GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness_desc)
    GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top_desc)
    GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom_desc)
    GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe_desc)
    GestureActionType.POINTER_GESTURE_RECORDER -> stringResource(R.string.gesture_action_pointer_gesture_recorder_desc)
    GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_pointer_realtime_gesture_desc)
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> stringResource(R.string.gesture_action_open_floating_pointer_radial_menu_desc)
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
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_adjust_volume_permission)
        }
        GestureActionType.ADJUST_BRIGHTNESS -> {
            if (PermissionHelper.canWriteSettings(context)) return null
            stringResource(R.string.gesture_action_adjust_brightness_permission)
        }
        GestureActionType.TOGGLE_MUTE -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_DND -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_WIFI, GestureActionType.TOGGLE_MOBILE_DATA -> {
            if (TaskManagerUtil.hasPermission()) return null
            stringResource(R.string.gesture_action_toggle_shell_permission)
        }
        GestureActionType.SCREEN_RECORD -> {
            if (PermissionHelper.canDrawOverlays(context)) return null
            stringResource(R.string.gesture_action_screen_record_permission)
        }
        GestureActionType.LOCK_SCREEN, GestureActionType.SCREENSHOT -> null
        GestureActionType.SCROLL_TO_TOP, GestureActionType.SCROLL_TO_BOTTOM -> null
        GestureActionType.QUICK_TOOLS_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_quick_tools_overlay_permission)
        }
        GestureActionType.WIDGET_POPUP_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_widget_popup_overlay_permission)
        }
        GestureActionType.OPEN_STASH_PANEL -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_stash_panel_permission)
        }
        GestureActionType.FLOATING_POINTER -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_floating_pointer_permission)
        }
        GestureActionType.SIMULATE_POINTER_SWIPE -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_pointer_swipe_permission)
        }
        GestureActionType.POINTER_GESTURE_RECORDER,
        GestureActionType.POINTER_REALTIME_GESTURE,
        -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_pointer_gesture_record_permission)
        }
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            stringResource(R.string.gesture_action_open_floating_pointer_radial_menu_permission)
        }
        else -> null
    }

fun requestPermissionForAdjustAction(context: Context, action: GestureAction) {
    when (action) {
        GestureAction.AdjustVolume, GestureAction.ToggleMute, GestureAction.ToggleDnd ->
            PermissionHelper.requestNotificationPolicyAccess(context)
        GestureAction.AdjustBrightness -> PermissionHelper.requestWriteSettingsAccess(context)
        GestureAction.QuickToolsOverlay -> {
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
        else -> Unit
    }
}
