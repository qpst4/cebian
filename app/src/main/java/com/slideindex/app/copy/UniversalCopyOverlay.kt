package com.slideindex.app.copy

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.slideindex.app.R
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.SlideIndexAccessibilityService
import java.lang.ref.WeakReference

object UniversalCopyOverlay {
    private const val AUTO_DISMISS_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var currentOverlay: WeakReference<View>? = null
    private var dismissRunnable: Runnable? = null
    private var hintView: WeakReference<TextView>? = null
    private var copyButton: WeakReference<TextView>? = null
    private var selectAllButton: WeakReference<TextView>? = null

    val isShowing: Boolean get() = currentOverlay?.get() != null

    fun show(context: Context, blocks: List<UniversalCopyBlock>) {
        handler.post {
            dismiss()
            if (blocks.isEmpty()) {
                Toast.makeText(context, R.string.universal_copy_no_text, Toast.LENGTH_SHORT).show()
                return@post
            }
            addOverlay(context, blocks.map { SelectableBlock(it.text, Rect(it.bounds)) })
        }
    }

    fun collectAndShow(service: SlideIndexAccessibilityService) {
        val root = service.rootInActiveWindow
        val blocks = UniversalCopyCollector.collectAll(root)
        root?.recycle()
        show(service, blocks)
    }

    fun dismiss() {
        dismissRunnable?.let(handler::removeCallbacks)
        dismissRunnable = null
        hintView = null
        copyButton = null
        selectAllButton = null
        val overlay = currentOverlay?.get() ?: return
        runCatching {
            val wm = overlay.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeViewImmediate(overlay)
            currentOverlay = null
        }
    }

    private class SelectableBlock(val text: String, val bounds: Rect, var selected: Boolean = false)

