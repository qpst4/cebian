package com.slideindex.app.otp

import org.json.JSONObject

internal object OtpRulesParser {
    fun parseRules(jsonText: String): List<OtpMatchRule> {
        val root = JSONObject(jsonText)
        val rulesArray = root.optJSONArray("rules") ?: return emptyList()
        return buildList {
            for (index in 0 until rulesArray.length()) {
                val item = rulesArray.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                val keyword = item.optString("keyword").takeIf { it.isNotBlank() } ?: continue
                val regex = item.optString("regex").takeIf { it.isNotBlank() } ?: continue
                val packageName = item.optString("packageName").takeIf { it.isNotBlank() }
                add(
                    OtpMatchRule(
                        id = id,
                        name = name,
                        keyword = keyword,
                        regex = regex,
                        packageName = packageName,
                        isOfficial = true,
                        enabled = true,
                    ),
                )
            }
        }
    }
}
