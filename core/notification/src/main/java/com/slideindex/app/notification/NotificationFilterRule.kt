package com.slideindex.app.notification

import java.util.UUID

data class NotificationFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val userCreated: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val channelId: String? = null,
    val appMode: AppMatchMode = AppMatchMode.ALL,
    val appTargets: List<AppTarget> = emptyList(),
    val textMode: TextMatchMode = TextMatchMode.ALL,
    val keywords: List<String> = emptyList(),
    val keywordsExclude: List<String> = emptyList(),
    val regex: String? = null,
    val advancedFilterJson: String? = null,
    val ignoreCase: Boolean = true,
    val timeStartMs: Int = 0,
    val timeEndMs: Int = 0,
    val weekDays: Set<Int> = NotificationRuleWeekDays.ALL,
    val screenMode: ScreenMode = ScreenMode.BOTH,
    val chargeMask: Int = NotificationRuleChargeMask.ALL,
    val actionEntries: List<RuleActionEntry> = listOf(RuleActionEntry(NotificationRuleActionType.HIDE)),
    /** @deprecated Legacy v1 comma-separated include list; merged by [normalized]. */
    val packageName: String = "",
    /** @deprecated Legacy v1 title pattern; merged by [normalized]. */
    val titlePattern: String? = null,
    /** @deprecated Legacy v1 text pattern; merged by [normalized]. */
    val textPattern: String? = null,
    /** @deprecated Legacy v1 regex flag; merged by [normalized]. */
    val useRegex: Boolean = false,
) {
    fun normalized(): NotificationFilterRule {
        var rule = this
        if (rule.appTargets.isEmpty() && rule.packageName.isNotBlank()) {
            rule = rule.copy(
                appMode = AppMatchMode.INCLUDE,
                appTargets = parsePackageNames(rule.packageName).map { AppTarget(it) },
            )
        }
        if (rule.textMode == TextMatchMode.ALL) {
            when {
                rule.useRegex && !rule.titlePattern.isNullOrBlank() -> {
                    rule = rule.copy(
                        textMode = TextMatchMode.REGEX,
                        regex = rule.titlePattern,
                    )
                }
                !rule.titlePattern.isNullOrBlank() || !rule.textPattern.isNullOrBlank() -> {
                    val words = buildList {
                        rule.titlePattern?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                        rule.textPattern?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                    if (words.isNotEmpty()) {
                        rule = rule.copy(
                            textMode = TextMatchMode.CONTAIN_ANY,
                            keywords = words,
                        )
                    }
                }
            }
        }
        if (rule.actionEntries.isEmpty()) {
            rule = rule.copy(
                actionEntries = listOf(RuleActionEntry(NotificationRuleActionType.HIDE)),
            )
        }
        return rule
    }

    fun displayName(): String {
        if (name.isNotBlank()) return name
        val n = normalized()
        val firstPkg = n.appTargets.firstOrNull()?.packageName
        if (!firstPkg.isNullOrBlank()) return firstPkg
        return ""
    }

    fun hidesNotification(): Boolean =
        normalized().actionEntries.any { it.type == NotificationRuleActionType.HIDE }

    fun packageNames(): Set<String> =
        normalized().appTargets.map { it.packageName }.toSet()

    companion object {
        fun formatPackageNames(names: Collection<String>): String =
            names.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")

        fun parsePackageNames(raw: String): Set<String> = raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        fun defaultAction(type: NotificationRuleActionType): RuleActionEntry =
            RuleActionEntry(type = type)
    }
}

fun findMatchingNotificationFilterRule(
    rules: List<NotificationFilterRule>,
    item: NotificationHistoryItem,
): NotificationFilterRule? {
    return rules.firstOrNull { rule ->
        rule.userCreated &&
            rule.hidesNotification() &&
            NotificationRuleMatcher.matches(
                rule = rule,
                packageName = item.packageName,
                channelId = null,
                title = item.title,
                text = item.text,
            )
    }
}

fun isNotificationHistoryItemHidden(
    item: NotificationHistoryItem,
    rules: List<NotificationFilterRule>,
): Boolean {
    return item.hidden || findMatchingNotificationFilterRule(rules, item) != null
}
