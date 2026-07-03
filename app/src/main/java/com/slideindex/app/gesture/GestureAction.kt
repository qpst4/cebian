package com.slideindex.app.gesture

enum class GestureActionType(val id: Int) {
    OPEN_INDEX(0),
    LAUNCH_APP(1),
    QUICK_LAUNCHER(2),
    TASK_SWITCHER(3),
    BACK(4),
    HOME(5),
    RECENTS(6),
    NONE(7),
    CLOSE_CURRENT_APP(8),
    FREE_WINDOW_CURRENT_APP(9),
    CLICK_PASSTHROUGH(10),
    FLASHLIGHT(11),
    ADJUST_VOLUME(12),
    ADJUST_BRIGHTNESS(13),
    LAUNCH_ASSISTANT(14),
    LAUNCH_SHORTCUT(15),
    TOGGLE_MUTE(16),
    MEDIA_PLAY_PAUSE(17),
    MEDIA_PREVIOUS(18),
    MEDIA_NEXT(19),
    PREVIOUS_APP(20),
    OPEN_NOTIFICATIONS(21),
    OPEN_QUICK_SETTINGS(22),
    LOCK_SCREEN(23),
    SCREENSHOT(24),
    POWER_MENU(25),
    KEEP_SCREEN_ON(26),
    SCROLL_TO_TOP(27),
    SCROLL_TO_BOTTOM(28),
    SHELL_COMMAND_PANEL(29),
    ;

    companion object {
        fun fromId(id: Int): GestureActionType =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

sealed class GestureAction {
    abstract val type: GestureActionType
    abstract val payload: String

    data object OpenIndex : GestureAction() {
        override val type = GestureActionType.OPEN_INDEX
        override val payload = ""
    }

    data class LaunchApp(
        val packageName: String,
        val fullscreen: Boolean = true,
    ) : GestureAction() {
        override val type = GestureActionType.LAUNCH_APP
        override val payload = packageName
    }

    data class LaunchShortcut(
        val payloadKey: String,
        val label: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.LAUNCH_SHORTCUT
        override val payload = payloadKey

        companion object {
            fun dynamic(packageName: String, shortcutId: String, label: String = "") =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeDynamic(packageName, shortcutId, label),
                    label = label,
                )

            fun component(componentFlat: String, label: String = "") =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeComponent(componentFlat, label),
                    label = label,
                )

            fun intent(intentUri: String, label: String = "") =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeIntent(intentUri, label),
                    label = label,
                )

            fun intents(intentUris: List<String>, label: String = "") =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeIntents(intentUris, label),
                    label = label,
                )

            fun fromCreated(created: com.slideindex.app.util.AppShortcutLoader.CreatedShortcut): LaunchShortcut {
                created.intentUri?.let { uri ->
                    return intent(uri, created.label)
                }
                val component = created.componentFlat
                    ?: error("CreatedShortcut missing component and intent")
                return component(component, created.label)
            }

