package com.slideindex.app.notification

import android.content.Context
import com.slideindex.app.message.NotificationData
import com.slideindex.app.settings.AppSettings

/** Opens notification content / target app; implemented in :app. */
interface NotificationIntentLaunchPort {
    fun open(context: Context, data: NotificationData): Boolean

    fun openInSmallWindow(context: Context, data: NotificationData, settings: AppSettings): Boolean
}
