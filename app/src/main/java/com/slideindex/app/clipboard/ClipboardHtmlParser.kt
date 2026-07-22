package com.slideindex.app.clipboard

import android.net.Uri
import android.util.Base64
import androidx.core.text.HtmlCompat
import java.util.regex.Pattern

internal object ClipboardHtmlParser {
    private val IMG_SRC_PATTERN = Pattern.compile(
        """<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE,
    )

    fun plainTextFromHtml(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .trim()

    fun firstImageSrc(html: String): String? = imageSources(html).firstOrNull()

    fun imageSources(html: String): List<String> {
        val matcher = IMG_SRC_PATTERN.matcher(html)
        val results = mutableListOf<String>()
        while (matcher.find()) {
            matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { results += it }
        }
        return results
    }

    fun buildHtml(plainText: String, imageSrc: String): String =
        buildHtml(plainText, listOf(imageSrc))

    fun buildHtml(plainText: String, imageSrcs: List<String>): String {
        val escaped = plainText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        val images = imageSrcs.joinToString("") { src -> """<img src="$src"/>""" }
        return "<html><body><p>$escaped</p>$images</body></html>"
    }

    fun isImageSrc(src: String): Boolean {
        val lower = src.lowercase()
        return lower.startsWith("data:image/") ||
            lower.startsWith("content://") ||
            lower.startsWith("file://") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://")
    }

    fun decodeDataUriImage(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:", ignoreCase = true)) return null
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex < 0) return null
        val meta = dataUri.substring(5, commaIndex)
        if (!meta.contains("base64", ignoreCase = true)) return null
        val payload = dataUri.substring(commaIndex + 1)
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
    }

    fun normalizeImageSrc(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        else -> src
    }

    fun uriLooksLikeImage(uri: Uri, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/") == true) return true
        val path = uri.toString().lowercase()
        return path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".webp") ||
            path.endsWith(".gif") ||
            path.endsWith(".bmp")
    }
}
