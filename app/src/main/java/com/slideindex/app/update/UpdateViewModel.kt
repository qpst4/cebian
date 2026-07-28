package com.slideindex.app.update

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UpdateRepository,
    private val preferencesStore: UpdatePreferencesStore,
    private val userMessageBus: UserMessageBus,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var installPromptDismissed = false

    init {
        viewModelScope.launch {
            var prevStatus: DownloadController.DownloadStatus? = null
            DownloadController.flow.collect { download ->
                val justEnded = prevStatus == DownloadController.DownloadStatus.DOWNLOADING &&
                    download.status != DownloadController.DownloadStatus.DOWNLOADING
                prevStatus = download.status
                recompute(if (justEnded) OpenMode.DownloadEnded else OpenMode.None)
            }
        }
        viewModelScope.launch {
            preferencesStore.preferences.collect { recompute(OpenMode.None) }
        }
    }

    fun checkOnLaunch() {
        viewModelScope.launch {
            val prefs = preferencesStore.read()
            val now = System.currentTimeMillis()
            if (prefs.autoCheckUpdate &&
                now - prefs.state.lastCheckSuccessTime >= LAUNCH_FRESH_MS &&
                now >= prefs.state.nextRetryTime
            ) {
                repository.checkAndCache(force = false)
            }
            recompute(OpenMode.Auto)
        }
    }

    fun onEntry() {
        viewModelScope.launch { recompute(OpenMode.Auto) }
    }

    fun onForeground() {
        viewModelScope.launch { recompute(OpenMode.Resume) }
    }

    fun checkManually() {
        viewModelScope.launch {
            if (_uiState.value.checking) return@launch
            _uiState.update { it.copy(checking = true) }
            val result = repository.checkAndCache(force = true)
            _uiState.update { it.copy(checking = false) }
            when (result) {
                UpdateRepository.CheckResult.Failed ->
                    showCheckFailed(CheckFailedReason.Generic)
                is UpdateRepository.CheckResult.RateLimited ->
                    showCheckFailed(CheckFailedReason.RateLimited)
                is UpdateRepository.CheckResult.NoApk ->
                    showCheckFailed(CheckFailedReason.NoApk)
                else -> {
                    if (DownloadController.flow.value.status == DownloadController.DownloadStatus.FAILED) {
                        DownloadController.reset()
                    }
                    recompute(OpenMode.Force)
                }
            }
        }
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch {
            preferencesStore.update { it.copy(autoCheckUpdate = enabled) }
        }
    }

    fun dismissCheckFailedDialog() {
        _uiState.update { it.copy(showCheckFailedDialog = false) }
    }

    fun evaluateNotificationPrompt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        viewModelScope.launch {
            val prefs = preferencesStore.read()
            if (prefs.notificationPermissionRequested || !prefs.autoCheckUpdate) return@launch
            preferencesStore.update { it.copy(notificationPermissionRequested = true) }
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                _uiState.update { it.copy(showNotificationRationale = true) }
            }
        }
    }

    fun dismissNotificationRationale() {
        _uiState.update { it.copy(showNotificationRationale = false) }
    }

    fun onConfirmUpdate() {
        val state = _uiState.value
        if (!ApkInstaller.canInstall(context)) {
            userMessageBus.showError(context.getString(R.string.update_install_permission_hint))
            ApkInstaller.gotoUnknownSourceSetting(context)
            return
        }
        if (state.apkUrl.isBlank()) {
            userMessageBus.showError(context.getString(R.string.update_download_failed))
            return
        }
        installPromptDismissed = false
        DownloadService.start(context, state.version, state.apkUrl, state.apkSize)
    }

    fun onInstall() {
        val state = _uiState.value
        val file = ApkInstaller.apkFile(context, state.version)
        if (!ApkInstaller.isDownloaded(file, state.apkSize)) {
            userMessageBus.showError(context.getString(R.string.update_download_failed))
            onConfirmUpdate()
            return
        }
        if (!ApkInstaller.canInstall(context)) {
            userMessageBus.showError(context.getString(R.string.update_install_permission_hint))
            ApkInstaller.gotoUnknownSourceSetting(context)
            return
        }
        _uiState.update { it.copy(showDialog = false) }
        if (!ApkInstaller.installApk(context, file)) {
            userMessageBus.showError(context.getString(R.string.update_download_failed))
        }
    }

    fun onMoveToBackground() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun onIgnoreVersion() {
        val version = _uiState.value.version
        viewModelScope.launch {
            preferencesStore.update { it.copy(ignoredUpdateVersion = version) }
            _uiState.update { it.copy(showDialog = false) }
        }
    }

    fun dismiss() {
        when (_uiState.value.phase) {
            UpdatePhase.Failed -> DownloadController.reset()
            UpdatePhase.Downloaded -> installPromptDismissed = true
            else -> Unit
        }
        _uiState.update { it.copy(showDialog = false) }
    }

    private fun showCheckFailed(reason: CheckFailedReason) {
        _uiState.update { it.copy(showCheckFailedDialog = true, checkFailedReason = reason) }
    }

    private suspend fun recompute(openMode: OpenMode) {
        val prefs = preferencesStore.read()
        val cache = prefs.state
        val ignored = prefs.ignoredUpdateVersion
        val download = DownloadController.flow.value

        val downloaded = ApkInstaller.isDownloaded(
            ApkInstaller.apkFile(context, cache.latestVersion),
            cache.apkSize,
        )
        val isNewer = cache.latestVersion.isNotBlank() &&
            UpdateChecker.isRemoteNewer(cache.latestVersion, BuildConfig.VERSION_NAME)

        val phase = when {
            download.status == DownloadController.DownloadStatus.DOWNLOADING -> UpdatePhase.Downloading
            download.status == DownloadController.DownloadStatus.FAILED -> UpdatePhase.Failed
            downloaded && isNewer -> UpdatePhase.Downloaded
            isNewer -> UpdatePhase.NewVersion
            else -> UpdatePhase.UpToDate
        }

        val version = if (phase == UpdatePhase.Downloading) download.version else cache.latestVersion

        val show = when (openMode) {
            OpenMode.None -> _uiState.value.showDialog
            OpenMode.Force -> true
            OpenMode.Auto -> when (phase) {
                UpdatePhase.Downloading, UpdatePhase.Downloaded, UpdatePhase.Failed -> true
                UpdatePhase.NewVersion -> cache.latestVersion != ignored
                UpdatePhase.UpToDate -> _uiState.value.showDialog
            }
            OpenMode.Resume -> when (phase) {
                UpdatePhase.Downloaded -> !installPromptDismissed
                UpdatePhase.Failed -> true
                else -> _uiState.value.showDialog
            }
            OpenMode.DownloadEnded -> when (phase) {
                UpdatePhase.Downloaded, UpdatePhase.Failed -> true
                else -> _uiState.value.showDialog
            }
        }

        _uiState.update {
            it.copy(
                showDialog = show,
                phase = phase,
                version = version,
                notes = cache.notes,
                apkUrl = cache.apkUrl,
                apkSize = cache.apkSize,
                progress = download.progress,
                autoCheckUpdate = prefs.autoCheckUpdate,
            )
        }
    }

    private enum class OpenMode {
        None,
        Auto,
        Resume,
        DownloadEnded,
        Force,
    }

    enum class UpdatePhase {
        Downloading,
        Downloaded,
        NewVersion,
        UpToDate,
        Failed,
    }

    enum class CheckFailedReason {
        Generic,
        RateLimited,
        NoApk,
    }

    data class UiState(
        val showDialog: Boolean = false,
        val phase: UpdatePhase = UpdatePhase.NewVersion,
        val version: String = "",
        val notes: String = "",
        val apkUrl: String = "",
        val apkSize: Long = 0L,
        val progress: Int = 0,
        val checking: Boolean = false,
        val showCheckFailedDialog: Boolean = false,
        val checkFailedReason: CheckFailedReason = CheckFailedReason.Generic,
        val showNotificationRationale: Boolean = false,
        val autoCheckUpdate: Boolean = true,
    )

    private companion object {
        const val LAUNCH_FRESH_MS = 60 * 60 * 1000L
    }
}
