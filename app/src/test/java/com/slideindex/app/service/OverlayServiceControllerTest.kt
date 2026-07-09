package com.slideindex.app.service

import androidx.compose.runtime.mutableStateOf
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.navigation.NavPermissionStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OverlayServiceControllerTest {

    @Test
    fun sendPreviewIntent_noOpWhenAccessibilityDisabled() {
        val app = RuntimeEnvironment.getApplication()
        val controller = controller(app, accessibilityGranted = false)

        controller.sendPreviewIntent(OverlayService.ACTION_PREVIEW_START)

        val started = Shadows.shadowOf(app).nextStartedService
        assertFalse(started?.action == OverlayService.ACTION_PREVIEW_START)
    }

    @Test
    fun sendPreviewIntent_startsServiceWhenAccessibilityGranted() {
        val app = RuntimeEnvironment.getApplication()
        val controller = controller(app, accessibilityGranted = true)

        controller.sendPreviewIntent(
            OverlayService.ACTION_PREVIEW_START,
        )

        val started = Shadows.shadowOf(app).nextStartedService
        assertTrue(started?.action == OverlayService.ACTION_PREVIEW_START)
    }

    @Test
    fun refreshPermissionState_reflectsRuntimePermissionSnapshot() {
        val app = RuntimeEnvironment.getApplication()
        val states = NavPermissionStates(
            notificationGranted = mutableStateOf(false),
            usageAccessGranted = mutableStateOf(false),
            shizukuGranted = mutableStateOf(false),
            accessibilityGranted = mutableStateOf(false),
            batteryOptimizationExempt = mutableStateOf(false),
            writeSecureSettingsGranted = mutableStateOf(false),
            notificationListenerEnabled = mutableStateOf(false),
        )
        val controller = OverlayServiceController(
            context = app,
            permissionStates = states,
            scope = CoroutineScope(Dispatchers.Unconfined),
            settingsRepository = SettingsRepository(app),
        )

        controller.refreshPermissionState()

        assertTrue(states.notificationGranted.value)
    }

    private fun controller(
        context: android.content.Context,
        accessibilityGranted: Boolean,
    ): OverlayServiceController =
        OverlayServiceController(
            context = context,
            permissionStates = NavPermissionStates(
                notificationGranted = mutableStateOf(true),
                usageAccessGranted = mutableStateOf(false),
                shizukuGranted = mutableStateOf(false),
                accessibilityGranted = mutableStateOf(accessibilityGranted),
                batteryOptimizationExempt = mutableStateOf(false),
                writeSecureSettingsGranted = mutableStateOf(false),
                notificationListenerEnabled = mutableStateOf(false),
            ),
            scope = CoroutineScope(Dispatchers.Unconfined),
            settingsRepository = SettingsRepository(context),
        )
}
