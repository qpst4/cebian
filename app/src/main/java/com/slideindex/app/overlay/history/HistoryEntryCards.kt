package com.slideindex.app.overlay.history

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.displayTypeLabelKey
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.hasRichPinContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.clipboard.shouldOfferExpand
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.PickResultFromHistoryCoordinator
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.combinedText
import com.slideindex.app.stash.resolvedContentBlocks
import com.slideindex.app.stash.shouldOfferExpand
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HistoryClipboardEntryCard(
    entry: ClipboardEntry,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    selectedImageIndex: Int,
    onSelectedImageIndexChange: (Int) -> Unit,
    previewWidthPx: Int,
    previewHeightPx: Int,
    onShowMessage: (Int) -> Unit,
    onCopy: () -> Unit,
    onStash: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hasImageContent = entry.hasImageContent()
    val contentBlocks = remember(entry.id, entry.contentBlocks, entry.text, entry.htmlText, entry.imageFileNames) {
        entry.resolvedContentBlocks()
    }
    val canExpand = remember(entry.id, contentBlocks) { entry.shouldOfferExpand() }
    val (thumbnails, imageLoadFailed) = rememberLoadedThumbnails(
        entryId = entry.id,
        loadKey = listOf(
            entry.imageFileName,
            entry.imageFileNames,
            entry.uri,
            entry.mimeType,
            entry.htmlText,
            previewWidthPx,
            previewHeightPx,
            hasImageContent,
        ),
        enabled = hasImageContent,
        loader = {
            ClipboardThumbnailCache.loadEntryThumbnailsForCard(
                context,
                entry,
                previewWidthPx,
                previewHeightPx,
            )
        },
    )
    val hasImages = thumbnails.isNotEmpty()
    val selectedBitmap = thumbnails.getOrNull(selectedImageIndex)
    val bodyText = entry.text.trim()
    val showBodyText = bodyText.isNotEmpty() && bodyText != entry.uri
    val summaryText = when {
        showBodyText -> bodyText
        !hasImages && !imageLoadFailed -> entry.uri ?: entry.intentUri.orEmpty()
        else -> ""
    }
    val pinLabel = stringResource(R.string.stash_action_pin)
    val shareLabel = stringResource(R.string.float_ball_action_share)
    val saveImageLabel = stringResource(R.string.clipboard_action_save_image)
    val deleteLabel = stringResource(R.string.stash_action_delete)
    val moreLabel = stringResource(R.string.notification_filter_more_menu)
    val onLongPressDrag: () -> Unit = {
        val clipData = ClipboardWriter.buildClipForEntry(context, entry)
        if (clipData == null) {
            onShowMessage(R.string.history_drag_unsupported)
        } else {
            HistoryEntryDragHelper.startDrag(
                view = view,
                clipData = clipData,
                preview = HistoryEntryDragHelper.previewForClipboardEntry(entry, thumbnails),
                onDragStart = { FloatBallStashPanel.setDragHidden(true) },
                onDragEnd = { FloatBallStashPanel.setDragHidden(false) },
            )
        }
    }

    HistoryEntryCardShell(
        entryId = entry.id,
        createdAtEpochMs = entry.createdAtEpochMs,
        starred = false,
        headerTrailing = {
            IconButton(
                onClick = {
                    PickResultFromHistoryCoordinator.openFromClipboard(
                        context,
                        entry,
                        selectedImageIndex,
                    )
                },
                modifier = Modifier.size(32.dp),
            ) {
                MiuixIcon(
                    imageVector = Icons.Outlined.TextFields,
                    contentDescription = stringResource(R.string.stash_action_open_pick),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = clipboardEntryTypeLabel(entry.displayTypeLabelKey()),
                style = HistoryPanelTypography.meta(),
                color = MiuixTheme.colorScheme.primary,
            )
        },
        content = {
            HistoryExpandableContentSection(
                entryId = entry.id,
                canExpand = canExpand,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                contentBlocks = contentBlocks,
                imageSource = HistoryImageSource.Clipboard,
                previewWidthPx = previewWidthPx,
                previewHeightPx = previewHeightPx,
                onLongPressDrag = onLongPressDrag,
                collapsedContent = {
                    if (hasImages) {
                        HistoryImagePagerSection(
                            thumbnails = thumbnails,
                            selectedIndex = selectedImageIndex,
                            onSelectedIndexChange = onSelectedImageIndexChange,
                            onLongPressDrag = onLongPressDrag,
                        )
                    } else if (imageLoadFailed) {
                        Text(
                            text = stringResource(R.string.clipboard_image_unavailable),
                            style = HistoryPanelTypography.hint(),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    HistoryCollapsedSummaryText(text = summaryText)
                },
            )
        },
        actions = {
            HistoryCardActionIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.clipboard_history_float_copy),
                onClick = onCopy,
            )
            HistoryCardActionIcon(
                icon = Icons.Outlined.Archive,
                contentDescription = stringResource(R.string.float_ball_action_stash),
                onClick = onStash,
            )
            Spacer(modifier = Modifier.weight(1f))
            HistoryCardOverflowMenu(
                contentDescription = moreLabel,
                actions = buildList {
                    if (entry.hasRichPinContent() || hasImages || showBodyText) {
                        add(
                            HistoryCardMenuAction(
                                label = pinLabel,
                                icon = Icons.Default.PushPin,
                                onClick = {
                                    when {
                                        entry.hasRichPinContent() -> StashCoordinator.pinRichFromClipboard(context, entry)
                                        hasImages && selectedBitmap != null -> {
                                            StashCoordinator.pinImageToScreen(context, selectedBitmap)
                                        }
                                        showBodyText -> StashCoordinator.pinTextToScreen(context, bodyText)
                                    }
                                },
                            ),
                        )
                    }
                    if (!expanded && hasImages && selectedBitmap != null) {
                        add(
                            HistoryCardMenuAction(
                                label = shareLabel,
                                icon = Icons.Default.Share,
                                onClick = { FloatBallTextPick.shareScreenshot(context, selectedBitmap) },
                            ),
                        )
                        add(
                            HistoryCardMenuAction(
                                label = saveImageLabel,
                                icon = Icons.Outlined.Save,
                                onClick = {
                                    val saved = FloatBallTextPick.saveScreenshot(context, selectedBitmap)
                                    onShowMessage(
                                        if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed,
                                    )
                                },
                            ),
                        )
                    } else if (!expanded && showBodyText) {
                        add(
                            HistoryCardMenuAction(
                                label = shareLabel,
                                icon = Icons.Default.Share,
                                onClick = { FloatBallTextPick.shareText(context, bodyText) },
                            ),
                        )
                    }
                    add(
                        HistoryCardMenuAction(
                            label = deleteLabel,
                            icon = Icons.Default.Delete,
                            onClick = onDelete,
                            iconTint = MiuixTheme.colorScheme.error,
                        ),
                    )
                },
            )
        },
    )
}

@Composable
internal fun HistoryStashEntryCard(
    entry: StashEntry,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    selectedImageIndex: Int,
    onSelectedImageIndexChange: (Int) -> Unit,
    onShowMessage: (Int) -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repo = StashAccess.repository
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyStashPreviewHeightPx()
    val richPreviewHeightPx = historyClipboardCardPreviewHeightPx()
    val richBlocks = remember(entry.id, entry.contentBlocks, entry.type, entry.text, entry.imageFileName) {
        entry.resolvedContentBlocks()
    }
    val canExpand = remember(entry.id, entry.type, richBlocks) { entry.shouldOfferExpand() }
    val summaryText = remember(entry.id, entry.type, entry.text, richBlocks) {
        when (entry.type) {
            StashEntryType.TEXT -> entry.text.orEmpty()
            StashEntryType.RICH -> entry.combinedText()
            else -> ""
        }
    }
    val richImageFileNames = remember(entry.id, entry.contentBlocks, entry.imageFileName) {
        entry.allImageFileNames()
    }
    val singleThumb = rememberLoadedSingleThumb(
        entryId = entry.id,
        loadKey = listOf(previewWidthPx, previewHeightPx, entry.type),
        enabled = entry.type == StashEntryType.IMAGE,
        loader = { repo?.loadImageThumbnailForCard(entry, previewWidthPx, previewHeightPx) },
    )
    val (richThumbnails, richImageLoadFailed) = rememberLoadedThumbnails(
        entryId = entry.id,
        loadKey = listOf(richImageFileNames, previewWidthPx, richPreviewHeightPx, entry.type),
        enabled = entry.type == StashEntryType.RICH && richImageFileNames.isNotEmpty(),
        loader = {
            repo?.loadEntryThumbnailsForCard(entry, previewWidthPx, richPreviewHeightPx).orEmpty()
        },
    )
    val richHasImages = richThumbnails.isNotEmpty()
    val richSelectedBitmap = richThumbnails.getOrNull(selectedImageIndex)
    val pinLabel = stringResource(R.string.stash_action_pin)
    val shareLabel = stringResource(R.string.float_ball_action_share)
    val saveImageLabel = stringResource(R.string.clipboard_action_save_image)
    val deleteLabel = stringResource(R.string.stash_action_delete)
    val moreLabel = stringResource(R.string.notification_filter_more_menu)
    val onLongPressDrag: () -> Unit = {
        val clipData = HistoryEntryDragHelper.buildClipForStashEntry(context, entry, repo)
        if (clipData == null) {
            onShowMessage(R.string.history_drag_unsupported)
        } else {
            HistoryEntryDragHelper.startDrag(
                view = view,
                clipData = clipData,
                preview = HistoryEntryDragHelper.previewForStashEntry(entry, singleThumb, richThumbnails),
                onDragStart = { FloatBallStashPanel.setDragHidden(true) },
                onDragEnd = { FloatBallStashPanel.setDragHidden(false) },
            )
        }
    }

    HistoryEntryCardShell(
        entryId = entry.id,
        createdAtEpochMs = entry.createdAtEpochMs,
        starred = entry.starred,
        headerTrailing = {
            IconButton(
                onClick = {
                    val imageIndex = when (entry.type) {
                        StashEntryType.RICH -> selectedImageIndex
                        else -> 0
                    }
                    PickResultFromHistoryCoordinator.openFromStash(context, entry, imageIndex)
                },
                modifier = Modifier.size(32.dp),
            ) {
                MiuixIcon(
                    imageVector = Icons.Outlined.TextFields,
                    contentDescription = stringResource(R.string.stash_action_open_pick),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onToggleStar, modifier = Modifier.size(32.dp)) {
                MiuixIcon(
                    imageVector = if (entry.starred) Icons.Default.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (entry.starred) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                )
            }
        },
        content = {
            when (entry.type) {
                StashEntryType.TEXT -> {
                    HistoryExpandableContentSection(
                        entryId = entry.id,
                        canExpand = canExpand,
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        contentBlocks = richBlocks,
                        imageSource = HistoryImageSource.Stash,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        onLongPressDrag = onLongPressDrag,
                        collapsedContent = {
                            HistoryCollapsedSummaryText(
                                text = entry.text.orEmpty(),
                                maxLines = if (canExpand) 3 else Int.MAX_VALUE,
                            )
                        },
                    )
                }
                StashEntryType.IMAGE -> {
                    HistoryExpandableContentSection(
                        entryId = entry.id,
                        canExpand = canExpand,
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        contentBlocks = entry.resolvedContentBlocks(),
                        imageSource = HistoryImageSource.Stash,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        onLongPressDrag = onLongPressDrag,
                        collapsedContent = {
                            HistorySingleImageThumb(bitmap = singleThumb)
                        },
                    )
                }
                StashEntryType.RICH -> {
                    HistoryExpandableContentSection(
                        entryId = entry.id,
                        canExpand = canExpand,
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        contentBlocks = richBlocks,
                        imageSource = HistoryImageSource.Stash,
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = richPreviewHeightPx,
                        onLongPressDrag = onLongPressDrag,
                        collapsedContent = {
                            if (richHasImages) {
                                HistoryImagePagerSection(
                                    thumbnails = richThumbnails,
                                    selectedIndex = selectedImageIndex,
                                    onSelectedIndexChange = onSelectedImageIndexChange,
                                    onLongPressDrag = onLongPressDrag,
                                )
                            } else if (richImageLoadFailed) {
                                Text(
                                    text = stringResource(R.string.clipboard_image_unavailable),
                                    style = HistoryPanelTypography.hint(),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            HistoryCollapsedSummaryText(text = summaryText)
                        },
                    )
                }
            }
        },
        actions = {
            HistoryCardActionIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.clipboard_history_float_copy),
                onClick = onCopy,
            )
            HistoryCardActionIcon(
                icon = Icons.Default.Share,
                contentDescription = null,
                onClick = {
                    when {
                        entry.type == StashEntryType.RICH && !expanded && richHasImages && richSelectedBitmap != null -> {
                            FloatBallTextPick.shareScreenshot(context, richSelectedBitmap)
                        }
                        entry.type == StashEntryType.RICH && !expanded && summaryText.isNotBlank() -> {
                            FloatBallTextPick.shareText(context, summaryText)
                        }
                        else -> onShare()
                    }
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            HistoryCardOverflowMenu(
                contentDescription = moreLabel,
                actions = buildList {
                    add(
                        HistoryCardMenuAction(
                            label = pinLabel,
                            icon = Icons.Default.PushPin,
                            onClick = onPin,
                        ),
                    )
                    if (entry.type == StashEntryType.RICH && !expanded && richHasImages && richSelectedBitmap != null) {
                        add(
                            HistoryCardMenuAction(
                                label = saveImageLabel,
                                icon = Icons.Outlined.Save,
                                onClick = {
                                    val saved = FloatBallTextPick.saveScreenshot(context, richSelectedBitmap)
                                    onShowMessage(
                                        if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed,
                                    )
                                },
                            ),
                        )
                    }
                    add(
                        HistoryCardMenuAction(
                            label = deleteLabel,
                            icon = Icons.Default.Delete,
                            onClick = onDelete,
                            iconTint = MiuixTheme.colorScheme.error,
                        ),
                    )
                },
            )
        },
    )
}

@Composable
private fun clipboardEntryTypeLabel(type: ClipboardEntryType): String = when (type) {
    ClipboardEntryType.TEXT -> stringResource(R.string.clipboard_entry_type_text)
    ClipboardEntryType.URI -> stringResource(R.string.clipboard_entry_type_uri)
    ClipboardEntryType.INTENT -> stringResource(R.string.clipboard_entry_type_intent)
    ClipboardEntryType.HTML -> stringResource(R.string.clipboard_entry_type_html)
}
