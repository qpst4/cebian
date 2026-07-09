package com.slideindex.app.otp

import android.content.Context

class OtpOfficialRulesLoader(private val context: Context) {
    @Volatile
    private var cachedRules: List<OtpMatchRule> = emptyList()

    fun getRules(): List<OtpMatchRule> {
        if (cachedRules.isEmpty()) {
            cachedRules = loadFromAssets()
        }
        return cachedRules
    }

    fun refresh(): List<OtpMatchRule> {
        cachedRules = loadFromAssets()
        return cachedRules
    }

    private fun loadFromAssets(): List<OtpMatchRule> =
        runCatching {
            context.assets.open(ASSET_FILE).bufferedReader().use { reader ->
                OtpRulesParser.parseRules(reader.readText())
            }
        }.getOrElse { emptyList() }

    companion object {
        private const val ASSET_FILE = "smscode-rules.json"
    }
}
