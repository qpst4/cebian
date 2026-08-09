package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import kotlin.math.roundToInt

@Composable
internal fun QuickLauncherPageSwitcher(
    currentPage: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = currentPage > 0,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.quick_launcher_page_previous),
            )
        }
        Text(
            text = stringResource(
                R.string.quick_launcher_page_indicator,
                currentPage + 1,
                pageCount,
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(
            onClick = onNext,
            enabled = currentPage < pageCount - 1,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.quick_launcher_page_next),
            )
        }
    }
}

@Composable
internal fun QuickLauncherEditorToolbar(
    editMode: Boolean,
    onAdd: () -> Unit,
    onToggleEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QuickLauncherToolbarButton(onClick = onAdd) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.quick_launcher_add),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        QuickLauncherToolbarButton(onClick = onToggleEdit, selected = editMode) {
            Text(
                text = if (editMode) "✓" else "−",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QuickLauncherToolbarButton(
    onClick: () -> Unit,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun QuickLauncherDeleteButtonLayer(
    columns: Int,
    pageSize: Int,
    pageStartIndex: Int,
    items: List<QuickLauncherItem>,
    dragFromGlobal: Int,
    dragSlotGlobal: Int,
    stepX: Float,
    stepY: Float,
    cellWidthPx: Float,
    density: Density,
    zIndex: Float = 3f,
    onRemoveAt: (Int) -> Unit,
) {
    val displayMapping = remember(items.size, dragFromGlobal, dragSlotGlobal, pageStartIndex, pageSize) {
        QuickLauncherGridLogic.displayMappingForPage(
            itemCount = items.size,
            dragFrom = dragFromGlobal,
            dragSlotGlobal = dragSlotGlobal,
            pageStart = pageStartIndex,
            pageSize = pageSize,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex),
    ) {
        for (localIndex in 0 until pageSize) {
            val originalIndex = displayMapping.getOrNull(localIndex) ?: continue
            val col = localIndex % columns
            val row = localIndex / columns
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (col * stepX).roundToInt(),
                            (row * stepY).roundToInt(),
                        )
                    }
                    .width(with(density) { cellWidthPx.toDp() })
                    .aspectRatio(0.82f),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable { onRemoveAt(originalIndex) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "−",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
