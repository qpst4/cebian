package com.slideindex.app.message

import org.json.JSONArray
import org.json.JSONObject

object MessageAppFilterCodec {
    private const val KEY_PACKAGE = "packageName"
    private const val KEY_MODE = "mode"
    private const val KEY_ONLY = "onlyMatchingConditions"
    private const val KEY_BLOCK = "blockMatchingConditions"
    private const val KEY_FIELD = "field"
    private const val KEY_TYPE = "type"
    private const val KEY_KEYWORD = "keyword"

    fun encode(rule: MessageAppFilterRule): String {
        val json = JSONObject()
        json.put(KEY_PACKAGE, rule.packageName)
        json.put(KEY_MODE, rule.mode.id)
        json.put(KEY_ONLY, encodeConditions(rule.onlyMatchingConditions))
        json.put(KEY_BLOCK, encodeConditions(rule.blockMatchingConditions))
        return json.toString()
    }

    fun decode(raw: String): MessageAppFilterRule? = runCatching {
        val json = JSONObject(raw)
        val packageName = json.optString(KEY_PACKAGE).takeIf { it.isNotBlank() } ?: return null
        MessageAppFilterRule(
            packageName = packageName,
            mode = MessageFilterMode.fromId(json.optString(KEY_MODE)),
            onlyMatchingConditions = decodeConditions(json.optJSONArray(KEY_ONLY)),
            blockMatchingConditions = decodeConditions(json.optJSONArray(KEY_BLOCK)),
        )
    }.getOrNull()

    fun encodeAll(rules: Collection<MessageAppFilterRule>): Set<String> =
        rules.mapNotNull { rule ->
            if (rule.hasCustomFilter()) encode(rule) else null
        }.toSet()

    fun decodeAll(raw: Set<String>): Map<String, MessageAppFilterRule> =
        raw.mapNotNull { decode(it) }.associateBy { it.packageName }

    private fun encodeConditions(conditions: List<MessageMatchCondition>): JSONArray {
        val array = JSONArray()
        conditions.forEach { condition ->
            if (condition.keyword.isBlank()) return@forEach
            array.put(
                JSONObject().apply {
                    put(KEY_FIELD, condition.field.id)
                    put(KEY_TYPE, condition.type.id)
                    put(KEY_KEYWORD, condition.keyword)
                },
            )
        }
        return array
    }

    private fun decodeConditions(array: JSONArray?): List<MessageMatchCondition> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val keyword = item.optString(KEY_KEYWORD)
                if (keyword.isBlank()) continue
                add(
                    MessageMatchCondition(
                        field = MessageMatchField.fromId(item.optString(KEY_FIELD)),
                        type = MessageMatchType.fromId(item.optString(KEY_TYPE)),
                        keyword = keyword,
                    ),
                )
            }
        }
    }
}
