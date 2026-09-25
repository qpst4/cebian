package com.slideindex.app.otp

import android.content.Context
import com.slideindex.app.common.repositoryRunCatching
import com.slideindex.app.otp.OtpRecordCategory
import com.slideindex.app.otp.OtpRecordLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtpRecordsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val recordsFile = File(appContext.filesDir, RECORDS_FILE_NAME)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _records = MutableStateFlow<List<OtpRecord>>(emptyList())
    val records: StateFlow<List<OtpRecord>> = _records.asStateFlow()

    @Volatile
    private var limits: Map<OtpRecordCategory, Int> = DEFAULT_LIMITS

    /**
     * 由 App 侧设置同步器推入每类记录条数上限；0 表示该类不记录。
     *
     * 仓库位于 `:feature:otp`，不依赖 `:feature:settings`，因此上限通过推入方式下发。
     */
    fun setCategoryLimits(codeLimit: Int, plainSmsLimit: Int, appNotifyLimit: Int) {
        limits = mapOf(
            OtpRecordCategory.CODE to OtpRecordLimits.normalize(codeLimit),
            OtpRecordCategory.PLAIN_SMS to OtpRecordLimits.normalize(plainSmsLimit),
            OtpRecordCategory.APP_NOTIFY to OtpRecordLimits.normalize(appNotifyLimit),
            OtpRecordCategory.TEST to OtpRecordLimits.normalize(codeLimit),
        )
    }

    init {
        scope.launch {
            mutex.withLock {
                val loaded = readFromDisk()
                val trimmed = trimByCategory(loaded)
                if (trimmed.size != loaded.size) {
                    runCatching { writeToDisk(trimmed) }
                }
                _records.value = trimmed
            }
        }
    }

    fun record(
        code: String,
        packageName: String,
        title: String,
        text: String,
        timestampMs: Long = System.currentTimeMillis(),
        ruleName: String? = null,
        isTest: Boolean = false,
        autoFillStatus: OtpRecordFillStatus = OtpRecordFillStatus.NONE,
        category: OtpRecordCategory = if (isTest) OtpRecordCategory.TEST else OtpRecordCategory.CODE,
        simSlot: Int = -1,
    ) {
        scope.launch {
            recordSuspend(
                code = code,
                packageName = packageName,
                title = title,
                text = text,
                timestampMs = timestampMs,
                ruleName = ruleName,
                isTest = isTest,
                autoFillStatus = autoFillStatus,
                category = category,
                simSlot = simSlot,
            )
        }
    }

    suspend fun recordSuspend(
        code: String,
        packageName: String,
        title: String,
        text: String,
        timestampMs: Long = System.currentTimeMillis(),
        ruleName: String? = null,
        isTest: Boolean = false,
        autoFillStatus: OtpRecordFillStatus = OtpRecordFillStatus.NONE,
        category: OtpRecordCategory = if (isTest) OtpRecordCategory.TEST else OtpRecordCategory.CODE,
        simSlot: Int = -1,
    ): Result<String?> = mutex.withLock {
        repositoryRunCatching {
            val limit = limits[category] ?: OtpRecordLimits.DEFAULT
            if (limit <= 0) return@repositoryRunCatching null
            val current = readFromDisk()
            val duplicate = current.firstOrNull { existing ->
                existing.category == category &&
                    existing.code == code &&
                    (category != OtpRecordCategory.PLAIN_SMS || existing.text == text) &&
                    (timestampMs - existing.timestampMs) in 0..DEDUPE_WINDOW_MS
            }
            if (duplicate != null) return@repositoryRunCatching duplicate.id

            val entry = OtpRecord(
                code = code,
                packageName = packageName,
                title = title,
                text = text,
                timestampMs = timestampMs,
                ruleName = ruleName,
                isTest = isTest,
                autoFillStatus = autoFillStatus,
                category = category,
                simSlot = simSlot,
            )
            val trimmed = trimByCategory(listOf(entry) + current)
            writeToDisk(trimmed)
            _records.value = trimmed
            entry.id
        }
    }

    suspend fun updateAutoFillOutcome(
        id: String,
        success: Boolean,
        strategy: String,
        reason: String = "",
    ): Result<Unit> = mutex.withLock {
        repositoryRunCatching {
            val status = OtpRecordFillStatus.fromFillResult(success, strategy)
            val fillReason = if (!success) reason.takeIf { it.isNotBlank() } else null
            val next = readFromDisk().map { record ->
                if (record.id == id) {
                    record.copy(autoFillStatus = status, autoFillReason = fillReason)
                } else {
                    record
                }
            }
            writeToDisk(next)
            _records.value = next
        }
    }

    suspend fun delete(id: String): Result<Unit> = mutex.withLock {
        repositoryRunCatching {
            val next = readFromDisk().filterNot { it.id == id }
            writeToDisk(next)
            _records.value = next
        }
    }

    suspend fun exportRawJson(): String? = mutex.withLock {
        if (!recordsFile.exists()) return@withLock null
        withContext(Dispatchers.IO) { recordsFile.readText() }
    }

    suspend fun importRawJson(json: String): Result<Unit> = mutex.withLock {
        repositoryRunCatching {
            val decoded = OtpRecordCodec.decode(json)
            val trimmed = decoded.take(MAX_RECORDS)
            writeToDisk(trimmed)
            _records.value = trimmed
        }
    }

    suspend fun clearAll(): Result<Unit> = mutex.withLock {
        repositoryRunCatching {
            writeToDisk(emptyList())
            _records.value = emptyList()
        }
    }

    private suspend fun readFromDisk(): List<OtpRecord> = withContext(Dispatchers.IO) {
        if (!recordsFile.exists()) return@withContext emptyList()
        runCatching {
            OtpRecordCodec.decode(recordsFile.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToDisk(items: List<OtpRecord>) = withContext(Dispatchers.IO) {
        recordsFile.writeText(OtpRecordCodec.encode(items))
    }

    /** 按分类各自保留最新 [limits] 条，再套一层总上限。 */
    private fun trimByCategory(items: List<OtpRecord>): List<OtpRecord> {
        val counts = mutableMapOf<OtpRecordCategory, Int>()
        val kept = items.filter { item ->
            val limit = limits[item.category] ?: OtpRecordLimits.DEFAULT
            if (limit <= 0) return@filter false
            val seen = counts[item.category] ?: 0
            if (seen >= limit) return@filter false
            counts[item.category] = seen + 1
            true
        }
        return kept.take(MAX_RECORDS)
    }

    companion object {
        private const val RECORDS_FILE_NAME = "otp_records.json"
        const val MAX_RECORDS = 200
        const val DEDUPE_WINDOW_MS = 60_000L
        private val DEFAULT_LIMITS = mapOf(
            OtpRecordCategory.CODE to OtpRecordLimits.DEFAULT,
            OtpRecordCategory.PLAIN_SMS to OtpRecordLimits.DEFAULT,
            OtpRecordCategory.APP_NOTIFY to OtpRecordLimits.DEFAULT,
            OtpRecordCategory.TEST to OtpRecordLimits.DEFAULT,
        )
    }
}
