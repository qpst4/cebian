package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.shake.ShakeGestureType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingsMutatorsTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(RuntimeEnvironment.getApplication())
    }

    private suspend fun awaitSettings(predicate: (AppSettings) -> Boolean = { true }): AppSettings =
        withTimeout(5_000) {
            repository.settings.first(predicate)
        }

    @Test
    fun otpKeywordsRegex_blankFallsBackToDefault() = runBlocking {
        repository.setOtpKeywordsRegex("")

        assertEquals(OtpKeywords.DEFAULT_KEYWORDS_REGEX, awaitSettings { it.otpKeywordsRegex == OtpKeywords.DEFAULT_KEYWORDS_REGEX }.otpKeywordsRegex)
    }

    @Test
    fun otpAutoInputDelayMs_clampsToRange() = runBlocking {
        repository.setOtpAutoInputDelayMs(-100)
        assertEquals(0, awaitSettings { it.otpAutoInputDelayMs == 0 }.otpAutoInputDelayMs)

        repository.setOtpAutoInputDelayMs(9_999)
        assertEquals(5_000, awaitSettings { it.otpAutoInputDelayMs == 5_000 }.otpAutoInputDelayMs)
    }

    @Test
    fun otpAutoInputIntervalMs_clampsToRange() = runBlocking {
        repository.setOtpAutoInputIntervalMs(-1)
        assertEquals(0, awaitSettings { it.otpAutoInputIntervalMs == 0 }.otpAutoInputIntervalMs)

        repository.setOtpAutoInputIntervalMs(1_000)
        assertEquals(500, awaitSettings { it.otpAutoInputIntervalMs == 500 }.otpAutoInputIntervalMs)
    }

    @Test
    fun otpOfficialRuleEnabled_togglesDisabledSet() = runBlocking {
        repository.setOtpOfficialRuleEnabled("rule-a", enabled = false)
        assertTrue(awaitSettings { it.otpDisabledOfficialRuleIds.contains("rule-a") }.otpDisabledOfficialRuleIds.contains("rule-a"))

        repository.setOtpOfficialRuleEnabled("rule-a", enabled = true)
        assertFalse(awaitSettings { !it.otpDisabledOfficialRuleIds.contains("rule-a") }.otpDisabledOfficialRuleIds.contains("rule-a"))
    }

    @Test
    fun triggerEdgeWidthDp_clampsToTwelveToThirtySix() = runBlocking {
        repository.setTriggerEdgeWidthDp(PanelSide.LEFT, TriggerHandle.DEFAULT_ID, 5f)
        val left = awaitSettings {
            it.leftTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }?.edgeWidthDp == 12f
        }.leftTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }
        assertEquals(12f, left.edgeWidthDp)

        repository.setTriggerEdgeWidthDp(PanelSide.RIGHT, TriggerHandle.DEFAULT_ID, 99f)
        val right = awaitSettings {
            it.rightTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }?.edgeWidthDp == 36f
        }.rightTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }
        assertEquals(36f, right.edgeWidthDp)
    }

    @Test
    fun triggerEdgeWidthDp_mirrorsOppositeSideWhenAligned() = runBlocking {
        repository.setTriggerEdgeWidthDp(PanelSide.LEFT, TriggerHandle.DEFAULT_ID, 24f)

        val snapshot = awaitSettings {
            val left = it.leftTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }
            val right = it.rightTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }
            left?.edgeWidthDp == 24f && right?.edgeWidthDp == 24f
        }
        assertEquals(24f, snapshot.leftTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }.edgeWidthDp)
        assertEquals(24f, snapshot.rightTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }.edgeWidthDp)
    }

    @Test
    fun triggerVerticalRange_swapsInvertedBoundsAndMirrorsOppositeSide() = runBlocking {
        repository.setTriggerVerticalRange(
            side = PanelSide.LEFT,
            handleId = TriggerHandle.DEFAULT_ID,
            topFraction = 0.70f,
            bottomFraction = 0.20f,
        )

        val snapshot = awaitSettings {
            val left = it.leftTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }
            left != null && kotlin.math.abs(left.topFraction - 0.20f) < 0.001f
        }
        val left = snapshot.leftTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }
        val right = snapshot.rightTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }

        assertEquals(0.20f, left.topFraction, 0.001f)
        assertEquals(0.50f, left.heightFraction, 0.001f)
        assertEquals(left.topFraction, right.topFraction, 0.001f)
        assertEquals(left.heightFraction, right.heightFraction, 0.001f)
    }

    @Test
    fun shakeDirectionSensitivity_clampsBetweenOneAndTen() = runBlocking {
        repository.setShakeDirectionSensitivity(ShakeGestureType.LEFT_FLIP, 0f)
        assertEquals(
            1f,
            awaitSettings {
                it.shakeGestureSettings.perDirectionSensitivity[ShakeGestureType.LEFT_FLIP] == 1f
            }.shakeGestureSettings.perDirectionSensitivity.getValue(ShakeGestureType.LEFT_FLIP),
        )

        repository.setShakeDirectionSensitivity(ShakeGestureType.LEFT_FLIP, 99f)
        assertEquals(
            10f,
            awaitSettings {
                it.shakeGestureSettings.perDirectionSensitivity[ShakeGestureType.LEFT_FLIP] == 10f
            }.shakeGestureSettings.perDirectionSensitivity.getValue(ShakeGestureType.LEFT_FLIP),
        )
    }

    @Test
    fun perAppShakeConfig_addAndRemoveRoundTrip() = runBlocking {
        repository.addPerAppShakeConfig("com.example.app")
        assertTrue("com.example.app" in awaitSettings { "com.example.app" in it.shakeGestureSettings.perAppActions }.shakeGestureSettings.perAppActions)

        repository.removePerAppShakeConfig("com.example.app")
        assertFalse("com.example.app" in awaitSettings { "com.example.app" !in it.shakeGestureSettings.perAppActions }.shakeGestureSettings.perAppActions)
    }

    @Test
    fun addFloatingPointerEdgeBarSlot_appendsZones() = runBlocking {
        val initial = awaitSettings().floatingPointerEdgeActionsConfig.top.layoutSlots().size
        repeat(3) {
            repository.addFloatingPointerEdgeBarSlot(FloatingPointerEdgeSide.TOP)
        }
        val updated = awaitSettings {
            it.floatingPointerEdgeActionsConfig.top.layoutSlots().size == initial + 3
        }.floatingPointerEdgeActionsConfig.top.layoutSlots().size
        assertEquals(initial + 3, updated)
    }

    @Test
    fun readSnapshot_reflectsLatestFlowEmission() = runBlocking {
        repository.setServiceEnabled(true)
        awaitSettings { it.serviceEnabled }
        repository.setShakeGesturesEnabled(true)
        awaitSettings { it.shakeGestureSettings.enabled }

        val snapshot = repository.readSnapshot()
        assertTrue(snapshot.serviceEnabled)
        assertTrue(snapshot.shakeGestureSettings.enabled)
    }
}

private val testSettingsLock = Any()

internal fun testSettingsRepository(context: Context): SettingsRepository = synchronized(testSettingsLock) {
    val editor = SettingsPreferencesEditor(context)
    return SettingsRepository(
        editor = editor,
        backupManager = SettingsBackupManager(context, editor),
        edge = EdgeSettingsMutator(editor),
        overlay = OverlaySettingsMutator(editor),
        shake = ShakeSettingsMutator(editor),
        message = MessageSettingsMutator(editor),
        otp = OtpSettingsMutator(editor),
    )
}
