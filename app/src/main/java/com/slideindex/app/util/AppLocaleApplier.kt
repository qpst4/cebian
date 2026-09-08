package com.slideindex.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.slideindex.app.settings.AppUiLanguage
import java.util.Locale

object AppLocaleApplier {
    @Volatile
    private var lastApplied: AppUiLanguage = AppUiLanguage.SYSTEM

    fun apply(context: Context, language: AppUiLanguage) {
        lastApplied = language
        val tags = language.toLanguageTags()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.applicationContext.getSystemService(
                android.app.LocaleManager::class.java,
            )
            localeManager.applicationLocales = if (tags == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(tags)
            }
        } else {
            val locales = if (tags == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tags)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun wrapContextIfNeeded(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val locale = resolveLocaleForLegacy() ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun resolveLocaleForLegacy(): Locale? {
        val tags = lastApplied.toLanguageTags() ?: return null
        return Locale.forLanguageTag(tags)
    }
}
