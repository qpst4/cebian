package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.FloatingPointerEdgeActionsSettingsScreen
import com.slideindex.app.ui.FloatingPointerEdgeSideSettingsScreen
import com.slideindex.app.ui.FloatingPointerJoystickSettingsScreen
import com.slideindex.app.ui.FloatingPointerPointerSettingsScreen
import com.slideindex.app.ui.FloatingPointerRadialMenuSettingsScreen
import com.slideindex.app.ui.FloatingPointerSettingsScreen
import com.slideindex.app.ui.ExtensionAboutScreen
import com.slideindex.app.ui.FreezerAppsScreen
import com.slideindex.app.ui.ThirdPartyNoticesScreen
import com.slideindex.app.ui.LicenseTextScreen
import com.slideindex.app.ui.FloatBallAppearanceSettingsScreen
import com.slideindex.app.ui.FloatBallStyleSettingsScreen
import com.slideindex.app.ui.FloatBallGestureSettingsScreen
import com.slideindex.app.ui.FloatBallPickSettingsScreen
import com.slideindex.app.ui.ShareImageOcrHistoryScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.ClipboardFloatSettingsScreen
import com.slideindex.app.ui.ClipboardHistorySettingsScreen
import com.slideindex.app.ui.StashPanelSettingsScreen
import com.slideindex.app.ui.StashClipboardSettingsScreen
import com.slideindex.app.ui.SearchPanelAppSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelContactSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelFileSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelSettingsScreen
import com.slideindex.app.ui.SearchPanelPresentationLayoutSettingsScreen
import com.slideindex.app.ui.SearchPanelSystemSettingsSearchSettingsScreen
import com.slideindex.app.ui.FloatBallSettingsScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.HolographicLauncherSettingsScreen
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.HoneycombDisplaySettingsScreen
import com.slideindex.app.ui.HoneycombLauncherEditorScreen
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddPickerScreen
import com.slideindex.app.ui.quicklauncher.QuickLauncherCreateFolderScreen
import com.slideindex.app.ui.quicklauncher.HoneycombLauncherAddPickerScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.MissingGesturePermissionsScreen
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.PointerSwipeConfigScreen
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.SearchEngineEditorCategory
import com.slideindex.app.ui.SearchEngineEditorScreen
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutShellIconBridge
import com.slideindex.app.activity.ActivityShortcutShellSupport
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.findForQuickLauncherItem
import com.slideindex.app.activity.toQuickLauncherItem
import com.slideindex.app.ui.ActivityShortcutScreen
import com.slideindex.app.ui.ActivityShortcutPresetsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppShortcutScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickShellScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.ShellCommandEditorScreen
import com.slideindex.app.ui.ShellOutputHistoryScreen
import com.slideindex.app.ui.ShellResultScreen
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.shell.ShellCommandIconStorage
import com.slideindex.app.util.ShellCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.slideindex.app.ui.WidgetPanelSettingsScreen
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.FloatBallTranslationSettingsScreen
import com.slideindex.app.ui.SearchEnginePreviewSortScreen
import com.slideindex.app.ui.SearchEngineSettingsScreen
import com.slideindex.app.ui.ImageSearchEngineDetailScreen
import com.slideindex.app.ui.ImageSearchEngineSettingsScreen
import com.slideindex.app.ui.resolveImageSearchEngine
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel
import com.slideindex.app.ui.TranslateModelSettingsScreen
import com.slideindex.app.ui.NativeEnginePackSettingsScreen
import com.slideindex.app.ui.OcrModelSettingsScreen
import com.slideindex.app.ui.viewmodel.NativeEnginePackSettingsViewModel
import com.slideindex.app.ui.viewmodel.OcrModelSettingsViewModel
import com.slideindex.app.ui.viewmodel.TranslateSettingsViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel
import com.slideindex.app.ui.viewmodel.ShareImageOcrHistoryViewModel
import com.slideindex.app.ui.viewmodel.StashClipboardSettingsViewModel

