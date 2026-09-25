package com.slideindex.app.otp

import org.json.JSONArray
import org.json.JSONObject

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

/**
 * 短信黑名单规则集：号码（精确）/ 前缀 / 内容（包含）/ 正则，命中后按 [actionBlock] /
 * [actionDelete] 执行。对应上游 XposedSmsCode 的 `SmsBlacklistConfig` 与黑名单偏好键。
 */
data class SmsBlacklistRuleSet(
    val enabled: Boolean = false,
    val actionBlock: Boolean = true,
    val actionDelete: Boolean = false,
    val numbers: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    val content: List<String> = emptyList(),
    val regex: List<String> = emptyList(),
) {
    val hasRules: Boolean
        get() = numbers.isNotEmpty() || prefixes.isNotEmpty() || content.isNotEmpty() || regex.isNotEmpty()

    companion object {
        val DISABLED = SmsBlacklistRuleSet()
    }
}

enum class SmsBlacklistMatchType {
    Number,
    Prefix,
    Content,
    Regex,
}

data class SmsBlacklistMatch(
    val matched: Boolean,
    val type: SmsBlacklistMatchType? = null,
    val pattern: String? = null,
    val actionBlock: Boolean = false,
    val actionDelete: Boolean = false,
) {
    companion object {
        val None = SmsBlacklistMatch(matched = false)
    }
}

/**
 * 黑名单匹配（纯逻辑，供电话进程与 App 进程共用）。
 *
 * 匹配顺序固定为 号码 → 前缀 → 内容 → 正则，任一命中即生效并记录命中类型。
 * 上游该顺序定义在缺失的共享库里、无法逐字核对，这里明确固定语义，便于排障与单测。
 */
object SmsBlacklistMatcher {
    fun match(rules: SmsBlacklistRuleSet, sender: String?, body: String?): SmsBlacklistMatch {
        if (!rules.enabled || !rules.hasRules) return SmsBlacklistMatch.None
        val normalizedSender = sender.orEmpty().trim()
        val normalizedBody = body.orEmpty()

        rules.numbers.forEach { candidate ->
            val pattern = candidate.trim()
            if (pattern.isNotEmpty() && pattern == normalizedSender) {
                return hit(rules, SmsBlacklistMatchType.Number, pattern)
            }
        }
        rules.prefixes.forEach { candidate ->
            val pattern = candidate.trim()
            if (pattern.isNotEmpty() && normalizedSender.startsWith(pattern)) {
                return hit(rules, SmsBlacklistMatchType.Prefix, pattern)
            }
        }
        rules.content.forEach { candidate ->
            val pattern = candidate.trim()
            if (pattern.isNotEmpty() && normalizedBody.contains(pattern, ignoreCase = true)) {
                return hit(rules, SmsBlacklistMatchType.Content, pattern)
            }
        }
        rules.regex.forEach { candidate ->
            val pattern = candidate.trim()
            if (pattern.isEmpty()) return@forEach
            val regex = runCatching { Regex(pattern) }.getOrNull() ?: return@forEach
            if (regex.containsMatchIn(normalizedBody)) {
                return hit(rules, SmsBlacklistMatchType.Regex, pattern)
            }
        }
        return SmsBlacklistMatch.None
    }

    private fun hit(
        rules: SmsBlacklistRuleSet,
        type: SmsBlacklistMatchType,
        pattern: String,
    ): SmsBlacklistMatch = SmsBlacklistMatch(
        matched = true,
        type = type,
        pattern = pattern,
        actionBlock = rules.actionBlock,
        actionDelete = rules.actionDelete,
    )
}

/** 黑名单规则集的 JSON 编解码（设置页存一个偏好键，hook 侧随快照下发）。 */
object SmsBlacklistRuleSetCodec {
    fun encode(rules: SmsBlacklistRuleSet): String = JSONObject()
        .put("enabled", rules.enabled)
        .put("actionBlock", rules.actionBlock)
        .put("actionDelete", rules.actionDelete)
        .put("numbers", rules.numbers.toJsonArray())
        .put("prefixes", rules.prefixes.toJsonArray())
        .put("content", rules.content.toJsonArray())
        .put("regex", rules.regex.toJsonArray())
        .toString()

    fun decode(raw: String?): SmsBlacklistRuleSet {
        if (raw.isNullOrBlank()) return SmsBlacklistRuleSet.DISABLED
        return runCatching {
            val json = JSONObject(raw)
            SmsBlacklistRuleSet(
                enabled = json.optBoolean("enabled", false),
                actionBlock = json.optBoolean("actionBlock", true),
                actionDelete = json.optBoolean("actionDelete", false),
                numbers = json.optJSONArray("numbers").toStringList(),
                prefixes = json.optJSONArray("prefixes").toStringList(),
                content = json.optJSONArray("content").toStringList(),
                regex = json.optJSONArray("regex").toStringList(),
            )
        }.getOrDefault(SmsBlacklistRuleSet.DISABLED)
    }

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().apply {
        this@toJsonArray.forEach { put(it) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }
}

/** 设置页多行文本框与规则列表之间的转换：一行一条，去空行、去重、保持顺序。 */
object SmsBlacklistTextCodec {
    fun parseLines(raw: String): List<String> = raw
        .split('\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    fun formatLines(items: List<String>): String = items.joinToString("\n")
}
