package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NativeEnginePackStatusBanner(
    title: String,
    installed: Boolean,
    sizeBytes: Long,
    installedRevision: Int? = null,
    installedDisplayVersion: String? = null,
    latestRevision: Int = 1,
    latestDisplayVersion: String? = null,
    updateAvailable: Boolean = false,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(
                        R.string.native_engine_pack_size,
                        formatMegabytes(sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = nativeEnginePackVersionStatusText(
                        installed = installed,
                        installedRevision = installedRevision,
                        installedDisplayVersion = installedDisplayVersion,
                        latestRevision = latestRevision,
                        latestDisplayVersion = latestDisplayVersion,
                        updateAvailable = updateAvailable,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        updateAvailable -> MaterialTheme.colorScheme.tertiary
                        installed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (installed && onDelete != null) {
                    OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.native_engine_pack_delete))
                    }
                }
                if (onManage != null) {
                    TextButton(onClick = onManage) {
                        Text(stringResource(R.string.native_engine_status_manage))
                    }
                }
            }
        }
    }
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${mb.roundToInt()} MB"
    }
}
