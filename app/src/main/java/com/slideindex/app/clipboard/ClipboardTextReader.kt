package com.slideindex.app.clipboard

import android.content.ClipboardManager
import android.content.Context

object ClipboardTextReader {
    fun read(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            val text = item.text?.toString()
                ?: item.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) return text.trim()
        }
        return null
    }
}