            fun fromPayload(payload: String): LaunchShortcut {
                val decoded = GestureShortcutPayload.decode(payload)
                return LaunchShortcut(
                    payloadKey = payload,
                    label = decoded?.label.orEmpty(),
                )
            }
        }
    }

    data object QuickLauncher : GestureAction() {
        override val type = GestureActionType.QUICK_LAUNCHER
        override val payload = ""
    }

    data object TaskSwitcher : GestureAction() {
        override val type = GestureActionType.TASK_SWITCHER
        override val payload = ""
    }

    data object Back : GestureAction() {
        override val type = GestureActionType.BACK
        override val payload = ""
    }

    data object Home : GestureAction() {
        override val type = GestureActionType.HOME
        override val payload = ""
    }

    data object Recents : GestureAction() {
        override val type = GestureActionType.RECENTS
        override val payload = ""
    }

    data object CloseCurrentApp : GestureAction() {
        override val type = GestureActionType.CLOSE_CURRENT_APP
        override val payload = ""
    }

    data object FreeWindowCurrentApp : GestureAction() {
        override val type = GestureActionType.FREE_WINDOW_CURRENT_APP
        override val payload = ""
    }

    data object ClickPassthrough : GestureAction() {
        override val type = GestureActionType.CLICK_PASSTHROUGH
        override val payload = ""
    }

    data object Flashlight : GestureAction() {
        override val type = GestureActionType.FLASHLIGHT
        override val payload = ""
    }

    data object AdjustVolume : GestureAction() {
        override val type = GestureActionType.ADJUST_VOLUME
        override val payload = ""
    }

    data object AdjustBrightness : GestureAction() {
        override val type = GestureActionType.ADJUST_BRIGHTNESS
        override val payload = ""
    }

    data object LaunchAssistant : GestureAction() {
        override val type = GestureActionType.LAUNCH_ASSISTANT
        override val payload = ""
    }

    data object ToggleMute : GestureAction() {
        override val type = GestureActionType.TOGGLE_MUTE
        override val payload = ""
    }

    data object MediaPlayPause : GestureAction() {
        override val type = GestureActionType.MEDIA_PLAY_PAUSE
        override val payload = ""
    }

    data object MediaPrevious : GestureAction() {
        override val type = GestureActionType.MEDIA_PREVIOUS
        override val payload = ""
    }

    data object MediaNext : GestureAction() {
        override val type = GestureActionType.MEDIA_NEXT
        override val payload = ""
    }

    data object PreviousApp : GestureAction() {
        override val type = GestureActionType.PREVIOUS_APP
        override val payload = ""
    }

    data object OpenNotifications : GestureAction() {
        override val type = GestureActionType.OPEN_NOTIFICATIONS
        override val payload = ""
    }

    data object OpenQuickSettings : GestureAction() {
        override val type = GestureActionType.OPEN_QUICK_SETTINGS
        override val payload = ""
    }

    data object LockScreen : GestureAction() {
        override val type = GestureActionType.LOCK_SCREEN
        override val payload = ""
    }

    data object Screenshot : GestureAction() {
        override val type = GestureActionType.SCREENSHOT
        override val payload = ""
    }

    data object PowerMenu : GestureAction() {
        override val type = GestureActionType.POWER_MENU
        override val payload = ""
    }

    data object KeepScreenOn : GestureAction() {
        override val type = GestureActionType.KEEP_SCREEN_ON
        override val payload = ""
    }

    data object ScrollToTop : GestureAction() {
        override val type = GestureActionType.SCROLL_TO_TOP
        override val payload = ""
    }

    data object ScrollToBottom : GestureAction() {
        override val type = GestureActionType.SCROLL_TO_BOTTOM
        override val payload = ""
    }

    data object ShellCommandPanel : GestureAction() {
        override val type = GestureActionType.SHELL_COMMAND_PANEL
        override val payload = ""
    }

    data object None : GestureAction() {
        override val type = GestureActionType.NONE
        override val payload = ""
    }

    companion object {
        /** Actions that support [GestureTriggerMode.CONTINUOUS] on compatible triggers. */
        val continuousTrackingActions: List<GestureAction> = listOf(
            OpenIndex,
            QuickLauncher,
            TaskSwitcher,
            ShellCommandPanel,
            AdjustVolume,
            AdjustBrightness,
        )

        fun from(type: GestureActionType, payload: String): GestureAction {
            return when (type) {
                GestureActionType.OPEN_INDEX -> OpenIndex
                GestureActionType.LAUNCH_APP -> LaunchApp(payload)
                GestureActionType.LAUNCH_SHORTCUT -> LaunchShortcut.fromPayload(payload)
                GestureActionType.QUICK_LAUNCHER -> QuickLauncher
                GestureActionType.TASK_SWITCHER -> TaskSwitcher
                GestureActionType.BACK -> Back
                GestureActionType.HOME -> Home
                GestureActionType.RECENTS -> Recents
                GestureActionType.CLOSE_CURRENT_APP -> CloseCurrentApp
                GestureActionType.FREE_WINDOW_CURRENT_APP -> FreeWindowCurrentApp
                GestureActionType.CLICK_PASSTHROUGH -> ClickPassthrough
                GestureActionType.FLASHLIGHT -> Flashlight
                GestureActionType.ADJUST_VOLUME -> AdjustVolume
                GestureActionType.ADJUST_BRIGHTNESS -> AdjustBrightness
                GestureActionType.LAUNCH_ASSISTANT -> LaunchAssistant
                GestureActionType.TOGGLE_MUTE -> ToggleMute
                GestureActionType.MEDIA_PLAY_PAUSE -> MediaPlayPause
                GestureActionType.MEDIA_PREVIOUS -> MediaPrevious
                GestureActionType.MEDIA_NEXT -> MediaNext
                GestureActionType.PREVIOUS_APP -> PreviousApp
                GestureActionType.OPEN_NOTIFICATIONS -> OpenNotifications
                GestureActionType.OPEN_QUICK_SETTINGS -> OpenQuickSettings
                GestureActionType.LOCK_SCREEN -> LockScreen
                GestureActionType.SCREENSHOT -> Screenshot
                GestureActionType.POWER_MENU -> PowerMenu
                GestureActionType.KEEP_SCREEN_ON -> KeepScreenOn
                GestureActionType.SCROLL_TO_TOP -> ScrollToTop
                GestureActionType.SCROLL_TO_BOTTOM -> ScrollToBottom
                GestureActionType.SHELL_COMMAND_PANEL -> ShellCommandPanel
                GestureActionType.NONE -> None
            }
        }
    }
}

fun GestureAction.isEffective(): Boolean = type != GestureActionType.NONE

fun GestureAction.supportsContinuousTracking(trigger: GestureTriggerType): Boolean {
    if (this !in GestureAction.continuousTrackingActions) return false
    return !trigger.isPressOrTap
}

fun GestureAction.preferredTriggerMode(trigger: GestureTriggerType): GestureTriggerMode? =
    when (this) {
        GestureAction.OpenIndex ->
            if (!trigger.isPressOrTap) GestureTriggerMode.CONTINUOUS else null
        GestureAction.QuickLauncher, GestureAction.ShellCommandPanel ->
            if (trigger.supportsIndex) GestureTriggerMode.CONTINUOUS else null
        GestureAction.AdjustVolume, GestureAction.AdjustBrightness ->
            if (!trigger.isPressOrTap) GestureTriggerMode.ON_RELEASE else null
        else -> null
    }
