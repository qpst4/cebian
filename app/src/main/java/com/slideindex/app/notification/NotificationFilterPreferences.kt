package com.slideindex.app.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.notificationFilterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_filter_preferences",
)

class NotificationFilterPreferences(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var cachedSettings: NotificationFilterSettings = NotificationFilterSettings()

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Flow<NotificationFilterSettings> = appContext.notificationFilterDataStore.data.map { prefs ->
        NotificationFilterSettings(
            notificationHistoryMaxCount = (prefs[NOTIFICATION_HISTORY_MAX_COUNT]
                ?: NotificationFilterSettings.DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT)
                .coerceIn(
                    NotificationFilterSettings.MIN_NOTIFICATION_HISTORY_MAX_COUNT,
                    NotificationFilterSettings.MAX_NOTIFICATION_HISTORY_MAX_COUNT,
                ),
        )
    }

    init {
        cacheScope.launch {
            settings.collect { cachedSettings = it }
        }
    }

    suspend fun setNotificationHistoryMaxCount(count: Int) {
        val clamped = count.coerceIn(
            NotificationFilterSettings.MIN_NOTIFICATION_HISTORY_MAX_COUNT,
            NotificationFilterSettings.MAX_NOTIFICATION_HISTORY_MAX_COUNT,
        )
        appContext.notificationFilterDataStore.edit { prefs ->
            prefs[NOTIFICATION_HISTORY_MAX_COUNT] = clamped
        }
    }

    fun readSnapshot(): NotificationFilterSettings = cachedSettings

    companion object {
        const val DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT =
            NotificationFilterSettings.DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT
        const val MIN_NOTIFICATION_HISTORY_MAX_COUNT =
            NotificationFilterSettings.MIN_NOTIFICATION_HISTORY_MAX_COUNT
        const val MAX_NOTIFICATION_HISTORY_MAX_COUNT =
            NotificationFilterSettings.MAX_NOTIFICATION_HISTORY_MAX_COUNT
        const val NOTIFICATION_HISTORY_MAX_COUNT_STEP = 50

        private val NOTIFICATION_HISTORY_MAX_COUNT = intPreferencesKey("notification_history_max_count")
    }
}
