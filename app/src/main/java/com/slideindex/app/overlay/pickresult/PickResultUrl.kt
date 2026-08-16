package com.slideindex.app.overlay.pickresult

internal sealed class PickResultOpenLinkAction {
    data class Open(val url: String) : PickResultOpenLinkAction()
    data class Choose(val urls: List<String>) : PickResultOpenLinkAction()
}

internal object PickResultUrl {
    private val httpUrlRegex = Regex("""https?://[^\s<>"')\]}]+""", RegexOption.IGNORE_CASE)
    private val wwwUrlRegex = Regex("""(?:^|[\s(\[{<"'])((?:www\.)[^\s<>"')\]}]+)""", RegexOption.IGNORE_CASE)
    private val schemeUrlRegex = Regex(
        """(?:^|[\s(\[{<"'])((?:[a-z][a-z0-9+.-]*://)[^\s<>"')\]}]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val systemUriRegex = Regex(
        """(?:^|[\s(\[{<"'])((?:tel|mailto|sms|geo):[^\s<>"')\]}]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val bareHostRegex = Regex(
        """^[\w\-.]+\.[a-zA-Z]{2,}([\w./?#=&+%\-]*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val androidPackageRegex = Regex(
        """^(com|org|net|cn|io|tw|hk|co|edu|gov|app|me|android|androidx|java|javax|kotlin|kotlinx)\.[a-zA-Z0-9_.]+$""",
        RegexOption.IGNORE_CASE,
    )
    private val blockedSchemes = setOf("javascript", "data")

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
        schemeUrlRegex.findAll(text).forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        systemUriRegex.findAll(text).forEach { match ->
            normalizeOpenableUrl(match.groupValues[1])?.let { found.add(it) }
        }
        return found.toList()
    }

    fun linkDisplayLabel(uri: String): String {
        val normalized = normalizeOpenableUrl(uri) ?: uri.trim()
        return when {
            normalized.startsWith("tel:", ignoreCase = true) -> {
                normalized.removePrefix("tel:").substringBefore('?').ifBlank { "tel" }
            }
            normalized.startsWith("mailto:", ignoreCase = true) -> {
                normalized.removePrefix("mailto:").substringBefore('?').ifBlank { "mailto" }
            }
            normalized.startsWith("sms:", ignoreCase = true) -> {
                normalized.removePrefix("sms:").substringBefore('?').ifBlank { "sms" }
            }
            isIntentUri(normalized) -> "Intent"
            else -> {
                val scheme = normalized.substringBefore("://", missingDelimiterValue = "")
                val afterScheme = normalized.substringAfter("://", missingDelimiterValue = normalized)
                val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                when {
                    host.contains('.') -> host
                    scheme.isNotBlank() -> scheme
                    host.isNotBlank() -> host
                    else -> normalized
                }
            }
        }
    }

    fun isIntentUri(uri: String): Boolean =
        uri.startsWith("intent://", ignoreCase = true) || uri.startsWith("intent:", ignoreCase = true)

    fun normalizeOpenableUrl(raw: String): String? {
        val candidate = trimTrailingPunctuation(raw.trim())
        if (candidate.isBlank()) return null
        if (isBlockedScheme(candidate)) return null
        return when {
            isIntentUri(candidate) -> candidate
            isSystemUri(candidate) -> candidate
            hasCustomScheme(candidate) -> candidate
            else -> normalizeWebUrl(candidate)
        }
    }

    private fun normalizeWebUrl(candidate: String): String? {
        val withScheme = when {
            candidate.startsWith("http://", ignoreCase = true) -> candidate
            candidate.startsWith("https://", ignoreCase = true) -> candidate
            candidate.startsWith("www.", ignoreCase = true) -> "https://$candidate"
            bareHostRegex.matches(candidate) -> {
                if (androidPackageRegex.matches(candidate)) return null
                "https://$candidate"
            }
            else -> return null
        }
        val sanitized = trimTrailingPunctuation(withScheme)
        if (!isPlausibleWebUrl(sanitized)) return null
        return sanitized
    }

    private fun isBlockedScheme(candidate: String): Boolean {
        val scheme = candidate.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme in blockedSchemes
    }

    private fun isSystemUri(candidate: String): Boolean {
        val scheme = candidate.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme in setOf("tel", "mailto", "sms", "geo") &&
            candidate.length > scheme.length + 1
    }

    private fun hasCustomScheme(candidate: String): Boolean {
        val colonIndex = candidate.indexOf("://")
        if (colonIndex <= 0) return false
        val scheme = candidate.substring(0, colonIndex)
        if (!scheme.matches(Regex("""[a-z][a-z0-9+.-]*""", RegexOption.IGNORE_CASE))) return false
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            return false
        }
        return candidate.length > colonIndex + 3
    }

    private fun isPlausibleWebUrl(url: String): Boolean {
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
