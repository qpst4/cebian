package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherLabels
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.util.QuickLauncherIconResolver

@Composable
internal fun QuickLauncherPageGrid(
    modifier: Modifier = Modifier,
    pageStart: Int,
    columns: Int,
    rows: Int,
    pageSize: Int,
    items: List<QuickLauncherItem>,
    appsByPackage: Map<String, AppInfo>,
    iconBitmapCache: Map<Int, android.graphics.Bitmap?>,
    editMode: Boolean,
    dragFromGlobal: Int,
    dragSlotGlobal: Int,
) {
    val displayMapping = remember(items.size, dragFromGlobal, dragSlotGlobal, pageStart, pageSize) {
        QuickLauncherGridLogic.displayMappingForPage(
            itemCount = items.size,
            dragFrom = dragFromGlobal,
            dragSlotGlobal = dragSlotGlobal,
            pageStart = pageStart,
            pageSize = pageSize,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = (rows * 88).dp, max = (rows * 96).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0 until columns) {
                    val cellIndex = row * columns + col
                    if (cellIndex >= pageSize) continue
                    Box(modifier = Modifier.weight(1f)) {
                        val originalIndex = displayMapping.getOrNull(cellIndex)
                        val item = originalIndex?.let { items.getOrNull(it) }
                        if (item == null) {
                            QuickLauncherEmptyGridCell()
                        } else {
                            QuickLauncherGridCell(
                                item = item,
                                appsByPackage = appsByPackage,
                                iconBitmap = iconBitmapCache[originalIndex],
                                showEditBadge = editMode && dragFromGlobal != originalIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickLauncherEmptyGridCell() {
    Box(
        modifier = Modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    )
}

@Composable
internal fun QuickLauncherGridCell(
    modifier: Modifier = Modifier,
    item: QuickLauncherItem,
    appsByPackage: Map<String, AppInfo>,
    iconBitmap: android.graphics.Bitmap? = null,
    showEditBadge: Boolean = false,
) {
    val context = LocalContext.current
    val label = quickLauncherGridLabel(context, item, appsByPackage)
    val action = remember(item.payload, item.type) {
        if (item.type == QuickLauncherItemType.ACTION) {
            QuickLauncherItemCodec.parseActionPayload(item.payload)
        } else {
            null
        }
    }
    val resolvedIconBitmap = iconBitmap ?: remember(item.type, item.payload) {
        QuickLauncherIconResolver.iconBitmap(item, appsByPackage, context = context)
    }
    Box(
        modifier = modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            if (resolvedIconBitmap != null) {
                Image(
                    bitmap = resolvedIconBitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp),
                )
            } else if (action != null) {
                Icon(
                    imageVector = gestureActionIcon(action),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showEditBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)),
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

internal fun quickLauncherGridLabel(
    context: android.content.Context,
    item: QuickLauncherItem,
    appsByPackage: Map<String, AppInfo>,
): String = QuickLauncherLabels.resolveLabel(context, item, appsByPackage)
