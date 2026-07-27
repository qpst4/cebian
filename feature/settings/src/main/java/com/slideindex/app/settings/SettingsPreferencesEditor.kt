package com.slideindex.app.settings



import android.content.Context

import androidx.datastore.core.DataStore

import androidx.datastore.preferences.core.MutablePreferences

import androidx.datastore.preferences.core.Preferences

import androidx.datastore.preferences.core.edit

import androidx.datastore.preferences.preferencesDataStore

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

import javax.inject.Singleton

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map



private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "slide_index_settings")



@Singleton

class SettingsPreferencesEditor @Inject constructor(

    @ApplicationContext private val context: Context,

) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        SettingsSnapshotReader.read(prefs)
    }

    val themeSettings: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
        )
    }

    val appRootSettings: Flow<AppRootSettings> = context.dataStore.data.map { prefs ->
        AppRootSettings(
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            onboardingCompleted = prefs[SettingsPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            hideFromRecents = prefs[SettingsPreferenceKeys.HIDE_FROM_RECENTS] ?: false,
        )
    }

    val gestureSettings: Flow<GestureSettings> = context.dataStore.data
        .map { prefs -> GestureSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val overlaySettings: Flow<OverlaySettings> = context.dataStore.data
        .map { prefs -> OverlaySettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()



    suspend fun edit(block: (MutablePreferences) -> Unit): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            block(prefs)
        }
    }

    suspend fun readRawPreferences(): Preferences = context.dataStore.data.first()
}


