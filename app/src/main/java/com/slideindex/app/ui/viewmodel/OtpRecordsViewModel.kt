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
