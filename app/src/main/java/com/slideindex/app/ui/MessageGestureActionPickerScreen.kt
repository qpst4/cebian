package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageAction
import com.slideindex.app.ui.settings.components.settingsCardScopeItem

private val messageGesturePickerActions = listOf(
    MessageAction.Read,
    MessageAction.ReadInSmallWindow,
    MessageAction.QuickReply,
    MessageAction.QuickReplyAndIgnore,
    MessageAction.QuickReplyAndRemove,
    MessageAction.Ignore,
    MessageAction.IgnoreAndRemove,
    MessageAction.IgnoreAll,
    MessageAction.IgnoreAndRemoveAll,
    MessageAction.IgnoreSameSource,
    MessageAction.IgnoreSameSourceAndRemove,
    MessageAction.Dnd5Min,
)

@Composable
fun MessageGestureActionPickerScreen(
    current: MessageAction,
    onBack: () -> Unit,
    onSelect: (MessageAction) -> Unit,
) {
    val radioItems = buildList {
        messageGesturePickerActions.forEach { action ->
            add(
                settingsCardScopeItem("action-${action.name}") {
                    SettingRadioRow(
                        title = messageActionLabel(action),
                        subtitle = messageActionSubtitle(action),
                        selected = action == current,
                        onClick = { onSelect(action) },
                    )
                },
            )
        }
    }
    SettingsRadioPickerScreen(
        title = stringResource(R.string.message_reminder_pick_action),
        onBack = onBack,
        items = radioItems,
    )
}

@Composable
fun messageActionLabel(action: MessageAction): String = when (action) {
    MessageAction.Read -> stringResource(R.string.message_action_read)
    MessageAction.ReadInSmallWindow -> stringResource(R.string.message_action_read_small_window)
    MessageAction.Ignore -> stringResource(R.string.message_action_ignore)
    MessageAction.IgnoreAndRemove -> stringResource(R.string.message_action_ignore_remove)
    MessageAction.IgnoreAll -> stringResource(R.string.message_action_ignore_all)
    MessageAction.IgnoreAndRemoveAll -> stringResource(R.string.message_action_ignore_remove_all)
    MessageAction.IgnoreSameSource -> stringResource(R.string.message_action_ignore_same_source)
    MessageAction.IgnoreSameSourceAndRemove -> stringResource(R.string.message_action_ignore_same_source_remove)
    MessageAction.Dnd5Min -> stringResource(R.string.message_action_dnd_5min)
    MessageAction.QuickReply -> stringResource(R.string.message_action_quick_reply)
    MessageAction.QuickReplyAndIgnore -> stringResource(R.string.message_action_quick_reply_ignore)
    MessageAction.QuickReplyAndRemove -> stringResource(R.string.message_action_quick_reply_remove)
}

@Composable
private fun messageActionSubtitle(action: MessageAction): String? = when (action) {
    MessageAction.ReadInSmallWindow -> stringResource(R.string.message_action_read_small_window_desc)
    MessageAction.IgnoreAll -> stringResource(R.string.message_action_ignore_all_desc)
    MessageAction.IgnoreAndRemoveAll -> stringResource(R.string.message_action_ignore_remove_all_desc)
    MessageAction.IgnoreSameSource -> stringResource(R.string.message_action_ignore_same_source_desc)
    MessageAction.IgnoreSameSourceAndRemove -> stringResource(R.string.message_action_ignore_same_source_remove_desc)
    MessageAction.Dnd5Min -> stringResource(R.string.message_action_dnd_5min_desc)
    MessageAction.QuickReply -> stringResource(R.string.message_action_quick_reply_desc)
    MessageAction.QuickReplyAndIgnore -> stringResource(R.string.message_action_quick_reply_ignore_desc)
    MessageAction.QuickReplyAndRemove -> stringResource(R.string.message_action_quick_reply_remove_desc)
    else -> null
}
