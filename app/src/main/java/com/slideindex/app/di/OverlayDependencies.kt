package com.slideindex.app.di

import com.slideindex.app.data.AppRepository
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.WidgetPanelPersistence

/** Dependencies required by accessibility-hosted overlay windows. */
interface OverlayDependencies {
    val settingsRepository: SettingsRepository
    val appRepository: AppRepository
    val notificationListenerPort: NotificationListenerPort
    val notificationIntentLaunchPort: NotificationIntentLaunchPort
    val widgetPanelPersistence: WidgetPanelPersistence
}
