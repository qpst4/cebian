package com.slideindex.app.update

import com.slideindex.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val store: UpdatePreferencesStore,
) {
    private companion object {
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val ATTEMPT_TTL_MS = 60 * 1000L
        private const val FAILED_RETRY_BACKOFF_MS = 30 * 60 * 1000L
        private const val FIRST_BACKGROUND_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    sealed interface CheckResult {
        data object Skipped : CheckResult
        data object Failed : CheckResult
        data class RateLimited(val resetEpochSeconds: Long) : CheckResult
        data class NoApk(val version: String) : CheckResult
        data class NewVersion(val state: UpdateState) : CheckResult
        data class UpToDate(val state: UpdateState) : CheckResult
    }

    suspend fun shouldCheck(): Boolean {
        val state = store.read().state
        val now = System.currentTimeMillis()
        if (now < state.nextRetryTime) return false
        if (state.lastCheckSuccessTime > 0L) {
            return now - state.lastCheckSuccessTime >= CHECK_INTERVAL_MS
        }
        return now - state.lastCheckAttemptTime >= FIRST_BACKGROUND_INTERVAL_MS
    }

    suspend fun checkAndCache(force: Boolean): CheckResult {
        val current = store.read()
        val now = System.currentTimeMillis()
        if (!force && now - current.state.lastCheckAttemptTime < ATTEMPT_TTL_MS) {
            return CheckResult.Skipped
        }
        store.updateState { it.copy(lastCheckAttemptTime = now) }

        val manifest = when (val result = UpdateChecker.fetchLatestManifest()) {
            is UpdateChecker.FetchResult.Success -> result.manifest
            UpdateChecker.FetchResult.Failed -> {
                setNextRetry(now + FAILED_RETRY_BACKOFF_MS)
                if (!force &&
                    current.state.latestVersion.isNotBlank() &&
                    current.state.apkUrl.isNotBlank() &&
                    UpdateChecker.isRemoteNewer(current.state.latestVersion, BuildConfig.VERSION_NAME)
                ) {
                    return CheckResult.NewVersion(current.state)
                }
                return CheckResult.Failed
            }
        }

        val versionTag = manifest.version
        val isNewer = UpdateChecker.isRemoteNewer(versionTag, BuildConfig.VERSION_NAME)
        val hasApk = manifest.apkUrl.isNotBlank() && manifest.apkSize > 0L

        if (isNewer && !hasApk) {
            setNextRetry(0L)
            return CheckResult.NoApk(versionTag)
        }

        val newState = store.updateState {
            it.copy(
                latestVersion = versionTag,
                notes = manifest.notes,
                apkUrl = manifest.apkUrl,
                apkSize = manifest.apkSize,
                lastCheckSuccessTime = System.currentTimeMillis(),
                nextRetryTime = 0L,
            )
        }
        return if (isNewer) {
            CheckResult.NewVersion(newState)
        } else {
            CheckResult.UpToDate(newState)
        }
    }

    private suspend fun setNextRetry(time: Long) {
        store.updateState { it.copy(nextRetryTime = time) }
    }
}
