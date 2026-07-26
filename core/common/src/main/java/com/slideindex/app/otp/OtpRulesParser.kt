package com.slideindex.app.otp

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import org.json.JSONObject

object OtpRulesParser {
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
