package com.slideindex.app.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification

object NotificationRuleMatcher {
    fun findMatching(
        rules: List<NotificationFilterRule>,
        sbn: StatusBarNotification,
        context: Context? = null,
    ): List<NotificationFilterRule> {
        val notification = sbn.notification ?: return emptyList()
        val extras = notification.extras ?: return emptyList()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val contentText = text.ifBlank { bigText }
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.channelId
        } else {
            null
        }
        val userId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sbn.user.hashCode()
        } else {
            0
        }
        return findMatching(
            rules = rules,
            sbn = sbn,
            context = context,
            packageName = sbn.packageName,
            userId = userId,
            channelId = channelId,
            title = title,
            text = contentText,
            subText = subText,
            timestampMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    fun findMatching(
        rules: List<NotificationFilterRule>,
        packageName: String,
        channelId: String?,
        title: String,
        text: String,
    ): List<NotificationFilterRule> {
        return rules.filter { rule ->
            matches(
                rule = rule,
                sbn = null,
                context = null,
                packageName = packageName,
                userId = 0,
                channelId = channelId,
                title = title,
                text = text,
                subText = "",
                timestampMs = System.currentTimeMillis(),
            )
        }
    }

    fun matches(
        rule: NotificationFilterRule,
        packageName: String,
        channelId: String?,
        title: String,
        text: String,
    ): Boolean {
        return matches(
            rule = rule,
            sbn = null,
            context = null,
            packageName = packageName,
            userId = 0,
            channelId = channelId,
            title = title,
            text = text,
            subText = "",
            timestampMs = System.currentTimeMillis(),
        )
    }

    private fun findMatching(
        rules: List<NotificationFilterRule>,
        sbn: StatusBarNotification?,
        context: Context?,
        packageName: String,
        userId: Int,
        channelId: String?,
        title: String,
        text: String,
        subText: String,
        timestampMs: Long,
    ): List<NotificationFilterRule> {
        return rules.filter { rule ->
            matches(rule, sbn, context, packageName, userId, channelId, title, text, subText, timestampMs)
        }
    }

    private fun matches(
        rule: NotificationFilterRule,
        sbn: StatusBarNotification?,
        context: Context?,
        packageName: String,
        userId: Int,
        channelId: String?,
        title: String,
        text: String,
        subText: String,
        timestampMs: Long,
    ): Boolean {
        val normalized = rule.normalized()
        if (!normalized.enabled) return false
        if (!NotificationRuleAppMatcher.matches(normalized, packageName, userId)) return false
        if (!normalized.channelId.isNullOrBlank() && normalized.channelId != channelId) return false
        val combined = NotificationRuleFieldExtractor.combinedText(title, text, subText)
        if (!NotificationRuleTextMatcher.matches(normalized, combined, sbn)) return false
        if (!NotificationRuleDeviceMatcher.matchesTime(normalized, timestampMs)) return false
        if (context != null) {
            if (!NotificationRuleDeviceMatcher.matchesScreen(context, normalized)) return false
            if (!NotificationRuleDeviceMatcher.matchesCharge(context, normalized)) return false
        }
        return true
    }
}
