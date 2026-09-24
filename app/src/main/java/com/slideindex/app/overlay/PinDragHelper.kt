package com.slideindex.app.overlay

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.clipboard.ClipboardDragShareFallback
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.overlay.history.HistoryDragPreview
import com.slideindex.app.overlay.history.HistoryEntryDragHelper

/** 长按拖拽要投出去的一段钉图内容。 */
internal sealed class PinDragItem {
    data class Text(val body: String) : PinDragItem()

    data class Image(
        val bitmap: Bitmap,
        val entryId: String,
    ) : PinDragItem() {
        val fileName: String get() = "$entryId.png"
    }
}

/**
 * 钉图长按拖拽（跨应用投放）。
 *
 * 钉图窗口保留「直接滑动 = 挪位置」，长按只负责搬运：把钉图内容打包成 [ClipData]，
 * 交给系统拖拽框架投放到其它应用，宿主不接受时退回分享。
 *
 * 底层复用 [HistoryEntryDragHelper] 的同一条链路（前台宿主 URI 重映射、拖影、分享兜底），
 * 保证暂存卡片与钉图的跨应用拖拽行为一致。
 */
internal object PinDragHelper {

    private const val FILE_PREFIX = "pin_drag"
    private const val CLIP_LABEL = "pin"
    private const val IMAGE_MIME = "image/png"

    fun entryIdFor(pinId: String, index: Int): String = "${cachePrefix(pinId)}$index"

    fun previewOf(content: PinContent): HistoryDragPreview = when (content) {
        is PinContent.Text -> HistoryDragPreview(text = content.body)
        is PinContent.Image -> HistoryDragPreview(bitmap = content.bitmap)
        is PinContent.Rich -> HistoryDragPreview(
            text = content.blocks.filterIsInstance<PinDisplayBlock.Text>()
                .joinToString("\n") { it.body },
            bitmap = content.blocks.filterIsInstance<PinDisplayBlock.Image>()
                .firstOrNull()
                ?.bitmap,
        )
    }

    /**
     * 按块顺序展开钉图内容，图片块的 [PinDragItem.Image.entryId] 用块下标命名，
     * 让同一个钉图重复拖拽时命中同一份缓存文件。
     */
    fun itemsOf(content: PinContent, pinId: String): List<PinDragItem> = when (content) {
        is PinContent.Text -> listOfNotNull(
            content.body.trim()
                .takeIf { it.isNotEmpty() }
                ?.let(PinDragItem::Text)
        )
        is PinContent.Image -> listOf(PinDragItem.Image(content.bitmap, entryIdFor(pinId, 0)))
        is PinContent.Rich -> content.blocks.mapIndexedNotNull { index, block ->
            when (block) {
                is PinDisplayBlock.Text -> block.body.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let(PinDragItem::Text)
                is PinDisplayBlock.Image -> PinDragItem.Image(block.bitmap, entryIdFor(pinId, index))
            }
        }
    }

    fun buildClip(
        context: Context,
        items: List<PinDragItem>,
        resolveUri: (fileName: String) -> Uri? = { ClipboardImageStore.uriForFile(context, it) },
    ): ClipData? {
        if (items.isEmpty()) return null
        val blocks = mutableListOf<ClipboardContentBlock>()
        for (item in items) {
            when (item) {
                is PinDragItem.Text -> blocks += ClipboardContentBlock.text(item.body)
                is PinDragItem.Image -> {
                    val fileName = persistImage(context, item) ?: continue
                    blocks += ClipboardContentBlock.image(fileName)
                }
            }
        }
        if (blocks.isEmpty()) return null
        if (blocks.all { it.kind == ClipboardBlockKind.TEXT }) {
            val text = blocks.joinToString("\n") { it.text.trim() }.trim()
            return text.takeIf { it.isNotEmpty() }?.let { ClipData.newPlainText(CLIP_LABEL, it) }
        }
        if (blocks.all { it.kind == ClipboardBlockKind.IMAGE }) {
            return imageClip(blocks.map { it.fileName }, resolveUri)
        }
        return ClipboardWriter.buildClipForBlocks(
            context = context,
            mimeType = IMAGE_MIME,
            htmlText = null,
            blocks = blocks,
            resolveDataUri = { fileName -> ClipboardImageStore.dataUriForFile(context, fileName) },
            resolveContentUri = resolveUri,
            resolveDimensions = { fileName -> ClipboardImageStore.imageDimensions(context, fileName) },
        )
    }

    /** 拖拽结束/钉图关闭后清掉这份缓存图，避免在 files 目录里长期堆积。 */
    fun releaseCache(context: Context, pinId: String) {
        val prefix = cachePrefix(pinId)
        runCatching {
            ClipboardImageStore.imageDir(context)
                .listFiles()
                ?.forEach { file -> if (file.name.startsWith(prefix)) file.delete() }
        }
    }

    /**
     * 发起跨应用拖拽。宿主不接受投放时退回分享，两者都不行才提示失败。
     */
    fun startDrag(
        view: View,
        clipData: ClipData,
        preview: HistoryDragPreview,
        onDragStart: () -> Unit = {},
        onDragEnd: () -> Unit = {},
    ): Boolean {
        val context = view.context
        val started = HistoryEntryDragHelper.startDrag(
            view = view,
            clipData = clipData,
            preview = preview,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            onDropRejected = {
                if (ClipboardDragShareFallback.hasShareableContent(clipData) &&
                    !ClipboardDragShareFallback.shareToForegroundHost(context, clipData)
                ) {
                    showUnsupported(context)
                }
            },
        )
        if (!started && !ClipboardDragShareFallback.shareToForegroundHost(context, clipData)) {
            showUnsupported(context)
        }
        return started
    }

    private fun cachePrefix(pinId: String): String = "${FILE_PREFIX}_${pinId}_"

    /**
     * 图片块的位图只会在钉图创建时确定，落盘一次后直接复用，
     * 避免每次长按都重新编码整张图（大截图编码在毫秒级到百毫秒级）。
     */
    private fun persistImage(context: Context, item: PinDragItem.Image): String? {
        val fileName = item.fileName
        val cached = ClipboardImageStore.imageFile(context, fileName)
        if (cached.exists() && cached.length() > 0L) return fileName
        return ClipboardImageStore.persistFromBitmap(context, item.entryId, item.bitmap)
    }

    private fun imageClip(fileNames: List<String>, resolveUri: (String) -> Uri?): ClipData? {
        val uris = fileNames.mapNotNull(resolveUri)
        if (uris.isEmpty()) return null
        val clip = ClipData(
            ClipDescription(CLIP_LABEL, arrayOf(IMAGE_MIME)),
            ClipData.Item(uris.first()),
        )
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return clip
    }

    private fun showUnsupported(context: Context) {
        Toast.makeText(context, R.string.pin_drag_unsupported, Toast.LENGTH_SHORT).show()
    }
}
