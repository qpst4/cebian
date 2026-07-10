package com.slideindex.app.notification

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationFilterRepositoryResultTest {

    @Test
    fun upsertRuleSuspend_returnsSuccessAndPersistsRule() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = NotificationFilterRepository(
            context = context,
            listenerPort = NoOpNotificationListenerPort,
            shadeActions = NoOpNotificationShadeActions,
        )
        delay(200)

        val rule = NotificationFilterRule(
            id = "rule-upsert",
            name = "Hide test",
            enabled = true,
            userCreated = true,
            appMode = AppMatchMode.INCLUDE,
            appTargets = listOf(AppTarget("com.test.app")),
            textMode = TextMatchMode.CONTAIN_ANY,
            keywords = listOf("验证码"),
            actionEntries = listOf(RuleActionEntry(type = NotificationRuleActionType.HIDE)),
        )

        val result = repository.upsertRuleSuspend(rule)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.rules.value.size)
        assertEquals("rule-upsert", repository.rules.value.first().id)
    }

    @Test
    fun removeRuleSuspend_returnsSuccessAndRemovesRule() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val existing = NotificationFilterRule(
            id = "rule-remove",
            name = "To remove",
            enabled = true,
            userCreated = true,
            appMode = AppMatchMode.INCLUDE,
            appTargets = listOf(AppTarget("com.test.app")),
            textMode = TextMatchMode.CONTAIN_ANY,
            keywords = listOf("OTP"),
            actionEntries = listOf(RuleActionEntry(type = NotificationRuleActionType.HIDE)),
        )
        val repository = NotificationFilterRepository(
            context = context,
            listenerPort = NoOpNotificationListenerPort,
            shadeActions = NoOpNotificationShadeActions,
        )
        repository.upsertRuleSuspend(existing)
        delay(200)
        assertEquals(1, repository.rules.value.size)

        val result = repository.removeRuleSuspend("rule-remove")

        assertTrue(result.isSuccess)
        assertTrue(repository.rules.value.isEmpty())
    }

    private object NoOpNotificationListenerPort : NotificationListenerPort {
        override fun listenerOrNull(): NotificationListenerService? = null
    }

    private object NoOpNotificationShadeActions : NotificationShadeActions {
        override fun hideFromShade(listener: NotificationListenerService, sbn: StatusBarNotification): Boolean =
            false

        override fun hideFromShadeOnMain(listener: NotificationListenerService, sbn: StatusBarNotification) = Unit

        override fun hideFromShade(
            listener: NotificationListenerService,
            key: String,
            sbn: StatusBarNotification?,
        ): Boolean = false

        override fun snoozeMatchingActive(
            context: Context,
            listener: NotificationListenerService,
            shouldHide: (StatusBarNotification) -> Boolean,
        ) = Unit

        override fun executeRules(
            context: Context,
            listener: NotificationListenerService,
            sbn: StatusBarNotification,
            rules: List<NotificationFilterRule>,
        ) = Unit

        override fun hideNotificationByKey(key: String): Boolean = false

        override fun restoreAllSnoozed(selfPackage: String): List<String> = emptyList()

        override fun unsnoozeNotification(key: String): Boolean = false
    }
}
