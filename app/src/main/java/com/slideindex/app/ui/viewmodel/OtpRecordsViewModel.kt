package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.otp.OtpRecord
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OtpRecordsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    private val otpRecordsRepository: OtpRecordsRepository,
    private val appRepository: AppRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    val records: StateFlow<List<OtpRecord>> = otpRecordsRepository.records
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        refreshRecords()
    }

    /**
     * 强制读一次盘。
     *
     * 记录状态是跨进程写的，进程之间只靠一条写盘广播通知重新读盘，而那条广播发给的是
     * 动态注册的接收者 —— 对方没活着 / 还没注册就丢了且不补发。所以打开记录页、回到前台时
     * 要主动刷一次，顺带让"过期还在填充中"的记录被超时兜底结掉。
     */
    fun refreshRecords() {
        viewModelScope.launch {
            runCatching { otpRecordsRepository.refreshFromDisk() }
        }
    }

    fun deleteRecord(id: String) = launchRepositoryWrite {
        otpRecordsRepository.delete(id)
    }

    fun getCachedAppInfo(packageName: String): AppInfo? =
        appRepository.getCachedAppInfo(packageName)

    fun ensureAppInfo(packageName: String): AppInfo? =
        appRepository.ensureAppInfo(packageName)

    fun loadApps() {
        viewModelScope.launch {
            runCatching { appRepository.loadApps() }.onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
        }
    }
}
