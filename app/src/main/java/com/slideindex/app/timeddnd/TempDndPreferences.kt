package com.slideindex.app.timeddnd

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tempDndDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "timed_dnd_state",
)

internal object TempDndPreferences {
    const val NO_FILTER_SNAPSHOT = Int.MIN_VALUE

    private val keyOriginalFilter = intPreferencesKey("original_filter")
    private val keyRuleId = stringPreferencesKey("temp_dnd_rule_id")
    private val keyScheduledMinutes = intPreferencesKey("scheduled_minutes")

    suspend fun readOriginalFilter(context: Context): Int {
        val prefs = context.applicationContext.tempDndDataStore.data.first()
        return prefs[keyOriginalFilter] ?: NO_FILTER_SNAPSHOT
    }

    suspend fun writeOriginalFilter(context: Context, filter: Int) {
        context.applicationContext.tempDndDataStore.edit { prefs ->
            prefs[keyOriginalFilter] = filter
        }
    }

    suspend fun clearOriginalFilter(context: Context) {
        context.applicationContext.tempDndDataStore.edit { prefs ->
            prefs.remove(keyOriginalFilter)
        }
    }

    suspend fun readRuleId(context: Context): String {
        val prefs = context.applicationContext.tempDndDataStore.data.first()
        return prefs[keyRuleId] ?: ""
    }

    suspend fun writeRuleId(context: Context, ruleId: String) {
        context.applicationContext.tempDndDataStore.edit { prefs ->
            prefs[keyRuleId] = ruleId
        }
    }

    suspend fun readScheduledMinutes(context: Context): Int? {
        val prefs = context.applicationContext.tempDndDataStore.data.first()
        val minutes = prefs[keyScheduledMinutes] ?: return null
        return minutes.takeIf { it > 0 }
    }

    suspend fun writeScheduledMinutes(context: Context, minutes: Int) {
        context.applicationContext.tempDndDataStore.edit { prefs ->
            prefs[keyScheduledMinutes] = minutes
        }
    }

    suspend fun clearScheduledMinutes(context: Context) {
        context.applicationContext.tempDndDataStore.edit { prefs ->
            prefs.remove(keyScheduledMinutes)
        }
    }

    suspend fun isSessionActive(context: Context): Boolean =
        context.applicationContext.tempDndDataStore.data.map { prefs ->
            prefs[keyOriginalFilter] != null
        }.first()
}
