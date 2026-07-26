package com.slideindex.app.otp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OtpAutoFillStats(
    val totalAttempts: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastAttemptAtEpochMs: Long? = null,
    val lastSuccess: Boolean? = null,
    val lastStrategy: String? = null,
    val lastReason: String? = null,
) {
    val successRatePercent: Int
        get() = if (totalAttempts <= 0) {
            0
        } else {
            ((successCount * 100f) / totalAttempts).toInt()
        }
}

@Singleton
class OtpAutoFillStatsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val statsFile = File(appContext.filesDir, STATS_FILE_NAME)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _stats = MutableStateFlow(OtpAutoFillStats())
    val stats: StateFlow<OtpAutoFillStats> = _stats.asStateFlow()

    init {
        _stats.value = readFromDiskSync()
    }

    suspend fun recordAttempt(
        success: Boolean,
        strategy: String,
        reason: String = "",
    ) {
        mutex.withLock {
            val current = readFromDisk()
            val updated = current.copy(
                totalAttempts = current.totalAttempts + 1,
                successCount = current.successCount + if (success) 1 else 0,
                failureCount = current.failureCount + if (success) 0 else 1,
                lastAttemptAtEpochMs = System.currentTimeMillis(),
                lastSuccess = success,
                lastStrategy = strategy,
                lastReason = reason.takeIf { it.isNotBlank() },
            )
            writeToDisk(updated)
            _stats.value = updated
        }
    }

    suspend fun reset() {
        mutex.withLock {
            val cleared = OtpAutoFillStats()
            writeToDisk(cleared)
            _stats.value = cleared
        }
    }

    suspend fun exportRawJson(): String? = mutex.withLock {
        if (!statsFile.exists()) return@withLock null
        withContext(Dispatchers.IO) { statsFile.readText() }
    }

    suspend fun importRawJson(raw: String): Result<Unit> = mutex.withLock {
        runCatching {
            val decoded = json.decodeFromString<OtpAutoFillStats>(raw)
            writeToDisk(decoded)
            _stats.value = decoded
        }
    }

    suspend fun reloadFromDisk() {
        mutex.withLock {
            _stats.value = readFromDiskSync()
        }
    }

    private fun readFromDiskSync(): OtpAutoFillStats = runCatching {
        if (!statsFile.exists()) return OtpAutoFillStats()
        json.decodeFromString<OtpAutoFillStats>(statsFile.readText())
    }.getOrDefault(OtpAutoFillStats())

    private suspend fun readFromDisk(): OtpAutoFillStats = withContext(Dispatchers.IO) {
        readFromDiskSync()
    }

    private suspend fun writeToDisk(stats: OtpAutoFillStats) = withContext(Dispatchers.IO) {
        statsFile.writeText(json.encodeToString(stats))
    }

    companion object {
        private const val STATS_FILE_NAME = "otp_autofill_stats.json"
    }
}
