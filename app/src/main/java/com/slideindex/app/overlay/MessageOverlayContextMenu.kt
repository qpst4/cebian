package com.slideindex.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.NotificationData

@Composable
fun MessageOverlayContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    onClearAll: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.message_overlay_menu_copy)) },
            onClick = onCopy,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.message_overlay_menu_close)) },
            onClick = onClose,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.message_overlay_menu_clear_all)) },
            onClick = onClearAll,
        )
    }
}

internal fun copyNotificationText(context: Context, data: NotificationData) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val text = buildString {
        val title = data.title.ifBlank { data.packageName }
        append(title)
        if (data.content.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(data.content)
        }
    }.trim()
    if (text.isEmpty()) return
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
}
