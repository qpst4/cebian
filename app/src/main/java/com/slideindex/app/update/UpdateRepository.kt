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
        private const val FAILED_RETRY_BACKOFF_MS = 2 * 60 * 60 * 1000L
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
        if (now - state.lastCheckSuccessTime < CHECK_INTERVAL_MS) return false
        return now >= state.nextRetryTime
    }

    suspend fun checkAndCache(force: Boolean): CheckResult {
        val current = store.read()
        val now = System.currentTimeMillis()
        if (!force && now - current.state.lastCheckAttemptTime < ATTEMPT_TTL_MS) {
            return CheckResult.Skipped
        }
        store.updateState { it.copy(lastCheckAttemptTime = now) }

        val release = when (val result = UpdateChecker.fetchLatestRelease()) {
            is UpdateChecker.FetchResult.Success -> result.release
            is UpdateChecker.FetchResult.RateLimited -> {
                setNextRetry(now + FAILED_RETRY_BACKOFF_MS)
                return CheckResult.RateLimited(result.resetEpochSeconds)
            }
            UpdateChecker.FetchResult.Failed -> {
                setNextRetry(now + FAILED_RETRY_BACKOFF_MS)
                return CheckResult.Failed
            }
        }
        val asset = UpdateChecker.pickApkAsset(release)
        val isNewer = UpdateChecker.isRemoteNewer(release.tagName, BuildConfig.VERSION_NAME)
        val hasApk = asset != null && asset.size > 0

        if (isNewer && !hasApk) {
            setNextRetry(0L)
            return CheckResult.NoApk(release.tagName)
        }

        val newState = store.updateState {
            it.copy(
                latestVersion = release.tagName,
                notes = release.body.ifBlank { release.name },
                apkUrl = asset?.browserDownloadUrl.orEmpty(),
                apkSize = asset?.size ?: 0L,
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
