#!/usr/bin/env python3
"""One-off splitter for ExtensionNavEntries.kt (run from repo root)."""
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASE = REPO / "app/src/main/java/com/slideindex/app/ui/navigation"
SRC = (BASE / "ExtensionNavEntries.kt").read_text(encoding="utf-8")
lines = SRC.splitlines()

PKG = "package com.slideindex.app.ui.navigation\n"


def slice_body(start: int, end: int) -> str:
    return "\n".join(lines[start - 1 : end]) + "\n"


def write_file(name: str, imports: str, body: str, *, close_fn: bool = True) -> None:
    if close_fn:
        body = body.rstrip() + "\n}\n"
    path = BASE / name
    path.write_text(PKG + "\n" + imports + "\n" + body, encoding="utf-8")
    print(f"wrote {name} ({body.count(chr(10)) + 1} lines body)")


write_file(
    "ExtensionHubNavEntries.kt",
    """import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.ui.DiagnosticLogScreen
import com.slideindex.app.ui.ExtensionAboutScreen
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.ExternalInvocationHelpScreen
import com.slideindex.app.ui.FreezerAppsPickerScreen
import com.slideindex.app.ui.FreezerHomeScreen
import com.slideindex.app.ui.LicenseTextScreen
import com.slideindex.app.ui.MissingGesturePermissionsScreen
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.ThirdPartyNoticesScreen
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel

fun NavEntryBuilder.extensionHubNavEntries(ctx: MainNavContext) {""",
    slice_body(134, 276),
)

write_file(
    "QuickLauncherNavEntries.kt",
    """import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddPickerScreen
import com.slideindex.app.ui.quicklauncher.QuickLauncherCreateFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.QuickLauncherEditorViewModel

fun NavEntryBuilder.quickLauncherNavEntries(ctx: MainNavContext) {""",
    slice_body(278, 512),
)

write_file(
    "HoneycombLauncherNavEntries.kt",
    """import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.HoneycombDisplaySettingsScreen
import com.slideindex.app.ui.HoneycombLauncherEditorScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.quicklauncher.HoneycombLauncherAddPickerScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.HoneycombLauncherEditorViewModel

fun NavEntryBuilder.honeycombLauncherNavEntries(ctx: MainNavContext) {""",
    slice_body(514, 660),
)

write_file(
    "HolographicLauncherNavEntries.kt",
    """import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.HolographicLauncherSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.holographicLauncherNavEntries(ctx: MainNavContext) {""",
    slice_body(662, 690),
)

write_file(
    "ActivityShortcutNavEntries.kt",
    """import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutShellIconBridge
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.findForQuickLauncherItem
import com.slideindex.app.activity.toQuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.ActivityShortcutPresetsScreen
import com.slideindex.app.ui.ActivityShortcutScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppShortcutScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickShellScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.activityShortcutNavEntries(ctx: MainNavContext) {""",
    slice_body(692, 817),
)

write_file(
    "ShellCommandNavEntries.kt",
    """import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.shell.ShellCommandIconStorage
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.ui.ShellCommandEditorScreen
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.ShellOutputHistoryScreen
import com.slideindex.app.ui.ShellResultScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.ShellCommandViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavEntryBuilder.shellCommandNavEntries(ctx: MainNavContext) {""",
    slice_body(819, 913).rstrip()
    + "\n}\n\nprivate fun copyShellOutputToClipboard(context: Context, text: String) {\n"
    + slice_body(1627, 1630).strip()
    + "\n}\n",
    close_fn=False,
)

write_file(
    "StashClipboardNavEntries.kt",
    """import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.ClipboardFloatSettingsScreen
import com.slideindex.app.ui.ClipboardHistorySettingsScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.StashClipboardSettingsScreen
import com.slideindex.app.ui.StashPanelSettingsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.viewmodel.StashClipboardSettingsViewModel

fun NavEntryBuilder.stashClipboardNavEntries(ctx: MainNavContext) {""",
    slice_body(923, 1047),
)

write_file(
    "SearchPanelNavEntries.kt",
    """import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.SearchPanelAppSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelContactSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelFileSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelPresentationLayoutSettingsScreen
import com.slideindex.app.ui.SearchPanelSettingsScreen
import com.slideindex.app.ui.SearchPanelSystemSettingsSearchSettingsScreen
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel

fun NavEntryBuilder.searchPanelNavEntries(ctx: MainNavContext) {""",
    slice_body(1049, 1135),
)

write_file(
    "WidgetPanelNavEntries.kt",
    """import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.ui.WidgetPanelSettingsScreen
import com.slideindex.app.ui.viewmodel.WidgetPanelEditorViewModel

fun NavEntryBuilder.widgetPanelNavEntries(ctx: MainNavContext) {""",
    slice_body(915, 921),
)

write_file(
    "FloatingPointerNavEntries.kt",
    """import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.FloatingPointerEdgeActionsSettingsScreen
import com.slideindex.app.ui.FloatingPointerEdgeSideSettingsScreen
import com.slideindex.app.ui.FloatingPointerJoystickSettingsScreen
import com.slideindex.app.ui.FloatingPointerPointerSettingsScreen
import com.slideindex.app.ui.FloatingPointerRadialMenuSettingsScreen
import com.slideindex.app.ui.FloatingPointerSettingsScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.PointerSwipeConfigScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.floatingPointerNavEntries(ctx: MainNavContext) {""",
    slice_body(1140, 1624),
)

aggregator = PKG + """
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

fun NavEntryBuilder.extensionNavEntries(ctx: MainNavContext) {
    layoutSettingsNavEntries(ctx)
    nativeEnginePackNavEntry(ctx)
    extensionHubNavEntries(ctx)
    quickLauncherNavEntries(ctx)
    honeycombLauncherNavEntries(ctx)
    holographicLauncherNavEntries(ctx)
    activityShortcutNavEntries(ctx)
    shellCommandNavEntries(ctx)
    widgetPanelNavEntries(ctx)
    stashClipboardNavEntries(ctx)
    searchPanelNavEntries(ctx)
    floatBallNavEntries(ctx)
    floatingPointerNavEntries(ctx)
}
"""
(BASE / "ExtensionNavEntries.kt").write_text(aggregator, encoding="utf-8")
print("updated ExtensionNavEntries.kt")
