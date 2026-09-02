package com.slideindex.app.external

import android.net.Uri
import com.slideindex.app.R

object ExternalInvocationCatalog {
    const val PACKAGE_NAME = "com.slideindex.app"
    const val SCHEME = "cebian"
    const val HOST = "open"
    const val QUERY_PARAM = "q"

    data class DeeplinkEntry(
        val titleRes: Int,
        val descriptionRes: Int,
        val path: String,
        val supportsQuery: Boolean,
    )

    data class ActionEntry(
        val titleRes: Int,
        val descriptionRes: Int,
        val action: String,
        val componentClass: String,
        val supportsQuery: Boolean,
    )

    val deeplinks: List<DeeplinkEntry> = listOf(
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_notification_history,
            descriptionRes = R.string.external_invocation_entry_notification_history_desc,
            path = "notification-history",
            supportsQuery = true,
        ),
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_stash,
            descriptionRes = R.string.external_invocation_entry_stash_desc,
            path = "stash",
            supportsQuery = true,
        ),
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_clipboard,
            descriptionRes = R.string.external_invocation_entry_clipboard_desc,
            path = "clipboard",
            supportsQuery = true,
        ),
        DeeplinkEntry(
            titleRes = R.string.external_invocation_entry_search_panel,
            descriptionRes = R.string.external_invocation_entry_search_panel_desc,
            path = "search-panel",
            supportsQuery = true,
        ),
    )

    val actions: List<ActionEntry> = listOf(
        ActionEntry(
            titleRes = R.string.external_invocation_entry_notification_history,
            descriptionRes = R.string.external_invocation_action_notification_history_desc,
            action = "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY",
            componentClass = "com.slideindex.app.MainActivity",
            supportsQuery = false,
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_stash,
            descriptionRes = R.string.external_invocation_action_stash_desc,
            action = "com.slideindex.app.action.OPEN_STASH_PANEL",
            componentClass = "com.slideindex.app.service.StashClipboardTrampolineActivity",
            supportsQuery = true,
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_clipboard,
            descriptionRes = R.string.external_invocation_action_clipboard_desc,
            action = "com.slideindex.app.action.OPEN_CLIPBOARD_PANEL",
            componentClass = "com.slideindex.app.service.StashClipboardTrampolineActivity",
            supportsQuery = true,
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_search_panel,
            descriptionRes = R.string.external_invocation_action_search_panel_desc,
            action = "com.slideindex.app.action.OPEN_SEARCH_PANEL",
            componentClass = "com.slideindex.app.service.SearchPanelTrampolineActivity",
            supportsQuery = true,
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_toggle_gesture,
            descriptionRes = R.string.external_invocation_action_toggle_gesture_desc,
            action = "com.slideindex.app.action.TOGGLE_GESTURE",
            componentClass = "com.slideindex.app.service.ToggleGestureTrampolineActivity",
            supportsQuery = false,
        ),
        ActionEntry(
            titleRes = R.string.external_invocation_entry_shell_panel,
            descriptionRes = R.string.external_invocation_action_shell_panel_desc,
            action = "com.slideindex.app.action.OPEN_SHELL_PANEL",
            componentClass = "com.slideindex.app.service.ShellCommandPanelTrampolineActivity",
            supportsQuery = false,
        ),
    )

    fun deeplinkUri(path: String): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(path)
            .build()
            .toString()

    fun adbViewCommand(uri: String): String =
        "adb shell am start -a android.intent.action.VIEW -d \"$uri\""

    fun adbActionCommand(entry: ActionEntry): String {
        val component = componentRelativeName(entry.componentClass)
        val builder = StringBuilder()
            .append("adb shell am start -a ")
            .append(entry.action)
            .append(" -n ")
            .append(PACKAGE_NAME)
            .append('/')
            .append(component)
        if (entry.supportsQuery) {
            builder.append(" --es ")
                .append(QUERY_PARAM)
                .append(" \"关键词\"")
        }
        return builder.toString()
    }

    private fun componentRelativeName(componentClass: String): String =
        componentClass.removePrefix("$PACKAGE_NAME.")
}
