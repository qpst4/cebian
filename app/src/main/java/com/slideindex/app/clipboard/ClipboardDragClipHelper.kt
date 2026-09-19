package com.slideindex.app.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import com.slideindex.app.clipboard.hasImageContent

/**
 * 拖放前按前台宿主准备 ClipData（微信需 [SendableClipUri]）。
 */
object ClipboardDragClipHelper {

    const val WECHAT_PACKAGE = "com.tencent.mm"

    fun buildClipForDrag(
        context: Context,
        entry: ClipboardEntry,
        foregroundPackage: String?,
    ): ClipData? {
        val base = ClipboardWriter.buildClipForEntry(context, entry) ?: return null
        if (foregroundPackage != WECHAT_PACKAGE || !entry.hasImageContent()) {
            return base
        }
        return remapForWeChatDrag(context, base, entry, foregroundPackage)
    }

    private fun remapForWeChatDrag(
        context: Context,
        base: ClipData,
        entry: ClipboardEntry,
        hostPackage: String,
    ): ClipData? {
        val items = mutableListOf<ClipData.Item>()
        val mimeTypes = linkedSetOf<String>()
        var label = base.description.label?.toString() ?: "clipboard"
        for (index in 0 until base.itemCount) {
            val item = base.getItemAt(index)
            val sourceUri = item.uri
            if (sourceUri == null) {
                items.add(item)
                continue
            }
            val name = item.text?.toString()?.takeIf { it.isNotBlank() }
                ?: sourceUri.lastPathSegment
            val sendUri = SendableClipUri.prepareForHost(
                context = context,
                source = sourceUri,
                displayName = name,
                mimeType = entry.mimeType,
                hostPackage = hostPackage,
            ) ?: return null
            val mime = entry.mimeType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            mimeTypes += mime
            mimeTypes += "image/*"
            items += ClipData.Item(sendUri)
        }
        if (items.isEmpty()) return null
        if (mimeTypes.isEmpty()) mimeTypes += "image/*"
        val description = ClipDescription(label, mimeTypes.toTypedArray())
        val clip = ClipData(description, items.first())
        items.drop(1).forEach { clip.addItem(it) }
        return clip
    }
}
