package com.slideindex.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.slideindex.app.ui.MainBottomNavDestination
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey {
    // Home tab
    @Serializable data object HomeMain : AppNavKey
    @Serializable data object HomeAppKeepAlive : AppNavKey
    @Serializable data object HomeLayout : AppNavKey
    @Serializable data object HomeHiddenApps : AppNavKey
    @Serializable data object HomeExcludedApps : AppNavKey
    @Serializable data object HomeExcludedAppsPick : AppNavKey
    @Serializable data object HomeFreeWindow : AppNavKey
    @Serializable data object HomeFreeWindowPreview : AppNavKey
    @Serializable data object HomeFreeWindowLaunchPolicy : AppNavKey
    @Serializable data object HomeFreeWindowMode : AppNavKey
    @Serializable data object HomeTriggerCollection : AppNavKey
    @Serializable data object HomeTriggerCollectionLandscape : AppNavKey
    @Serializable data class HomeSideGestures(val side: String, val handleId: String) : AppNavKey
    @Serializable data class HomeSideGesturesDefaultMode(val side: String, val handleId: String) : AppNavKey
    @Serializable data class HomeSideGestureSlotConfig(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotActionPick(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotModePick(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotQuickLauncherPanel(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val panelId: String = "",
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotShellCommand(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class HomeSideGesturesAppearance(val side: String, val handleId: String) : AppNavKey
    @Serializable data class HomeSideGesturesDesign(val side: String, val handleId: String) : AppNavKey
    @Serializable data object HomeGestureAngle : AppNavKey
    @Serializable data object HomeAnimationStyleSelect : AppNavKey
    @Serializable data object HomeWaveAnimationStyle : AppNavKey
    @Serializable data object HomeCapsuleAnimationStyle : AppNavKey
    @Serializable data object HomeBubbleAnimationStyle : AppNavKey

    @Serializable data object HomeCornerGesture : AppNavKey
    @Serializable data class HomeCornerGestureSlotActionPick(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey

    @Serializable data object HomeCornerGestureInnerZoneActionPick : AppNavKey

    // Shake tab
    @Serializable data object ShakeGestures : AppNavKey
    @Serializable data object ShakeGestureBlacklist : AppNavKey
    @Serializable data object ShakeGestureBlacklistPick : AppNavKey
    @Serializable data object ShakeLockScreenSettings : AppNavKey
    @Serializable data object ShakeIndependentSensitivity : AppNavKey
    @Serializable data object ShakeIndependentAppSettings : AppNavKey
    @Serializable data object ShakeIndependentAppPick : AppNavKey
    @Serializable data class ShakePerAppActions(val packageName: String) : AppNavKey
    @Serializable data class ShakeGestureActionPick(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
    ) : AppNavKey
    @Serializable data class ShakeGestureActionShellCommand(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
        val initialCommand: String = "",
    ) : AppNavKey

    // Notification tab
    @Serializable data object NotificationHub : AppNavKey
    @Serializable data object NotificationHistory : AppNavKey
    @Serializable data object NotificationFilterRules : AppNavKey
    @Serializable data class NotificationFilterRuleEditor(val ruleId: String = "") : AppNavKey
    @Serializable data object NotificationFilterSettings : AppNavKey
    @Serializable data object MessageReminder : AppNavKey
    @Serializable data object MessageReminderAllowedApps : AppNavKey
    @Serializable data class MessageReminderAppFilterEdit(val packageName: String) : AppNavKey
    @Serializable data class MessageReminderGestureActionPick(val slot: String) : AppNavKey
    @Serializable data object MessageReminderDndApps : AppNavKey
    @Serializable data class MessageStyleDetail(val styleId: String) : AppNavKey
    @Serializable data object MessageStyleSideBubbleCount : AppNavKey
    @Serializable data object OtpHub : AppNavKey
    @Serializable data object OtpSettings : AppNavKey
    @Serializable data class OtpRecords(val returnTo: OtpRecordsReturn) : AppNavKey
    @Serializable data object OtpRulesList : AppNavKey
    @Serializable data object OtpAutoInput : AppNavKey
    @Serializable data class OtpAutoFillStats(val returnTo: OtpAutoFillStatsReturn) : AppNavKey

    // Extension tab
    @Serializable data object ExtensionHub : AppNavKey
    @Serializable data object ExtensionAbout : AppNavKey
    @Serializable data object ExtensionBackup : AppNavKey
    @Serializable data object ExtensionMissingPermissions : AppNavKey
    @Serializable data object ExtensionPrivacy : AppNavKey
    @Serializable data object ExtensionThirdPartyNotices : AppNavKey
    @Serializable data class ExtensionLicenseText(val assetFileName: String) : AppNavKey
    @Serializable data object QuickLauncher : AppNavKey
    @Serializable data object HoneycombLauncher : AppNavKey
    @Serializable data object HoneycombDisplaySettings : AppNavKey
    @Serializable data object ShellCommands : AppNavKey
    @Serializable data object ActivityShortcuts : AppNavKey
    @Serializable data object ActivityShortcutPresets : AppNavKey
    @Serializable data object ActivityShortcutPickApp : AppNavKey
    @Serializable data class ActivityShortcutPickActivity(val packageName: String) : AppNavKey
    @Serializable data object ActivityShortcutPickAppShortcut : AppNavKey
    @Serializable data object ShellCommandHistory : AppNavKey
    @Serializable data class ShellCommandEditor(val commandId: String = "") : AppNavKey
    @Serializable data object ShellCommandResult : AppNavKey
    @Serializable data object WidgetPanel : AppNavKey
    @Serializable data object FloatingPointer : AppNavKey
    @Serializable data object StashClipboard : AppNavKey
    @Serializable data object SearchPanel : AppNavKey
    @Serializable data object SearchPanelFileSearch : AppNavKey
    @Serializable data object SearchPanelAppSearch : AppNavKey
    @Serializable data object SearchPanelContactSearch : AppNavKey
    @Serializable data object SearchPanelSystemSettingsSearch : AppNavKey
    @Serializable data object FloatBall : AppNavKey
    @Serializable data object OcrModels : AppNavKey
    @Serializable data object NativeEnginePacks : AppNavKey
    @Serializable data object FloatBallAppearance : AppNavKey
    @Serializable data object FloatBallStyle : AppNavKey
    @Serializable data object FloatBallGesture : AppNavKey
    @Serializable data class FloatBallGestureActionPick(val gestureTypeId: Int) : AppNavKey
    @Serializable data class FloatBallGestureShellCommand(
        val gestureTypeId: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data object FloatBallPick : AppNavKey
    @Serializable data object ShareImageOcrHistory : AppNavKey
    @Serializable data object FloatBallTranslation : AppNavKey
    @Serializable data object FloatBallSearchEngine : AppNavKey
    @Serializable data class FloatBallSearchEngineEditor(val engineId: String = "") : AppNavKey
    @Serializable data object FloatBallSearchEnginePreviewSort : AppNavKey
    @Serializable data object FloatBallImageSearchEngine : AppNavKey
    @Serializable data class FloatBallImageSearchEngineEditor(val engineId: String = "") : AppNavKey
    @Serializable data class FloatBallImageSearchEngineDetail(val engineId: String) : AppNavKey
    @Serializable data object TranslateModels : AppNavKey
    @Serializable data object FloatingPointerPointer : AppNavKey
    @Serializable data object FloatingPointerJoystick : AppNavKey
    @Serializable data object FloatingPointerRadialMenu : AppNavKey
    @Serializable data class FloatingPointerRadialActionPick(
        val target: FloatingPointerRadialActionTarget,
        val slotIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialShellCommand(
        val slotIndex: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialSwipeConfig(val slotIndex: Int) : AppNavKey
    @Serializable data object FloatingPointerEdgeActions : AppNavKey
    @Serializable data class FloatingPointerEdgeSideSettings(val side: String) : AppNavKey
    @Serializable data class FloatingPointerEdgeActionPick(val side: String, val slotIndex: Int) : AppNavKey
    @Serializable data class FloatingPointerEdgeShellCommand(
        val side: String,
        val slotIndex: Int,
        val initialCommand: String = "",
    ) : AppNavKey
}

@Serializable
enum class FloatingPointerRadialActionTarget {
    LONG_PRESS,
    SLOT,
}

@Serializable
enum class OtpRecordsReturn {
    Hub,
    Settings,
}

@Serializable
enum class OtpAutoFillStatsReturn {
    Hub,
    AutoInput,
}

fun AppNavKey.isRootDestination(): Boolean = when (this) {
    AppNavKey.HomeMain,
    AppNavKey.ShakeGestures,
    AppNavKey.NotificationHub,
    AppNavKey.ExtensionHub,
    -> true
    else -> false
}

fun AppNavKey.toBottomNavDestination(): MainBottomNavDestination = when (this) {
    AppNavKey.ShakeGestures -> MainBottomNavDestination.Shake
    AppNavKey.NotificationHub -> MainBottomNavDestination.Notification
    AppNavKey.ExtensionHub -> MainBottomNavDestination.Extension
    else -> MainBottomNavDestination.Home
}

fun MainBottomNavDestination.toRootNavKey(): AppNavKey = when (this) {
    MainBottomNavDestination.Home -> AppNavKey.HomeMain
    MainBottomNavDestination.Shake -> AppNavKey.ShakeGestures
    MainBottomNavDestination.Notification -> AppNavKey.NotificationHub
    MainBottomNavDestination.Extension -> AppNavKey.ExtensionHub
}