fun NavEntryBuilder.extensionNavEntries(ctx: MainNavContext) {
    layoutSettingsNavEntries(ctx)

    hiltEntry<AppNavKey.ExtensionHub> {
        val permissions = ctx.collectPermissions()
        val viewModel: ExtensionHubViewModel = hiltViewModel()
        val hubSettings by viewModel.extensionHubSettings.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val stashEntryCount by viewModel.stashEntryCount.collectAsStateWithLifecycle()
        ExtensionHubScreen(
            settings = hubSettings,
            gestureActive = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            stashEntryCount = stashEntryCount,
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
            onOpenLayoutSettings = { ctx.navigate(AppNavKey.HomeLayout) },
            onOpenQuickLauncher = { ctx.navigate(AppNavKey.QuickLauncher) },
            onOpenHoneycombLauncher = { ctx.navigate(AppNavKey.HoneycombLauncher) },
            onOpenHolographicLauncher = { ctx.navigate(AppNavKey.HolographicLauncherSettings) },
            onOpenActivityShortcuts = { ctx.navigate(AppNavKey.ActivityShortcuts) },
            onOpenShellCommands = { ctx.navigate(AppNavKey.ShellCommands) },
            onOpenWidgetPanel = { ctx.navigate(AppNavKey.WidgetPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
            onOpenStashClipboard = { ctx.navigate(AppNavKey.StashClipboard) },
            onOpenSearchPanel = { ctx.navigate(AppNavKey.SearchPanel) },
            onOpenFreezer = { ctx.navigate(AppNavKey.ExtensionFreezer) },
            onOpenSettingsBackup = { ctx.navigate(AppNavKey.ExtensionBackup) },
            onOpenAbout = { ctx.navigate(AppNavKey.ExtensionAbout) },
        )
    }

    hiltEntry<AppNavKey.ExtensionFreezer> {
        FreezerAppsScreen(
            settingsRepository = ctx.deps.settingsRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionAbout> {
        val updateViewModel: com.slideindex.app.update.UpdateViewModel = hiltViewModel(ctx.activity)
        val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()
        ExtensionAboutScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPrivacyPolicy = { ctx.navigate(AppNavKey.ExtensionPrivacy) },
            onOpenThirdPartyNotices = { ctx.navigate(AppNavKey.ExtensionThirdPartyNotices) },
            onOpenNativeEnginePacks = { ctx.navigate(AppNavKey.NativeEnginePacks) },
            onCheckUpdate = updateViewModel::checkManually,
            autoCheckUpdate = updateUiState.autoCheckUpdate,
            onAutoCheckUpdateChange = updateViewModel::setAutoCheckUpdate,
        )
    }

    hiltEntry<AppNavKey.ExtensionThirdPartyNotices> {
        ThirdPartyNoticesScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionAbout) },
            onOpenLicenseText = { fileName ->
                ctx.navigate(AppNavKey.ExtensionLicenseText(fileName))
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionLicenseText> { key ->
        LicenseTextScreen(
            assetFileName = key.assetFileName,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionThirdPartyNotices) },
        )
    }

    hiltEntry<AppNavKey.ExtensionPrivacy> {
        PrivacyPolicyScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackup> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val importPreviewState by viewModel.importPreviewState.collectAsStateWithLifecycle()
        val navigateToMissingPermissions by viewModel.navigateToMissingPermissions.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val missingCount = remember(settings) {
            GestureActionPermissionAuditor.auditMissingPermissions(context, settings).size
        }
        LaunchedEffect(navigateToMissingPermissions) {
            if (navigateToMissingPermissions) {
                ctx.navigate(AppNavKey.ExtensionMissingPermissions)
                viewModel.consumeNavigateToMissingPermissions()
            }
        }
        SettingsBackupScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onExport = viewModel::exportSettings,
            onImport = viewModel::previewImport,
            importPreviewState = importPreviewState,
            onDismissPreview = viewModel::dismissPreview,
            onConfirmImport = viewModel::confirmImport,
            missingPermissionCount = missingCount,
            onOpenMissingPermissions = { ctx.navigate(AppNavKey.ExtensionMissingPermissions) },
        )
    }

    hiltEntry<AppNavKey.ExtensionMissingPermissions> {
        val viewModel: SettingsBackupViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        MissingGesturePermissionsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionBackup) },
        )
    }

    hiltEntry<AppNavKey.QuickLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        QuickLauncherEditorScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSavePanels = viewModel::setQuickLauncherPanels,
            onDisplayChange = viewModel::setQuickLauncherDisplaySettings,
            onAdd = { panelId -> ctx.navigate(AppNavKey.QuickLauncherAdd(panelId)) },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherAdd> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        QuickLauncherAddPickerScreen(
            panelId = key.panelId,
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.QuickLauncher) },
            onToggleItem = { item, added -> viewModel.toggleQuickLauncherPanelItem(key.panelId, item, added) },
            onAddItem = { item -> viewModel.addQuickLauncherPanelItem(key.panelId, item) },
            onPickApp = { ctx.navigate(AppNavKey.QuickLauncherPickApp(key.panelId)) },
            onMyShortcuts = { ctx.navigate(AppNavKey.QuickLauncherMyShortcuts(key.panelId)) },
            onPresetShortcuts = { ctx.navigate(AppNavKey.QuickLauncherPresetShortcuts(key.panelId)) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.QuickLauncherShellCommand(key.panelId, cmd)) },
            onOpenCreateFolder = { ctx.navigate(AppNavKey.QuickLauncherCreateFolder(key.panelId)) },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherPickApp> { key ->
        ActivityShortcutPickAppScreen(
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.QuickLauncherPickActivity(
                        panelId = key.panelId,
                        packageName = app.packageName,
                        fromCreateFolder = key.fromCreateFolder,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val item = QuickLauncherItem.shortcut(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val panel = remember(settings.quickLauncherPanels, key.panelId) {
            QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).find { it.id == key.panelId }
                ?: QuickLauncherPanelDefaults.defaultPanel()
        }
        val sourceItems = if (key.fromCreateFolder) folderDraft?.items.orEmpty() else panel.items
        val configuredShortcutKeys = remember(sourceItems) {
            sourceItems.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = { ctx.backStack.removeLastOrNull() },
            onBrowseNewShortcut = {
                ctx.navigate(
                    AppNavKey.QuickLauncherPickApp(
                        panelId = key.panelId,
                        fromCreateFolder = key.fromCreateFolder,
                    ),
                )
            },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                if (key.fromCreateFolder) {
                    viewModel.toggleFolderDraftItem(item, added)
                } else {
                    viewModel.toggleQuickLauncherPanelItem(key.panelId, item, added)
                }
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherPresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val panel = remember(settings.quickLauncherPanels, key.panelId) {
            QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).find { it.id == key.panelId }
                ?: QuickLauncherPanelDefaults.defaultPanel()
        }
        val sourceItems = if (key.fromCreateFolder) folderDraft?.items.orEmpty() else panel.items
        val configuredShortcutKeys = remember(sourceItems) {
            sourceItems.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.backStack.removeLastOrNull() },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                if (key.fromCreateFolder) {
                    viewModel.toggleFolderDraftItem(item, added)
                } else {
                    viewModel.toggleQuickLauncherPanelItem(key.panelId, item, added)
                }
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                val item = QuickLauncherItem.action(
                    GestureAction.ExecuteShellCommand(command),
                    label,
                )
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                val label = keyEventAction.keyName.ifBlank {
                    com.slideindex.app.gesture.KeyEventPresets.getDisplayName(keyEventAction.keyCode)
                }
                val item = QuickLauncherItem.action(
                    keyEventAction,
                    label,
                )
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherCreateFolder> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        LaunchedEffect(key.panelId) {
            viewModel.initFolderDraft(key.panelId)
        }
        QuickLauncherCreateFolderScreen(
            settings = settings,
            draft = folderDraft,
            onFolderNameChange = viewModel::updateFolderDraftName,
            onToggleItem = viewModel::toggleFolderDraftItem,
            onAddItem = viewModel::addFolderDraftItem,
            onBack = {
                viewModel.clearFolderDraft()
                ctx.navigateBackTo(AppNavKey.QuickLauncherAdd(key.panelId))
            },
            onConfirmCreateFolder = { folderName, _ ->
                viewModel.commitFolderDraft(folderName)
                ctx.navigateBackTo(AppNavKey.QuickLauncher)
            },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(
                    AppNavKey.QuickLauncherShellCommand(
                        panelId = key.panelId,
                        initialCommand = cmd,
                        fromCreateFolder = true,
                    ),
                )
            },
            onPickApp = {
                ctx.navigate(
                    AppNavKey.QuickLauncherPickApp(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
            onMyShortcuts = {
                ctx.navigate(
                    AppNavKey.QuickLauncherMyShortcuts(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
            onPresetShortcuts = {
                ctx.navigate(
                    AppNavKey.QuickLauncherPresetShortcuts(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombLauncherEditorScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveItems = viewModel::setHoneycombLauncherItems,
            onOpenDisplaySettings = { ctx.navigate(AppNavKey.HoneycombDisplaySettings) },
            onAdd = { ctx.navigate(AppNavKey.HoneycombLauncherAdd) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherAdd> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombLauncherAddPickerScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncher) },
            onToggleItem = { item, added -> viewModel.toggleHoneycombItem(item, added) },
            onAddItem = { item -> viewModel.addHoneycombItem(item) },
            onPickApp = { ctx.navigate(AppNavKey.HoneycombLauncherPickApp) },
            onMyShortcuts = { ctx.navigate(AppNavKey.HoneycombLauncherMyShortcuts) },
            onPresetShortcuts = { ctx.navigate(AppNavKey.HoneycombLauncherPresetShortcuts) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.HoneycombLauncherShellCommand(cmd)) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherPickApp> {
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.HoneycombLauncherPickActivity(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                viewModel.addHoneycombItem(
                    QuickLauncherItem.shortcut(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherMyShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val items = remember(settings.honeycombLauncher) {
            settings.honeycombLauncher.honeycombRuntimeItems()
        }
        val configuredShortcutKeys = remember(items) {
            items.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.HoneycombLauncherPickApp) },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added -> viewModel.toggleHoneycombItem(item, added) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherPresetShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val items = remember(settings.honeycombLauncher) {
            settings.honeycombLauncher.honeycombRuntimeItems()
        }
        val configuredShortcutKeys = remember(items) {
            items.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added -> viewModel.toggleHoneycombItem(item, added) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onConfirm = { command ->
                val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                viewModel.addHoneycombItem(
                    QuickLauncherItem.action(
                        GestureAction.ExecuteShellCommand(command),
                        label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onConfirm = { keyEventAction ->
                val label = keyEventAction.keyName.ifBlank {
                    com.slideindex.app.gesture.KeyEventPresets.getDisplayName(keyEventAction.keyCode)
                }
                viewModel.addHoneycombItem(
                    QuickLauncherItem.action(
                        keyEventAction,
                        label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombDisplaySettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombDisplaySettingsScreen(
            display = settings.honeycombDisplay,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncher) },
            onDisplayChange = viewModel::setHoneycombDisplaySettings,
        )
    }

    hiltEntry<AppNavKey.HolographicLauncherSettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val holographicSettings = gestureSettings.holographicLauncher
        HolographicLauncherSettingsScreen(
            settings = holographicSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onTimeoutSecondsChange = viewModel::setHolographicLauncherTimeoutSeconds,
            onRotationSensitivityChange = viewModel::setHolographicRotationSensitivity,
            onHapticLevelChange = viewModel::setHolographicHapticLevel,
            onBackgroundStyleChange = viewModel::setHolographicBackgroundStyle,
            onBlurDpChange = viewModel::setHolographicBlurDp,
            onDimPercentChange = viewModel::setHolographicDimPercent,
            onOpenHiddenApps = { ctx.navigate(AppNavKey.HolographicLauncherHiddenApps) },
        )
    }

    hiltEntry<AppNavKey.HolographicLauncherHiddenApps> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        HiddenAppsScreen(
            hiddenPackages = gestureSettings.holographicLauncher.hiddenAppPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.HolographicLauncherSettings) },
            onHideApp = viewModel::addHolographicHiddenApp,
            onUnhideApp = viewModel::removeHolographicHiddenApp,
            titleRes = R.string.holographic_hidden_apps_title,
            descriptionRes = R.string.holographic_hidden_apps_desc,
        )
    }

    hiltEntry<AppNavKey.ActivityShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
            onAdd = { ctx.navigate(AppNavKey.ActivityShortcutPickApp) },
            onAddAppShortcut = { ctx.navigate(AppNavKey.ActivityShortcutPickAppShortcut) },
            onAddShellCommand = { ctx.navigate(AppNavKey.ActivityShortcutPickShell) },
            onOpenPresets = { ctx.navigate(AppNavKey.ActivityShortcutPresets) },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickShell> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickShellScreen(
            shellCommands = settings.shellCommands,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onPick = { cmd ->
                val shortcut = ActivityShortcutShellIconBridge.withCopiedIcon(ctx.activity, cmd)
                if (settings.activityShortcuts.none { it.identityKey() == shortcut.identityKey() }) {
                    viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPresets> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPresetsScreen(
            settings = settings,
            shortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSaveShortcuts = viewModel::setActivityShortcuts,
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickApp> {
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.ActivityShortcutPickActivity(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickAppShortcut> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickAppShortcutScreen(
            existingIdentityKeys = settings.activityShortcuts.map { it.identityKey() }.toSet(),
            onBack = { ctx.navigateBackTo(AppNavKey.ActivityShortcuts) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.ActivityShortcutPickAppShortcutPresets) },
            onAddShortcut = { shortcut ->
                if (settings.activityShortcuts.none { it.identityKey() == shortcut.identityKey() }) {
                    viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickAppShortcutPresets> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val configuredShortcutKeys = remember(settings.activityShortcuts) {
            settings.activityShortcuts.mapNotNull { shortcut ->
                com.slideindex.app.launcher.QuickLauncherItemCodec.shortcutItemKey(
                    shortcut.toQuickLauncherItem(),
                )
            }.toSet()
        }
        val returnKey = AppNavKey.ActivityShortcutPickAppShortcut
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                val existing = settings.activityShortcuts.findForQuickLauncherItem(item)
                if (added) {
                    if (existing != null) {
                        viewModel.setActivityShortcuts(settings.activityShortcuts - existing)
                    }
                } else {
                    val shortcut = activityShortcutFromQuickLauncherItem(item)
                    if (shortcut != null && existing == null) {
                        viewModel.setActivityShortcuts(settings.activityShortcuts + shortcut)
                    }
                }
            },
        )
    }

    hiltEntry<AppNavKey.ActivityShortcutPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val candidate = ActivityShortcut.component(
                    label = activity.label,
                    packageName = activity.packageName,
                    activityClassName = activity.className,
                )
                val duplicate = settings.activityShortcuts.any {
                    it.identityKey() == candidate.identityKey()
                }
                if (!duplicate) {
                    viewModel.setActivityShortcuts(
                        settings.activityShortcuts + candidate,
                    )
                }
                ctx.navigateBackTo(AppNavKey.ActivityShortcuts)
            },
        )
    }

    hiltEntry<AppNavKey.ShellCommands> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ShellCommandPanelScreen(
            settings = settings,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSaveCommands = viewModel::setShellCommands,
            onRequestShizuku = { ctx.requestShizuku() },
            shellViewModel = shellViewModel,
            onOpenHistory = { ctx.navigate(AppNavKey.ShellCommandHistory) },
            onOpenEditor = { commandId ->
                ctx.navigate(AppNavKey.ShellCommandEditor(commandId.orEmpty()))
            },
            onOpenResult = { ctx.navigate(AppNavKey.ShellCommandResult) },
        )
    }

    hiltEntry<AppNavKey.ShellCommandHistory> {
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        ShellOutputHistoryScreen(
            repository = shellViewModel.historyRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ShellCommands) },
            onClear = shellViewModel::clearHistory,
        )
    }

    hiltEntry<AppNavKey.ShellCommandEditor> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val initial = key.commandId.takeIf { it.isNotEmpty() }
            ?.let { id -> settings.shellCommands.find { it.id == id } }
        ShellCommandEditorScreen(
            initial = initial,
            shizukuGranted = permissions.shizukuGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.ShellCommands) },
            onSave = { draft ->
                val current = settings.shellCommands
                val updated = if (current.any { it.id == draft.id }) {
                    current.map { if (it.id == draft.id) draft else it }
                } else {
                    current + draft
                }
                viewModel.setShellCommands(updated)
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            },
            onDelete = initial?.let { existing ->
                {
                    ShellCommandIconStorage.deleteIconIfOwned(context, existing.iconPath)
                    viewModel.setShellCommands(
                        settings.shellCommands.filter { it.id != existing.id },
                    )
                    ctx.navigateBackTo(AppNavKey.ShellCommands)
                }
            },
            onTest = { command, callback ->
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        ShellCommandRunner.execute(context, command)
                    }
                    callback(outcome.exitCode, outcome.output)
                }
            },
        )
    }

    hiltEntry<AppNavKey.ShellCommandResult> {
        val shellViewModel: ShellCommandViewModel = hiltViewModel()
        val context = LocalContext.current
        val pending = shellViewModel.pendingResult
        if (pending == null) {
            LaunchedEffect(Unit) {
                ctx.navigateBackTo(AppNavKey.ShellCommands)
            }
        } else {
            ShellResultScreen(
                label = pending.label,
                command = pending.command,
                exitCode = pending.exitCode,
                output = pending.output,
                onBack = {
                    shellViewModel.clearPendingResult()
                    ctx.navigateBackTo(AppNavKey.ShellCommands)
                },
                onCopy = { copyShellOutputToClipboard(context, pending.output) },
            )
        }
    }

    hiltEntry<AppNavKey.WidgetPanel> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        WidgetPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSavePages = viewModel::setWidgetPanelPages,
            onBlurEnabledChange = viewModel::setWidgetPanelBlurEnabled,
            onBlurRadiusChange = viewModel::setWidgetPanelBlurRadiusDp,
            onWidthFractionChange = viewModel::setWidgetPanelWidthFraction,
        )
    }

    hiltEntry<AppNavKey.StashClipboard> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val clipboardEntryCount by viewModel.clipboardHistoryRepository.entryCount.collectAsStateWithLifecycle()
        val stashEntries by viewModel.stashRepository.entries.collectAsStateWithLifecycle()
        StashClipboardSettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntryCount,
            stashEntryCount = stashEntries.size,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenClipboardHistory = { ctx.navigate(AppNavKey.ClipboardHistorySettings) },
            onOpenStashPanel = { ctx.navigate(AppNavKey.StashPanelSettings) },
            onOpenClipboardFloat = { ctx.navigate(AppNavKey.ClipboardFloatSettings) },
            onClearStash = viewModel::clearStash,
        )
        LaunchedEffect(permissions.overlayGranted) {
            if (permissions.overlayGranted) {
                viewModel.syncHistoryFloatFromSettings()
            }
        }
        LaunchedEffect(permissions.accessibilityGranted, settings.clipboardFloatEnabled) {
            if (permissions.accessibilityGranted) {
                viewModel.syncClipboardFloatFromSettings()
            }
        }
    }

    hiltEntry<AppNavKey.ClipboardHistorySettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val clipboardEntryCount by viewModel.clipboardHistoryRepository.entryCount.collectAsStateWithLifecycle()
        ClipboardHistorySettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntryCount,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onClipboardHistoryMaxEntriesChange = viewModel::setClipboardHistoryMaxEntries,
            onClearClipboardHistory = viewModel::clearClipboardHistory,
            onClipboardScreenshotMonitoringChange = viewModel::setClipboardScreenshotMonitoring,
            onClipboardMonitoringChange = viewModel::setClipboardBackgroundMonitoring,
            onClipboardMonitoringModeChange = viewModel::setClipboardBackgroundMonitoringMode,
            onOpenOverlayPermission = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
        )
    }

    hiltEntry<AppNavKey.StashPanelSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        StashPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onStashPanelBackgroundBlurEnabledChange = viewModel::setStashPanelBackgroundBlurEnabled,
            onStashPanelBackgroundBlurRadiusDpChange = viewModel::setStashPanelBackgroundBlurRadiusDp,
            onClipboardHistoryFloatEnabledChange = viewModel::setClipboardHistoryFloatEnabled,
            onClipboardHistoryFloatEnabledLandscapeChange = viewModel::setClipboardHistoryFloatEnabledLandscape,
            onClipboardHistoryFloatLockPositionChange = viewModel::setClipboardHistoryFloatLockPosition,
            onClipboardHistoryFloatHandleWidthChange = viewModel::setClipboardHistoryFloatHandleWidthDp,
            onOpenOverlayPermission = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ClipboardFloatSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onRequestAccessibility = { ctx.openAccessibilitySettings() },
            onClipboardFloatEnabledChange = viewModel::setClipboardFloatEnabled,
            onClipboardFloatShowChipChange = viewModel::setClipboardFloatShowChip,
            onClipboardFloatPinPositionChange = viewModel::setClipboardFloatPinPosition,
            onClipboardFloatEntryClickActionChange = viewModel::setClipboardFloatEntryClickAction,
            onClipboardFloatListStyleChange = viewModel::setClipboardFloatListStyle,
            onClipboardFloatPasteHapticEnabledChange = viewModel::setClipboardFloatPasteHapticEnabled,
            onClipboardFloatAlphaChange = viewModel::setClipboardFloatAlpha,
            onClipboardFloatAutoDimWhenUnfocusedChange = viewModel::setClipboardFloatAutoDimWhenUnfocused,
            onClipboardFloatAutoCloseSecondsChange = viewModel::setClipboardFloatAutoCloseSeconds,
            onOpenClipboardFloatBlacklist = { ctx.navigate(AppNavKey.ClipboardFloatBlacklist) },
            onResetClipboardFloatLayout = viewModel::resetClipboardFloatLayout,
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatBlacklist> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = settings.clipboardFloatBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ClipboardFloatBlacklistPick) },
            onRemoveBlacklistedApp = viewModel::removeClipboardFloatBlockedPackage,
            titleRes = R.string.clipboard_float_app_blacklist,
            descriptionRes = R.string.clipboard_float_app_blacklist_page_desc,
            blockedSectionTitleRes = R.string.clipboard_float_blacklist_section_blocked,
            emptyRes = R.string.clipboard_float_blacklist_empty,
            removeActionDescriptionRes = R.string.clipboard_float_blacklist_remove,
            addSectionTitleRes = R.string.clipboard_float_blacklist_section_add,
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatBlacklistPick> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.clipboard_float_blacklist_section_add,
            excludePackageNames = settings.clipboardFloatBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardFloatBlacklist) },
            onSelectApp = { app ->
                viewModel.addClipboardFloatBlockedPackage(app.packageName)
                ctx.navigateBackTo(AppNavKey.ClipboardFloatBlacklist)
            },
        )
    }

    hiltEntry<AppNavKey.SearchPanel> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val searchHistoryEntryCount by viewModel.searchHistoryEntryCount.collectAsStateWithLifecycle()
        SearchPanelSettingsScreen(
            settings = settings,
            searchHistoryEntryCount = searchHistoryEntryCount,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onSetDefaultEngineId = viewModel::setDefaultEngineId,
            onSetSearchPanelInputBehavior = viewModel::setSearchPanelInputBehavior,
            onSetSearchPanelContactSearchEnabled = viewModel::setSearchPanelContactSearchEnabled,
            onSetSearchPanelFileSearchEnabled = viewModel::setSearchPanelFileSearchEnabled,
            onSetSearchPanelAppSearchEnabled = viewModel::setSearchPanelAppSearchEnabled,
            onSetSearchPanelSettingsSearchEnabled = viewModel::setSearchPanelSettingsSearchEnabled,
            onSetSearchPanelSectionAliases = viewModel::setSearchPanelSectionAliases,
            onOpenAppSearchSettings = { ctx.navigate(AppNavKey.SearchPanelAppSearch) },
            onOpenContactSearchSettings = { ctx.navigate(AppNavKey.SearchPanelContactSearch) },
            onOpenFileSearchSettings = { ctx.navigate(AppNavKey.SearchPanelFileSearch) },
            onOpenSystemSettingsSearchSettings = { ctx.navigate(AppNavKey.SearchPanelSystemSettingsSearch) },
            onSetSearchPanelPresentationMode = viewModel::setSearchPanelPresentationMode,
            onSetSearchPanelBarPosition = viewModel::setSearchPanelBarPosition,
            onSetSearchPanelListOrder = viewModel::setSearchPanelListOrder,
            onSetSearchPanelAppDisplayStyle = viewModel::setSearchPanelAppDisplayStyle,
            onSetSearchPanelCalculatorEnabled = viewModel::setSearchPanelCalculatorEnabled,
            onSetSearchPanelWebSuggestionsEnabled = viewModel::setSearchPanelWebSuggestionsEnabled,
            onSetSearchPanelWebSuggestionsCount = viewModel::setSearchPanelWebSuggestionsCount,
            onSetSearchPanelHistoryMaxEntries = viewModel::setSearchPanelHistoryMaxEntries,
            onClearSearchHistory = viewModel::clearSearchHistory,
            onSetSearchPanelBackgroundStyle = viewModel::setSearchPanelBackgroundStyle,
            onSetSearchPanelBlurRadiusDp = viewModel::setSearchPanelBlurRadiusDp,
            onSetSearchPanelDimPercent = viewModel::setSearchPanelDimPercent,
            onOpenPresentationLayoutSettings = { ctx.navigate(AppNavKey.SearchPanelPresentationLayout) },
            onOpenTextSearchEngines = { ctx.navigate(AppNavKey.FloatBallSearchEngine) },
            onOpenImageSearchEngines = { ctx.navigate(AppNavKey.FloatBallImageSearchEngine) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelPresentationLayout> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        SearchPanelPresentationLayoutSettingsScreen(
            settings = overlaySettings.toMinimalAppSettings(),
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
            onSetSearchPanelPresentationMode = viewModel::setSearchPanelPresentationMode,
            onSetSearchPanelBarPosition = viewModel::setSearchPanelBarPosition,
            onSetSearchPanelListOrder = viewModel::setSearchPanelListOrder,
            onSetSearchPanelAppDisplayStyle = viewModel::setSearchPanelAppDisplayStyle,
            onSetSearchPanelBackgroundStyle = viewModel::setSearchPanelBackgroundStyle,
            onSetSearchPanelBlurRadiusDp = viewModel::setSearchPanelBlurRadiusDp,
            onSetSearchPanelDimPercent = viewModel::setSearchPanelDimPercent,
        )
    }

    hiltEntry<AppNavKey.SearchPanelAppSearch> {
        SearchPanelAppSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelContactSearch> {
        SearchPanelContactSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelSystemSettingsSearch> {
        SearchPanelSystemSettingsSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelFileSearch> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        SearchPanelFileSearchSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
            onSetFileTypesEnabled = viewModel::setSearchPanelFileTypesEnabled,
            onSetShowFolders = viewModel::setSearchPanelFileShowFolders,
            onSetShowSystemFiles = viewModel::setSearchPanelFileShowSystemFiles,
            onSetFilePreviewsEnabled = viewModel::setSearchPanelFilePreviewsEnabled,
            onSetFolderWhitelist = viewModel::setSearchPanelFileFolderWhitelist,
            onSetFolderBlacklist = viewModel::setSearchPanelFileFolderBlacklist,
        )
    }

    floatBallNavEntries(ctx)


    hiltEntry<AppNavKey.FloatingPointer> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val areaPreviewEnabled = ctx.collectAreaPreviewEnabled()
        FloatingPointerSettingsScreen(
            settings = settings,
            areaPreviewEnabled = areaPreviewEnabled,
            previewAccessibilityGranted = permissions.accessibilityGranted,
            onAreaPreviewEnabledChange = { ctx.setFloatingPointerAreaPreviewEnabled(it) },
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenPointerSettings = { ctx.navigate(AppNavKey.FloatingPointerPointer) },
            onOpenJoystickSettings = { ctx.navigate(AppNavKey.FloatingPointerJoystick) },
            onOpenRadialMenuSettings = { ctx.navigate(AppNavKey.FloatingPointerRadialMenu) },
            onOpenEdgeActionsSettings = { ctx.navigate(AppNavKey.FloatingPointerEdgeActions) },
            onPointerSensitivityChange = viewModel::setFloatingPointerSensitivityFraction,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerPointer> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerPointerSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onPointerDiameterChange = viewModel::setFloatingPointerPointerDiameterPx,
            onRingThicknessChange = viewModel::setFloatingPointerRingThicknessPx,
            onDotDiameterChange = viewModel::setFloatingPointerDotDiameterPx,
            onRingColorChange = viewModel::setFloatingPointerRingColor,
            onFillColorChange = viewModel::setFloatingPointerFillColor,
            onDotColorChange = viewModel::setFloatingPointerDotColor,
            onClickVisualFeedbackChange = viewModel::setFloatingPointerClickVisualFeedbackEnabled,
            onClickHapticChange = viewModel::setFloatingPointerClickHapticEnabled,
            onRippleColorChange = viewModel::setFloatingPointerRippleColor,
            onRippleSizeChange = viewModel::setFloatingPointerRippleSizeDp,
            onRippleDurationChange = viewModel::setFloatingPointerRippleDurationMs,
            onTrailTypeChange = viewModel::setFloatingPointerTrailType,
            onTrailDurationChange = viewModel::setFloatingPointerTrailDurationMs,
            onTrailColorChange = viewModel::setFloatingPointerTrailColor,
            onHideWhenReleasedChange = viewModel::setFloatingPointerHideWhenJoystickReleased,
            onPointerDesignChange = { design -> viewModel.setFloatingPointerDesignId(design.id) },
            onResetVisualDefaults = viewModel::resetFloatingPointerVisualDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerJoystick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerJoystickSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onJoystickDiameterChange = viewModel::setFloatingPointerJoystickDiameterPx,
            onInnerColorChange = viewModel::setFloatingPointerJoystickInnerColor,
            onOuterColorChange = viewModel::setFloatingPointerJoystickOuterColor,
            onGradientRadiusChange = viewModel::setFloatingPointerJoystickGradientRadiusFraction,
            onHideOnOutsideClickChange = viewModel::setFloatingPointerHideOnOutsideClick,
            onHideOnQuickSwipeChange = viewModel::setFloatingPointerHideOnQuickSwipe,
            onHideWhenIdleChange = viewModel::setFloatingPointerHideWhenIdle,
            onIdleDelayChange = viewModel::setFloatingPointerIdleHideDelayMs,
            onReleaseClickAndDismissChange = viewModel::setFloatingPointerReleaseClickAndDismiss,
            onHoverEnterSelectChange = viewModel::setFloatingPointerHoverEnterSelect,
            onClickDistanceThresholdChange = viewModel::setFloatingPointerClickDistanceThresholdDp,
            onResetVisualDefaults = viewModel::resetFloatingPointerJoystickVisualDefaults,
            onResetBehaviorDefaults = viewModel::resetFloatingPointerJoystickBehaviorDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialMenu> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerRadialMenuSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onAlwaysVisibleChange = viewModel::setFloatingPointerRadialAlwaysVisible,
            onLongPressMsChange = viewModel::setFloatingPointerRadialLongPressMs,
            onOpenLongPressActionPick = {
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialActionPick(FloatingPointerRadialActionTarget.LONG_PRESS),
                )
            },
            onOpenSlotActionPick = { slotIndex ->
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialActionPick(
                        target = FloatingPointerRadialActionTarget.SLOT,
                        slotIndex = slotIndex,
                    ),
                )
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(AppNavKey.FloatingPointerRadialShellCommand(slotIndex, command))
            },
            onOpenSwipeConfig = { slotIndex ->
                ctx.navigate(AppNavKey.FloatingPointerRadialSwipeConfig(slotIndex))
            },
            onSlotActionChange = viewModel::setFloatingPointerRadialSlotAction,
            onOuterDiameterChange = viewModel::setFloatingPointerRadialOuterDiameterPx,
            onInnerDiameterChange = viewModel::setFloatingPointerRadialInnerDiameterPx,
            onOuterColorChange = viewModel::setFloatingPointerRadialOuterColor,
            onInnerColorChange = viewModel::setFloatingPointerRadialInnerColor,
            onDividerThicknessChange = viewModel::setFloatingPointerRadialDividerThicknessPx,
            onDividerColorChange = viewModel::setFloatingPointerRadialDividerColor,
            onIconSizeFractionChange = viewModel::setFloatingPointerRadialIconSizeFraction,
            onIconColorChange = viewModel::setFloatingPointerRadialIconColor,
            onResetDesignDefaults = viewModel::resetFloatingPointerRadialDesignDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> settings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                settings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            includePointerGestureActions = true,
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                if (action is GestureAction.FloatingPointer) return@GestureActionPickerScreen
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> {
                        viewModel.setFloatingPointerJoystickLongPressAction(action)
                        ctx.navigateBackTo(returnKey)
                    }
                    FloatingPointerRadialActionTarget.SLOT -> {
                        if (action is GestureAction.SimulatePointerSwipe) {
                            ctx.navigate(AppNavKey.FloatingPointerRadialSwipeConfig(key.slotIndex))
                        } else {
                            viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                            ctx.navigateBackTo(returnKey)
                        }
                    }
                }
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.FloatingPointerRadialMyShortcuts(key.target, key.slotIndex)) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.FloatingPointerRadialPresetShortcuts(key.target, key.slotIndex)) },
            onOpenPickApp = { ctx.navigate(AppNavKey.FloatingPointerRadialPickApp(key.target, key.slotIndex)) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.FloatingPointerRadialShellCommand(key.slotIndex, cmd)) },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialSimulateKeyEvent(
                        key.slotIndex,
                        keyEvent.keyCode,
                        keyEvent.keyName,
                        keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(key.target, key.slotIndex)
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> appSettings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                appSettings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.FloatingPointerRadialPickApp(key.target, key.slotIndex)) },
            currentAction = current,
            onSelectRadio = { action ->
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> viewModel.setFloatingPointerJoystickLongPressAction(action)
                    FloatingPointerRadialActionTarget.SLOT -> viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.FloatingPointerRadialMenu)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialPresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(key.target, key.slotIndex)
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> appSettings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                appSettings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = current,
            onSelectRadio = { action ->
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> viewModel.setFloatingPointerJoystickLongPressAction(action)
                    FloatingPointerRadialActionTarget.SLOT -> viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.FloatingPointerRadialMenu)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialPickApp> { key ->
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(key.target, key.slotIndex)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.FloatingPointerRadialPickActivity(key.target, key.slotIndex, app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> viewModel.setFloatingPointerJoystickLongPressAction(action)
                    FloatingPointerRadialActionTarget.SLOT -> viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.FloatingPointerRadialMenu)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(FloatingPointerRadialActionTarget.SLOT, key.slotIndex)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    keyEventAction,
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialSwipeConfig> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        val current = settings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        val initialConfig = (current as? GestureAction.SimulatePointerSwipe)?.config
            ?: PointerSwipeConfig.DEFAULT
        PointerSwipeConfigScreen(
            initialConfig = initialConfig,
            onBack = { ctx.navigateBackTo(returnKey) },
            onConfirm = { config ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    GestureAction.SimulatePointerSwipe(config),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeActions> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerEdgeActionsSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onThresholdChange = viewModel::setFloatingPointerEdgeThresholdDp,
            onPreviewSensitivityChange = viewModel::setFloatingPointerEdgePreviewSensitivity,
            onPreviewGlowSizeChange = viewModel::setFloatingPointerEdgePreviewGlowSize,
            onPreviewShowIconChange = viewModel::setFloatingPointerEdgePreviewShowIcon,
            onVisualColorChange = viewModel::setFloatingPointerEdgeVisualColor,
            onOpenSideSettings = { side ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeSideSettings(side.toNavSide()))
            },
            onResetDefaults = viewModel::resetFloatingPointerEdgeDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeSideSettings> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val side = key.side.toFloatingPointerEdgeSide()
        FloatingPointerEdgeSideSettingsScreen(
            side = side,
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeActions) },
            onEnabledChange = { enabled -> viewModel.setFloatingPointerEdgeBarEnabled(side, enabled) },
            onOpenActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeActionPick(key.side, slotIndex))
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeShellCommand(key.side, slotIndex, command))
            },
            onAddSlot = { viewModel.addFloatingPointerEdgeBarSlot(side) },
            onRemoveSlot = { slotIndex -> viewModel.removeFloatingPointerEdgeBarSlot(side, slotIndex) },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        val current = settings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            includePointerGestureActions = false,
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(returnKey)
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.FloatingPointerEdgeMyShortcuts(key.side, key.slotIndex)) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.FloatingPointerEdgePresetShortcuts(key.side, key.slotIndex)) },
            onOpenPickApp = { ctx.navigate(AppNavKey.FloatingPointerEdgePickApp(key.side, key.slotIndex)) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.FloatingPointerEdgeShellCommand(key.side, key.slotIndex, cmd)) },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.FloatingPointerEdgeSimulateKeyEvent(
                        key.side,
                        key.slotIndex,
                        keyEvent.keyCode,
                        keyEvent.keyName,
                        keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        val current = appSettings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.FloatingPointerEdgePickApp(key.side, key.slotIndex)) },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeSideSettings(key.side))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgePresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        val current = appSettings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeSideSettings(key.side))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgePickApp> { key ->
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.FloatingPointerEdgePickActivity(key.side, key.slotIndex, app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgePickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val side = key.side.toFloatingPointerEdgeSide()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeSideSettings(key.side))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatingPointerEdgeBarSlotAction(
                    side,
                    key.slotIndex,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                viewModel.setFloatingPointerEdgeBarSlotAction(
                    side,
                    key.slotIndex,
                    keyEventAction,
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }
}

private fun copyShellOutputToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
}
