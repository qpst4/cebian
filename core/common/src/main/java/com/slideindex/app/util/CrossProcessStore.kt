package com.slideindex.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
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

    /**
     * 读-改-写整套在跨进程文件锁内完成。[read] 必须重新从磁盘读取（不要用内存缓存），
     * [write] 需要是原子写（临时文件 + rename，或覆盖写后 fsync）。
     */
    suspend fun <T> mutate(
        file: File,
        read: () -> T,
        write: (T) -> Unit,
        transform: (T) -> T,
    ): T = withContext(Dispatchers.IO) {
        lockOf(file).use { lock ->
            lock.channel.lock().use {
                val current = read()
                val next = transform(current)
                write(next)
                next
            }
        }
    }

    /** 需要在锁内做更复杂的操作时使用（调用方自己保证锁内重新读盘）。 */
    suspend fun <T> withFileLock(file: File, block: () -> T): T = withContext(Dispatchers.IO) {
        lockOf(file).use { lock ->
            lock.channel.lock().use { block() }
        }
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

    private fun lockOf(file: File): RandomAccessFile {
        val lockFile = File(file.parentFile ?: file, "${file.name}.lock")
        lockFile.parentFile?.mkdirs()
        return RandomAccessFile(lockFile, "rw")
    }

    private fun currentProcessName(): String =
        runCatching { android.app.Application.getProcessName() }.getOrNull().orEmpty()
}
