package com.slideindex.app.di

import android.content.Context
import android.content.Intent
import com.slideindex.app.data.AppLaunchPort
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.notification.NotificationShadeActions
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationHider
import com.slideindex.app.notification.AppNotificationIntentLaunchPort
import com.slideindex.app.notification.AppNotificationOtpSideEffects
import com.slideindex.app.notification.NotificationHistoryLaunchPort
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationOtpSideEffects
import com.slideindex.app.notification.NotificationRuleExecutor
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.service.MediaNotificationListener
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.FreeWindowLauncher
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaNotificationListenerPort @Inject constructor() : NotificationListenerPort {
    override fun listenerOrNull(): NotificationListenerService? = MediaNotificationListener.instance
}

@Singleton
class AppNotificationShadeActions @Inject constructor() : NotificationShadeActions {
    override fun hideFromShade(listener: NotificationListenerService, sbn: StatusBarNotification): Boolean =
        NotificationHider.hideFromShade(listener, sbn)

    override fun hideFromShadeOnMain(listener: NotificationListenerService, sbn: StatusBarNotification) {
        NotificationHider.hideFromShadeOnMain(listener, sbn)
    }

    override fun hideFromShade(
        listener: NotificationListenerService,
        key: String,
        sbn: StatusBarNotification?,
    ): Boolean = NotificationHider.hideFromShade(listener, key, sbn)

    override fun snoozeMatchingActive(
        context: Context,
        listener: NotificationListenerService,
        shouldHide: (StatusBarNotification) -> Boolean,
    ) {
        listener.activeNotifications?.forEach { sbn ->
            if (sbn.packageName == context.packageName) return@forEach
            if (shouldHide(sbn)) {
                NotificationHider.hideFromShade(listener, sbn)
            }
        }
    }

    override fun executeRules(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
        rules: List<NotificationFilterRule>,
    ) {
        NotificationRuleExecutor.execute(context, listener, sbn, rules)
    }

    override fun hideNotificationByKey(key: String): Boolean = NotificationHider.hideNotification(key)

    override fun restoreAllSnoozed(selfPackage: String): List<String> =
        NotificationHider.restoreAllSnoozed(selfPackage)

    override fun unsnoozeNotification(key: String): Boolean = NotificationHider.unsnoozeNotification(key)
}

@Singleton
class AppNotificationHistoryLaunchPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationHistoryLaunchPort {
    override fun startPendingIntentTrampoline(pendingIntentBase64: String, fallbackIntent: Intent?): Boolean =
        runCatching {
            context.startActivity(
                LaunchTrampolineActivity.createPendingIntentIntent(
                    context = context,
                    pendingIntentBase64 = pendingIntentBase64,
                    fallbackIntent = fallbackIntent,
                ),
            )
        }.isSuccess

    override fun launchReplayIntent(intent: Intent, packageName: String, extrasBase64: String?): Boolean {
        val trampoline = LaunchTrampolineActivity.createIntent(context, intent)
        if (runCatching { context.startActivity(trampoline) }.isSuccess) return true
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

@Singleton
class FreeWindowAppLaunchPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLaunchPort {
    override fun launch(intent: Intent, settings: AppSettings, fullscreen: Boolean) {
        FreeWindowLauncher.launch(context, intent, settings, fullscreen)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppPortsModule {
    @Binds
    @Singleton
    abstract fun bindNotificationListenerPort(impl: MediaNotificationListenerPort): NotificationListenerPort

    @Binds
    @Singleton
    abstract fun bindNotificationShadeActions(impl: AppNotificationShadeActions): NotificationShadeActions

    @Binds
    @Singleton
    abstract fun bindAppLaunchPort(impl: FreeWindowAppLaunchPort): AppLaunchPort

    @Binds
    @Singleton
    abstract fun bindNotificationHistoryLaunchPort(
        impl: AppNotificationHistoryLaunchPort,
    ): NotificationHistoryLaunchPort

    @Binds
    @Singleton
    abstract fun bindNotificationOtpSideEffects(
        impl: AppNotificationOtpSideEffects,
    ): NotificationOtpSideEffects

    @Binds
    @Singleton
    abstract fun bindNotificationIntentLaunchPort(
        impl: AppNotificationIntentLaunchPort,
    ): NotificationIntentLaunchPort
}
