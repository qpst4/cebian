package com.slideindex.app.message

enum class MessageFilterMode(val id: String) {
    NO_FILTER("no_filter"),
    ONLY_MATCHING("only_matching"),
    BLOCK_MATCHING("block_matching"),
    ;

    companion object {
        fun fromId(id: String?): MessageFilterMode =
            entries.firstOrNull { it.id == id } ?: NO_FILTER
    }
}

enum class MessageMatchField(val id: String) {
    TITLE("title"),
    CONTENT("content"),
    TITLE_AND_CONTENT("title_and_content"),
    ;

    companion object {
        fun fromId(id: String?): MessageMatchField =
            entries.firstOrNull { it.id == id } ?: TITLE_AND_CONTENT
    }
}

enum class MessageMatchType(val id: String) {
    CONTAINS("contains"),
    FULL_MATCH("full_match"),
    REGEX("regex"),
    ;

    companion object {
        fun fromId(id: String?): MessageMatchType =
            entries.firstOrNull { it.id == id } ?: CONTAINS
    }
}

data class MessageMatchCondition(
    val field: MessageMatchField = MessageMatchField.TITLE_AND_CONTENT,
    val type: MessageMatchType = MessageMatchType.CONTAINS,
    val keyword: String = "",
)

data class MessageAppFilterRule(
    val packageName: String,
    val mode: MessageFilterMode = MessageFilterMode.NO_FILTER,
    val onlyMatchingConditions: List<MessageMatchCondition> = emptyList(),
    val blockMatchingConditions: List<MessageMatchCondition> = emptyList(),
) {
    fun hasCustomFilter(): Boolean =
        mode != MessageFilterMode.NO_FILTER ||
            onlyMatchingConditions.isNotEmpty() ||
            blockMatchingConditions.isNotEmpty()

    companion object {
        fun default(packageName: String): MessageAppFilterRule =
            MessageAppFilterRule(packageName = packageName)
    }
}

object MessageAppFilterMatcher {
    fun passes(rule: MessageAppFilterRule, title: String, content: String): Boolean {
        if (!rule.hasCustomFilter()) return true
        return when (rule.mode) {
            MessageFilterMode.NO_FILTER -> true
            MessageFilterMode.ONLY_MATCHING -> {
                val conditions = rule.onlyMatchingConditions.filter { it.keyword.isNotBlank() }
                conditions.isEmpty() || conditions.any { matches(it, title, content) }
            }
            MessageFilterMode.BLOCK_MATCHING -> {
                val conditions = rule.blockMatchingConditions.filter { it.keyword.isNotBlank() }
                conditions.isEmpty() || conditions.none { matches(it, title, content) }
            }
        }
    }

    private fun matches(condition: MessageMatchCondition, title: String, content: String): Boolean {
        val text = when (condition.field) {
            MessageMatchField.TITLE -> title
            MessageMatchField.CONTENT -> content
            MessageMatchField.TITLE_AND_CONTENT -> "$title $content".trim()
        }
        if (text.isBlank() || condition.keyword.isBlank()) return false
        return when (condition.type) {
            MessageMatchType.CONTAINS -> text.contains(condition.keyword, ignoreCase = true)
            MessageMatchType.FULL_MATCH -> text.equals(condition.keyword, ignoreCase = true)
            MessageMatchType.REGEX -> runCatching {
                Regex(condition.keyword, RegexOption.IGNORE_CASE).containsMatchIn(text)
            }.getOrDefault(false)
        }
    }
}
