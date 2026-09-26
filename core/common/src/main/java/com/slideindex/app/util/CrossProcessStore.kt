package com.slideindex.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 文件型存储的跨进程写入工具。
 *
 * 这些仓库（通知历史、暂存、OTP 记录、搜索/Shell 历史、统计、过滤规则）都是
 * 「读文件 → 改内存 → 写回」的 JSON 存储，原先只有进程内 [kotlinx.coroutines.sync.Mutex]：
 * 进程拆开后两个进程同时改就会互相覆盖（丢数据）。
 *
 * 用法：
 * 1. 变更走 [mutate]（或 [withFileLock]）：**在跨进程文件锁内重新读盘**再计算，最后原子写回；
 * 2. 写完后调用 [notifyChanged]，另一个进程通过 [registerListener] 收到通知并重载缓存。
 */
object CrossProcessStore {
    private const val TAG = "CrossProcessStore"
    private const val ACTION = "com.slideindex.app.action.STORE_CHANGED"
    private const val EXTRA_PATH = "store_path"
    private const val EXTRA_FROM = "store_from_process"

    private var receiver: BroadcastReceiver? = null

    /** 重载缓存用；放在工具里，调用方不必自带 scope。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 同一进程内按文件路径串行化。
     *
     * `FileChannel.lock()` 的重叠检测是**进程级**的：同一 JVM 内两个线程同时对同一文件加锁会抛
     * [java.nio.channels.OverlappingFileLockException]，而不是阻塞等待。所以文件锁之前必须先拿进程内锁。
     */
    private val localLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private fun localLockFor(file: File): kotlinx.coroutines.sync.Mutex =
        localLocks.computeIfAbsent(file.canonicalPathOrAbsolute()) { kotlinx.coroutines.sync.Mutex() }

    /**
     * 读-改-写整套在跨进程文件锁内完成。[read] 必须重新从磁盘读取（不要用内存缓存），
     * [write] 需要是原子写（临时文件 + rename，或覆盖写后 fsync）。
     */
    suspend fun <T> mutate(
        file: File,
        read: () -> T,
        write: (T) -> Unit,
        transform: (T) -> T,
    ): T = localLockFor(file).withLock {
        withContext(Dispatchers.IO) {
            withStoreFileLock(file) {
                val current = read()
                val next = transform(current)
                write(next)
                next
            }
        }
    }

    /**
     * 需要在锁内做更复杂的操作时使用（调用方自己保证锁内重新读盘）。
     * block 允许挂起：仓库内部的读写都是 `withContext(IO)`，嵌套不会破锁。
     */
    suspend fun <T> withFileLock(file: File, block: suspend () -> T): T = localLockFor(file).withLock {
        withContext(Dispatchers.IO) {
            withStoreFileLock(file) { block() }
        }
    }

    /**
     * 跨进程文件锁（带重试）。
     *
     * 以前直接调 `channel.lock()`：它在「同一 JVM 内已经有线程**阻塞在 lock() 里**」或「本 JVM
     * 已持有重叠锁」时会抛 [java.nio.channels.OverlappingFileLockException]，而不是排队等待。
     * 该异常发生在用户协程里没人接，会直接打死整个进程（真机：通知历史 updateCapture 写盘 →
     * OverlappingFileLockException → `:overlay` 进程死亡 → 悬浮球与手势整片消失）。
     *
     * 改成 `tryLock()` 轮询：拿不到锁就等一会儿再试，连重叠异常也一起吞掉重试；
     * 连续失败很多次之后退化成「只进程内互斥」，**保证调用方不会因为锁而崩溃**。
     */
    private suspend fun <T> withStoreFileLock(file: File, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            val holder = LockHolder<T>()
            var acquired = false
            val outcome = runCatching {
                lockOf(file).use { handle ->
                    val lock = try {
                        handle.channel.tryLock()
                    } catch (overlap: java.nio.channels.OverlappingFileLockException) {
                        Log.w(TAG, "overlapping file lock on ${file.name}, retrying", overlap)
                        null
                    }
                    if (lock == null) return@use
                    acquired = true
                    lock.use { holder.value = block() }
                }
            }
            if (acquired && outcome.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                return holder.value as T
            }
            val error = outcome.exceptionOrNull()
            // 锁已经拿到、业务代码自己抛异常：原样往上抛（不能吞掉调用方的错误）。
            if (acquired && error != null) throw error
            attempt++
            if (attempt >= MAX_LOCK_ATTEMPTS) {
                Log.w(
                    TAG,
                    "file lock unavailable after $attempt attempts for ${file.name}; " +
                        "continuing with in-process lock only",
                    error,
                )
                return block()
            }
            delay(LOCK_RETRY_DELAY_MS)
        }
    }

    private class LockHolder<T> {
        var value: T? = null
    }

    fun notifyChanged(context: Context?, file: File) {
        val ctx = context?.applicationContext ?: return
        val intent = Intent(ACTION).apply {
            setPackage(ctx.packageName)
            putExtra(EXTRA_PATH, file.absolutePath)
            putExtra(EXTRA_FROM, currentProcessName())
        }
        runCatching { ctx.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "notifyChanged(${file.name}) failed", it) }
    }

    /** 只注册一次；收到其它进程的写通知后按路径分发。 */
    fun registerListener(context: Context, onChanged: (File) -> Unit) {
        if (receiver != null) return
        val ctx = context.applicationContext
        val listener = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION) return
                // 自己写的不用重载。
                if (intent.getStringExtra(EXTRA_FROM) == currentProcessName()) return
                val path = intent.getStringExtra(EXTRA_PATH) ?: return
                runCatching { onChanged(File(path)) }
                    .onFailure { Log.w(TAG, "onChanged($path) failed", it) }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(ctx, listener, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = listener
        }.onFailure { Log.w(TAG, "registerListener failed", it) }
    }

    /** 只关心某个文件时的重载写法（回调在后台线程执行，可直接做磁盘读取）。 */
    fun registerListener(context: Context, file: File, onChanged: suspend () -> Unit) {
        registerListener(context) { changed ->
            if (changed.absolutePath == file.absolutePath) {
                scope.launch { onChanged() }
            }
        }
    }

    private fun lockOf(file: File): RandomAccessFile {
        val lockFile = File(file.parentFile ?: file, "${file.name}.lock")
        lockFile.parentFile?.mkdirs()
        return RandomAccessFile(lockFile, "rw")
    }

    private fun File.canonicalPathOrAbsolute(): String =
        runCatching { canonicalPath }.getOrElse { absolutePath }

    private const val LOCK_RETRY_DELAY_MS = 8L
    /** 轮询上限：约 12 秒。超过才退化到「只进程内互斥」，避免真的丢写。 */
    private const val MAX_LOCK_ATTEMPTS = 1_500

    private fun currentProcessName(): String =
        runCatching { android.app.Application.getProcessName() }.getOrNull().orEmpty()

    /**
     * 与 [kotlinx.coroutines.sync.Mutex] 同形的包装：进程内互斥 + 跨进程文件锁，
     * 并在临界区结束后广播一次变更通知。这样仓库里既有的 `mutex.withLock { }`
     * 调用点（含 `return@withLock`）一行都不用改就获得跨进程安全。
     */
    class CrossProcessMutex(
        context: Context,
        private val file: File,
    ) {
        private val appContext = context.applicationContext
        private val local = kotlinx.coroutines.sync.Mutex()

        suspend fun <T> withLock(block: suspend () -> T): T = local.withLock {
            withFileLock(file) {
                val result = block()
                notifyChanged(appContext, file)
                result
            }
        }
    }
}
