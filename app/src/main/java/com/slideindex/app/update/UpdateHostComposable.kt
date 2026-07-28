package com.slideindex.app.update

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R

@Composable
fun UpdateHost(viewModel: UpdateViewModel, entryIntentAction: String? = null) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            val activity = context as? android.app.Activity
            if (activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkOnLaunch()
        viewModel.onEntry()
        viewModel.evaluateNotificationPrompt()
    }

    LaunchedEffect(entryIntentAction) {
        viewModel.onEntry()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rationaleShowing = uiState.showNotificationRationale
    if (uiState.showDialog && !rationaleShowing) {
        UpdateDialog(
            state = uiState,
            onDismissRequest = viewModel::dismiss,
            onIgnore = viewModel::onIgnoreVersion,
            onConfirm = viewModel::onConfirmUpdate,
            onInstall = viewModel::onInstall,
            onMoveToBackground = viewModel::onMoveToBackground,
            onOpenRelease = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, UpdateChecker.RELEASES_PAGE_URL.toUri()),
                )
            },
        )
    }

    if (uiState.showCheckFailedDialog && !rationaleShowing) {
        val titleRes = when (uiState.checkFailedReason) {
            UpdateViewModel.CheckFailedReason.RateLimited -> R.string.update_check_rate_limited_title
            UpdateViewModel.CheckFailedReason.NoApk -> R.string.update_check_no_apk_title
            UpdateViewModel.CheckFailedReason.Generic -> R.string.update_check_failed_title
        }
        val messageRes = when (uiState.checkFailedReason) {
            UpdateViewModel.CheckFailedReason.RateLimited -> R.string.update_check_rate_limited_message
            UpdateViewModel.CheckFailedReason.NoApk -> R.string.update_check_no_apk_message
            UpdateViewModel.CheckFailedReason.Generic -> R.string.update_check_failed_message
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissCheckFailedDialog,
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissCheckFailedDialog()
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, UpdateChecker.RELEASES_PAGE_URL.toUri()),
                        )
                    },
                ) {
                    Text(stringResource(R.string.update_view_on_github))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCheckFailedDialog) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (rationaleShowing) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNotificationRationale,
            title = { Text(stringResource(R.string.update_notification_permission_title)) },
            text = { Text(stringResource(R.string.update_notification_permission_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissNotificationRationale()
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNotificationRationale) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
