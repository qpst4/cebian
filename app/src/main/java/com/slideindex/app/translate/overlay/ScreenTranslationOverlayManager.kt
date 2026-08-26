package com.slideindex.app.translate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateDependencyAccess
import com.slideindex.app.translate.TranslateEngine
import com.slideindex.app.translate.TranslateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ScreenTranslationOverlayManager(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var rootView: FrameLayout? = null
    private var backHandler: OverlayViewBackHandler? = null
    @Volatile private var active = false

    val isActive: Boolean get() = active

    fun toggle() {
        if (active) {
            dismiss()
            return
        }
        active = true
        Toast.makeText(service, R.string.screen_translate_started, Toast.LENGTH_SHORT).show()
        job = scope.launch {
            val items = runCatching { buildDisplayItems() }.getOrElse { e ->
                if (e is CancellationException) throw e
                null
            }
            withContext(Dispatchers.Main) {
                if (!active) return@withContext
                when {
                    items == null -> finishWithToast(R.string.screen_translate_failed)
                    items.isEmpty() -> finishWithToast(R.string.screen_translate_no_text)
                    else -> showOverlay(items)
                }
            }
        }
    }

    fun dismiss() {
        active = false
        job?.cancel()
        job = null
        val root = rootView ?: return
        rootView = null
        backHandler?.detach()
        backHandler = null
        OverlaySceneController.onContentPanelHidden()
        ScreenTranslationController.onManagerDismissed(this)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeWindow(root)
        } else {
            service.mainExecutor.execute { removeWindow(root) }
        }
    }

    private fun removeWindow(root: View) {
        runCatching {
            if (root.windowToken != null) windowManager.removeView(root)
        }
    }

    private fun finishWithToast(resId: Int) {
        active = false
        Toast.makeText(service, resId, Toast.LENGTH_SHORT).show()
    }

    private data class TextSegment(val bounds: Rect, val text: String)
    private data class DisplayItem(
        val bounds: Rect,
        val translated: String,
        val bgColor: Int,
        val textColor: Int,
        val singleLine: Boolean,
    )

    private suspend fun buildDisplayItems(): List<DisplayItem> {
        val root = service.rootInActiveWindow ?: return emptyList()
        if (root.packageName == service.packageName) return emptyList()
        val segments = collectTextSegments(root)
        if (segments.isEmpty()) return emptyList()
        val screenshot = takeScreenshot()
        val darkMode = if (screenshot == null) isAppDarkMode(root.packageName?.toString()) else null
        val settings = OverlayDependencyAccess.overlayDependencies(service)
            ?.settingsRepository
            ?.readSnapshot()
        val targetLang = settings?.floatBallTranslateTargetLang?.ifBlank { "zh-CN" } ?: "zh-CN"
        val engine = when (settings?.floatBallTranslateEngine) {
            FloatBallTranslateEngine.ML_KIT -> TranslateEngine.ML_KIT
            else -> TranslateEngine.GOOGLE
        }
        val translateService = TranslateDependencyAccess.translateService(service)
            ?: return emptyList()
        val translated = mutableListOf<String>()
        for (segment in segments) {
            when (val result = translateService.translate(segment.text, targetLang, engine)) {
                is TranslateResult.Success -> translated += result.translatedText
                is TranslateResult.Failure -> translated += segment.text
            }
        }
        val items = mutableListOf<DisplayItem>()
        val density = service.resources.displayMetrics.density
        for (i in segments.indices) {
            val text = translated.getOrNull(i)?.trim().orEmpty()
            if (text.isEmpty() || text == segments[i].text) continue
            val bounds = segments[i].bounds
            val marginH = maxOf((2 * density).toInt(), bounds.width() / 30)
            val marginV = maxOf((2 * density).toInt(), bounds.height() / 20)
            val cover = Rect(
                (bounds.left - marginH).coerceAtLeast(0),
                (bounds.top - marginV).coerceAtLeast(0),
                bounds.right + marginH,
                bounds.bottom + marginV,
            )
            val bg = screenshot?.let { sampleBorderColor(it, cover) }
                ?: if (darkMode == true) DARK_BG else Color.WHITE
            val fg = if (isLightColor(bg)) Color.BLACK else Color.WHITE
            items += DisplayItem(cover, text, bg, fg, isLikelySingleLine(segments[i].text))
        }
        return items
    }

    private fun collectTextSegments(root: AccessibilityNodeInfo): List<TextSegment> {
        val density = service.resources.displayMetrics.density
        val minWidth = (18 * density).toInt()
        val minHeight = (10 * density).toInt()
        val segments = mutableListOf<TextSegment>()
        fun visit(node: AccessibilityNodeInfo) {
            if (segments.size >= MAX_SEGMENTS) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length <= MAX_TEXT_LENGTH &&
                node.isVisibleToUser && !node.isEditable && !isNoiseText(node, text)
            ) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty && bounds.width() >= minWidth && bounds.height() >= minHeight) {
                    segments += TextSegment(bounds, text)
                }
            }
            for (i in 0 until node.childCount) {
                if (segments.size >= MAX_SEGMENTS) return
                val child = node.getChild(i) ?: continue
                visit(child)
            }
        }
        visit(root)
        val cleaned = mutableListOf<TextSegment>()
        for (segment in segments) {
            val contained = cleaned.any { it.bounds.contains(segment.bounds) && it.text == segment.text }
            if (contained) continue
            cleaned.removeAll { segment.bounds.contains(it.bounds) && it.text == segment.text }
            cleaned += segment
        }
        return cleaned
    }

    private fun isNoiseText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (text.length < 2) return true
        if (text.none { it.isLetterOrDigit() }) return true
        if (text.all { it.isDigit() || it.isWhitespace() || it in ":./年月日时分秒-｜" }) return true
        val viewId = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (viewId.isNotEmpty()) {
            val noise = listOf("name", "nick", "author", "user_name", "time", "date", "count", "badge", "avatar", "index", "icon")
            if (noise.any { viewId.contains(it) }) return true
        }
        return false
    }

    private suspend fun takeScreenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        runCatching {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (cont.isCancelled) return
                        try {
                            val wrapped = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                            cont.resume(wrapped?.copy(Bitmap.Config.ARGB_8888, false))
                        } catch (_: Exception) {
                            cont.resume(null)
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        if (!cont.isCancelled) cont.resume(null)
                    }
                },
            )
        }.onFailure {
            if (!cont.isCancelled) cont.resume(null)
        }
    }

    private fun sampleBorderColor(bitmap: Bitmap, bounds: Rect): Int {
        val left = bounds.left.coerceIn(0, bitmap.width - 1)
        val top = bounds.top.coerceIn(0, bitmap.height - 1)
        val right = (bounds.right - 1).coerceIn(0, bitmap.width - 1)
        val bottom = (bounds.bottom - 1).coerceIn(0, bitmap.height - 1)
        if (right <= left || bottom <= top) return Color.WHITE
        var r = 0L; var g = 0L; var b = 0L; var count = 0L
        fun sample(x: Int, y: Int) {
            val color = bitmap.getPixel(x, y)
            r += Color.red(color); g += Color.green(color); b += Color.blue(color); count++
        }
        val stepX = maxOf(1, (right - left) / 40)
        val stepY = maxOf(1, (bottom - top) / 40)
        var x = left
        while (x <= right) { sample(x, top); sample(x, bottom); x += stepX }
        var y = top + 1
        while (y < bottom) { sample(left, y); sample(right, y); y += stepY }
        if (count == 0L) return Color.WHITE
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun isAppDarkMode(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        return runCatching {
            val appContext = service.createPackageContext(packageName, 0)
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }.getOrDefault(false)
    }

    private fun isLightColor(color: Int): Boolean =
        0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color) > 150f

    private fun isLikelySingleLine(text: String): Boolean = !text.contains('\n') && text.length <= 12

    private fun showOverlay(items: List<DisplayItem>) {
        val root = FrameLayout(service).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val dismissScrim = View(service).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setOnClickListener { dismiss() }
        }
        root.addView(dismissScrim)
        for (item in items) {
            val view = createItemView(item)
            view.isClickable = false
            view.isFocusable = false
            val lp = FrameLayout.LayoutParams(item.bounds.width(), item.bounds.height())
            lp.leftMargin = item.bounds.left
            lp.topMargin = item.bounds.top
            root.addView(view, lp)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(service),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        backHandler = OverlayViewBackHandler(root) { dismiss() }.also { it.attach() }
        windowManager.addView(root, params)
        rootView = root
        OverlaySceneController.onContentPanelShown()
        FloatBallOverlay.scheduleChromeAbovePanels()
        root.requestFocus()
    }

    private fun createItemView(item: DisplayItem): TextView {
        val density = service.resources.displayMetrics.density
        val paddingH = (item.bounds.width() * 0.03f).toInt().coerceAtLeast((2 * density).toInt())
        val paddingV = (item.bounds.height() * 0.05f).toInt().coerceAtLeast(density.toInt())
        val textView = TextView(service).apply {
            setBackgroundColor(item.bgColor)
            setTextColor(item.textColor)
            text = item.translated
            gravity = if (item.singleLine) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            includeFontPadding = false
        }
        fitTextToBounds(textView, (item.bounds.width() - paddingH * 2).coerceAtLeast(1),
            (item.bounds.height() - paddingV * 2).coerceAtLeast(1), item.singleLine)
        return textView
    }

    private fun fitTextToBounds(textView: TextView, maxWidth: Int, maxHeight: Int, singleLine: Boolean) {
        val lineCount = textView.text.count { it == '\n' } + 1
        var size = (maxHeight / lineCount.toFloat()).coerceIn(8f, 40f)
        while (size > 6f) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            val layout = StaticLayout.Builder.obtain(textView.text, 0, textView.text.length, textView.paint, maxWidth)
                .setAlignment(if (singleLine) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .build()
            if (layout.height <= maxHeight) return
            size *= 0.9f
        }
    }

    companion object {
        private const val MAX_SEGMENTS = 150
        private const val MAX_TEXT_LENGTH = 1800
        private val DARK_BG = Color.rgb(0x1e, 0x1e, 0x1e)
    }
}

object ScreenTranslationController {
    private var manager: ScreenTranslationOverlayManager? = null

    fun toggle(service: AccessibilityService) {
        val current = manager
        if (current?.isActive == true) {
            current.dismiss()
            manager = null
            return
        }
        val created = ScreenTranslationOverlayManager(service)
        manager = created
        created.toggle()
    }

    fun dismissIfActive() {
        manager?.takeIf { it.isActive }?.dismiss()
        manager = null
    }

    fun handleBack(): Boolean {
        val current = manager
        if (current?.isActive == true) {
            current.dismiss()
            manager = null
            return true
        }
        return false
    }

    internal fun onManagerDismissed(dismissed: ScreenTranslationOverlayManager) {
        if (manager === dismissed) {
            manager = null
        }
    }

    val isActive: Boolean get() = manager?.isActive == true
}
