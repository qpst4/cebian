package com.slideindex.app.di

import com.slideindex.app.data.AppRepository
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryRecorder
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.WidgetPanelPersistence
import com.slideindex.app.ui.feedback.UserMessageBus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Singleton
class AppDependencies @Inject constructor(
    override val appRepository: AppRepository,
    override val settingsRepository: SettingsRepository,
    val notificationHistoryRepository: NotificationHistoryRepository,
    val notificationHistoryRecorder: NotificationHistoryRecorder,
    val notificationFilterPreferences: NotificationFilterPreferences,
    val notificationFilterRepository: NotificationFilterRepository,
    override val notificationIntentLaunchPort: NotificationIntentLaunchPort,
    override val notificationListenerPort: NotificationListenerPort,
    val otpOfficialRulesLoader: OtpOfficialRulesLoader,
    val otpRecordsRepository: OtpRecordsRepository,
    val userMessageBus: UserMessageBus,
    val applicationScope: CoroutineScope,
    override val widgetPanelPersistence: WidgetPanelPersistence,
) : OverlayDependencies
