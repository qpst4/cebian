package com.slideindex.app.otp

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtpOfficialRulesLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
