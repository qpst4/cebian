package com.slideindex.app.util

import android.content.Context

/**
 * Synchronous mirror of [com.slideindex.app.settings.AppSettings.serviceEnabled] for QS tile
 * toggles. DataStore reads can take seconds on cold wake; this file is read with [commit] on write.
 */
object ServiceEnabledStore {
    private const val PREFS_NAME = "gesture_service_enabled_mirror"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    @Volatile
    private var memoryCache: Boolean? = null

    fun read(context: Context): Boolean {
        memoryCache?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SERVICE_ENABLED)) {
            return false
        }
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false).also { memoryCache = it }
    }

    fun hasPersistedValue(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SERVICE_ENABLED)

    fun write(context: Context, enabled: Boolean) {
        memoryCache = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .commit()
    }
}
