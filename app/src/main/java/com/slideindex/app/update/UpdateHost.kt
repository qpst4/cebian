@file:OptIn(ExperimentalMaterial3Api::class)

package com.slideindex.app.update

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton

@Composable
fun UpdateDialog(
    state: UpdateViewModel.UiState,
    onDismissRequest: () -> Unit,
    onIgnore: () -> Unit,
    onConfirm: () -> Unit,
    onInstall: () -> Unit,
    onMoveToBackground: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val isUpToDate = state.phase == UpdateViewModel.UpdatePhase.UpToDate
    val titleRes = when (state.phase) {
        UpdateViewModel.UpdatePhase.UpToDate -> R.string.update_already_latest_title
        UpdateViewModel.UpdatePhase.Failed -> R.string.update_download_failed_title
        else -> R.string.update_available_title
    }

    MiuixBottomSheet(
        show = true,
        title = stringResource(titleRes),
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = onOpenRelease) {
                MiuixIcon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.update_view_on_github),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                modifier = Modifier.padding(vertical = 4.dp),
                text = if (isUpToDate) {
                    UpdateChecker.displayVersion(BuildConfig.VERSION_NAME)
                } else {
                    stringResource(
                        R.string.update_version_compare,
                        UpdateChecker.displayVersion(BuildConfig.VERSION_NAME),
                        UpdateChecker.displayVersion(state.version),
                    )
                },
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall,
            )
            val density = LocalDensity.current
            val maxNotesHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp() * 0.35f
            }
            Text(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .heightIn(max = maxNotesHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                text = UpdateChecker.formatNotesForDisplay(state.notes).ifBlank { "—" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            when (state.phase) {
                UpdateViewModel.UpdatePhase.Downloading -> {
                    DownloadingContent(progress = state.progress)
                    PrimaryButtonRow(
                        text = stringResource(R.string.update_move_to_background),
                        onClick = onMoveToBackground,
                    )
                }
                UpdateViewModel.UpdatePhase.Downloaded -> {
                    PrimaryButtonRow(
                        text = stringResource(R.string.update_install_now),
                        onClick = onInstall,
                    )
                }
                UpdateViewModel.UpdatePhase.Failed -> {
                    PrimaryButtonRow(
                        text = stringResource(R.string.update_retry),
                        onClick = onConfirm,
                    )
                }
                UpdateViewModel.UpdatePhase.NewVersion -> {
                    ActionRow(onIgnore = onIgnore, onConfirm = onConfirm)
                }
                UpdateViewModel.UpdatePhase.UpToDate -> {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onIgnore: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(modifier = Modifier.weight(1f), onClick = onIgnore) {
            Text(stringResource(R.string.update_ignore_version))
        }
        Button(modifier = Modifier.weight(1f), onClick = onConfirm) {
            Text(stringResource(R.string.update_now))
        }
    }
}

@Composable
private fun PrimaryButtonRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
            Text(text)
        }
    }
}

@Composable
private fun DownloadingContent(progress: Int) {
    val animatedFraction by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        label = "downloadProgress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.update_downloading_label),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$progress%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
