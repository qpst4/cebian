package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.clearTestSettings
import com.slideindex.app.settings.seedShakeSensitivityMigrationFlags
import com.slideindex.app.settings.testSettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShakeHubViewModelTest : ViewModelCoroutineTest() {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        // 先标记灵敏度已迁移，否则全新 store 读到的默认值是迁移后的 13.6667，
        // 本用例「与 AppSettings() 默认一致」的断言就会随执行顺序时好时坏。
        seedShakeSensitivityMigrationFlags(context)
        repository = testSettingsRepository(context)
    }

    @Test
    fun settings_initialValue_matchesAppDefaults() = runViewModelTest {
        val viewModel = newViewModel()
        assertEquals(
            AppSettings().shakeGestureSettings,
            viewModel.settings.value.shakeGestureSettings,
        )
    }

    @Test
    fun setEnabled_persistsToSettingsFlow() = runViewModelTest {
        // viewModelScope 在构造时就会捕获 Dispatchers.Main，因此必须在 setMain 之后再构造 ViewModel，
        // 否则写盘协程会被投递到 Robolectric 暂停的主 looper 上而永远不执行。
        val viewModel = newViewModel()
        primeSettingsFlow(repository)
        viewModel.setEnabled(true)

        // Robolectric 主 looper 默认暂停，写盘完成后的分发要显式 idle 才能推进；
        // 这里交替 idle + 真实时间片轮询，超时即失败（避免主线程 runBlocking 挂住整个 JVM）。
        val enabled = withTimeout(5_000) {
            var current = repository.readSnapshot().shakeGestureSettings.enabled
            while (!current) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                current = repository.readSnapshot().shakeGestureSettings.enabled
            }
            current
        }
        assertTrue(enabled)
    }

    private fun newViewModel() = ShakeHubViewModel(
        settingsRepository = repository,
        userMessageBus = UserMessageBus(),
        context = context,
    )
}
