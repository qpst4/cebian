package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.corner.resolveHostPackageName
import com.slideindex.app.service.CornerGestureSlotPickTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerSlotSubMenuConfig
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.util.AppShortcutLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface CornerSlotEditorPage {
    data object SlotSettings : CornerSlotEditorPage
    data object ActionPick : CornerSlotEditorPage
    data object ActionPickMyShortcuts : CornerSlotEditorPage
    data object ActionPickPresetShortcuts : CornerSlotEditorPage
    data object ActionPickPickApp : CornerSlotEditorPage
    data class ActionPickPickActivity(val packageName: String) : CornerSlotEditorPage
    data object SubMenuShortcutPick : CornerSlotEditorPage
    data object SubMenuMyShortcuts : CornerSlotEditorPage
    data object SubMenuPresetShortcuts : CornerSlotEditorPage
    data object SubMenuPickApp : CornerSlotEditorPage
    data class SubMenuPickActivity(val packageName: String) : CornerSlotEditorPage
    data class ShellCommand(val initialCommand: String) : CornerSlotEditorPage
}

@Composable
fun CornerGestureSlotEditorHost(
    corner: String,
    slotIndex: Int,
    cornerTitle: String,
    onExit: () -> Unit,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf(AppSettings()) }
    var currentAction by remember { mutableStateOf<GestureAction>(GestureAction.None) }
    var subMenuConfig by remember { mutableStateOf(CornerSlotSubMenuConfig()) }
    var page by remember { mutableStateOf<CornerSlotEditorPage>(CornerSlotEditorPage.SlotSettings) }

    LaunchedEffect(corner, slotIndex) {
        val overlay = settingsRepository.overlaySettings.first()
        val cornerSettings = overlay.cornerGestureSettings
        currentAction = when (corner) {
            CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT -> {
                if (cornerSettings.unifiedSlots) {
                    cornerSettings.leftSlots
                } else {
                    cornerSettings.rightSlots
                }.getOrElse(slotIndex) { GestureAction.None }
            }
            else -> cornerSettings.leftSlots.getOrElse(slotIndex) { GestureAction.None }
        }
        subMenuConfig = when {
            corner == CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT && !cornerSettings.unifiedSlots ->
                cornerSettings.rightSlotSubMenus.getOrElse(slotIndex) { CornerSlotSubMenuConfig() }
            else ->
                cornerSettings.leftSlotSubMenus.getOrElse(slotIndex) { CornerSlotSubMenuConfig() }
        }
        appSettings = settingsRepository.settings.first()
    }

    val saveCornerAction: (GestureAction) -> Unit = { action ->
        scope.launch {
            val overlay = settingsRepository.overlaySettings.first()
            val unified = overlay.cornerGestureSettings.unifiedSlots
            when {
                unified -> settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
                corner == CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT ->
                    settingsRepository.setCornerGestureRightSlotAction(slotIndex, action)
                else ->
                    settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
            }
            currentAction = action
            page = CornerSlotEditorPage.SlotSettings
        }
    }

    val saveSubMenuConfig: (CornerSlotSubMenuConfig) -> Unit = { config ->
        scope.launch {
            val overlay = settingsRepository.overlaySettings.first()
            val unified = overlay.cornerGestureSettings.unifiedSlots
            val isLeft = unified || corner != CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT
            settingsRepository.setCornerSlotSubMenu(isLeft, slotIndex, config)
            subMenuConfig = config
        }
    }

    val appendSubMenuShortcut: (GestureAction.LaunchShortcut) -> Unit = { shortcut ->
        if (!subMenuConfig.items.any { it.payloadKey == shortcut.payloadKey }) {
            saveSubMenuConfig(
                subMenuConfig.copy(
                    enabled = true,
                    items = subMenuConfig.items + shortcut,
                ),
            )
        }
    }

    val hostPackage = currentAction.resolveHostPackageName()

    val existingPayloadKeys = remember(subMenuConfig.items) {
        subMenuConfig.items.map { it.payloadKey }.toSet()
    }
    val configuredSubMenuShortcutKeys = remember(subMenuConfig.items) {
        subMenuConfig.items.mapNotNull { GestureShortcutPayload.shortcutToggleKey(it.payloadKey) }.toSet()
    }

    BackHandler {
        when (page) {
            CornerSlotEditorPage.SlotSettings -> onExit()
            CornerSlotEditorPage.ActionPick -> page = CornerSlotEditorPage.SlotSettings
            CornerSlotEditorPage.ActionPickMyShortcuts -> page = CornerSlotEditorPage.ActionPick
            CornerSlotEditorPage.ActionPickPresetShortcuts -> page = CornerSlotEditorPage.ActionPick
            CornerSlotEditorPage.ActionPickPickApp -> page = CornerSlotEditorPage.ActionPick
            is CornerSlotEditorPage.ActionPickPickActivity -> page = CornerSlotEditorPage.ActionPickPickApp
            CornerSlotEditorPage.SubMenuShortcutPick -> page = CornerSlotEditorPage.SlotSettings
            CornerSlotEditorPage.SubMenuMyShortcuts -> page = CornerSlotEditorPage.SubMenuShortcutPick
            CornerSlotEditorPage.SubMenuPresetShortcuts -> page = CornerSlotEditorPage.SubMenuShortcutPick
            CornerSlotEditorPage.SubMenuPickApp -> page = CornerSlotEditorPage.SubMenuShortcutPick
            is CornerSlotEditorPage.SubMenuPickActivity -> page = CornerSlotEditorPage.SubMenuPickApp
            is CornerSlotEditorPage.ShellCommand -> page = CornerSlotEditorPage.ActionPick
        }
    }

    when (val screen = page) {
        CornerSlotEditorPage.SlotSettings -> {
            CornerGestureSlotSettingsScreen(
                slotIndex = slotIndex,
                cornerTitle = cornerTitle,
                currentAction = currentAction,
                subMenuConfig = subMenuConfig,
                appSettings = appSettings,
                onBack = onExit,
                onPickMainAction = { page = CornerSlotEditorPage.ActionPick },
                onSubMenuEnabledChange = { enabled ->
                    saveSubMenuConfig(subMenuConfig.copy(enabled = enabled))
                },
                onRemoveSubMenuItem = { index ->
                    val items = subMenuConfig.items.toMutableList()
                    if (index in items.indices) {
                        items.removeAt(index)
                        saveSubMenuConfig(subMenuConfig.copy(items = items))
                    }
                },
                onAddSubMenuShortcut = { page = CornerSlotEditorPage.SubMenuShortcutPick },
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
                        saveSubMenuConfig(
                            subMenuConfig.copy(
                                enabled = true,
                                items = subMenuConfig.items + shortcuts,
                            ),
                        )
                    }
                },
                canImportFromHost = hostPackage != null,
            )
        }

        CornerSlotEditorPage.ActionPick -> {
            GestureActionPickerScreen(
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                current = currentAction,
                onDismiss = { page = CornerSlotEditorPage.SlotSettings },
                onSelect = { action ->
                    if (action is GestureAction.FloatingPointer) {
                        return@GestureActionPickerScreen
                    }
                    saveCornerAction(action)
                },
                onOpenMyShortcuts = { page = CornerSlotEditorPage.ActionPickMyShortcuts },
                onOpenPresetShortcuts = { page = CornerSlotEditorPage.ActionPickPresetShortcuts },
                onOpenPickApp = { page = CornerSlotEditorPage.ActionPickPickApp },
                onOpenExecuteShellCommand = { command ->
                    page = CornerSlotEditorPage.ShellCommand(command)
                },
            )
        }

        CornerSlotEditorPage.ActionPickMyShortcuts -> {
            MyShortcutsFolderScreen(
                activityShortcuts = appSettings.activityShortcuts,
                onBack = { page = CornerSlotEditorPage.ActionPick },
                onBrowseNewShortcut = { page = CornerSlotEditorPage.ActionPickPickApp },
                currentAction = currentAction,
                onSelectRadio = { action -> saveCornerAction(action) },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.ActionPickPresetShortcuts -> {
            PresetShortcutsFolderScreen(
                onBack = { page = CornerSlotEditorPage.ActionPick },
                currentAction = currentAction,
                onSelectRadio = { action ->
                    if (action is GestureAction.LaunchShortcut) {
                        saveCornerAction(action)
                    }
                },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.ActionPickPickApp -> {
            ActivityShortcutPickAppScreen(
                embedInParentChrome = true,
                onBack = { page = CornerSlotEditorPage.ActionPick },
                onSelectApp = { app ->
                    page = CornerSlotEditorPage.ActionPickPickActivity(app.packageName)
                },
            )
        }

        is CornerSlotEditorPage.ActionPickPickActivity -> {
            ActivityShortcutPickActivityScreen(
                packageName = screen.packageName,
                embedInParentChrome = true,
                onBack = { page = CornerSlotEditorPage.ActionPickPickApp },
                onSelectActivity = { activity ->
                    val component = "${activity.packageName}/${activity.className}"
                    saveCornerAction(
                        GestureAction.LaunchShortcut.component(component, activity.label),
                    )
                },
            )
        }

        CornerSlotEditorPage.SubMenuShortcutPick -> {
            CornerSlotSubMenuShortcutPickScreen(
                appSettings = appSettings,
                existingPayloadKeys = existingPayloadKeys,
                onBack = { page = CornerSlotEditorPage.SlotSettings },
                onAddShortcut = appendSubMenuShortcut,
                onOpenMyShortcuts = { page = CornerSlotEditorPage.SubMenuMyShortcuts },
                onOpenPresetShortcuts = { page = CornerSlotEditorPage.SubMenuPresetShortcuts },
                onBrowseActivityShortcut = { page = CornerSlotEditorPage.SubMenuPickApp },
            )
        }

        CornerSlotEditorPage.SubMenuMyShortcuts -> {
            MyShortcutsFolderScreen(
                activityShortcuts = appSettings.activityShortcuts,
                onBack = { page = CornerSlotEditorPage.SubMenuShortcutPick },
                onBrowseNewShortcut = { page = CornerSlotEditorPage.SubMenuPickApp },
                configuredShortcutKeys = configuredSubMenuShortcutKeys,
                onToggle = { item, added ->
                    if (!added && item.type == QuickLauncherItemType.SHORTCUT) {
                        val action = activityShortcutFromQuickLauncherItem(item)?.toLaunchShortcut()
                        if (action != null && action.payloadKey !in existingPayloadKeys) {
                            appendSubMenuShortcut(action)
                        }
                    }
                },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.SubMenuPresetShortcuts -> {
            PresetShortcutsFolderScreen(
                onBack = { page = CornerSlotEditorPage.SubMenuShortcutPick },
                configuredShortcutKeys = configuredSubMenuShortcutKeys,
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
                            appendSubMenuShortcut(action)
                        }
                    }
                },
                overlayMode = true,
            )
        }

        CornerSlotEditorPage.SubMenuPickApp -> {
            ActivityShortcutPickAppScreen(
                embedInParentChrome = true,
                onBack = { page = CornerSlotEditorPage.SubMenuShortcutPick },
                onSelectApp = { app ->
                    page = CornerSlotEditorPage.SubMenuPickActivity(app.packageName)
                },
            )
        }

        is CornerSlotEditorPage.SubMenuPickActivity -> {
            ActivityShortcutPickActivityScreen(
                packageName = screen.packageName,
                embedInParentChrome = true,
                onBack = { page = CornerSlotEditorPage.SubMenuPickApp },
                onSelectActivity = { activity ->
                    val component = "${activity.packageName}/${activity.className}"
                    appendSubMenuShortcut(
                        GestureAction.LaunchShortcut.component(component, activity.label),
                    )
                },
            )
        }

        is CornerSlotEditorPage.ShellCommand -> {
            GestureExecuteShellCommandScreen(
                initialCommand = screen.initialCommand,
                shellCommands = appSettings.shellCommands,
                onBack = { page = CornerSlotEditorPage.ActionPick },
                onConfirm = { command ->
                    saveCornerAction(GestureAction.ExecuteShellCommand(command))
                },
            )
        }
    }
}
