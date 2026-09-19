package com.slideindex.app.clipboardfloat

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.AccessibilityForegroundResolver

/**
 * 微信等宿主对跨应用拖放图片支持差；参考 AI 剪贴板在起拖前走「写剪贴板 + 无障碍粘贴」链路。
 */
object ClipboardDragHostPaste {
    const val WECHAT_PACKAGE = "com.tencent.mm"

    fun isWeChatForeground(context: Context): Boolean =
        AccessibilityForegroundResolver.resolve(context) == WECHAT_PACKAGE

    /** 微信前台图片：不起拖，走剪贴板 + 无障碍粘贴。 */
    fun shouldPasteInsteadOfDrag(context: Context, entry: ClipboardEntry): Boolean =
        entry.hasImageContent() && isWeChatForeground(context)

    fun pasteEntryToForegroundHost(
        context: Context,
        entry: ClipboardEntry,
        fvStyle: Boolean,
        onFinished: ((PasteResult?) -> Unit)? = null,
    ) {
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            Toast.makeText(context, R.string.search_engine_accessibility_required, Toast.LENGTH_SHORT).show()
            onFinished?.invoke(null)
            return
        }
        if (entry.hasImageContent() && isWeChatForeground(context)) {
            ClipboardPasteCoordinator.pasteEntryViaWeChatHostChain(
                service = service,
                context = context,
                entry = entry,
                hostPackage = WECHAT_PACKAGE,
                onFinished = { result ->
                    deliverPasteResult(context, result, onFinished)
                },
            )
            return
        }
        ClipboardPasteCoordinator.pasteEntry(
            service = service,
            context = context,
            entry = entry,
            writeToClipboardFirst = true,
            fvStyle = fvStyle,
        ) { result ->
            deliverPasteResult(context, result, onFinished)
        }
    }

    private fun deliverPasteResult(
        context: Context,
        result: PasteResult?,
        onFinished: ((PasteResult?) -> Unit)?,
    ) {
        if (result is PasteResult.Failure) {
            val messageRes = when (result.reason) {
                PasteFailureReason.NO_ACTIVE_WINDOW -> R.string.clipboard_float_paste_no_window
                PasteFailureReason.NO_EDITABLE_FOCUS -> R.string.clipboard_float_paste_failed
                PasteFailureReason.PASTE_AND_INSERT_FAILED -> R.string.clipboard_float_paste_insert_failed
            }
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
        onFinished?.invoke(result)
    }
}
