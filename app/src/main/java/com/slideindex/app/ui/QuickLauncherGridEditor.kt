package com.slideindex.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

private fun <T> List<T>.moveIndex(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

@Composable
fun QuickLauncherGridEditor(
    settings: AppSettings,
    items: List<QuickLauncherItem>,
    appsByPackage: Map<String, AppInfo>,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onAdd: () -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 88.dp.toPx() }

    SettingsCard {
        Row(modifier = Modifier.padding(12.dp)) {
            if (items.isEmpty()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_launcher_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.appsPerRow.coerceIn(2, 5)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 220.dp, max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items,
                        key = { index, item -> "${item.type.id}:${item.payload}:$index" },
                    ) { index, item ->
                        val dragModifier = if (editMode) {
                            Modifier
                                .zIndex(if (index == draggingIndex) 1f else 0f)
                                .offset {
                                    IntOffset(
                                        if (index == draggingIndex) dragOffsetX.roundToInt() else 0,
                                        if (index == draggingIndex) dragOffsetY.roundToInt() else 0,
                                    )
                                }
                                .pointerInput(items.size, index) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetX += dragAmount.x
                                            dragOffsetY += dragAmount.y
                                            val target = (draggingIndex + (dragOffsetY / rowHeightPx).roundToInt())
                                                .coerceIn(0, items.lastIndex)
                                            if (target != draggingIndex) {
                                                onItemsChange(items.moveIndex(draggingIndex, target))
                                                draggingIndex = target
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = -1
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                        },
                                    )
                                }
                        } else {
                            Modifier
                        }
                        QuickLauncherGridCell(
                            modifier = dragModifier,
                            item = item,
                            appsByPackage = appsByPackage,
                            editMode = editMode,
                            onRemove = {
                                onItemsChange(items.filterIndexed { i, _ -> i != index })
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            QuickLauncherEditorToolbar(
                editMode = editMode,
                onAdd = onAdd,
                onToggleEdit = { editMode = !editMode },
            )
        }
    }
}

@Composable
private fun QuickLauncherEditorToolbar(
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
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_launcher_add))
        }
        QuickLauncherToolbarButton(onClick = onToggleEdit, selected = editMode) {
            Text(
                text = if (editMode) "✓" else "−",
                style = MaterialTheme.typography.titleMedium,
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
private fun QuickLauncherGridCell(
    modifier: Modifier = Modifier,
    item: QuickLauncherItem,
    appsByPackage: Map<String, AppInfo>,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    val label = quickLauncherGridLabel(item, appsByPackage)
    val iconBitmap = remember(item, appsByPackage) { quickLauncherGridIcon(item, appsByPackage) }
    Box(
        modifier = modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(40.dp),
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
            )
        }
        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable(onClick = onRemove),
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

private fun quickLauncherGridLabel(item: QuickLauncherItem, appsByPackage: Map<String, AppInfo>): String =
    when (item.type) {
        QuickLauncherItemType.APP ->
            appsByPackage[item.payload]?.label ?: item.label.ifBlank { item.payload }
        QuickLauncherItemType.SHORTCUT ->
            item.label.ifBlank { "快捷方式" }
        QuickLauncherItemType.ACTION ->
            item.label.ifBlank { "动作" }
        QuickLauncherItemType.WIDGET ->
            item.label.ifBlank { "小组件" }
    }

private fun quickLauncherGridIcon(item: QuickLauncherItem, appsByPackage: Map<String, AppInfo>): Bitmap? {
    return when (item.type) {
        QuickLauncherItemType.APP -> {
            val drawable = appsByPackage[item.payload]?.icon ?: return null
            iconBitmapFromDrawable(drawable, 128)
        }
        QuickLauncherItemType.SHORTCUT -> {
            val packageName = QuickLauncherItemCodec.parseShortcutPayload(item.payload)?.first
                ?: item.payload.substringBefore('/').takeIf { it.isNotBlank() }
            val drawable = packageName?.let { appsByPackage[it]?.icon } ?: return null
            iconBitmapFromDrawable(drawable, 128)
        }
        QuickLauncherItemType.ACTION -> null
        QuickLauncherItemType.WIDGET -> null
    }
}

private fun iconBitmapFromDrawable(drawable: android.graphics.drawable.Drawable, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val mutate = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
    mutate.setBounds(0, 0, size, size)
    mutate.draw(canvas)
    return bitmap
}
