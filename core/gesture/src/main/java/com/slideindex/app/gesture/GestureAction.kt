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
    CORNER_INNER_CANCEL(50),
    CORNER_INNER_PIN_WHEEL(51),
    SNOOZE_OVERLAYS(52),
    HONEYCOMB_LAUNCHER(53),
    REGIONAL_SCREENSHOT_PICK(54),
    CLIPBOARD_PICK(55),
    APP_SWITCHER(56),
    OPEN_CLIPBOARD_FLOAT(57),
    HOLOGRAPHIC_LAUNCHER(58),
    VOLUME_PANEL(59),
    SCREEN_TRANSLATE(60),
    REMIND_1M(61),
    REMIND_3M(62),
    REMIND_5M(63),
    REMIND_10M(64),
    REMIND_15M(65),
    UNIVERSAL_COPY(66),
    FREEZER_PANEL(67),
    REFREEZE(68),
    TOGGLE_AUTO_BRIGHTNESS(69),
    REMIND(70),
    VOICE_SEARCH(71),
    VOICE_ASSISTANT(72),
    TOGGLE_AUTO_ROTATE(73),
    FORCE_PORTRAIT(74),
    FORCE_LANDSCAPE(75),
    OPEN_INTERNET_PANEL(76),
    OPEN_VOLUME_PANEL(77),
    CURRENT_APP_INFO(79),
    SIMULATE_KEY_EVENT(80),
    SCREEN_OFF_KEEP_AWAKE(81),
    PIN_TO_SCREEN(82),
    APP_CAROUSEL_SWITCHER(83),
    FOREGROUND_ACTIVITY_INSPECTOR(84),
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

            fun intent(intentUri: String, label: String = "", hostPackage: String? = null) =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeIntent(intentUri, label, hostPackage),
                    label = label,
                )

            fun intents(intentUris: List<String>, label: String = "", hostPackage: String? = null) =
                LaunchShortcut(
                    payloadKey = GestureShortcutPayload.encodeIntents(intentUris, label, hostPackage),
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

    data class QuickLauncher(
        val panelId: String = "",
    ) : GestureAction() {
        override val type = GestureActionType.QUICK_LAUNCHER
        override val payload = panelId
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

    data object VoiceSearch : GestureAction() {
        override val type = GestureActionType.VOICE_SEARCH
        override val payload = ""
    }

    data object VoiceAssistant : GestureAction() {
        override val type = GestureActionType.VOICE_ASSISTANT
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

    /** Edge-gesture regional screenshot & text pick (no persistent float ball). */
    data object RegionalScreenshotPick : GestureAction() {
        override val type = GestureActionType.REGIONAL_SCREENSHOT_PICK
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

    /** Opens the floating clipboard window via [ClipboardFloatService]. */
    data object ClipboardFloat : GestureAction() {
        override val type = GestureActionType.OPEN_CLIPBOARD_FLOAT
        override val payload = ""
    }

    /** Reads the current system clipboard and opens the text pick panel. */
    data object ClipboardPick : GestureAction() {
        override val type = GestureActionType.CLIPBOARD_PICK
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

    /** 底角轮盘内环空白：松手取消轮盘。 */
    data object CornerInnerCancel : GestureAction() {
        override val type = GestureActionType.CORNER_INNER_CANCEL
        override val payload = ""
    }

    /** 底角轮盘内环空白：松手后轮盘驻留，直至点槽位/轮盘外/再次点内环。 */
    data object CornerInnerPinWheel : GestureAction() {
        override val type = GestureActionType.CORNER_INNER_PIN_WHEEL
        override val payload = ""
    }

    /** 临时隐藏触钮、边角轮盘与悬浮球。 */
    data object SnoozeOverlays : GestureAction() {
        override val type = GestureActionType.SNOOZE_OVERLAYS
        override val payload = ""
    }

    /** 蜂窝布局应用启动器，按住滑选后松手启动。 */
    data object HoneycombLauncher : GestureAction() {
        override val type = GestureActionType.HONEYCOMB_LAUNCHER
        override val payload = ""
    }

    /** FV 风格贴边半圆圆环启动器，按住滑选后松手启动。 */
    data object AppSwitcher : GestureAction() {
        override val type = GestureActionType.APP_SWITCHER
        override val payload = ""
    }

    /** 独立应用切换器：卡片轮播与自适应 Squircle 大图标，支持持续/松手/即时触发。 */
    data object AppCarouselSwitcher : GestureAction() {
        override val type = GestureActionType.APP_CAROUSEL_SWITCHER
        override val payload = ""
    }

    /** 全屏 3D 球应用启动器，弹出后拖拽旋转、点击图标启动。 */
    data object HolographicLauncher : GestureAction() {
        override val type = GestureActionType.HOLOGRAPHIC_LAUNCHER
        override val payload = ""
    }

    /** 弹出音量面板，同时调节闹钟/铃声/媒体音量与亮度。 */
    data object VolumePanel : GestureAction() {
        override val type = GestureActionType.VOLUME_PANEL
        override val payload = ""
    }

    /** 屏幕翻译：在原位覆盖译文（开关式）。 */
    data object ScreenTranslate : GestureAction() {
        override val type = GestureActionType.SCREEN_TRANSLATE
        override val payload = ""
    }

    /** 触发后弹出时长选择，设置 N 分钟后闹钟。 */
    data object Remind : GestureAction() {
        override val type = GestureActionType.REMIND
        override val payload = ""
    }

    data object Remind1m : GestureAction() {
        override val type = GestureActionType.REMIND_1M
        override val payload = ""
    }

    data object Remind3m : GestureAction() {
        override val type = GestureActionType.REMIND_3M
        override val payload = ""
    }

    data object Remind5m : GestureAction() {
        override val type = GestureActionType.REMIND_5M
        override val payload = ""
    }

    data object Remind10m : GestureAction() {
        override val type = GestureActionType.REMIND_10M
        override val payload = ""
    }

    data object Remind15m : GestureAction() {
        override val type = GestureActionType.REMIND_15M
        override val payload = ""
    }

    /** Google Lens 风格全局复制：高亮框选屏幕文本后复制。 */
    data object UniversalCopy : GestureAction() {
        override val type = GestureActionType.UNIVERSAL_COPY
        override val payload = ""
    }

    /** 打开冰箱应用管理页。 */
    data object FreezerPanel : GestureAction() {
        override val type = GestureActionType.FREEZER_PANEL
        override val payload = ""
    }

    /** 一键重冻冰箱列表中已启用的应用。 */
    data object Refreeze : GestureAction() {
        override val type = GestureActionType.REFREEZE
        override val payload = ""
    }

    /** 打开或关闭系统自动亮度。 */
    data object ToggleAutoBrightness : GestureAction() {
        override val type = GestureActionType.TOGGLE_AUTO_BRIGHTNESS
        override val payload = ""
    }

    /** 开启或关闭屏幕自动旋转开关。 */
    data object ToggleAutoRotate : GestureAction() {
        override val type = GestureActionType.TOGGLE_AUTO_ROTATE
        override val payload = ""
    }

    /** 锁定屏幕为竖屏方向。 */
    data object ForcePortrait : GestureAction() {
        override val type = GestureActionType.FORCE_PORTRAIT
        override val payload = ""
    }

    /** 锁定屏幕为横屏方向。 */
    data object ForceLandscape : GestureAction() {
        override val type = GestureActionType.FORCE_LANDSCAPE
        override val payload = ""
    }

    /** 打开原生网络连接面板（Wi-Fi/移动网络）。 */
    data object OpenInternetPanel : GestureAction() {
        override val type = GestureActionType.OPEN_INTERNET_PANEL
        override val payload = ""
    }

    /** 调出系统原生声音调节面板。 */
    data object OpenVolumePanel : GestureAction() {
        override val type = GestureActionType.OPEN_VOLUME_PANEL
        override val payload = ""
    }

    /** 打开当前前台应用信息页面。 */
    data object CurrentAppInfo : GestureAction() {
        override val type = GestureActionType.CURRENT_APP_INFO
        override val payload = ""
    }

    /** 息屏挂机 / 伪息屏（全屏黑屏+最低亮度+保持唤醒+双击/音量键解除）。 */
    data object ScreenOffKeepAwake : GestureAction() {
        override val type = GestureActionType.SCREEN_OFF_KEEP_AWAKE
        override val payload = ""
    }

    /** 钉到屏幕（轻量弹窗选择文本或图片，生成悬浮便签或图片置顶）。 */
    data object PinToScreen : GestureAction() {
        override val type = GestureActionType.PIN_TO_SCREEN
        override val payload = ""
    }

    /** 实时前台活动探测悬浮窗（显示当前前台应用包名与 Activity 类名）。 */
    data object ForegroundActivityInspector : GestureAction() {
        override val type = GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR
        override val payload = ""
    }

    /** 模拟按键事件（支持自定义 KeyCode 与长按）。 */
    data class SimulateKeyEvent(
        val keyCode: Int = 82,
        val keyName: String = "",
        val isLongPress: Boolean = false,
    ) : GestureAction() {
        override val type = GestureActionType.SIMULATE_KEY_EVENT
        override val payload: String
            get() = "$keyCode:$isLongPress:$keyName"

        companion object {
            fun fromPayload(payload: String): SimulateKeyEvent {
                if (payload.isBlank()) return SimulateKeyEvent(82, "KEYCODE_MENU", false)
                val parts = payload.split(":", limit = 3)
                val code = parts.getOrNull(0)?.toIntOrNull() ?: 82
                val longPress = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: false
                val name = parts.getOrNull(2).orEmpty()
                return SimulateKeyEvent(code, name, longPress)
            }
        }
    }

    companion object {
        /** Actions that support [GestureTriggerMode.CONTINUOUS] on compatible triggers. */
        val continuousTrackingActions: List<GestureAction> = listOf(
            OpenIndex,
            QuickLauncher(),
            TaskSwitcher,
            ShellCommandPanel,
            HoneycombLauncher,
            AppSwitcher,
            AppCarouselSwitcher,
            AdjustVolume,
            AdjustBrightness,
            FloatingPointer,
            RegionalScreenshotPick,
        )

        fun from(type: GestureActionType, payload: String): GestureAction =
            when (type) {
                GestureActionType.OPEN_INDEX -> OpenIndex
                GestureActionType.LAUNCH_APP -> LaunchApp(payload)
                GestureActionType.LAUNCH_SHORTCUT -> LaunchShortcut.fromPayload(payload)
                GestureActionType.QUICK_LAUNCHER -> QuickLauncher(payload)
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
                GestureActionType.VOICE_SEARCH -> VoiceSearch
                GestureActionType.VOICE_ASSISTANT -> VoiceAssistant
                GestureActionType.TOGGLE_AUTO_ROTATE -> ToggleAutoRotate
                GestureActionType.FORCE_PORTRAIT -> ForcePortrait
                GestureActionType.FORCE_LANDSCAPE -> ForceLandscape
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
                GestureActionType.REGIONAL_SCREENSHOT_PICK -> RegionalScreenshotPick
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
                GestureActionType.OPEN_CLIPBOARD_FLOAT -> ClipboardFloat
                GestureActionType.CLIPBOARD_PICK -> ClipboardPick
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
                GestureActionType.CORNER_INNER_CANCEL -> CornerInnerCancel
                GestureActionType.CORNER_INNER_PIN_WHEEL -> CornerInnerPinWheel
                GestureActionType.SNOOZE_OVERLAYS -> SnoozeOverlays
                GestureActionType.HONEYCOMB_LAUNCHER -> HoneycombLauncher
                GestureActionType.APP_SWITCHER -> AppSwitcher
                GestureActionType.APP_CAROUSEL_SWITCHER -> AppCarouselSwitcher
                GestureActionType.HOLOGRAPHIC_LAUNCHER -> HolographicLauncher
                GestureActionType.VOLUME_PANEL -> VolumePanel
                GestureActionType.SCREEN_TRANSLATE -> ScreenTranslate
                GestureActionType.REMIND -> Remind
                GestureActionType.REMIND_1M,
                GestureActionType.REMIND_3M,
                GestureActionType.REMIND_5M,
                GestureActionType.REMIND_10M,
                GestureActionType.REMIND_15M,
                -> Remind
                GestureActionType.UNIVERSAL_COPY -> UniversalCopy
                GestureActionType.FREEZER_PANEL -> FreezerPanel
                GestureActionType.REFREEZE -> Refreeze
                GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> ToggleAutoBrightness
                GestureActionType.OPEN_INTERNET_PANEL -> OpenInternetPanel
                GestureActionType.OPEN_VOLUME_PANEL -> OpenVolumePanel
                GestureActionType.CURRENT_APP_INFO -> CurrentAppInfo
                GestureActionType.SIMULATE_KEY_EVENT -> SimulateKeyEvent.fromPayload(payload)
                GestureActionType.SCREEN_OFF_KEEP_AWAKE -> ScreenOffKeepAwake
                GestureActionType.PIN_TO_SCREEN -> PinToScreen
                GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> ForegroundActivityInspector
                GestureActionType.NONE -> None
            }.normalized()

        fun remindMinutes(type: GestureActionType): Int? = when (type) {
            GestureActionType.REMIND_1M -> 1
            GestureActionType.REMIND_3M -> 3
            GestureActionType.REMIND_5M -> 5
            GestureActionType.REMIND_10M -> 10
            GestureActionType.REMIND_15M -> 15
            else -> null
        }

        val legacyRemindTypes: Set<GestureActionType> = setOf(
            GestureActionType.REMIND_1M,
            GestureActionType.REMIND_3M,
            GestureActionType.REMIND_5M,
            GestureActionType.REMIND_10M,
            GestureActionType.REMIND_15M,
        )
    }
}

fun GestureAction.isEffective(): Boolean = type != GestureActionType.NONE

/** 将旧版固定档位延时提醒迁移为统一的 [GestureAction.Remind]。 */
fun GestureAction.normalized(): GestureAction =
    if (type in GestureAction.legacyRemindTypes) GestureAction.Remind else this

fun GestureAction.isRemindAction(): Boolean = when (this) {
    GestureAction.Remind,
    GestureAction.Remind1m,
    GestureAction.Remind3m,
    GestureAction.Remind5m,
    GestureAction.Remind10m,
    GestureAction.Remind15m,
    -> true
    else -> false
}

fun GestureAction.isCornerInnerZoneOnly(): Boolean =
    this is GestureAction.CornerInnerCancel || this is GestureAction.CornerInnerPinWheel

/** Actions that only work with [GestureTriggerMode.CONTINUOUS] (not on-release / immediate). */
fun GestureAction.requiresContinuousTriggerOnly(): Boolean =
    this is GestureAction.RegionalScreenshotPick

/** [GestureAction.continuousTrackingActions] membership by action kind (not payload). */
fun GestureAction.isContinuousTrackingKind(): Boolean =
    GestureAction.continuousTrackingActions.any { ref ->
        when (ref) {
            is GestureAction.QuickLauncher -> this is GestureAction.QuickLauncher
            else -> this == ref
        }
    }

fun GestureAction.supportsContinuousTracking(trigger: GestureTriggerType): Boolean {
    if (!isContinuousTrackingKind()) return false
    return when (this) {
        GestureAction.AppSwitcher,
        GestureAction.AppCarouselSwitcher,
        GestureAction.HoneycombLauncher,
        is GestureAction.QuickLauncher,
        GestureAction.ShellCommandPanel,
        -> trigger.isLongPress || !trigger.isPressOrTap
        else -> !trigger.isPressOrTap
    }
}

fun GestureAction.preferredTriggerMode(trigger: GestureTriggerType): GestureTriggerMode? =
    when (this) {
        GestureAction.OpenIndex ->
            if (!trigger.isPressOrTap) GestureTriggerMode.CONTINUOUS else null
        is GestureAction.QuickLauncher, GestureAction.ShellCommandPanel, GestureAction.HoneycombLauncher,
        GestureAction.AppSwitcher,
        ->
            when {
                trigger.isLongPress -> GestureTriggerMode.CONTINUOUS
                trigger.supportsIndex -> GestureTriggerMode.CONTINUOUS
                else -> null
            }
        GestureAction.AdjustVolume, GestureAction.AdjustBrightness ->
            if (!trigger.isPressOrTap) GestureTriggerMode.ON_RELEASE else null
        GestureAction.AppCarouselSwitcher,
        GestureAction.RegionalScreenshotPick,
        GestureAction.FloatingPointer,
        ->
            if (!trigger.isPressOrTap) GestureTriggerMode.CONTINUOUS else null
        else -> null
    }