    private fun addOverlay(context: Context, blocks: List<SelectableBlock>) {
        val density = context.resources.displayMetrics.density
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val accentColor = "#326D32".toColorInt()
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismiss()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusableInTouchMode = true
            isFocusable = true
        }
        val blocksView = TextBlocksView(context, blocks, density, accentColor,
            onEmptyTap = { dismiss() },
            onSelectionChanged = { updateToolbarState(blocks) },
        )
        root.addView(blocksView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        val toolbar = createToolbar(context, blocks, density, accentColor)
        root.addView(toolbar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(48)
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(root, params)
        currentOverlay = WeakReference(root)
        val runnable = Runnable { dismiss() }
        dismissRunnable = runnable
        handler.postDelayed(runnable, AUTO_DISMISS_MS)
    }

    private fun createToolbar(
        context: Context,
        blocks: List<SelectableBlock>,
        density: Float,
        accentColor: Int,
    ): LinearLayout {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor("#E8222222".toColorInt())
                cornerRadius = dp(28).toFloat()
            }
            setPadding(dp(16), dp(10), dp(8), dp(10))
            setOnTouchListener { _, _ -> true }
        }
        val hint = TextView(context).apply {
            text = context.getString(R.string.universal_copy_tap_to_select)
            setTextColor("#AAAAAA".toColorInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        hintView = WeakReference(hint)
        toolbar.addView(hint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(12)
        })
        val selectAll = createToolbarButton(context, density,
            context.getString(R.string.universal_copy_select_all),
            "#3A3A3A".toColorInt(),
            "#CCCCCC".toColorInt(),
        ) {
            val allSelected = blocks.all { it.selected }
            blocks.forEach { it.selected = !allSelected }
            (currentOverlay?.get() as? ViewGroup)?.getChildAt(0)?.invalidate()
            updateToolbarState(blocks)
        }
        selectAllButton = WeakReference(selectAll)
        toolbar.addView(selectAll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
            marginEnd = dp(6)
        })
        val copy = createToolbarButton(context, density,
            context.getString(R.string.universal_copy_copy),
            accentColor,
            Color.WHITE,
        ) {
            val selected = blocks.filter { it.selected }
            if (selected.isNotEmpty()) {
                val text = selected.joinToString("\n") { it.text }
                copyToClipboard(context, text)
                Toast.makeText(context, context.getString(R.string.universal_copy_copied, selected.size), Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
        copy.alpha = 0.4f
        copy.isEnabled = false
        copyButton = WeakReference(copy)
        toolbar.addView(copy, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
            marginEnd = dp(6)
        })
        val close = createToolbarButton(context, density, "×", "#3A3A3A".toColorInt(), "#CCCCCC".toColorInt()) {
            dismiss()
        }
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        toolbar.addView(close, LinearLayout.LayoutParams(dp(34), dp(34)))
        return toolbar
    }

    private fun createToolbarButton(
        context: Context,
        density: Float,
        label: String,
        bgColor: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): TextView {
        val dp = { value: Int -> (value * density + 0.5f).toInt() }
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(17).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateToolbarState(blocks: List<SelectableBlock>) {
        val count = blocks.count { it.selected }
        hintView?.get()?.text = if (count > 0) {
            currentOverlay?.get()?.context?.getString(R.string.universal_copy_selected, count)
        } else {
            currentOverlay?.get()?.context?.getString(R.string.universal_copy_tap_to_select)
        }
        copyButton?.get()?.apply {
            alpha = if (count > 0) 1f else 0.4f
            isEnabled = count > 0
        }
        selectAllButton?.get()?.text = if (blocks.all { it.selected }) {
            currentOverlay?.get()?.context?.getString(R.string.universal_copy_deselect)
        } else {
            currentOverlay?.get()?.context?.getString(R.string.universal_copy_select_all)
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Cebian", text))
    }

    private class TextBlocksView(
        context: Context,
        private val blocks: List<SelectableBlock>,
        private val density: Float,
        accentColor: Int,
        private val onEmptyTap: () -> Unit,
        private val onSelectionChanged: () -> Unit,
    ) : View(context) {
        private val cornerRadius = 4f * density
        private val tapPadding = (10 * density).toInt()
        private val tapSlopSq = (24 * density * 24 * density)
        private val scrimPaint = Paint().apply { color = "#28000000".toColorInt() }
        private val normalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#18FFFFFF".toColorInt() }
        private val normalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#44FFFFFF".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0x55 shl 24)
        }
        private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0xDD.toInt() shl 24)
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        private var downX = 0f
        private var downY = 0f
        private var downBlock: SelectableBlock? = null
        private val tmpRect = RectF()
        private var viewOffsetX = 0
        private var viewOffsetY = 0
        private val locationOnScreen = IntArray(2)

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            getLocationOnScreen(locationOnScreen)
            viewOffsetX = locationOnScreen[0]
            viewOffsetY = locationOnScreen[1]
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            for (block in blocks) {
                tmpRect.set(
                    block.bounds.left.toFloat() - viewOffsetX,
                    block.bounds.top.toFloat() - viewOffsetY,
                    block.bounds.right.toFloat() - viewOffsetX,
                    block.bounds.bottom.toFloat() - viewOffsetY,
                )
                if (block.selected) {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selectedFillPaint)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, selectedStrokePaint)
                } else {
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalFillPaint)
                    canvas.drawRoundRect(tmpRect, cornerRadius, cornerRadius, normalStrokePaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downBlock = findBlockAt(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy < tapSlopSq) {
                        val upBlock = findBlockAt(event.rawX, event.rawY)
                        if (upBlock != null && upBlock === downBlock) {
                            upBlock.selected = !upBlock.selected
                            invalidate()
                            onSelectionChanged()
                        } else if (upBlock == null && downBlock == null) {
                            onEmptyTap()
                        }
                    }
                    downBlock = null
                }
            }
            return true
        }

        private fun findBlockAt(x: Float, y: Float): SelectableBlock? {
            val ix = x.toInt()
            val iy = y.toInt()
            var best: SelectableBlock? = null
            var bestArea = Int.MAX_VALUE
            for (block in blocks) {
                val b = block.bounds
                if (ix >= b.left - tapPadding && ix <= b.right + tapPadding &&
                    iy >= b.top - tapPadding && iy <= b.bottom + tapPadding
                ) {
                    val area = b.width() * b.height()
                    if (area < bestArea) {
                        best = block
                        bestArea = area
                    }
                }
            }
            return best
        }
    }
}
