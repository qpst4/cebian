package com.slideindex.app.overlay.history

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
                style = HistoryPanelTypography.content(),
                color = MiuixTheme.colorScheme.onSurface,
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
                    HistoryExpandedImage(imageBitmap = imageBitmap)
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
                    style = HistoryPanelTypography.hint(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
internal fun HistoryExpandedImage(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
internal fun rememberHistoryImageBitmap(bitmap: Bitmap?): ImageBitmap? =
    remember(bitmap) { bitmap?.asImageBitmap() }
