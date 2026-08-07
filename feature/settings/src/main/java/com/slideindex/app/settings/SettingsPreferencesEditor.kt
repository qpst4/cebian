package com.slideindex.app.settings



import android.content.Context

import androidx.datastore.core.DataStore

import androidx.datastore.preferences.core.MutablePreferences

import androidx.datastore.preferences.core.Preferences

import androidx.datastore.preferences.core.edit

import androidx.datastore.preferences.preferencesDataStore

import com.slideindex.app.message.MessageSettings

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
            themePaletteStyleId = prefs[SettingsPreferenceKeys.THEME_PALETTE_STYLE]
                ?: ThemePaletteStyle.TONAL_SPOT.id,
        )
    }.distinctUntilChanged()

    val appRootSettings: Flow<AppRootSettings> = context.dataStore.data.map { prefs ->
        AppRootSettings(
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            themePaletteStyleId = prefs[SettingsPreferenceKeys.THEME_PALETTE_STYLE]
                ?: ThemePaletteStyle.TONAL_SPOT.id,
            onboardingCompleted = prefs[SettingsPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            hideFromRecents = prefs[SettingsPreferenceKeys.HIDE_FROM_RECENTS] ?: false,
            predictiveBackEnabled = prefs[SettingsPreferenceKeys.PREDICTIVE_BACK_ENABLED] ?: false,
        )
    }.distinctUntilChanged()

    val gestureSettings: Flow<GestureSettings> = context.dataStore.data
        .map { prefs -> GestureSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val overlaySettings: Flow<OverlaySettings> = context.dataStore.data
        .map { prefs -> OverlaySettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val homeMainSettings: Flow<HomeMainSettings> = context.dataStore.data
        .map { prefs -> HomeMainSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val extensionHubSettings: Flow<ExtensionHubSettings> = context.dataStore.data
        .map { prefs -> ExtensionHubSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val keepAliveUiSettings: Flow<KeepAliveUiSettings> = context.dataStore.data
        .map { prefs -> KeepAliveUiSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val shakeUiSettings: Flow<ShakeUiSettings> = context.dataStore.data
        .map { prefs -> ShakeUiSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val freeWindowUiSettings: Flow<FreeWindowUiSettings> = context.dataStore.data
        .map { prefs -> FreeWindowUiSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val otpUiSettings: Flow<OtpUiSettings> = context.dataStore.data
        .map { prefs -> OtpUiSettings.from(SettingsSnapshotReader.read(prefs)) }
        .distinctUntilChanged()

    val messageReminderSettings: Flow<MessageSettings> = context.dataStore.data
        .map { prefs -> SettingsSnapshotReader.read(prefs).messageReminderSettings }
        .distinctUntilChanged()



    suspend fun edit(block: (MutablePreferences) -> Unit): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            block(prefs)
        }
    }

    suspend fun readRawPreferences(): Preferences = context.dataStore.data.first()
}


