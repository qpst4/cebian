package com.slideindex.app.ui.viewmodel

import android.os.Looper
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessage
import com.slideindex.app.ui.feedback.UserMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows

/**
 * 当前 [runViewModelTest] 给 `Dispatchers.Main` 安装的测试调度器。
 *
 * `viewModelScope` 上的 `debounce(...)` 等延迟流用的是**虚拟时间**，测试不主动推进就永远不会发射，
 * 阻塞在主线程 `runBlocking` 里会让整个测试 JVM 挂住。需要等待这类流的用例请调用
 * [advanceMainVirtualTime]。
 */
private var mainTestScheduler: TestCoroutineScheduler? = null

@OptIn(ExperimentalCoroutinesApi::class)
internal fun runViewModelTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    val scheduler = TestCoroutineScheduler()
    mainTestScheduler = scheduler
    Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    try {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        block()
    } finally {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
        mainTestScheduler = null
    }
}

/** 推进 `Dispatchers.Main`（viewModelScope）的虚拟时间。 */
internal fun advanceMainVirtualTime(delayMs: Long) {
    mainTestScheduler?.advanceTimeBy(delayMs)
}

internal suspend fun primeSettingsFlow(repository: SettingsRepository) {
    repository.settings.first()
}

internal suspend fun awaitSettings(
    repository: SettingsRepository,
    predicate: (AppSettings) -> Boolean = { true },
): AppSettings =
    withTimeout(5_000) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        repository.settings.first(predicate)
    }

abstract class ViewModelCoroutineTest

internal suspend fun awaitUserError(bus: UserMessageBus): UserMessage.Error {
    val message = bus.messages.first()
    require(message is UserMessage.Error) { "Expected error message but got $message" }
    return message
}
