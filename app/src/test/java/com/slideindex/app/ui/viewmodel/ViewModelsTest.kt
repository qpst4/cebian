package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.slideindex.app.data.AppLaunchPort
import com.slideindex.app.data.AppRepository
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryLaunchPort
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.notification.NotificationShadeActions
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.clearTestSettings
import com.slideindex.app.settings.testSettingsRepository
import com.slideindex.app.ui.feedback.UserMessage
import com.slideindex.app.ui.feedback.UserMessageBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeViewModelTest : ViewModelCoroutineTest() {
    private lateinit var context: Context
    private lateinit var repository: com.slideindex.app.settings.SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        repository = testSettingsRepository(context)
    }

    @Test
    fun settings_initialValue_matchesDefaults() {
        val viewModel = HomeViewModel(
            repository,
            UserMessageBus(),
            context,
            object : HomeScreenEffects {
                override fun refreshServiceState() = Unit
                override fun requestNotificationPermission() = Unit
                override fun requestShizuku() = Unit
                override fun openAccessibilitySettings() = Unit
                override fun previewHaptic(enabled: Boolean, strengthLevel: Int?) = Unit
            },
        )

        assertFalse(viewModel.settings.value.serviceEnabled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class KeepAliveSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = KeepAliveSettingsViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
            object : KeepAliveScreenEffects {
                override fun applyHideFromRecents(enabled: Boolean) = Unit
                override fun onAccessibilityKeepAliveEnabled() = Unit
            },
        )

        assertFalse(viewModel.settings.value.hideFromRecents)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ExtensionHubViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = ExtensionHubViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
        )

        assertFalse(viewModel.settings.value.serviceEnabled)
        assertEquals(AppSettings().quickLauncherColumnsPerPage, viewModel.settings.value.quickLauncherColumnsPerPage)
    }

    @Test
    fun settings_reflectRepositoryUpdates() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = ExtensionHubViewModel(repository, UserMessageBus(), context)
        val settingsCollector = launch { viewModel.settings.collect { } }
        primeSettingsFlow(repository)

        repository.setServiceEnabled(true)

        assertTrue(awaitSettings(repository) { it.serviceEnabled }.serviceEnabled)
        assertTrue(viewModel.settings.first { it.serviceEnabled }.serviceEnabled)
        settingsCollector.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ExtensionSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = ExtensionSettingsViewModel(repository, UserMessageBus(), context)

        assertEquals(AppSettings().quickLauncherColumnsPerPage, viewModel.settings.value.quickLauncherColumnsPerPage)
    }

    @Test
    fun setQuickLauncherColumnsPerPage_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = ExtensionSettingsViewModel(repository, UserMessageBus(), context)
        primeSettingsFlow(repository)

        viewModel.setQuickLauncherColumnsPerPage(5)

        assertEquals(5, awaitSettings(repository) { it.quickLauncherColumnsPerPage == 5 }.quickLauncherColumnsPerPage)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeDetailSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = HomeDetailSettingsViewModel(repository, UserMessageBus(), context)

        assertEquals(AppSettings().panelOpacity, viewModel.settings.value.panelOpacity)
    }

    @Test
    fun setPanelOpacity_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = HomeDetailSettingsViewModel(repository, UserMessageBus(), context)
        primeSettingsFlow(repository)

        viewModel.setPanelOpacity(0.72f)

        assertEquals(
            0.72f,
            awaitSettings(repository) { kotlin.math.abs(it.panelOpacity - 0.72f) < 0.001f }.panelOpacity,
            0.001f,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MessageSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = MessageSettingsViewModel(repository, UserMessageBus(), context)

        assertFalse(viewModel.settings.value.messageReminderSettings.enabled)
    }

    @Test
    fun setMessageReminderEnabled_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = MessageSettingsViewModel(repository, UserMessageBus(), context)
        primeSettingsFlow(repository)

        viewModel.setMessageReminderEnabled(true)

        assertTrue(awaitSettings(repository) { it.messageReminderSettings.enabled }.messageReminderSettings.enabled)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OtpSettingsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun refreshOfficialRules_reloadsBundledRules() {
        val context = RuntimeEnvironment.getApplication()
        val loader = OtpOfficialRulesLoader(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = loader,
            otpRecordsRepository = OtpRecordsRepository(context),
        )

        viewModel.refreshOfficialRules()

        assertTrue(viewModel.officialRules.value.isNotEmpty())
    }

    @Test
    fun settings_initialValue_matchesDefaults() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = OtpRecordsRepository(context),
        )

        assertFalse(viewModel.settings.value.otpAutoInputEnabled)
    }

    @Test
    fun setOtpAutoInputEnabled_persistsToSettingsFlow() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = repository,
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = OtpRecordsRepository(context),
        )
        primeSettingsFlow(repository)

        viewModel.setOtpAutoInputEnabled(true)

        assertTrue(awaitSettings(repository) { it.otpAutoInputEnabled }.otpAutoInputEnabled)
    }

    @Test
    fun recordTestOtp_persistsRecord() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val recordsRepository = OtpRecordsRepository(context)
        val viewModel = OtpSettingsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = recordsRepository,
        )
        delay(200)

        viewModel.recordTestOtp(code = "654321", sampleText = "code 654321", ruleName = "test-rule")
        delay(200)

        assertEquals(1, recordsRepository.records.value.size)
        assertEquals("654321", recordsRepository.records.value.first().code)
        assertTrue(recordsRepository.records.value.first().isTest)
    }

    @Test
    fun recordTestOtp_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = OtpSettingsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = bus,
            context,
            otpOfficialRulesLoader = OtpOfficialRulesLoader(context),
            otpRecordsRepository = OtpRecordsRepository(context),
        )
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }
        context.filesDir.setWritable(false)

        try {
            viewModel.recordTestOtp(code = "000000", sampleText = "code", ruleName = null)
            delay(200)
            assertTrue(message.await().text.isNotEmpty())
        } finally {
            context.filesDir.setWritable(true)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OtpRecordsViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun records_initialValue_isEmpty() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpRecordsRepository = OtpRecordsRepository(context),
            appRepository = AppRepository(
                context = context,
                appLaunchPort = NoOpAppLaunchPort,
            ),
        )

        assertTrue(viewModel.records.value.isEmpty())
    }

    @Test
    fun deleteRecord_removesFromRepository() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        repository.recordSuspend(
            code = "654321",
            packageName = "com.test.app",
            title = "Test",
            text = "code 654321",
            isTest = true,
        )
        val recordId = repository.records.first { it.isNotEmpty() }.first().id

        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            otpRecordsRepository = repository,
            appRepository = AppRepository(context, NoOpAppLaunchPort),
        )

        viewModel.deleteRecord(recordId)
        delay(200)

        assertTrue(repository.records.value.none { it.id == recordId })
    }

    @Test
    fun deleteRecord_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val repository = OtpRecordsRepository(context)
        repository.recordSuspend(
            code = "111111",
            packageName = "com.test.app",
            title = "Test",
            text = "code 111111",
            isTest = true,
        )
        val recordId = repository.records.first { it.isNotEmpty() }.first().id
        val bus = UserMessageBus()
        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = bus,
            context,
            otpRecordsRepository = repository,
            appRepository = AppRepository(context, NoOpAppLaunchPort),
        )
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }
        context.filesDir.setWritable(false)

        try {
            viewModel.deleteRecord(recordId)
            delay(200)
            assertTrue(message.await().text.isNotEmpty())
        } finally {
            context.filesDir.setWritable(true)
        }
    }

    @Test
    fun loadApps_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = OtpRecordsViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = bus,
            context,
            otpRecordsRepository = OtpRecordsRepository(context),
            appRepository = AppRepository(ThrowingPackageManagerContext(context), NoOpAppLaunchPort),
        )
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }

        viewModel.loadApps()
        delay(100)

        assertTrue(message.await().text.isNotEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationHubViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun visibleHistoryCount_startsAtZero() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = NotificationHubViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            notificationHistoryRepository = notificationHistoryRepository(context),
            notificationFilterRepository = notificationFilterRepository(context),
        )

        assertEquals(0, viewModel.visibleHistoryCount.value)
    }

    @Test
    fun visibleHistoryCount_reflectsRecordedItems() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val historyRepository = notificationHistoryRepository(context)
        val viewModel = NotificationHubViewModel(
            settingsRepository = testSettingsRepository(context),
            userMessageBus = UserMessageBus(),
            context,
            notificationHistoryRepository = historyRepository,
            notificationFilterRepository = notificationFilterRepository(context),
        )
        val countCollector = launch { viewModel.visibleHistoryCount.collect { } }
        delay(100)

        historyRepository.record(
            NotificationHistoryItem(
                id = "hub-item-1",
                packageName = "com.test",
                title = "Title",
                text = "Body",
                postedAtMs = 1L,
                intentUri = null,
                notificationKey = "key-1",
            ),
        )

        assertEquals(1, viewModel.visibleHistoryCount.first { it == 1 })
        countCollector.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingsViewModelWriteTest : ViewModelCoroutineTest() {
    @Test
    fun launchSettingsWrite_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = TestSettingsViewModel(testSettingsRepository(context), bus, context)
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }

        viewModel.triggerFailingWrite()

        assertTrue(message.await().text.isNotEmpty())
    }

    @Test
    fun launchRepositoryWrite_onFailure_emitsError() = runViewModelTest {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = TestSettingsViewModel(testSettingsRepository(context), bus, context)
        val message = async(Dispatchers.Unconfined) { awaitUserError(bus) }

        viewModel.triggerFailingRepositoryWrite()

        assertTrue(message.await().text.isNotEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationHistoryViewModelTest : ViewModelCoroutineTest() {
    @Test
    fun hideNotification_whenListenerDisabled_emitsError() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bus = UserMessageBus()
        val viewModel = notificationHistoryViewModel(context, bus)
        val item = NotificationHistoryItem(
            id = "item-1",
            packageName = "com.test",
            title = "Title",
            text = "Body",
            postedAtMs = 1L,
            intentUri = null,
            notificationKey = null,
        )

        val message = async(Dispatchers.Unconfined) { bus.messages.first() }
        viewModel.hideNotification(item, listenerEnabled = false)
        assertTrue(message.await() is UserMessage.Error)
    }

    @Test
    fun restoreAllSnoozed_whenListenerDisabled_returnsNegativeOne() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = notificationHistoryViewModel(context, UserMessageBus())

        val result = viewModel.restoreAllSnoozed(listenerEnabled = false)

        assertEquals(-1, result)
    }

    @Test
    fun refreshActive_incrementsGeneration() {
        val context = RuntimeEnvironment.getApplication()
        val viewModel = notificationHistoryViewModel(context, UserMessageBus())
        val before = viewModel.refreshGeneration.value

        viewModel.refreshActive()

        assertEquals(before + 1, viewModel.refreshGeneration.value)
    }
}

