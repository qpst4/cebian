package com.slideindex.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityComposeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        ComposeTestSupport.markOnboardingCompleted(context)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.recreate()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun launchesHomeBottomNavigation() {
        composeRule.onNodeWithText(context.getString(R.string.main_nav_home)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.main_nav_shake)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.main_nav_notification)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.main_nav_extension)).assertIsDisplayed()
    }

    @Test
    fun navigatesAcrossAllRootTabs() {
        composeRule.onNodeWithText(context.getString(R.string.main_nav_shake)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.shake_gestures_title)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.main_nav_notification)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.main_nav_notification)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.main_nav_extension)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_backup_entry_title)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.main_nav_home)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.main_settings_subtitle)).assertIsDisplayed()
    }

    @Test
    fun opensSettingsBackupScreen() {
        composeRule.onNodeWithText(context.getString(R.string.main_nav_extension)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_backup_entry_title)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_backup_export)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_backup_import)).assertIsDisplayed()
    }

    @Test
    fun homeTab_matchesGoldenScreenshot() {
        ScreenshotGolden.assertMatchesGolden(composeRule, "home_tab")
    }
}
