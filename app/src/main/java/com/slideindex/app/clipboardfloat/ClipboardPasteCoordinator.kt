package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FV-style paste: 2+ red-frame picker; 1 rect direct paste; 0 rects fall back to focused field.
 */
object ClipboardPasteCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private const val WECHAT_CLIP_SETTLE_MS = 60L
    private const val WECHAT_PASTE_RETRY_DELAY_MS = 150L
    private const val WECHAT_PASTE_MAX_ATTEMPTS = 8

    /**
     * 微信图片：grantUri + 写剪贴板 + 延迟后按包名无障碍粘贴（对齐参考 PASTE_CLIP / b9 重试）。
     */
    fun pasteEntryViaWeChatHostChain(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        hostPackage: String,
        onFinished: (PasteResult?) -> Unit,
    ) {
        scope.launch {
            val prepared = withContext(Dispatchers.Main.immediate) {
                ClipboardWriter.writeForPasteToHost(context, entry, hostPackage)
            }
            if (!prepared) {
                onFinished(PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED))
                return@launch
            }
            SystemClock.sleep(WECHAT_CLIP_SETTLE_MS)
            val result = withContext(Dispatchers.Default) {
                repeat(WECHAT_PASTE_MAX_ATTEMPTS) { attempt ->
                    when (
                        ClipboardPasteHelper.attemptPasteInHostPackage(
                            service = service,
                            hostPackage = hostPackage,
                        )
                    ) {
                        ClipboardPasteHelper.HostPasteAttempt.SUCCESS ->
                            return@withContext PasteResult.Success
                        ClipboardPasteHelper.HostPasteAttempt.RETRY -> {
                            if (attempt < WECHAT_PASTE_MAX_ATTEMPTS - 1) {
                                SystemClock.sleep(WECHAT_PASTE_RETRY_DELAY_MS)
                            }
                        }
                    }
                }
                PasteResult.Failure(PasteFailureReason.NO_EDITABLE_FOCUS)
            }
            onFinished(result)
        }
    }

    fun pasteEntry(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        writeToClipboardFirst: Boolean = false,
        fvStyle: Boolean = false,
        onFinished: (PasteResult?) -> Unit,
    ) {
        scope.launch {
            if (writeToClipboardFirst) {
                ClipboardWriter.write(context, entry)
            }
            if (!fvStyle) {
                val result = withContext(Dispatchers.Default) {
                    ClipboardPasteHelper.pasteEntryToFocusedField(
                        service = service,
                        context = context,
                        entry = entry,
                    )
                }
                onFinished(result)
                return@launch
            }
            val targets = withContext(Dispatchers.IO) {
                ClipboardPasteTargetFinder.findTargetRects(service)
            }
            when {
                targets.isEmpty() -> {
                    val result = withContext(Dispatchers.Default) {
                        ClipboardPasteHelper.pasteEntryToFocusedField(
                            service = service,
                            context = context,
                            entry = entry,
                        )
                    }
                    onFinished(result)
                }
                targets.size == 1 -> {
                    val result = withContext(Dispatchers.Default) {
                        val rectResult = ClipboardPasteHelper.pasteAtScreenRect(
                            service = service,
                            context = context,
                            entry = entry,
                            rect = targets.single()
                        )
                        if (rectResult is PasteResult.Failure) {
                            ClipboardPasteHelper.pasteEntryToFocusedField(
                                service = service,
                                context = context,
                                entry = entry,
                            )
                        } else {
                            rectResult
                        }
                    }
                    onFinished(result)
                }
                else -> {
                    ClipboardPasteTargetOverlay.show(
                        context = context,
                        service = service,
                        targets = targets,
                        entry = entry,
                        onFinished = onFinished
                    )
                }
            }
        }
    }

    /** Paste into a known screen rect (drag-ball / red-frame tap). Runs off the UI thread. */
    fun pasteEntryAtScreenRect(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        rect: Rect,
        fvStyle: Boolean = false,
        onFinished: (PasteResult) -> Unit,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                if (!fvStyle) {
                    return@withContext ClipboardPasteHelper.pasteEntryToFocusedField(
                        service = service,
                        context = context,
                        entry = entry,
                    )
                }
                val rectResult = ClipboardPasteHelper.pasteAtScreenRect(
                    service = service,
                    context = context,
                    entry = entry,
                    rect = rect,
                )
                if (rectResult is PasteResult.Failure) {
                    ClipboardPasteHelper.pasteEntryToFocusedField(
                        service = service,
                        context = context,
                        entry = entry,
                    )
                } else {
                    rectResult
                }
            }
            onFinished(result)
        }
    }

    fun toastPasteFailure(context: Context, reason: PasteFailureReason) {
        val messageRes = when (reason) {
            PasteFailureReason.NO_ACTIVE_WINDOW -> R.string.clipboard_float_paste_no_window
            PasteFailureReason.NO_EDITABLE_FOCUS -> R.string.clipboard_float_paste_failed
            PasteFailureReason.PASTE_AND_INSERT_FAILED -> R.string.clipboard_float_paste_insert_failed
        }
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }
}
