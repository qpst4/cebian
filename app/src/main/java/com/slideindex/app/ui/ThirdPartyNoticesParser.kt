package com.slideindex.app.ui

internal data class ThirdPartyNoticeSection(
    val title: String,
    val bodyMarkdown: String,
)

internal fun parseThirdPartyNoticeSections(markdown: String): Pair<String, List<ThirdPartyNoticeSection>> {
    val headingPattern = Regex("""^## (.+)$""", RegexOption.MULTILINE)
    val matches = headingPattern.findAll(markdown).toList()
    if (matches.isEmpty()) {
        return markdown.trim() to emptyList()
    }
    val intro = markdown.substring(0, matches.first().range.first).trim()
    val sections = matches.mapIndexed { index, match ->
        val title = match.groupValues[1].trim()
        val bodyStart = match.range.last + 1
        val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: markdown.length
        val body = markdown.substring(bodyStart, bodyEnd)
            .trim()
            .trimStart('-', '\n', '\r')
        ThirdPartyNoticeSection(title = title, bodyMarkdown = body)
    }
    return intro to sections
}
