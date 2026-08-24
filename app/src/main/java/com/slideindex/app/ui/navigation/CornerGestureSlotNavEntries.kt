package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.corner.resolveHostPackageName
import com.slideindex.app.ui.CornerGestureSlotSettingsScreen
import com.slideindex.app.ui.CornerSlotSubMenuShortcutPickScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.taskSwitcherItemToLaunchShortcut
import com.slideindex.app.ui.viewmodel.HomeDetailSettingsViewModel
import com.slideindex.app.util.AppShortcutLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

fun NavEntryBuilder.cornerGestureSlotNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HomeCornerGestureSlotEditor> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val currentAction = cornerSlotCurrentAction(key.corner, key.slotIndex, cornerSettings)
        val subMenuConfig = cornerSlotSubMenuConfig(key.corner, key.slotIndex, cornerSettings)
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val cornerTitle = when (key.corner) {
            CORNER_SLOT_CORNER_RIGHT -> stringResource(R.string.corner_gesture_slot_corner_right)
            else -> stringResource(R.string.corner_gesture_slot_corner_left)
        }
        CornerGestureSlotSettingsScreen(
            slotIndex = key.slotIndex,
            cornerTitle = cornerTitle,
            currentAction = currentAction,
            subMenuConfig = subMenuConfig,
            appSettings = appSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots) },
            onPickMainAction = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotActionPick(key.corner, key.slotIndex))
            },
            onSubMenuEnabledChange = { enabled ->
                viewModel.setCornerSlotSubMenu(
                    key.corner,
                    key.slotIndex,
                    subMenuConfig.copy(enabled = enabled),
                )
            },
            onRemoveSubMenuItem = { index ->
                val items = subMenuConfig.items.toMutableList()
                if (index in items.indices) {
                    items.removeAt(index)
                    viewModel.setCornerSlotSubMenu(
                        key.corner,
                        key.slotIndex,
                        subMenuConfig.copy(items = items),
                    )
                }
            },
            onAddSubMenuShortcut = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotSubMenuPick(key.corner, key.slotIndex))
            },
            onImportFromApp = {
                scope.launch {
                    val pkg = currentAction.resolveHostPackageName() ?: return@launch
                    val loaded = withContext(Dispatchers.IO) {
                        AppShortcutLoader.loadFastShortcuts(context, pkg)
                    }
                    val existing = subMenuConfig.items.map { it.payloadKey }
                    val shortcuts = loaded.map { item ->
                        taskSwitcherItemToLaunchShortcut(item, pkg)
                    }.filter { it.payloadKey !in existing }
                    viewModel.setCornerSlotSubMenu(
                        key.corner,
                        key.slotIndex,
                        subMenuConfig.copy(
                            enabled = true,
                            items = subMenuConfig.items + shortcuts,
                        ),
                    )
                }
            },
            canImportFromHost = currentAction.resolveHostPackageName() != null,
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotActionPick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val currentAction = cornerSlotCurrentAction(key.corner, key.slotIndex, cornerSettings)
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = currentAction,
            onDismiss = { ctx.navigateBackTo(editorKey) },
            onSelect = { action ->
                if (action is GestureAction.FloatingPointer) return@GestureActionPickerScreen
                viewModel.setCornerSlotAction(key.corner, key.slotIndex, action)
                ctx.navigateBackTo(editorKey)
            },
            onOpenMyShortcuts = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotMyShortcuts(key.corner, key.slotIndex))
            },
            onOpenPresetShortcuts = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotPresetShortcuts(key.corner, key.slotIndex))
            },
            onOpenPickApp = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotPickApp(key.corner, key.slotIndex))
            },
            onOpenExecuteShellCommand = { command ->
                ctx.navigate(AppNavKey.HomeCornerGestureSlotShellCommand(key.corner, key.slotIndex, command))
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotMyShortcuts> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val currentAction = cornerSlotCurrentAction(key.corner, key.slotIndex, cornerSettings)
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        val returnKey = AppNavKey.HomeCornerGestureSlotActionPick(key.corner, key.slotIndex)
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotPickApp(key.corner, key.slotIndex))
            },
            currentAction = currentAction,
            onSelectRadio = { action ->
                viewModel.setCornerSlotAction(key.corner, key.slotIndex, action)
                ctx.navigateBackTo(editorKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotPresetShortcuts> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val currentAction = cornerSlotCurrentAction(key.corner, key.slotIndex, cornerSettings)
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        val returnKey = AppNavKey.HomeCornerGestureSlotActionPick(key.corner, key.slotIndex)
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = currentAction,
            onSelectRadio = { action ->
                if (action is GestureAction.LaunchShortcut) {
                    viewModel.setCornerSlotAction(key.corner, key.slotIndex, action)
                    ctx.navigateBackTo(editorKey)
                }
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotPickApp> { key ->
        val returnKey = AppNavKey.HomeCornerGestureSlotActionPick(key.corner, key.slotIndex)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.HomeCornerGestureSlotPickActivity(key.corner, key.slotIndex, app.packageName),
                )
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotPickActivity> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                viewModel.setCornerSlotAction(key.corner, key.slotIndex, action)
                ctx.navigateBackTo(editorKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotShellCommand> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        val returnKey = AppNavKey.HomeCornerGestureSlotActionPick(key.corner, key.slotIndex)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.navigateBackTo(returnKey) },
            onConfirm = { command ->
                viewModel.setCornerSlotAction(key.corner, key.slotIndex, GestureAction.ExecuteShellCommand(command))
                ctx.navigateBackTo(editorKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotSubMenuPick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val subMenuConfig = cornerSlotSubMenuConfig(key.corner, key.slotIndex, cornerSettings)
        val editorKey = cornerSlotEditorKey(key.corner, key.slotIndex)
        val existingPayloadKeys = subMenuConfig.items.map { it.payloadKey }.toSet()
        CornerSlotSubMenuShortcutPickScreen(
            appSettings = appSettings,
            existingPayloadKeys = existingPayloadKeys,
            onBack = { ctx.navigateBackTo(editorKey) },
            onAddShortcut = { shortcut ->
                if (shortcut.payloadKey !in existingPayloadKeys) {
                    viewModel.setCornerSlotSubMenu(
                        key.corner,
                        key.slotIndex,
                        subMenuConfig.copy(
                            enabled = true,
                            items = subMenuConfig.items + shortcut,
                        ),
                    )
                }
            },
            onOpenMyShortcuts = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotSubMenuMyShortcuts(key.corner, key.slotIndex))
            },
            onOpenPresetShortcuts = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotSubMenuPresetShortcuts(key.corner, key.slotIndex))
            },
            onBrowseActivityShortcut = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotSubMenuPickApp(key.corner, key.slotIndex))
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotSubMenuMyShortcuts> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val subMenuConfig = cornerSlotSubMenuConfig(key.corner, key.slotIndex, cornerSettings)
        val existingPayloadKeys = subMenuConfig.items.map { it.payloadKey }.toSet()
        val configuredKeys = subMenuConfig.items.mapNotNull { GestureShortcutPayload.shortcutToggleKey(it.payloadKey) }.toSet()
        val returnKey = AppNavKey.HomeCornerGestureSlotSubMenuPick(key.corner, key.slotIndex)
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = {
                ctx.navigate(AppNavKey.HomeCornerGestureSlotSubMenuPickApp(key.corner, key.slotIndex))
            },
            configuredShortcutKeys = configuredKeys,
            onToggle = { item, added ->
                if (!added && item.type == QuickLauncherItemType.SHORTCUT) {
                    val action = activityShortcutFromQuickLauncherItem(item)?.toLaunchShortcut()
                    if (action != null && action.payloadKey !in existingPayloadKeys) {
                        viewModel.setCornerSlotSubMenu(
                            key.corner,
                            key.slotIndex,
                            subMenuConfig.copy(
                                enabled = true,
                                items = subMenuConfig.items + action,
                            ),
                        )
                    }
                }
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotSubMenuPresetShortcuts> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val subMenuConfig = cornerSlotSubMenuConfig(key.corner, key.slotIndex, cornerSettings)
        val existingPayloadKeys = subMenuConfig.items.map { it.payloadKey }.toSet()
        val configuredKeys = subMenuConfig.items.mapNotNull { GestureShortcutPayload.shortcutToggleKey(it.payloadKey) }.toSet()
        val returnKey = AppNavKey.HomeCornerGestureSlotSubMenuPick(key.corner, key.slotIndex)
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            configuredShortcutKeys = configuredKeys,
            onToggle = { item, added ->
                if (!added && item.type == QuickLauncherItemType.SHORTCUT) {
                    val action = activityShortcutFromQuickLauncherItem(item)?.toLaunchShortcut()
                        ?: run {
                            val uri = QuickLauncherItemCodec.parseIntentPayload(item.payload)
                            val host = QuickLauncherItemCodec.resolveHostPackageName(item.payload)
                            if (uri != null) {
                                GestureAction.LaunchShortcut.intent(uri, item.label, host)
                            } else {
                                null
                            }
                        }
                    if (action != null && action.payloadKey !in existingPayloadKeys) {
                        viewModel.setCornerSlotSubMenu(
                            key.corner,
                            key.slotIndex,
                            subMenuConfig.copy(
                                enabled = true,
                                items = subMenuConfig.items + action,
                            ),
                        )
                    }
                }
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotSubMenuPickApp> { key ->
        val returnKey = AppNavKey.HomeCornerGestureSlotSubMenuPick(key.corner, key.slotIndex)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.HomeCornerGestureSlotSubMenuPickActivity(key.corner, key.slotIndex, app.packageName),
                )
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlotSubMenuPickActivity> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val cornerSettings = overlaySettings.cornerGestureSettings
        val subMenuConfig = cornerSlotSubMenuConfig(key.corner, key.slotIndex, cornerSettings)
        val existingPayloadKeys = subMenuConfig.items.map { it.payloadKey }.toSet()
        val returnKey = AppNavKey.HomeCornerGestureSlotSubMenuPick(key.corner, key.slotIndex)
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val shortcut = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                if (shortcut.payloadKey !in existingPayloadKeys) {
                    viewModel.setCornerSlotSubMenu(
                        key.corner,
                        key.slotIndex,
                        subMenuConfig.copy(
                            enabled = true,
                            items = subMenuConfig.items + shortcut,
                        ),
                    )
                }
                ctx.navigateBackTo(returnKey)
            },
        )
    }
}
