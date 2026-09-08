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

    fun currentLanguage(): AppUiLanguage = lastApplied

    fun apply(context: Context, language: AppUiLanguage, refreshOverlays: Boolean = false) {
        val changed = language != lastApplied
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
        if (refreshOverlays && changed) {
            com.slideindex.app.overlay.OverlayLocaleCoordinator.onApplicationLocaleChanged(context)
        }
    }

    fun wrapContextIfNeeded(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        return wrapOverlayContext(base)
    }

    /**
     * 无障碍浮窗经 [android.content.Context.createWindowContext] 后常仍按系统语言读资源；
     * 对非 SYSTEM 的应用内语言显式包一层 Configuration。
     */
    fun wrapOverlayContext(base: Context): Context {
        val locale = resolveAppLocale() ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    private fun resolveAppLocale(): Locale? {
        val tags = lastApplied.toLanguageTags() ?: return null
        return Locale.forLanguageTag(tags)
    }
}
