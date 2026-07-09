package com.slideindex.app.notification

import android.content.Context
import com.slideindex.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationRuleUiStrings @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationRuleUiStrings {
    override val callNotifyChannelName: String
        get() = context.getString(R.string.notification_rule_action_call)

    override val callNotifyFallbackTitle: String
        get() = context.getString(R.string.notification_rule_action_call)
}
