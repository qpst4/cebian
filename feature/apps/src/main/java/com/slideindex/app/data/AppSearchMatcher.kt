package com.slideindex.app.data

import com.slideindex.app.util.PinyinHelper

object AppSearchMatcher {
    fun search(
        apps: List<AppInfo>,
        query: String,
        limit: Int = Int.MAX_VALUE,
    ): List<AppInfo> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return apps
        if (limit <= 0) return emptyList()
        val pinyinQuery = PinyinHelper.sortKey(query)
        return apps
            .mapNotNull { app ->
                score(app, normalized, pinyinQuery)?.let { app to it }
            }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Int>> { it.second }
                    .thenBy { matchIndex(it.first, normalized, pinyinQuery) }
                    .thenBy { it.first.label.length }
                    .thenBy { it.first.pinyinKey },
            )
            .map { it.first }
            .take(limit)
    }

    internal fun score(
        app: AppInfo,
        normalizedQuery: String,
        pinyinQuery: String,
    ): Int? {
        val labelLower = app.label.trim().lowercase()
        if (labelLower.isEmpty()) return null

        val labelPinyin = app.pinyinKey
        var best = textScore(
            lowerText = labelLower,
            pinyinText = labelPinyin,
            normalizedQuery = normalizedQuery,
            pinyinQuery = pinyinQuery,
            titleBoost = 100,
        )
        if (app.packageName.lowercase().contains(normalizedQuery)) {
            best = maxOf(best, 20)
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
        if (lowerText.startsWith(normalizedQuery) || pinyinText.startsWith(pinyinQuery)) {
            return titleBoost + 30
        }
        if (lowerText.contains(normalizedQuery) || pinyinText.contains(pinyinQuery)) {
            return titleBoost + 10
        }
        return 0
    }

    private fun matchIndex(
        app: AppInfo,
        normalizedQuery: String,
        pinyinQuery: String,
    ): Int {
        val labelLower = app.label.lowercase()
        val labelIndex = labelLower.indexOf(normalizedQuery)
        if (labelIndex >= 0) return labelIndex
        val pinyinIndex = app.pinyinKey.indexOf(pinyinQuery)
        if (pinyinIndex >= 0) return pinyinIndex + 1_000
        val packageIndex = app.packageName.lowercase().indexOf(normalizedQuery)
        if (packageIndex >= 0) return packageIndex + 10_000
        return Int.MAX_VALUE
    }
}
