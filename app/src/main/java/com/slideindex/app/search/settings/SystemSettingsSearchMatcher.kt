package com.slideindex.app.search.settings

import com.slideindex.app.util.PinyinHelper

object SystemSettingsSearchMatcher {
    fun search(
        entries: List<SystemSettingsSearchEntry>,
        query: String,
        limit: Int,
    ): List<SystemSettingsSearchEntry> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val pinyinQuery = PinyinHelper.sortKey(query)
        return entries
            .asSequence()
            .mapNotNull { entry ->
                score(entry, normalized, pinyinQuery)?.let { entry to it }
            }
            .sortedWith(
                compareByDescending<Pair<SystemSettingsSearchEntry, Int>> { it.second }
                    .thenBy { PinyinHelper.sortKey(it.first.title) },
            )
            .map { it.first }
            .distinctBy { it.dedupeKey }
            .take(limit)
            .toList()
    }

    internal fun score(
        entry: SystemSettingsSearchEntry,
        normalizedQuery: String,
        pinyinQuery: String,
    ): Int? {
        val title = entry.title.trim()
        if (title.isEmpty()) return null

        val titleLower = title.lowercase()
        val screenLower = entry.screenTitle?.trim()?.lowercase().orEmpty()
        val keywordLower = entry.keywords?.trim()?.lowercase().orEmpty()
        val titlePinyin = PinyinHelper.sortKey(title)
        val screenPinyin = entry.screenTitle?.let(PinyinHelper::sortKey).orEmpty()
        val keywordPinyin = entry.keywords?.let(PinyinHelper::sortKey).orEmpty()

        var best = 0
        best = maxOf(best, textScore(titleLower, titlePinyin, normalizedQuery, pinyinQuery, titleBoost = 100))
        if (screenLower.isNotEmpty()) {
            best = maxOf(best, textScore(screenLower, screenPinyin, normalizedQuery, pinyinQuery, titleBoost = 60))
        }
        if (keywordLower.isNotEmpty()) {
            best = maxOf(best, textScore(keywordLower, keywordPinyin, normalizedQuery, pinyinQuery, titleBoost = 40))
        }
        return best.takeIf { it > 0 }
    }

    private fun textScore(
        lowerText: String,
        pinyinText: String,
        normalizedQuery: String,
        pinyinQuery: String,
        titleBoost: Int,
    ): Int {
        if (lowerText == normalizedQuery || pinyinText == pinyinQuery) return titleBoost + 40
        if (lowerText.startsWith(normalizedQuery) || pinyinText.startsWith(pinyinQuery)) return titleBoost + 30
        if (lowerText.contains(normalizedQuery) || pinyinText.contains(pinyinQuery)) return titleBoost + 10
        return 0
    }
}
