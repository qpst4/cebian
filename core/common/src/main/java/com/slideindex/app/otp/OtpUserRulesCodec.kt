package com.slideindex.app.otp

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 用户验证码规则表的导入导出格式（JSON）。
 *
 * 只包含用户规则；官方/内置规则随包发布，不参与交换。
 * 解码时对残缺项宽容处理：缺 id 补 UUID、`isOfficial` 一律置 false（导入的永远是用户规则）。
 */
object OtpUserRulesCodec {
    const val FORMAT_VERSION = 1

    fun encode(rules: List<OtpMatchRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("keyword", rule.keyword)
                    .put("regex", rule.regex)
                    .put("packageName", rule.packageName.orEmpty())
                    .put("enabled", rule.enabled),
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("rules", array)
            .toString(2)
    }

    /** 解析失败（不是 JSON、没有 rules 数组）返回 null，供 UI 提示导入失败。 */
    fun decode(raw: String?): List<OtpMatchRule>? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("rules") ?: return null
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val keyword = item.optString("keyword").trim()
                    val regex = item.optString("regex").trim()
                    if (name.isEmpty() || keyword.isEmpty() || regex.isEmpty()) continue
                    add(
                        OtpMatchRule(
                            id = item.optString("id").takeIf { it.isNotBlank() }
                                ?: UUID.randomUUID().toString(),
                            name = name,
                            keyword = keyword,
                            regex = regex,
                            packageName = item.optString("packageName").takeIf { it.isNotBlank() },
                            isOfficial = false,
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrNull()
    }

    /**
     * 合并导入：按 id 去重，再按 名称+关键词+正则 去重（避免同一份规则表反复导入产生重复项）。
     */
    fun merge(existing: List<OtpMatchRule>, imported: List<OtpMatchRule>): List<OtpMatchRule> {
        val ids = existing.map { it.id }.toMutableSet()
        val fingerprints = existing.map { it.fingerprint() }.toMutableSet()
        val merged = existing.toMutableList()
        imported.forEach { rule ->
            if (!ids.add(rule.id)) return@forEach
            if (!fingerprints.add(rule.fingerprint())) return@forEach
            merged += rule
        }
        return merged
    }

    private fun OtpMatchRule.fingerprint(): String =
        "${name.trim()}\u001F${keyword.trim()}\u001F${regex.trim()}\u001F${packageName.orEmpty()}"
}