private fun notificationHistoryViewModel(context: Context, bus: UserMessageBus): NotificationHistoryViewModel =
    NotificationHistoryViewModel(
        settingsRepository = testSettingsRepository(context),
        userMessageBus = bus,
        context = context,
        notificationHistoryRepository = notificationHistoryRepository(context),
        notificationFilterRepository = notificationFilterRepository(context),
        notificationFilterPreferences = NotificationFilterPreferences(context),
        appRepository = AppRepository(context, NoOpAppLaunchPort),
    )

private fun notificationHistoryRepository(context: Context): NotificationHistoryRepository =
    NotificationHistoryRepository(
        context = context,
        filterPreferences = NotificationFilterPreferences(context),
        listenerPort = NoOpNotificationListenerPort,
        shadeActions = NoOpNotificationShadeActions,
        launchPort = NoOpNotificationHistoryLaunchPort,
    )

private fun notificationFilterRepository(context: Context): NotificationFilterRepository =
    NotificationFilterRepository(
        context = context,
        listenerPort = NoOpNotificationListenerPort,
        shadeActions = NoOpNotificationShadeActions,
    )

private object NoOpAppLaunchPort : AppLaunchPort {
    override fun launch(intent: Intent, settings: AppSettings, fullscreen: Boolean) = Unit
}

