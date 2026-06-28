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

    data object None : GestureAction() {
        override val type = GestureActionType.NONE
        override val payload = ""
    }

    companion object {
        fun from(type: GestureActionType, payload: String): GestureAction {
            return when (type) {
                GestureActionType.OPEN_INDEX -> OpenIndex
                GestureActionType.LAUNCH_APP -> LaunchApp(payload)
                GestureActionType.QUICK_LAUNCHER -> QuickLauncher
                GestureActionType.TASK_SWITCHER -> TaskSwitcher
                GestureActionType.BACK -> Back
                GestureActionType.HOME -> Home
                GestureActionType.RECENTS -> Recents
                GestureActionType.CLOSE_CURRENT_APP -> CloseCurrentApp
                GestureActionType.FREE_WINDOW_CURRENT_APP -> FreeWindowCurrentApp
                GestureActionType.CLICK_PASSTHROUGH -> ClickPassthrough
                GestureActionType.NONE -> None
            }
        }
    }
}

fun GestureAction.isEffective(): Boolean = type != GestureActionType.NONE
