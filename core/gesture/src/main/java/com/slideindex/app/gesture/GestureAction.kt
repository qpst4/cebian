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
    QUICK_TOOLS_OVERLAY(31),
    TOGGLE_DND(32),
    SCREEN_RECORD(33),
    TOGGLE_WIFI(34),
    TOGGLE_MOBILE_DATA(35),
    SWITCH_INPUT_METHOD(36),
    WIDGET_POPUP_OVERLAY(37),
    FLOATING_POINTER(38),
    SIMULATE_POINTER_SWIPE(39),
    POINTER_GESTURE_RECORDER(40),
    POINTER_REALTIME_GESTURE(41),
    OPEN_FLOATING_POINTER_RADIAL_MENU(42),
    OPEN_STASH_PANEL(43),
    EXECUTE_SHELL_COMMAND(44),
    FULLSCREEN_SCREENSHOT_PICK(45),
    SEARCH_PANEL(46),
    LOCK_SCREEN_AND_SILENCE_RING(47),
    LOCK_SCREEN_AND_MUTE_ALL(48),
    OPEN_CLIPBOARD_PANEL(49),
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

    data object LockScreenAndSilenceRing : GestureAction() {
        override val type = GestureActionType.LOCK_SCREEN_AND_SILENCE_RING
        override val payload = ""
    }

    data object LockScreenAndMuteAll : GestureAction() {
        override val type = GestureActionType.LOCK_SCREEN_AND_MUTE_ALL
        override val payload = ""
    }

    data object Screenshot : GestureAction() {
        override val type = GestureActionType.SCREENSHOT
        override val payload = ""
    }

    /** Captures the full screen via accessibility screenshot and opens the text pick panel. */
    data object FullscreenScreenshotPick : GestureAction() {
        override val type = GestureActionType.FULLSCREEN_SCREENSHOT_PICK
        override val payload = ""
    }

    /** Opens the search panel overlay with text/image search. */
    data object SearchPanel : GestureAction() {
        override val type = GestureActionType.SEARCH_PANEL
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

    /** Runs a saved shell command when the gesture fires. */
    data class ExecuteShellCommand(
        val command: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.EXECUTE_SHELL_COMMAND
        override val payload = command
    }

    /** Samsung OHO+ style quick-tools popup, rendered top-level via [com.slideindex.app.overlay.OhoQuickToolsOverlayWindow]. */
    data object QuickToolsOverlay : GestureAction() {
        override val type = GestureActionType.QUICK_TOOLS_OVERLAY
        override val payload = ""
    }

    /** Samsung OHO+ style widget popup hosting system App Widgets via [com.slideindex.app.overlay.WidgetPopupOverlayWindow]. */
    data object WidgetPopupOverlay : GestureAction() {
        override val type = GestureActionType.WIDGET_POPUP_OVERLAY
        override val payload = ""
    }

    /** Opens the float-ball stash panel via [com.slideindex.app.overlay.FloatBallStashPanel]. */
    data object StashPanel : GestureAction() {
        override val type = GestureActionType.OPEN_STASH_PANEL
        override val payload = ""
    }

    /** Opens the float-ball clipboard tab via [com.slideindex.app.overlay.FloatBallStashPanel]. */
    data object ClipboardPanel : GestureAction() {
        override val type = GestureActionType.OPEN_CLIPBOARD_PANEL
        override val payload = ""
    }

    /** Virtual joystick + on-screen pointer; tap joystick to click at pointer via accessibility. */
    data object FloatingPointer : GestureAction() {
        override val type = GestureActionType.FLOATING_POINTER
        override val payload = ""
    }

    /** Simulates a swipe starting at the floating pointer position. */
    data class SimulatePointerSwipe(
        val config: PointerSwipeConfig = PointerSwipeConfig.DEFAULT,
    ) : GestureAction() {
        override val type = GestureActionType.SIMULATE_POINTER_SWIPE
        override val payload = PointerSwipeConfigCodec.encode(config)

        companion object {
            fun fromPayload(payload: String) =
                SimulatePointerSwipe(PointerSwipeConfigCodec.decode(payload))
        }
    }

    /** Starts the pointer gesture recorder: samples the pointer path and replays it on release. */
    data object PointerGestureRecorder : GestureAction() {
        override val type = GestureActionType.POINTER_GESTURE_RECORDER
        override val payload = ""
    }

    /** Starts the real-time gesture: the pointer follows the finger via continueStroke. */
    data object PointerRealtimeGesture : GestureAction() {
        override val type = GestureActionType.POINTER_REALTIME_GESTURE
        override val payload = ""
    }

    /** Opens the floating pointer radial action ring. */
    data object OpenFloatingPointerRadialMenu : GestureAction() {
        override val type = GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU
        override val payload = ""
    }

    data object ToggleDnd : GestureAction() {
        override val type = GestureActionType.TOGGLE_DND
        override val payload = ""
    }

    data object ScreenRecord : GestureAction() {
        override val type = GestureActionType.SCREEN_RECORD
        override val payload = ""
    }

    data object ToggleWifi : GestureAction() {
        override val type = GestureActionType.TOGGLE_WIFI
        override val payload = ""
    }

    data object ToggleMobileData : GestureAction() {
        override val type = GestureActionType.TOGGLE_MOBILE_DATA
        override val payload = ""
    }

    data object SwitchInputMethod : GestureAction() {
        override val type = GestureActionType.SWITCH_INPUT_METHOD
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
            FloatingPointer,
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
                GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> LockScreenAndSilenceRing
                GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> LockScreenAndMuteAll
                GestureActionType.SCREENSHOT -> Screenshot
                GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> FullscreenScreenshotPick
                GestureActionType.SEARCH_PANEL -> SearchPanel
                GestureActionType.POWER_MENU -> PowerMenu
                GestureActionType.KEEP_SCREEN_ON -> KeepScreenOn
                GestureActionType.SCROLL_TO_TOP -> ScrollToTop
                GestureActionType.SCROLL_TO_BOTTOM -> ScrollToBottom
                GestureActionType.SHELL_COMMAND_PANEL -> ShellCommandPanel
                GestureActionType.EXECUTE_SHELL_COMMAND -> ExecuteShellCommand(payload)
                GestureActionType.QUICK_TOOLS_OVERLAY -> QuickToolsOverlay
                GestureActionType.WIDGET_POPUP_OVERLAY -> WidgetPopupOverlay
                GestureActionType.OPEN_STASH_PANEL -> StashPanel
                GestureActionType.OPEN_CLIPBOARD_PANEL -> ClipboardPanel
                GestureActionType.FLOATING_POINTER -> FloatingPointer
                GestureActionType.SIMULATE_POINTER_SWIPE -> SimulatePointerSwipe.fromPayload(payload)
                GestureActionType.POINTER_GESTURE_RECORDER -> PointerGestureRecorder
                GestureActionType.POINTER_REALTIME_GESTURE -> PointerRealtimeGesture
                GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> OpenFloatingPointerRadialMenu
                GestureActionType.TOGGLE_DND -> ToggleDnd
                GestureActionType.SCREEN_RECORD -> ScreenRecord
                GestureActionType.TOGGLE_WIFI -> ToggleWifi
                GestureActionType.TOGGLE_MOBILE_DATA -> ToggleMobileData
                GestureActionType.SWITCH_INPUT_METHOD -> SwitchInputMethod
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
