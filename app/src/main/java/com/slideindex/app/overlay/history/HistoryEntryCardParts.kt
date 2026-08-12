@file:OptIn(ExperimentalFoundationApi::class)

package com.slideindex.app.overlay.history

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.ui.miuix.CardSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistoryImagePagerSection(
    thumbnails: List<Bitmap>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onLongPressDrag: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (thumbnails.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, thumbnails.lastIndex.coerceAtLeast(0)),
        pageCount = { thumbnails.size },
    )
    LaunchedEffect(pagerState.settledPage) {
        if (thumbnails.isNotEmpty()) {
            onSelectedIndexChange(pagerState.settledPage.coerceIn(0, thumbnails.lastIndex))
        }
    }
    LaunchedEffect(selectedIndex) {
        if (thumbnails.isNotEmpty() && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex.coerceIn(0, thumbnails.lastIndex))
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (onLongPressDrag != null) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onLongPressDrag,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                val bitmap = thumbnails.getOrNull(page) ?: return@HorizontalPager
                val imageBitmap = rememberHistoryImageBitmap(bitmap) ?: return@HorizontalPager
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (thumbnails.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${thumbnails.size}",
                    style = HistoryPanelTypography.meta(),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (thumbnails.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(thumbnails.size, key = { it }) { index ->
                    val selected = index == selectedIndex
                    val thumbBitmap = thumbnails[index]
                    val thumbImage = rememberHistoryImageBitmap(thumbBitmap)
                    val scheme = MiuixTheme.colorScheme
                    if (thumbImage != null) {
                        Image(
                            bitmap = thumbImage,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        scheme.primary
                                    } else {
                                        scheme.dividerLine.copy(alpha = 0.6f)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable { onSelectedIndexChange(index) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryExpandableContentSection(
    entryId: String,
    canExpand: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    contentBlocks: List<ClipboardContentBlock>,
    imageSource: HistoryImageSource,
    previewWidthPx: Int,
    previewHeightPx: Int,
    collapsedContent: @Composable () -> Unit,
    onLongPressDrag: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val gestureModifier = when {
        canExpand && onLongPressDrag != null -> {
            Modifier.combinedClickable(
                onClick = onExpandedChange,
                onLongClick = onLongPressDrag,
            )
        }
        canExpand -> {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onExpandedChange() }
        }
        onLongPressDrag != null -> {
            Modifier.combinedClickable(
                onClick = {},
                onLongClick = onLongPressDrag,
            )
        }
        else -> Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(gestureModifier)
            .then(
                if (canExpand) {
                    Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                contentBlocks.forEach { block ->
                    HistoryContentBlockView(
                        block = block,
                        imageSource = imageSource,
                        entryId = entryId,
                        context = context,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        expanded = true,
                    )
                }
            }
        } else {
            collapsedContent()
        }
    }
}

@Composable
internal fun HistoryEntryCardShell(
    entryId: String,
    createdAtEpochMs: Long,
    starred: Boolean = false,
    headerTrailing: @Composable () -> Unit,
    content: @Composable () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val cardShape = RoundedCornerShape(16.dp)
    val containerColor = HistoryPanelColors.cardBackground(starred)
    CardSegment(
        isFirst = true,
        isLast = true,
        color = containerColor,
        contentColor = scheme.onSurfaceContainer,
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (starred) {
                    Modifier.border(
                        width = 1.dp,
                        color = scheme.primary.copy(alpha = 0.45f),
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            ),
        insidePadding = PaddingValues(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatHistoryRelativeTime(createdAtEpochMs),
                    style = HistoryPanelTypography.meta(),
                    color = scheme.onSurfaceVariantSummary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    headerTrailing()
                }
            }
            content()
            HorizontalDivider(color = scheme.dividerLine.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@Composable
internal fun HistoryCardActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val iconTint = scheme.onBackground
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        top.yukonga.miuix.kmp.basic.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = iconTint,
        )
    }
}

internal data class HistoryCardMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val iconTint: Color? = null,
)

@Composable
internal fun HistoryCardOverflowMenu(
    contentDescription: String,
    actions: List<HistoryCardMenuAction>,
) {
    if (actions.isEmpty()) return
    val scheme = MiuixTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp),
        ) {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = scheme.onBackground,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, style = HistoryPanelTypography.content()) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = action.iconTint ?: scheme.onBackground,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun HistoryCollapsedSummaryText(
    text: String,
    maxLines: Int = 3,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = HistoryPanelTypography.content(),
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun HistorySingleImageThumb(
    bitmap: Bitmap?,
    maxHeightDp: androidx.compose.ui.unit.Dp = 150.dp,
) {
    val imageBitmap = rememberHistoryImageBitmap(bitmap)
    if (imageBitmap == null) return
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeightDp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
internal fun rememberLoadedThumbnails(
    entryId: String,
    loadKey: Any,
    enabled: Boolean,
    loader: suspend () -> List<Bitmap>,
): Pair<List<Bitmap>, Boolean> {
    var thumbnails by remember(entryId) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var failed by remember(entryId) { mutableStateOf(false) }
    LaunchedEffect(entryId, loadKey, enabled) {
        if (!enabled) {
            thumbnails = emptyList()
            failed = false
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { loader() }
        thumbnails = loaded
        failed = loaded.isEmpty()
    }
    return thumbnails to failed
}

@Composable
internal fun rememberLoadedSingleThumb(
    entryId: String,
    loadKey: Any,
    enabled: Boolean,
    loader: suspend () -> Bitmap?,
): Bitmap? {
    var thumb by remember(entryId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entryId, loadKey, enabled) {
        if (!enabled) {
            thumb = null
            return@LaunchedEffect
        }
        thumb = withContext(Dispatchers.IO) { loader() }
    }
    return thumb
}
