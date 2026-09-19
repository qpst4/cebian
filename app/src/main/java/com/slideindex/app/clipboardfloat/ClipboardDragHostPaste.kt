package com.slideindex.app.clipboardfloat

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.AccessibilityForegroundResolver

/**
 * 部分 IM 对跨应用拖放图片支持差；起拖前走「写剪贴板 + grant + 无障碍粘贴」链路。
 */
object ClipboardDragHostPaste {
    const val WECHAT_PACKAGE = "com.tencent.mm"
    const val TELEGRAM_PACKAGE = "org.telegram.messenger"
    const val TELEGRAM_PLUS_PACKAGE = "org.telegram.plus"

    private val pasteInsteadOfDragHosts: Set<String> = setOf(
        WECHAT_PACKAGE,
        TELEGRAM_PACKAGE,
        TELEGRAM_PLUS_PACKAGE,
    )

    fun resolvePasteInsteadOfDragHost(context: Context): String? {
        val pkg = AccessibilityForegroundResolver.resolve(context) ?: return null
        return pkg.takeIf { it in pasteInsteadOfDragHosts }
    }

    fun isPasteInsteadOfDragHostForeground(context: Context): Boolean =
        resolvePasteInsteadOfDragHost(context) != null

    /** 前台为已知宿主且含图片：不起拖，走剪贴板 + 无障碍粘贴。 */
    fun shouldPasteInsteadOfDrag(context: Context, entry: ClipboardEntry): Boolean =
        entry.hasImageContent() && resolvePasteInsteadOfDragHost(context) != null

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
        val hostPackage = resolvePasteInsteadOfDragHost(context)
        if (entry.hasImageContent() && hostPackage != null) {
            ClipboardPasteCoordinator.pasteEntryViaHostChain(
                service = service,
                context = context,
                entry = entry,
                hostPackage = hostPackage,
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
