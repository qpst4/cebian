package com.slideindex.app.clipboard

/**
 * 纯图片条目里常见的「标签文字」：截图文件名、IMG_xxx、或图片 content URI 本身。
 * 这些不应作为正文写入系统剪贴板。
 */
internal object ClipboardImageLabel {
    private val SCREENSHOT_FILE_NAME = Regex(
        """^Screenshot_\d{8}-\d{6,}\.(png|jpg|jpeg|webp)$""",
        RegexOption.IGNORE_CASE,
    )
    private val CAMERA_FILE_NAME = Regex(
        """^IMG_\d+\.(png|jpg|jpeg|webp|gif)$""",
        RegexOption.IGNORE_CASE,
    )

    fun isMetadataText(
        text: String,
        imageSources: List<String> = emptyList(),
        uri: String? = null,
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val normalizedUri = uri?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedUri != null && trimmed == normalizedUri) return true
        val normalizedText = ClipboardHtmlParser.normalizeImageSrc(trimmed)
        if (imageSources.any { source ->
                val normalizedSource = ClipboardHtmlParser.normalizeImageSrc(source.trim())
                normalizedSource == normalizedText || source.trim() == trimmed
            }
        ) {
            return true
        }
        if (ClipboardHtmlParser.isImageSrc(trimmed)) return true
        if (SCREENSHOT_FILE_NAME.matches(trimmed)) return true
        if (CAMERA_FILE_NAME.matches(trimmed)) return true
        val fileName = trimmed.substringAfterLast('/')
        if (fileName != trimmed && looksLikeImageFileName(fileName)) {
            return imageSources.any { source ->
                source.substringAfterLast('/').equals(fileName, ignoreCase = true)
            }
        }
        return false
    }

    fun blocksForClipboardWrite(
        blocks: List<ClipboardContentBlock>,
        imageSources: List<String>,
        uri: String?,
    ): List<ClipboardContentBlock> {
        if (blocks.isEmpty()) return blocks
        return blocks.filterNot { block ->
            block.kind == ClipboardBlockKind.TEXT &&
                isMetadataText(block.text, imageSources, uri)
        }
    }

    fun stripMetadataText(
        text: String,
        imageSources: List<String>,
        uri: String?,
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return if (isMetadataText(trimmed, imageSources, uri)) "" else trimmed
    }

    private fun looksLikeImageFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".bmp")
    }
}
