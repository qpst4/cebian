package com.slideindex.app.clipboardfloat

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.displayTypeLabelKey
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.overlay.history.HistoryPanelColors
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Date
import java.util.Locale

enum class ClipboardFloatDisplayMode {
    Chip,
    Expanded,
}

@Composable
fun ClipboardFloatRoot(
    mode: ClipboardFloatDisplayMode,
    pinned: Boolean,
    listController: ClipboardFloatListController,
    windowWidthDp: Int,
    onOpenExpanded: () -> Unit,
    onTogglePin: () -> Unit,
    onResetLayout: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onDragWindowStart: () -> Unit,
    onDragWindowEnd: () -> Unit,
    onResizeWindow: (Float, Float) -> Unit,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
) {
    OverlayAwareModuleTheme {
        when (mode) {
            ClipboardFloatDisplayMode.Chip -> ClipboardFloatChip(
                onClick = onOpenExpanded,
                onDragWindow = onDragWindow,
                onDragWindowStart = onDragWindowStart,
                onDragWindowEnd = onDragWindowEnd,
            )
            ClipboardFloatDisplayMode.Expanded -> ClipboardFloatExpandedChrome(
                pinned = pinned,
                onTogglePin = onTogglePin,
                onResetLayout = onResetLayout,
                onCollapse = onCollapse,
                onClose = onClose,
                onDragWindow = onDragWindow,
                onDragWindowStart = onDragWindowStart,
                onDragWindowEnd = onDragWindowEnd,
                onResizeWindow = onResizeWindow,
            ) {
                ClipboardFloatGridSection(
                    listController = listController,
                    windowWidthDp = windowWidthDp,
                    onEntryClick = onEntryClick,
                    onEntryLongClick = onEntryLongClick,
                )
            }
        }
    }
}

@Composable
private fun ClipboardFloatGridSection(
    listController: ClipboardFloatListController,
    windowWidthDp: Int,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
) {
    val entries by listController.entries.collectAsState()
    ClipboardFloatGrid(
        entries = entries,
        windowWidthDp = windowWidthDp,
        onEntryClick = onEntryClick,
        onEntryLongClick = onEntryLongClick,
    )
}

@Composable
private fun Modifier.clipboardFloatDragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier {
    val onDragStartState by rememberUpdatedState(onDragStart)
    val onDragState by rememberUpdatedState(onDrag)
    val onDragEndState by rememberUpdatedState(onDragEnd)
    return pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onDragStartState() },
            onDragEnd = { onDragEndState() },
            onDragCancel = { onDragEndState() },
        ) { change, dragAmount ->
            change.consume()
            onDragState(dragAmount.x, dragAmount.y)
        }
    }
}

@Composable
private fun ClipboardFloatChip(
    onClick: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onDragWindowStart: () -> Unit,
    onDragWindowEnd: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .clipboardFloatDragHandle(onDragWindowStart, onDragWindow, onDragWindowEnd)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainer.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.12f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = stringResource(R.string.clipboard_float_open),
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ClipboardFloatExpandedChrome(
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onResetLayout: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onDragWindowStart: () -> Unit,
    onDragWindowEnd: () -> Unit,
    onResizeWindow: (Float, Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = HistoryPanelColors.panelChrome(),
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(scheme.onSurface.copy(alpha = 0.08f))
                    .clipboardFloatDragHandle(onDragWindowStart, onDragWindow, onDragWindowEnd),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.clipboard_float_pin),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onResetLayout, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = stringResource(R.string.clipboard_float_reset_layout),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.clipboard_float_title),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipboardFloatDragHandle(onDragWindowStart, onDragWindow, onDragWindowEnd),
                    style = MiuixTheme.textStyles.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.clipboard_float_collapse),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clipboard_float_close),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(HistoryPanelColors.listBackground()),
            ) {
                content()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResizeWindow(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp, bottom = 4.dp)
                            .size(width = 14.dp, height = 2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(scheme.primary.copy(alpha = 0.55f)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp, bottom = 4.dp)
                            .size(width = 2.dp, height = 14.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(scheme.primary.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardFloatGrid(
    entries: List<ClipboardEntry>,
    windowWidthDp: Int,
    onEntryClick: (ClipboardEntry) -> Unit,
    onEntryLongClick: (ClipboardEntry) -> Unit,
) {
    val columnCount = ClipboardFloatWindowMetrics.columnCount(windowWidthDp)
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.clipboard_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            ClipboardFloatEntryCard(
                entry = entry,
                columnCount = columnCount,
                onClick = { onEntryClick(entry) },
                onLongClick = { onEntryLongClick(entry) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardFloatEntryCard(
    entry: ClipboardEntry,
    columnCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val bodyText = entry.text.trim().ifBlank { entry.uri.orEmpty() }
    val previewLines = if (columnCount <= 1) 3 else 2
    val metaText = buildClipboardFloatMeta(entry, bodyText)
    val typeLabel = clipboardFloatTypeLabel(entry.displayTypeLabelKey())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = HistoryPanelColors.cardBackground(starred = false),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = typeLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = formatClipboardFloatRelativeTime(entry.createdAtEpochMs),
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            if (bodyText.isNotEmpty()) {
                Text(
                    text = bodyText,
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurface,
                    maxLines = previewLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metaText.isNotEmpty()) {
                Text(
                    text = metaText,
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun clipboardFloatTypeLabel(type: ClipboardEntryType): String = when (type) {
    ClipboardEntryType.TEXT -> stringResource(R.string.clipboard_entry_type_text)
    ClipboardEntryType.URI -> stringResource(R.string.clipboard_entry_type_uri)
    ClipboardEntryType.INTENT -> stringResource(R.string.clipboard_entry_type_intent)
    ClipboardEntryType.HTML -> stringResource(R.string.clipboard_entry_type_html)
}

@Composable
private fun buildClipboardFloatMeta(entry: ClipboardEntry, bodyText: String): String {
    if (entry.hasImageContent()) {
        val mime = entry.mimeType?.substringAfter('/')?.uppercase().orEmpty()
        return listOfNotNull(
            stringResource(R.string.clipboard_float_meta_image),
            mime.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
    }
    if (bodyText.isBlank()) return ""
    val chars = bodyText.length
    val lines = bodyText.lineSequence().count().coerceAtLeast(1)
    return stringResource(R.string.clipboard_float_meta_text, chars, lines)
}

@Composable
private fun formatClipboardFloatRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L -> stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L -> stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMs }
            val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                if (locale.language == "zh") "M月d日" else "MMM d"
            } else {
                "yyyy/M/d"
            }
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
}
