package com.slideindex.app.overlay.pickresult

internal sealed class PickResultOpenLinkAction {
    data class Open(val url: String) : PickResultOpenLinkAction()
    data class Choose(val urls: List<String>) : PickResultOpenLinkAction()
}

internal object PickResultUrl {
    private val httpUrlRegex = Regex("""https?://[^\s<>"')\]}]+""", RegexOption.IGNORE_CASE)
    private val wwwUrlRegex = Regex("""(?:^|[\s(\[{<"'])((?:www\.)[^\s<>"')\]}]+)""", RegexOption.IGNORE_CASE)
    private val bareHostRegex = Regex(
        """^[\w\-.]+\.[a-zA-Z]{2,}([\w./?#=&+%\-]*)?$""",
        RegexOption.IGNORE_CASE,
    )

    fun resolveOpenLinkAction(
        fullText: String,
        activeText: String,
        hasSelection: Boolean,
    ): PickResultOpenLinkAction? {
        if (hasSelection) {
            return normalizeOpenableUrl(activeText)?.let { PickResultOpenLinkAction.Open(it) }
        }
        val trimmedFull = fullText.trim()
        normalizeOpenableUrl(trimmedFull)?.let { return PickResultOpenLinkAction.Open(it) }
        val urls = extractOpenableUrls(fullText)
        return when {
            urls.isEmpty() -> null
            urls.size == 1 -> PickResultOpenLinkAction.Open(urls.single())
            else -> PickResultOpenLinkAction.Choose(urls)
        }
    }

    fun extractOpenableUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val found = linkedSetOf<String>()
        httpUrlRegex.findAll(text).forEach { match ->
            normalizeOpenableUrl(match.value)?.let { found.add(it) }
        }
        wwwUrlRegex.findAll(text).forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        return found.toList()
    }

    fun normalizeOpenableUrl(raw: String): String? {
        val candidate = trimTrailingPunctuation(raw.trim())
        if (candidate.isBlank()) return null
        val withScheme = when {
            candidate.startsWith("http://", ignoreCase = true) -> candidate
            candidate.startsWith("https://", ignoreCase = true) -> candidate
            candidate.startsWith("www.", ignoreCase = true) -> "https://$candidate"
            bareHostRegex.matches(candidate) -> "https://$candidate"
            else -> return null
        }
        val sanitized = trimTrailingPunctuation(withScheme)
        if (!isPlausibleUrl(sanitized)) return null
        return sanitized
    }

    private fun isPlausibleUrl(url: String): Boolean {
        val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.contains('.') && host.none { it.isWhitespace() }
    }

    private fun trimTrailingPunctuation(value: String): String {
        return value.trimEnd { ch ->
            ch in ".,;:!?)」》\"'、，。；：！？"
        }
    }
}
