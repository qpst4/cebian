package com.slideindex.app.overlay.history

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.stash.StashAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class HistoryImageSource {
    Clipboard,
    Stash,
}

@Composable
internal fun HistoryContentBlockView(
    block: ClipboardContentBlock,
    imageSource: HistoryImageSource,
    entryId: String,
    context: Context,
    previewWidthPx: Int,
    previewHeightPx: Int,
    expanded: Boolean,
) {
    when (block.kind) {
        ClipboardBlockKind.TEXT -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
        ClipboardBlockKind.IMAGE -> {
            val decodeMaxSidePx = if (expanded) historyExpandedImageMaxSidePx() else previewWidthPx
            var bitmap by remember(entryId, block.fileName, decodeMaxSidePx, previewHeightPx, expanded, imageSource) {
                mutableStateOf<Bitmap?>(null)
            }
            LaunchedEffect(entryId, block.fileName, decodeMaxSidePx, previewHeightPx, expanded, imageSource) {
                if (block.fileName.isBlank()) return@LaunchedEffect
                bitmap = withContext(Dispatchers.IO) {
                    when (imageSource) {
                        HistoryImageSource.Clipboard -> {
                            if (expanded) {
                                ClipboardThumbnailCache.loadBlockThumbnail(context, block.fileName, decodeMaxSidePx)
                            } else {
                                ClipboardThumbnailCache.loadBlockThumbnailForCard(
                                    context,
                                    block.fileName,
                                    previewWidthPx,
                                    previewHeightPx,
                                )
                            }
                        }
                        HistoryImageSource.Stash -> {
                            StashAccess.repository?.loadThumbnailByFileName(
                                entryId,
                                block.fileName,
                                decodeMaxSidePx,
                            )
                        }
                    }
                }
            }
            val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
            if (imageBitmap != null) {
                if (expanded) {
                    HistoryScrollableExpandedImage(imageBitmap = imageBitmap)
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (imageSource == HistoryImageSource.Stash) 150.dp else 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.clipboard_image_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HistoryScrollableExpandedImage(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = HISTORY_EXPANDED_IMAGE_SCROLL_MAX)
            .verticalScroll(scrollState),
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
internal fun rememberHistoryImageBitmap(bitmap: Bitmap?): ImageBitmap? =
    remember(bitmap) { bitmap?.asImageBitmap() }
