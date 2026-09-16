package com.slideindex.app.imageeditor

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.PickResultImageSharePrefs
import com.slideindex.app.overlay.pickresult.loadPickResultSearchEngineBitmap
import com.slideindex.app.overlay.pickresult.preloadPickResultSearchEngineIcons
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.HapticHelper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 分享按钮长按：竖向排列图片分享引擎，不松手滑动选择，松手分享到对应 App。
 */
class ImageEditorShareEngineDragHelper(
    private val anchor: View,
    private val settingsRepository: SettingsRepository,
    private val onSystemShare: () -> Unit,
    private val onShareEngine: (SearchEngineConfig) -> Unit,
) {
    private val context: Context = anchor.context
    private val iconSizePx = dp(32f)
    private val itemSpacingPx = dp(4f)
    private val columnPaddingHPx = dp(8f)
    private val columnPaddingVPx = dp(6f)
    private val rowPaddingHPx = dp(5f)
    private val itemRowMinHeightPx = dp(36f)
    private val xCancelThresholdPx = dp(60f)
    private val itemCornerPx = dp(8f)
    private val panelCornerPx = dp(12f)
    private val iconCornerPx = dp(8f)
    private val popupGapAboveAnchorPx = dp(8f)
    private val maxVisibleRows = 7
    private val scrollEdgePx = dp(28f)
    private val scrollStepPx = dp(10f).roundToInt()

    private var popup: PopupWindow? = null
    private var rowContainer: LinearLayout? = null
    private var itemsScrollView: ScrollView? = null
    private var itemsColumn: LinearLayout? = null
    private var rowHeightsPx: FloatArray = FloatArray(0)
    private var itemViews: List<EngineRowViews> = emptyList()
    private var engines: List<SearchEngineConfig> = emptyList()
    private var hoveredIndex = -1
    private var dragging = false
    private var dragStartFingerX = 0f
    private var popupTopInWindow = 0f
    private var popupLeftInWindow = 0f
    private var popupWidthPx = 0f
    private var popupHeightPx = 0f
    private var pendingPopupEnterAnimation = false
    private var appSettings: AppSettings = AppSettings()

    private data class EngineRowViews(
        val row: LinearLayout,
        val iconHost: FrameLayout,
    )

    private val popupHost: View
        get() = anchor.rootView

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!dragging) {
                    onSystemShare()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (engines.isEmpty() || dragging) return
                beginDrag(e.rawX, e.rawY)
            }
        },
    )

    fun attach() {
        anchor.setOnTouchListener(shareTouchListener)
    }

    fun detach() {
        anchor.setOnTouchListener(null)
        dismissPopup()
    }

    fun refreshEngines() {
        engines = SearchEngineStore.imageSharePanelEngines(settingsRepository.readSnapshot().searchEngines)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val shareTouchListener = View.OnTouchListener { _, event ->
        refreshEngines()
        appSettings = settingsRepository.readSnapshot()
        val handled = gestureDetector.onTouchEvent(event)
        if (!dragging) {
            return@OnTouchListener handled
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                maybeAutoScrollItems(event.rawY)
                val index = if (abs(event.rawX - dragStartFingerX) > xCancelThresholdPx) {
                    -1
                } else {
                    hitTestShareIndex(event.rawX, event.rawY)
                }
                if (index != hoveredIndex) {
                    hoveredIndex = index
                    updateHoverUi()
                    HapticHelper.appTick(anchor, appSettings)
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val selected = if (hoveredIndex in engines.indices) engines[hoveredIndex] else null
                endDrag()
                if (selected != null) {
                    PickResultImageSharePrefs.rememberLastUsedEngine(context, selected)
                    onShareEngine(selected)
                }
                true
            }
            else -> true
        }
    }

    private fun beginDrag(fingerX: Float, fingerY: Float) {
        dragging = true
        dragStartFingerX = fingerX
        hoveredIndex = -1
        preloadPickResultSearchEngineIcons(context.applicationContext, engines)
        showPopup()
        if (popup == null) {
            dragging = false
            return
        }
        hoveredIndex = hitTestShareIndex(fingerX, fingerY)
        updateHoverUi()
        HapticHelper.appTick(anchor, appSettings)
    }

    private fun endDrag() {
        dragging = false
        hoveredIndex = -1
        dismissPopup()
    }

    private fun maybeAutoScrollItems(fingerY: Float) {
        val scroll = itemsScrollView ?: return
        val loc = IntArray(2)
        scroll.getLocationOnScreen(loc)
        val top = loc[1].toFloat()
        val bottom = top + scroll.height
        when {
            fingerY > bottom - scrollEdgePx -> scroll.scrollBy(0, scrollStepPx)
            fingerY < top + scrollEdgePx -> scroll.scrollBy(0, -scrollStepPx)
        }
    }

    private fun hitTestShareIndex(fingerX: Float, fingerY: Float): Int {
        if (engines.isEmpty() || popupWidthPx <= 0f || popupHeightPx <= 0f) return -1
        if (fingerX < popupLeftInWindow || fingerX > popupLeftInWindow + popupWidthPx) return -1
        if (fingerY < popupTopInWindow || fingerY > popupTopInWindow + popupHeightPx) return -1
        val column = itemsColumn ?: return -1
        val colLoc = IntArray(2)
        column.getLocationOnScreen(colLoc)
        if (fingerX < colLoc[0] || fingerX > colLoc[0] + column.width) return -1
        val localY = fingerY - colLoc[1]
        val itemsBottom = rowHeightsPx.sum() +
            (engines.size - 1).coerceAtLeast(0) * itemSpacingPx
        if (localY < 0f || localY > itemsBottom) return -1
        var offset = 0f
        for (i in engines.indices) {
            if (i > 0) {
                offset += itemSpacingPx
            }
            val rowHeight = rowHeightsPx.getOrNull(i)?.takeIf { it > 0f } ?: itemRowMinHeightPx
            if (localY >= offset && localY < offset + rowHeight) {
                return i
            }
            offset += rowHeight
        }
        return -1
    }

    private fun isDarkTheme(): Boolean {
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return AppThemeMode.fromId(appSettings.themeModeId).resolveIsDark(systemDark)
    }

    private fun panelColors(dark: Boolean): PanelColors {
        val chipBg = if (dark) 0xFF3A3A3C.toInt() else 0xFFDFE4EA.toInt()
        val strokeColor = MaterialColors.getColor(
            anchor,
            com.google.android.material.R.attr.colorOutlineVariant,
            if (dark) 0x33FFFFFF else 0x22000000,
        )
        val labelColor = if (dark) 0xFFE8EAED.toInt() else 0xFF2F3542.toInt()
        val onSurface = MaterialColors.getColor(
            anchor,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK,
        )
        val rowIdleBg = ColorUtils.setAlphaComponent(onSurface, if (dark) 0x14 else 0x0A)
        val hintColor = if (dark) 0xFFA0AAB5.toInt() else 0xFF747D8C.toInt()
        return PanelColors(
            panelBg = chipBg,
            strokeColor = strokeColor,
            labelColor = labelColor,
            rowIdleBg = rowIdleBg,
            hintColor = hintColor,
        )
    }

    private data class PanelColors(
        val panelBg: Int,
        val strokeColor: Int,
        val labelColor: Int,
        val rowIdleBg: Int,
        val hintColor: Int,
    )

    private fun anchorPopupAboveShareButton() {
        val container = rowContainer ?: return
        measurePopupContent(container)
        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val margin = dp(8f)
        val popupH = container.measuredHeight.toFloat()
        val desiredTop = anchorLoc[1] - popupH - popupGapAboveAnchorPx
        val screenH = context.resources.displayMetrics.heightPixels.toFloat()
        popupTopInWindow = desiredTop.coerceIn(margin, (screenH - popupH - margin).coerceAtLeast(margin))
    }

    private fun showPopup() {
        dismissPopup()
        val dark = isDarkTheme()
        val colors = panelColors(dark)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(
                columnPaddingHPx.roundToInt(),
                columnPaddingVPx.roundToInt(),
                columnPaddingHPx.roundToInt(),
                columnPaddingVPx.roundToInt(),
            )
            background = GradientDrawable().apply {
                cornerRadius = panelCornerPx
                setColor(colors.panelBg)
                setStroke(dp(0.5f).roundToInt().coerceAtLeast(1), colors.strokeColor)
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, panelCornerPx)
                }
            }
            elevation = dp(6f)
            translationZ = dp(2f)
        }
        val itemsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val builtRows = mutableListOf<EngineRowViews>()
        engines.forEachIndexed { index, engine ->
            val rowViews = buildEngineRow(engine, colors)
            builtRows += rowViews
            itemsLayout.addView(
                rowViews.row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) {
                        topMargin = itemSpacingPx.roundToInt()
                    }
                },
            )
        }
        itemViews = builtRows
        itemsColumn = itemsLayout
        val contentWidthPx = alignRowsToWidestEngine(itemsLayout)
        rowHeightsPx = measureRowHeightsPx()
        val scrollMaxHeight = (
            itemRowMinHeightPx * maxVisibleRows +
                itemSpacingPx * (maxVisibleRows - 1).coerceAtLeast(0)
            ).roundToInt()
        val needsScroll = engines.size > maxVisibleRows
        val listHost: View = if (needsScroll) {
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isFillViewport = false
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    contentWidthPx,
                    scrollMaxHeight,
                )
                addView(
                    itemsLayout,
                    FrameLayout.LayoutParams(contentWidthPx, FrameLayout.LayoutParams.WRAP_CONTENT),
                )
                itemsScrollView = this
            }
        } else {
            itemsScrollView = null
            itemsLayout
        }
        container.addView(
            TextView(context).apply {
                text = context.getString(R.string.pick_result_image_share_editor_drag_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(colors.hintColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(
                contentWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(6f).roundToInt()
            },
        )
        container.addView(
            listHost,
            LinearLayout.LayoutParams(
                contentWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        rowContainer = container
        measurePopupContent(container)
        if (container.measuredWidth <= 0 || container.measuredHeight <= 0) {
            itemViews = emptyList()
            itemsColumn = null
            itemsScrollView = null
            rowContainer = null
            rowHeightsPx = FloatArray(0)
            return
        }
        popup = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            isTouchable = false
            isOutsideTouchable = false
            elevation = dp(6f)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        pendingPopupEnterAnimation = true
        anchorPopupAboveShareButton()
        updatePopupPosition()
    }

    private fun buildEngineRow(
        engine: SearchEngineConfig,
        colors: PanelColors,
    ): EngineRowViews {
        val iconHostSize = iconSizePx.roundToInt()
        val iconHost = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconHostSize, iconHostSize)
            bindEngineIcon(engine, this)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = itemRowMinHeightPx.roundToInt()
            val padH = rowPaddingHPx.roundToInt()
            setPadding(padH, dp(3f).roundToInt(), padH, dp(3f).roundToInt())
            background = roundedRect(colors.rowIdleBg, itemCornerPx)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, itemCornerPx)
                }
            }
            addView(iconHost)
            addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = dp(6f).roundToInt()
                    }
                    text = engine.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(colors.labelColor)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
        }
        return EngineRowViews(row, iconHost)
    }

    private fun alignRowsToWidestEngine(itemsLayout: LinearLayout): Int {
        val screenMaxWidth = (
            context.resources.displayMetrics.widthPixels - dp(16f) * 2f
            ).roundToInt().coerceAtLeast(dp(120f).roundToInt())
        val widthSpec = View.MeasureSpec.makeMeasureSpec(screenMaxWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var maxRowWidth = 0
        itemViews.forEach { entry ->
            entry.row.measure(widthSpec, heightSpec)
            maxRowWidth = maxOf(maxRowWidth, entry.row.measuredWidth)
        }
        if (maxRowWidth <= 0) {
            maxRowWidth = (iconSizePx + dp(6f) + dp(48f)).roundToInt()
        }
        itemViews.forEach { entry ->
            entry.row.layoutParams = LinearLayout.LayoutParams(maxRowWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        itemsLayout.layoutParams = LinearLayout.LayoutParams(maxRowWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        return maxRowWidth
    }

    private fun measureRowHeightsPx(): FloatArray {
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        return FloatArray(itemViews.size) { index ->
            val row = itemViews[index].row
            if (row.measuredHeight > 0) {
                row.measuredHeight.toFloat()
            } else {
                row.measure(widthSpec, heightSpec)
                row.measuredHeight.toFloat().takeIf { it > 0f } ?: itemRowMinHeightPx
            }
        }
    }

    private fun bindEngineIcon(engine: SearchEngineConfig, iconHost: FrameLayout) {
        val labelColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            Color.DKGRAY,
        )
        val iconBg = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceVariant,
            0xFFE8EAED.toInt(),
        )
        iconHost.background = roundedRect(iconBg, iconCornerPx)
        if (engine.iconType == SearchIconType.TEXT) {
            val label = engine.textIcon?.take(2).orEmpty().ifBlank { engine.name.take(1) }
            iconHost.addView(
                TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    text = label
                    gravity = Gravity.CENTER
                    textSize = 15f
                    setTextColor(labelColor)
                },
            )
            return
        }
        val bitmap = loadPickResultSearchEngineBitmap(context.applicationContext, engine)
        if (bitmap != null) {
            iconHost.addView(
                ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, iconCornerPx)
                        }
                    }
                },
            )
            return
        }
        val pkg = engine.targetPackage?.takeIf { it.isNotBlank() }
        if (pkg != null) {
            runCatching {
                iconHost.addView(
                    ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(context.packageManager.getApplicationIcon(pkg))
                    },
                )
            }
            return
        }
        iconHost.addView(
            TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                text = engine.name.take(1)
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(labelColor)
            },
        )
    }

    private fun measurePopupContent(container: View) {
        val screenMaxWidth = (
            context.resources.displayMetrics.widthPixels - dp(16f) * 2f
            ).roundToInt().coerceAtLeast(dp(120f).roundToInt())
        val widthSpec = View.MeasureSpec.makeMeasureSpec(screenMaxWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        container.measure(widthSpec, heightSpec)
    }

    private fun updatePopupPosition() {
        val popupWindow = popup ?: return
        val container = rowContainer ?: return
        if (!anchor.isAttachedToWindow || !popupHost.isAttachedToWindow) return
        measurePopupContent(container)
        val measuredW = container.measuredWidth
        val measuredH = container.measuredHeight
        if (measuredW <= 0 || measuredH <= 0) return
        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val anchorCenterX = anchorLoc[0] + anchor.width / 2f
        val margin = dp(8f).roundToInt()
        val screenW = context.resources.displayMetrics.widthPixels
        val x = (anchorCenterX - measuredW / 2f).roundToInt()
            .coerceIn(margin, (screenW - measuredW - margin).coerceAtLeast(margin))
        val y = popupTopInWindow.roundToInt()
        popupLeftInWindow = x.toFloat()
        popupWidthPx = measuredW.toFloat()
        popupHeightPx = measuredH.toFloat()
        if (!popupWindow.isShowing) {
            popupWindow.showAtLocation(popupHost, Gravity.NO_GRAVITY, x, y)
            if (pendingPopupEnterAnimation) {
                pendingPopupEnterAnimation = false
                playPopupEnterAnimation(container)
            }
        } else {
            popupWindow.update(x, y, measuredW, measuredH)
        }
    }

    private fun playPopupEnterAnimation(container: View) {
        container.animate().cancel()
        container.alpha = 0f
        container.translationY = dp(6f)
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(0.85f))
            .start()
    }

    private fun updateHoverUi() {
        val dark = isDarkTheme()
        val highlight = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimaryContainer,
            if (dark) 0xFF3D4F7A.toInt() else 0xFFDDE8FF.toInt(),
        )
        val colors = panelColors(dark)
        itemViews.forEachIndexed { index, entry ->
            if (!entry.row.isAttachedToWindow) return@forEachIndexed
            val selected = index == hoveredIndex
            entry.row.background = roundedRect(
                if (selected) highlight else colors.rowIdleBg,
                itemCornerPx,
            )
            val targetScale = if (selected) 1.08f else 1f
            entry.row.animate().cancel()
            val rowScale = if (selected) 1.02f else 1f
            entry.row.animate()
                .scaleX(rowScale)
                .scaleY(rowScale)
                .setDuration(180)
                .setInterpolator(OvershootInterpolator(1.05f))
                .start()
            entry.iconHost.animate().cancel()
            entry.iconHost.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(180)
                .setInterpolator(OvershootInterpolator(1.12f))
                .start()
        }
    }

    private fun dismissPopup() {
        pendingPopupEnterAnimation = false
        popup?.dismiss()
        popup = null
        rowContainer = null
        itemsScrollView = null
        itemsColumn = null
        itemViews = emptyList()
        rowHeightsPx = FloatArray(0)
    }

    private fun roundedRect(color: Int, cornerPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = cornerPx
        }

    private fun dp(value: Float): Float =
        value * context.resources.displayMetrics.density
}
