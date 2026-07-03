package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress

@Composable
fun ShortcutScanProgressContent(
    progress: ShortcutScanProgress?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fraction = progress?.fraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = shortcutScanProgressLabel(progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun shortcutScanProgressLabel(progress: ShortcutScanProgress?): String {
    if (progress == null) {
        return stringResource(R.string.quick_launcher_loading_shortcuts)
    }
    return when (progress.phase) {
        ShortcutScanPhase.SYSTEM_XML ->
            stringResource(R.string.quick_launcher_scan_system_xml)
        ShortcutScanPhase.DUMPSYS ->
            stringResource(R.string.quick_launcher_scan_dumpsys)
        ShortcutScanPhase.PACKAGES ->
            stringResource(
                R.string.quick_launcher_scan_packages,
                progress.current,
                progress.total,
            )
        ShortcutScanPhase.APPS ->
            stringResource(
                R.string.quick_launcher_scan_apps,
                progress.current,
                progress.total,
            )
        ShortcutScanPhase.FINALIZING ->
            stringResource(R.string.quick_launcher_scan_finalizing)
    }
}
