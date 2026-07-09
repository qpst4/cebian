package com.slideindex.app.notification

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

internal object NotificationRuleFieldExtractor {
    fun combinedText(title: String, text: String, subText: String = ""): String {
        return listOf(title, text, subText).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun fromSbn(sbn: StatusBarNotification): Map<String, String> {
        val notification = sbn.notification ?: return emptyMap()
        val extras = notification.extras ?: return emptyMap()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val content = text.ifBlank { bigText }
        return mapOf(
            "packageName" to sbn.packageName,
            "title" to title,
            "text" to content,
            "subText" to subText,
            "channelId" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId else ""),
            "category" to (notification.category.orEmpty()),
            "key" to sbn.key,
        )
    }
}

internal object NotificationRuleTextMatcher {
    private val regexCache = ConcurrentHashMap<String, Regex>()
    private val patternCache = ConcurrentHashMap<String, Pattern>()

    fun matches(
        rule: NotificationFilterRule,
        combinedText: String,
        sbn: StatusBarNotification?,
    ): Boolean {
        val normalized = rule.normalized()
        val text = combinedText
        val ignoreCase = normalized.ignoreCase
        return when (normalized.textMode) {
            TextMatchMode.ALL -> true
            TextMatchMode.CONTAIN_ANY -> normalized.keywords.any { contains(text, it, ignoreCase) }
            TextMatchMode.NOT_CONTAIN_ANY -> normalized.keywords.none { contains(text, it, ignoreCase) }
            TextMatchMode.CONTAIN_ALL -> normalized.keywords.isNotEmpty() &&
                normalized.keywords.all { contains(text, it, ignoreCase) }
            TextMatchMode.NOT_CONTAIN_ALL -> normalized.keywords.isNotEmpty() &&
                !normalized.keywords.all { contains(text, it, ignoreCase) }
            TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> {
                val include = normalized.keywords.any { contains(text, it, ignoreCase) }
                val exclude = normalized.keywordsExclude.any { contains(text, it, ignoreCase) }
                include && !exclude
            }
            TextMatchMode.REGEX -> {
                val pattern = normalized.regex?.takeIf { it.isNotBlank() } ?: return false
                runCatching {
                    cachedRegex(pattern, ignoreCase).containsMatchIn(text)
                }.getOrDefault(false)
            }
            TextMatchMode.ADVANCED -> matchesAdvanced(normalized.advancedFilterJson, sbn)
        }
    }

    private fun contains(text: String, keyword: String, ignoreCase: Boolean): Boolean {
        if (keyword.isBlank()) return false
        if (text.isBlank()) return false
        return text.contains(keyword, ignoreCase = ignoreCase)
    }

    private fun matchesAdvanced(json: String?, sbn: StatusBarNotification?): Boolean {
        if (json.isNullOrBlank() || sbn == null) return false
        val filter = runCatching {
            val obj = JSONObject(json)
            val matchType = obj.optString("match", "ALL").ifBlank { "ALL" }
            val nodesArray = obj.optJSONArray("node") ?: JSONArray()
            val nodes = buildList(nodesArray.length()) {
                for (i in 0 until nodesArray.length()) {
                    val node = nodesArray.optJSONObject(i) ?: continue
                    val field = node.optString("field")
                    val regex = node.optString("regex")
                    if (field.isNotBlank() && regex.isNotBlank()) {
                        add(AdvancedFilterNode(field, regex))
                    }
                }
            }
            AdvancedFilter(matchType, nodes)
        }.getOrNull() ?: return false
        if (filter.nodes.isEmpty()) return false
        val fields = NotificationRuleFieldExtractor.fromSbn(sbn)
        val results = filter.nodes.map { node ->
            val value = fields[node.field].orEmpty()
            runCatching {
                cachedPattern(node.regex).matcher(value).find()
            }.getOrDefault(false)
        }
        return when (filter.matchType.uppercase()) {
            "ANY" -> results.any { it }
            "NONE" -> results.none { it }
            else -> results.all { it }
        }
    }

    private fun cachedRegex(pattern: String, ignoreCase: Boolean): Regex {
        val key = if (ignoreCase) "i:$pattern" else "n:$pattern"
        return regexCache.getOrPut(key) {
            Regex(pattern, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }
    }

    private fun cachedPattern(regex: String): Pattern {
        return patternCache.getOrPut(regex) {
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        }
    }
}

internal object NotificationRuleDeviceMatcher {
    fun matchesTime(rule: NotificationFilterRule, timestampMs: Long): Boolean {
        val normalized = rule.normalized()
        if (!matchesTimeRange(normalized.timeStartMs, normalized.timeEndMs, timestampMs)) return false
        return matchesWeekDay(normalized.weekDays, timestampMs)
    }

    fun matchesScreen(context: Context, rule: NotificationFilterRule): Boolean {
        return when (rule.normalized().screenMode) {
            ScreenMode.BOTH -> true
            ScreenMode.ON -> isScreenOn(context)
            ScreenMode.OFF -> !isScreenOn(context)
        }
    }

    fun matchesCharge(context: Context, rule: NotificationFilterRule): Boolean {
        val mask = rule.normalized().chargeMask
        if (mask == NotificationRuleChargeMask.ALL) return true
        val pluggedMask = currentChargeMask(context)
        return pluggedMask and mask != 0
    }

    private fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isInteractive
    }

    private fun currentChargeMask(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return if (plugged != 0) plugged shl 1 else NotificationRuleChargeMask.BATTERY
    }

    private fun matchesTimeRange(startMs: Int, endMs: Int, timestampMs: Long): Boolean {
        if (startMs == endMs) return true
        val offset = ((timestampMs + TimeZone.getDefault().getOffset(timestampMs)) % 86_400_000L).toInt()
        return if (startMs < endMs) {
            offset in startMs..endMs
        } else {
            offset >= startMs || offset <= endMs
        }
    }

    private fun matchesWeekDay(days: Set<Int>, timestampMs: Long): Boolean {
        if (days.isEmpty() || days.size >= 7) return true
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestampMs
        return days.contains(calendar.get(Calendar.DAY_OF_WEEK))
    }
}

internal object NotificationRuleAppMatcher {
    fun matches(rule: NotificationFilterRule, packageName: String, userId: Int): Boolean {
        val normalized = rule.normalized()
        return when (normalized.appMode) {
            AppMatchMode.ALL -> true
            AppMatchMode.INCLUDE -> normalized.appTargets.any {
                it.packageName == packageName && (it.userId == 0 || it.userId == userId)
            }
            AppMatchMode.EXCLUDE -> normalized.appTargets.none {
                it.packageName == packageName && (it.userId == 0 || it.userId == userId)
            }
        }
    }
}