private object NoOpNotificationListenerPort : NotificationListenerPort {
    override fun listenerOrNull() = null
}

private object NoOpNotificationShadeActions : NotificationShadeActions {
    override fun hideFromShade(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = false

    override fun hideFromShadeOnMain(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = Unit

    override fun cancelDismissibleFromShade(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = false

    override fun cancelDismissibleFromShadeOnMain(
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
    ) = Unit

    override fun hideFromShade(
        listener: android.service.notification.NotificationListenerService,
        key: String,
        sbn: android.service.notification.StatusBarNotification?,
    ) = false

    override fun snoozeMatchingActive(
        context: Context,
        listener: android.service.notification.NotificationListenerService,
        shouldHide: (android.service.notification.StatusBarNotification) -> Boolean,
    ) = Unit

    override fun executeRules(
        context: Context,
        listener: android.service.notification.NotificationListenerService,
        sbn: android.service.notification.StatusBarNotification,
        rules: List<com.slideindex.app.notification.NotificationFilterRule>,
    ) = Unit

    override fun hideNotificationByKey(key: String) = false

    override fun restoreAllSnoozed(selfPackage: String) = emptyList<String>()

    override fun unsnoozeNotification(key: String) = false
}

private object NoOpNotificationHistoryLaunchPort : NotificationHistoryLaunchPort {
    override fun startPendingIntentTrampoline(pendingIntentBase64: String, fallbackIntent: Intent?) = false

    override fun launchReplayIntent(intent: Intent, packageName: String, extrasBase64: String?) = false
}

private class TestSettingsViewModel(
    settingsRepository: com.slideindex.app.settings.SettingsRepository,
    userMessageBus: UserMessageBus,
    context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun triggerFailingWrite() = launchSettingsWrite { Result.failure(IOException("settings failed")) }

    fun triggerFailingRepositoryWrite() = launchRepositoryWrite { Result.failure(IOException("repo failed")) }
}

private class ThrowingPackageManagerContext(
    base: Context,
) : android.content.ContextWrapper(base) {
    override fun getPackageManager(): android.content.pm.PackageManager {
        throw IOException("package manager failed")
    }
}
