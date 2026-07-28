package com.slideindex.app.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.updatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_update",
)

@Singleton
class UpdatePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val preferences: Flow<UpdatePreferences> = context.updatePreferencesDataStore.data.map { prefs ->
        val raw = prefs[PREF_KEY] ?: return@map UpdatePreferences()
        runCatching { json.decodeFromString<UpdatePreferences>(raw) }.getOrDefault(UpdatePreferences())
    }

    suspend fun read(): UpdatePreferences = preferences.first()

    suspend fun update(transform: (UpdatePreferences) -> UpdatePreferences): UpdatePreferences {
        var result = UpdatePreferences()
        context.updatePreferencesDataStore.edit { prefs ->
            val current = prefs[PREF_KEY]?.let { raw ->
                runCatching { json.decodeFromString<UpdatePreferences>(raw) }.getOrDefault(UpdatePreferences())
            } ?: UpdatePreferences()
            result = transform(current)
            prefs[PREF_KEY] = json.encodeToString(result)
        }
        return result
    }

    suspend fun updateState(transform: (UpdateState) -> UpdateState): UpdateState {
        var result = UpdateState()
        update { prefs ->
            result = transform(prefs.state)
            prefs.copy(state = result)
        }
        return result
    }

    private companion object {
        private val PREF_KEY = stringPreferencesKey("update_prefs_json")
    }
}
