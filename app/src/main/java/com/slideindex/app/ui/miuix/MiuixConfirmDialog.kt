package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    text: @Composable (() -> Unit)? = null,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    dismissText: String = stringResource(R.string.cancel),
    dismissOnConfirm: Boolean = true,
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
    secondaryDismissOnConfirm: Boolean = true,
) {
    if (!show) return

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            when {
                text != null -> text()
                message != null -> {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            DialogActionsRow(
                dismissText = dismissText,
                onDismissRequest = onDismissRequest,
                confirmText = confirmText,
                onConfirm = {
                    onConfirm()
                    if (dismissOnConfirm) {
                        onDismissRequest()
                    }
                },
                secondaryConfirmText = secondaryConfirmText,
                onSecondaryConfirm = onSecondaryConfirm?.let { action ->
                    {
                        action()
                        if (secondaryDismissOnConfirm) {
                            onDismissRequest()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun MiuixFormDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String? = stringResource(R.string.cancel),
    dismissOnConfirm: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!show) return

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            content()
            Spacer(Modifier.height(20.dp))
            if (dismissText != null) {
                DialogActionsRow(
                    dismissText = dismissText,
                    onDismissRequest = onDismissRequest,
                    confirmText = confirmText,
                    confirmEnabled = confirmEnabled,
                    onConfirm = {
                        onConfirm()
                        if (dismissOnConfirm) {
                            onDismissRequest()
                        }
                    },
                )
            } else {
                TextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        if (dismissOnConfirm) {
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
fun MiuixScrollableConfirmDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = stringResource(R.string.confirm),
    onConfirm: () -> Unit,
    dismissText: String = stringResource(R.string.cancel),
    content: @Composable () -> Unit,
) {
    MiuixConfirmDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        },
    )
}

@Composable
private fun DialogActionsRow(
    dismissText: String,
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    secondaryConfirmText: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (secondaryConfirmText != null && onSecondaryConfirm != null) {
            TextButton(
                text = secondaryConfirmText,
                onClick = onSecondaryConfirm,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
        }
        TextButton(
            text = dismissText,
            onClick = onDismissRequest,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
