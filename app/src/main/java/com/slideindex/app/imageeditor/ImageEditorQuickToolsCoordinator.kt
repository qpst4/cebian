package com.slideindex.app.imageeditor

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.slideindex.app.R
import com.slideindex.app.databinding.ActivityInspireImageEditorBinding
import com.slideindex.app.databinding.PopupInspireImageEditorScrubBinding
import com.slideindex.app.imageeditor.model.EditorMode
import com.slideindex.app.imageeditor.model.NumberBadgeStyle
import com.slideindex.app.imageeditor.model.ShapeType
import com.slideindex.app.imageeditor.state.EditorSession
import com.slideindex.app.imageeditor.state.EditorUiState
import com.slideindex.app.imageeditor.ui.BrushSizePreviewView
import com.slideindex.app.imageeditor.ui.ImageEditorView
import com.slideindex.app.imageeditor.ui.NumberBadgeStylePreviewView
import com.slideindex.app.imageeditor.ui.ShapeTypePreviewView
import kotlin.math.abs
import kotlin.math.roundToInt

class ImageEditorQuickToolsCoordinator(
    private val activity: AppCompatActivity,
    private val binding: ActivityInspireImageEditorBinding,
    private val editorSession: EditorSession,
) {
    var isModeQuickToolsExpanded: Boolean = false

    private var scrubPopupWindow: PopupWindow? = null
    private var colorScrubOptions: List<ScrubOption<Int>> = emptyList()
    private var shapeScrubOptions: List<ScrubOption<ShapeType>> = emptyList()
    private var brushScrubOptions: List<ScrubOption<Float>> = emptyList()
    private var numberStyleScrubOptions: List<ScrubOption<NumberBadgeStyle>> = emptyList()

    private var colorScrubStartX = 0f
    private var isColorScrubbing = false
    private var shapeScrubStartX = 0f
    private var isShapeScrubbing = false
    private var brushDragStartX = 0f
    private var isBrushToolDragging = false

    private val brushDragTouchSlop by lazy {
        ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
    }

    private data class ScrubOption<T>(val value: T, val view: View)

    private data class HorizontalScreenBounds(val left: Float, val right: Float) {
        fun centerX(): Float = (left + right) / 2f
    }

    private class CompositeTouchDelegate(view: View) : TouchDelegate(Rect(), view) {
        private val delegates = ArrayList<TouchDelegate>()

        fun add(delegate: TouchDelegate) {
            delegates += delegate
        }

        fun isEmpty(): Boolean = delegates.isEmpty()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            for (i in delegates.indices.reversed()) {
                if (delegates[i].onTouchEvent(event)) return true
            }
            return false
        }
    }

    fun onDestroy() {
        dismissScrubPopup()
    }

    fun dismissQuickToolPopup() {
        isModeQuickToolsExpanded = false
        dismissScrubPopup()
        binding.editorView.setCenterBrushPreviewVisible(false)
        renderModeQuickTools(editorSession.state)
    }

    fun dismissScrubPopup() {
        scrubPopupWindow?.dismiss()
        scrubPopupWindow = null
        colorScrubOptions = emptyList()
        shapeScrubOptions = emptyList()
        brushScrubOptions = emptyList()
        numberStyleScrubOptions = emptyList()
    }

    fun supportsColorSelection(mode: EditorMode): Boolean =
        mode == EditorMode.DOODLE || mode == EditorMode.SHAPE || mode == EditorMode.TEXT || mode == EditorMode.NUMBER

    fun supportsBrushSize(mode: EditorMode): Boolean = brushModes().contains(mode)

    fun supportsModeQuickTools(mode: EditorMode): Boolean =
        supportsColorSelection(mode) || supportsBrushSize(mode) || mode == EditorMode.SHAPE || mode == EditorMode.NUMBER

    fun setupCompactColorControls() {
        binding.compactColorPanel.setOnClickListener { toggleColorScrubPopup(it) }
        binding.compactColorPanel.setOnTouchListener { view, event ->
            handleCompactColorPreviewTouch(view, event)
        }
    }

    fun setupCompactBrushControls() {
        binding.compactBrushPanel.setOnClickListener { toggleBrushScrubPopup(it) }
        binding.compactBrushPanel.setOnTouchListener { view, event ->
            handleCompactBrushTouch(view, event)
        }
    }

    fun setupCompactShapeControls() {
        binding.compactShapePreview.setShapePreview(ShapeType.RECTANGLE, Color.WHITE)
        binding.compactShapePanel.setOnClickListener { toggleShapeScrubPopup(it) }
        binding.compactShapePanel.setOnTouchListener { view, event ->
            handleCompactShapeTouch(view, event)
        }
    }

    fun setupCompactNumberStyleControls() {
        binding.compactNumberStylePanel.setOnClickListener { toggleNumberStyleScrubPopup(it) }
    }

    fun renderNumberStyleSelection(state: EditorUiState) {
        val tintColor = if (state.currentColor == Color.WHITE) {
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        } else {
            state.currentColor
        }
        binding.compactNumberStylePreview.setStylePreview(state.currentNumberStyle, tintColor)
        updateNumberStyleScrubSelection(state.currentNumberStyle, tintColor)
        binding.compactNumberStylePanel.contentDescription = numberStyleDescription(state.currentNumberStyle)
    }

    fun renderBrushControls(state: EditorUiState) {
        if (supportsBrushSize(state.currentMode)) {
            renderCompactBrushPreview(state)
        }
    }

    fun renderColorSelection(currentColor: Int) {
        val outlineColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
        val swatch = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(5f)
            setColor(currentColor)
            setStroke(dp(1f).roundToInt().coerceAtLeast(1), outlineColor)
        }
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(dpToPx(5), dpToPx(5))
            val dotColor = if (currentColor == Color.WHITE) {
                Color.argb(120, 0, 0, 0)
            } else {
                Color.argb(110, 255, 255, 255)
            }
            setColor(dotColor)
        }
        binding.compactColorPreview.background = LayerDrawable(arrayOf(swatch, dot)).apply {
            setLayerInset(1, dpToPx(10), dpToPx(2), dpToPx(1), dpToPx(9))
        }
        binding.compactColorPanel.contentDescription = colorDescription(currentColor)
        updateCompactColorSelection(currentColor)
    }

    fun renderShapeSelection(currentShapeType: ShapeType) {
        val tintColor = if (editorSession.state.currentColor == Color.WHITE) {
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        } else {
            editorSession.state.currentColor
        }
        binding.compactShapePreview.setShapePreview(currentShapeType, tintColor)
        updateShapeScrubSelection(currentShapeType, tintColor)
        binding.compactShapePanel.contentDescription = shapeDescription(currentShapeType)
    }

    fun renderModeQuickTools(state: EditorUiState) {
        val cropMode = state.currentMode == EditorMode.CROP
        val supportsQuick = supportsModeQuickTools(state.currentMode)
        if (!supportsQuick) {
            isModeQuickToolsExpanded = false
            dismissScrubPopup()
        }
        val expanded = !cropMode && isModeQuickToolsExpanded && supportsQuick
        val visibility = if (expanded) View.VISIBLE else View.GONE

        binding.modeQuickToolsHost.visibility = visibility
        binding.modeQuickToolsContainer.visibility = visibility
        binding.cropActionsGroup.visibility = if (cropMode) View.VISIBLE else View.GONE
        binding.defaultTopActionsGroup.visibility = if (cropMode) View.GONE else View.VISIBLE

        binding.compactColorPanel.visibility =
            if (expanded && supportsColorSelection(state.currentMode)) View.VISIBLE else View.GONE
        binding.compactShapePanel.visibility =
            if (expanded && state.currentMode == EditorMode.SHAPE) View.VISIBLE else View.GONE
        binding.compactNumberStylePanel.visibility =
            if (expanded && state.currentMode == EditorMode.NUMBER) View.VISIBLE else View.GONE
        binding.compactBrushPanel.visibility =
            if (expanded && supportsBrushSize(state.currentMode)) View.VISIBLE else View.GONE
        binding.compactAddTextButton.visibility =
            if (expanded && state.currentMode == EditorMode.TEXT) View.VISIBLE else View.GONE

        if (binding.compactBrushPanel.visibility != View.VISIBLE) {
            binding.editorView.setCenterBrushPreviewVisible(false)
        }

        renderModeQuickToolsContainer()
        renderCompactTextButtonState(
            binding.compactAddTextButton,
            binding.compactAddTextButton.visibility == View.VISIBLE,
        )
        binding.root.post { updateQuickToolTouchDelegates() }
    }

    private fun renderModeQuickToolsContainer() {
        val visible = listOf(
            binding.compactAddTextButton,
            binding.compactBrushPanel,
            binding.compactShapePanel,
            binding.compactNumberStylePanel,
            binding.compactColorPanel,
        ).filter { it.visibility == View.VISIBLE }

        binding.modeQuickToolsContainer.background =
            if (visible.isEmpty()) null else quickToolsContainerBackground()

        visible.forEachIndexed { index, view ->
            view.background = null
            view.alpha = 1f
            val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.marginStart = if (index == 0) 0 else dpToPx(1)
                view.layoutParams = lp
            }
        }
    }

    private fun quickToolsContainerBackground(): GradientDrawable {
        val radius = dp(18f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh),
            )
            setStroke(
                dp(0.8f).roundToInt().coerceAtLeast(1),
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant),
            )
        }
    }

    private fun renderCompactTextButtonState(button: MaterialButton, visible: Boolean) {
        if (!visible) return
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(0)
        button.setTextColor(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface),
        )
        button.strokeWidth = 0
        button.cornerRadius = 0
    }

    private fun renderCompactBrushPreview(state: EditorUiState) {
        val surfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
        val previewColor = currentBrushPreviewColor(state)
        val outlineColor = currentBrushOutlineColor(state, previewColor)
        binding.compactBrushPreview.setOutlineColor(outlineColor)
        val fill = if (state.currentMode == EditorMode.ERASER) surfaceColor else previewColor
        binding.compactBrushPreview.setBrushPreview(
            state.currentStrokeWidth,
            fill,
            state.currentMode == EditorMode.ERASER,
        )
        binding.compactBrushPanel.contentDescription = activity.getString(
            R.string.inspire_image_edit_brush_value,
            state.currentStrokeWidth.roundToInt(),
        )
        updateBrushScrubSelection(state.currentStrokeWidth, state)
    }

    private fun currentBrushPreviewColor(state: EditorUiState): Int {
        if (state.currentMode == EditorMode.MOSAIC) {
            return MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        return state.currentColor
    }

    private fun currentBrushOutlineColor(state: EditorUiState, previewColor: Int): Int {
        val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        return if (previewColor == Color.WHITE || state.currentMode == EditorMode.ERASER) {
            onSurface
        } else {
            Color.WHITE
        }
    }

    private fun handleCompactColorPreviewTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                colorScrubStartX = event.rawX
                isColorScrubbing = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isColorScrubbing) dismissScrubPopup() else view.performClick()
                isColorScrubbing = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dismissScrubPopup()
                isColorScrubbing = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.rawX - colorScrubStartX
                if (!isColorScrubbing && abs(delta) > brushDragTouchSlop) {
                    isColorScrubbing = true
                    showColorScrubPopup(view)
                }
                if (isColorScrubbing) {
                    ensureColorScrubPopup(view)
                    updateColorFromHorizontalScrub(event.rawX)
                }
                return true
            }
        }
        return false
    }

    private fun handleCompactBrushTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                brushDragStartX = event.rawX
                isBrushToolDragging = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isBrushToolDragging) {
                    binding.editorView.setCenterBrushPreviewVisible(false)
                    dismissScrubPopup()
                } else {
                    view.performClick()
                }
                isBrushToolDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                binding.editorView.setCenterBrushPreviewVisible(false)
                dismissScrubPopup()
                isBrushToolDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.rawX - brushDragStartX
                if (!isBrushToolDragging && abs(delta) > brushDragTouchSlop) {
                    isBrushToolDragging = true
                    binding.editorView.setCenterBrushPreviewVisible(true)
                    showBrushScrubPopup(view)
                }
                if (isBrushToolDragging) {
                    ensureBrushScrubPopup(view)
                    updateBrushFromHorizontalScrub(event.rawX)
                }
                return true
            }
        }
        return false
    }

    private fun handleCompactShapeTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                shapeScrubStartX = event.rawX
                isShapeScrubbing = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isShapeScrubbing) dismissScrubPopup() else view.performClick()
                isShapeScrubbing = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dismissScrubPopup()
                isShapeScrubbing = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.rawX - shapeScrubStartX
                if (!isShapeScrubbing && abs(delta) > brushDragTouchSlop) {
                    isShapeScrubbing = true
                    showShapeScrubPopup(view)
                }
                if (isShapeScrubbing) {
                    ensureShapeScrubPopup(view)
                    updateShapeFromHorizontalScrub(event.rawX)
                }
                return true
            }
        }
        return false
    }

    private fun toggleColorScrubPopup(anchor: View) {
        if (isColorScrubPopupShowing()) dismissScrubPopup() else showColorScrubPopup(anchor)
    }

    private fun toggleBrushScrubPopup(anchor: View) {
        if (isBrushScrubPopupShowing()) dismissScrubPopup() else showBrushScrubPopup(anchor)
    }

    private fun toggleShapeScrubPopup(anchor: View) {
        if (isShapeScrubPopupShowing()) dismissScrubPopup() else showShapeScrubPopup(anchor)
    }

    private fun toggleNumberStyleScrubPopup(anchor: View) {
        if (isNumberStyleScrubPopupShowing()) dismissScrubPopup() else showNumberStyleScrubPopup(anchor)
    }

    private fun isColorScrubPopupShowing(): Boolean {
        val popup = scrubPopupWindow
        return popup != null && popup.isShowing && colorScrubOptions.isNotEmpty()
    }

    private fun isBrushScrubPopupShowing(): Boolean {
        val popup = scrubPopupWindow
        return popup != null && popup.isShowing && brushScrubOptions.isNotEmpty()
    }

    private fun isShapeScrubPopupShowing(): Boolean {
        val popup = scrubPopupWindow
        return popup != null && popup.isShowing && shapeScrubOptions.isNotEmpty()
    }

    private fun isNumberStyleScrubPopupShowing(): Boolean {
        val popup = scrubPopupWindow
        return popup != null && popup.isShowing && numberStyleScrubOptions.isNotEmpty()
    }

    private fun ensureColorScrubPopup(anchor: View) {
        if (!isColorScrubPopupShowing()) showColorScrubPopup(anchor)
    }

    private fun ensureBrushScrubPopup(anchor: View) {
        if (!isBrushScrubPopupShowing()) showBrushScrubPopup(anchor)
    }

    private fun ensureShapeScrubPopup(anchor: View) {
        if (!isShapeScrubPopupShowing()) showShapeScrubPopup(anchor)
    }

    private fun showNumberStyleScrubPopup(anchor: View) {
        val tintColor = if (editorSession.state.currentColor == Color.WHITE) {
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        } else {
            editorSession.state.currentColor
        }
        val current = editorSession.state.currentNumberStyle
        numberStyleScrubOptions = showScrubPopup(
            anchor = anchor,
            options = availableNumberBadgeStyles(),
            selected = current,
            createView = { createNumberStyleScrubView(it, tintColor) },
            applySelection = { view, style, selected ->
                applyNumberStyleScrubSelection(view, style, selected, tintColor)
            },
            onOptionClick = { style ->
                if (editorSession.state.currentNumberStyle != style) {
                    editorSession.updateNumberStyle(style)
                }
            },
        )
        colorScrubOptions = emptyList()
        shapeScrubOptions = emptyList()
        brushScrubOptions = emptyList()
    }

    private fun showColorScrubPopup(anchor: View) {
        val current = editorSession.state.currentColor
        colorScrubOptions = showScrubPopup(
            anchor = anchor,
            options = availableColors(),
            selected = current,
            createView = { createColorScrubView(it) },
            applySelection = { view, color, selected ->
                applyColorScrubSelection(view, color, selected)
            },
            onOptionClick = { color ->
                if (editorSession.state.currentColor != color) {
                    editorSession.updateColor(color)
                }
            },
        )
        shapeScrubOptions = emptyList()
        brushScrubOptions = emptyList()
        numberStyleScrubOptions = emptyList()
    }

    private fun showBrushScrubPopup(anchor: View) {
        val state = editorSession.state
        val sizes = availableBrushSizes()
        val nearest = sizes.minByOrNull { abs(it - state.currentStrokeWidth) } ?: state.currentStrokeWidth
        brushScrubOptions = showScrubPopup(
            anchor = anchor,
            options = sizes,
            selected = nearest,
            createView = { createBrushScrubView(it) },
            applySelection = { view, size, selected ->
                applyBrushScrubSelection(view, size, selected, state)
            },
            onOptionClick = { size ->
                if (abs(editorSession.state.currentStrokeWidth - size) > 0.5f) {
                    editorSession.updateStrokeWidth(size)
                }
            },
        )
        colorScrubOptions = emptyList()
        shapeScrubOptions = emptyList()
    }

    private fun showShapeScrubPopup(anchor: View) {
        val tintColor = if (editorSession.state.currentColor == Color.WHITE) {
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        } else {
            editorSession.state.currentColor
        }
        val current = editorSession.state.currentShapeType
        shapeScrubOptions = showScrubPopup(
            anchor = anchor,
            options = availableShapeTypes(),
            selected = current,
            createView = { createShapeScrubView(it) },
            applySelection = { view, shape, selected ->
                applyShapeScrubSelection(view, shape, selected, tintColor)
            },
            onOptionClick = { shape ->
                if (editorSession.state.currentShapeType != shape) {
                    editorSession.updateShapeType(shape)
                }
            },
        )
        colorScrubOptions = emptyList()
        brushScrubOptions = emptyList()
        numberStyleScrubOptions = emptyList()
    }

    private fun <T> showScrubPopup(
        anchor: View,
        options: List<T>,
        selected: T,
        createView: (T) -> View,
        applySelection: (View, T, Boolean) -> Unit,
        onOptionClick: (T) -> Unit,
    ): List<ScrubOption<T>> {
        dismissScrubPopup()
        val popupBinding = PopupInspireImageEditorScrubBinding.inflate(LayoutInflater.from(activity))
        popupBinding.scrubPopupRoot.background = buildScrubPopupBackground()
        val scrubOptions = options.map { value ->
            val itemView = createView(value).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onOptionClick(value)
                    dismissScrubPopup()
                }
            }
            popupBinding.scrubOptionsRow.addView(itemView)
            ScrubOption(value, itemView)
        }
        updateScrubSelection(scrubOptions, selected, applySelection)
        val popup = PopupWindow(popupBinding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.isTouchable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dp(10f)
        popup.setOnDismissListener {
            if (scrubPopupWindow == popup) {
                scrubPopupWindow = null
                colorScrubOptions = emptyList()
                shapeScrubOptions = emptyList()
                brushScrubOptions = emptyList()
                numberStyleScrubOptions = emptyList()
            }
        }
        popupBinding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val xOff = ((anchor.width - popupBinding.root.measuredWidth) / 2f).roundToInt()
        val yOff = -(anchor.height + popupBinding.root.measuredHeight + dpToPx(8))
        popup.showAsDropDown(anchor, xOff, yOff)
        scrubPopupWindow = popup
        return scrubOptions
    }

    private fun <T> updateScrubSelection(
        options: List<ScrubOption<T>>,
        selected: T,
        applySelection: (View, T, Boolean) -> Unit,
    ) {
        options.forEach { option ->
            applySelection(option.view, option.value, option.value == selected)
        }
    }

    private fun <T> scrubValueAtRawX(options: List<ScrubOption<T>>, rawX: Float): T? {
        if (options.isEmpty()) return null
        options.firstOrNull { option ->
            val bounds = screenBoundsX(option.view) ?: return@firstOrNull false
            rawX in bounds.left..bounds.right
        }?.let { return it.value }

        return options.minByOrNull { option ->
            val bounds = screenBoundsX(option.view)
            if (bounds == null) Float.MAX_VALUE else abs(bounds.centerX() - rawX)
        }?.value
    }

    private fun screenBoundsX(view: View): HorizontalScreenBounds? {
        val width = if (view.width > 0) view.width else view.measuredWidth
        if (width <= 0) return null
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return HorizontalScreenBounds(loc[0].toFloat(), loc[0] + width.toFloat())
    }

    private fun buildScrubPopupBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f)
            setColor(
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainerHigh),
            )
            setStroke(
                dp(1f).roundToInt().coerceAtLeast(1),
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant),
            )
        }
    }

    private fun updateCompactColorSelection(currentColor: Int) {
        updateScrubSelection(colorScrubOptions, currentColor) { view, color, selected ->
            applyColorScrubSelection(view, color, selected)
        }
    }

    private fun updateBrushScrubSelection(currentWidth: Float, state: EditorUiState) {
        if (brushScrubOptions.isEmpty()) return
        val nearest = brushScrubOptions.minByOrNull {
            abs(it.value - currentWidth)
        }?.value ?: return
        updateScrubSelection(brushScrubOptions, nearest) { view, size, selected ->
            applyBrushScrubSelection(view, size, selected, state)
        }
    }

    private fun updateShapeScrubSelection(currentShapeType: ShapeType, tintColor: Int) {
        updateScrubSelection(shapeScrubOptions, currentShapeType) { view, shape, selected ->
            applyShapeScrubSelection(view, shape, selected, tintColor)
        }
    }

    private fun updateNumberStyleScrubSelection(currentStyle: NumberBadgeStyle, tintColor: Int) {
        updateScrubSelection(numberStyleScrubOptions, currentStyle) { view, style, selected ->
            applyNumberStyleScrubSelection(view, style, selected, tintColor)
        }
    }

    private fun updateColorFromHorizontalScrub(rawX: Float) {
        val color = scrubValueAtRawX(colorScrubOptions, rawX) ?: return
        if (editorSession.state.currentColor != color) {
            editorSession.updateColor(color)
        }
    }

    private fun updateBrushFromHorizontalScrub(rawX: Float) {
        val size = scrubValueAtRawX(brushScrubOptions, rawX) ?: return
        if (abs(editorSession.state.currentStrokeWidth - size) > 0.5f) {
            editorSession.updateStrokeWidth(size)
        }
    }

    private fun updateShapeFromHorizontalScrub(rawX: Float) {
        val shape = scrubValueAtRawX(shapeScrubOptions, rawX) ?: return
        if (editorSession.state.currentShapeType != shape) {
            editorSession.updateShapeType(shape)
        }
    }

    fun availableColors(): List<Int> = listOf(
        Color.BLACK,
        Color.RED,
        Color.rgb(255, 152, 0),
        Color.rgb(255, 235, 59),
        Color.rgb(76, 175, 80),
        Color.rgb(33, EditorViewMetrics.BLUE_RGB_G, 243),
        Color.rgb(156, 39, 176),
        Color.WHITE,
    )

    private fun availableBrushSizes(): List<Float> =
        listOf(6f, 12f, 20f, 32f, 48f, 72f, 96f, 120f)

    private fun availableShapeTypes(): List<ShapeType> = listOf(
        ShapeType.RECTANGLE,
        ShapeType.ROUNDED_RECTANGLE,
        ShapeType.OVAL,
        ShapeType.LINE,
        ShapeType.ARROW,
        ShapeType.DIAMOND,
        ShapeType.TRIANGLE,
    )

    private fun availableNumberBadgeStyles(): List<NumberBadgeStyle> = listOf(
        NumberBadgeStyle.FILLED_CIRCLE,
        NumberBadgeStyle.OUTLINE_CIRCLE,
        NumberBadgeStyle.FILLED_SQUARE,
    )

    private fun brushModes(): List<EditorMode> =
        listOf(EditorMode.DOODLE, EditorMode.ERASER, EditorMode.SHAPE, EditorMode.MOSAIC)

    private fun createColorScrubView(color: Int): View {
        return FrameLayout(activity).apply {
            layoutParams = ViewGroup.MarginLayoutParams(dpToPx(24), dpToPx(24)).apply {
                marginStart = dpToPx(3)
                marginEnd = dpToPx(3)
            }
            background = ContextCompat.getDrawable(activity, colorSwatchDrawableRes(color))
        }
    }

    private fun applyColorScrubSelection(view: View, color: Int, selected: Boolean) {
        view.alpha = if (selected) 1f else 0.68f
        view.scaleX = if (selected) 1.06f else 1f
        view.scaleY = if (selected) 1.06f else 1f
        view.foreground = if (selected) {
            ContextCompat.getDrawable(activity, R.drawable.inspire_editor_color_selected_ring)
        } else {
            null
        }
    }

    private fun colorSwatchDrawableRes(color: Int): Int = when (color) {
        Color.BLACK -> R.drawable.inspire_editor_color_black
        Color.RED -> R.drawable.inspire_editor_color_red
        Color.rgb(255, 152, 0) -> R.drawable.inspire_editor_color_orange
        Color.rgb(255, 235, 59) -> R.drawable.inspire_editor_color_yellow
        Color.rgb(76, 175, 80) -> R.drawable.inspire_editor_color_green
        Color.rgb(33, EditorViewMetrics.BLUE_RGB_G, 243) -> R.drawable.inspire_editor_color_blue
        Color.rgb(156, 39, 176) -> R.drawable.inspire_editor_color_purple
        else -> R.drawable.inspire_editor_color_white
    }

    private fun createBrushScrubView(sizePx: Float): View {
        val state = editorSession.state
        val previewColor = currentBrushPreviewColor(state)
        val outlineColor = currentBrushOutlineColor(state, previewColor)
        val surfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
        return FrameLayout(activity).apply {
            layoutParams = ViewGroup.MarginLayoutParams(dpToPx(30), dpToPx(28)).apply {
                marginStart = dpToPx(2)
                marginEnd = dpToPx(2)
            }
            val preview = BrushSizePreviewView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
                setOutlineColor(outlineColor)
                val fill = if (state.currentMode == EditorMode.ERASER) surfaceColor else previewColor
                setBrushPreview(sizePx, fill, state.currentMode == EditorMode.ERASER)
            }
            addView(preview)
        }
    }

    private fun applyBrushScrubSelection(
        view: View,
        sizePx: Float,
        selected: Boolean,
        state: EditorUiState,
    ) {
        var previewColor = currentBrushPreviewColor(state)
        val outlineColor = currentBrushOutlineColor(state, previewColor)
        val surfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
        (view as? FrameLayout)?.getChildAt(0)?.let { child ->
            (child as? BrushSizePreviewView)?.apply {
                setOutlineColor(outlineColor)
                if (state.currentMode == EditorMode.ERASER) previewColor = surfaceColor
                setBrushPreview(sizePx, previewColor, state.currentMode == EditorMode.ERASER)
            }
        }
        view.alpha = if (selected) 1f else 0.72f
        view.scaleX = if (selected) 1.05f else 1f
        view.scaleY = if (selected) 1.05f else 1f
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12f)
            if (selected) {
                setColor(
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer),
                )
                setStroke(
                    dp(0.8f).roundToInt().coerceAtLeast(1),
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary),
                )
            }
        }
        view.background = bg
    }

    private fun createShapeScrubView(shapeType: ShapeType): View {
        return FrameLayout(activity).apply {
            layoutParams = ViewGroup.MarginLayoutParams(dpToPx(30), dpToPx(24)).apply {
                marginStart = dpToPx(2)
                marginEnd = dpToPx(2)
            }
            val preview = ShapeTypePreviewView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                setShapePreview(shapeType, editorSession.state.currentColor)
            }
            addView(preview)
        }
    }

    private fun applyShapeScrubSelection(
        view: View,
        shapeType: ShapeType,
        selected: Boolean,
        tintColor: Int,
    ) {
        (view as? FrameLayout)?.getChildAt(0)?.let { child ->
            (child as? ShapeTypePreviewView)?.setShapePreview(shapeType, tintColor)
        }
        view.alpha = if (selected) 1f else 0.72f
        view.scaleX = if (selected) 1.05f else 1f
        view.scaleY = if (selected) 1.05f else 1f
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12f)
            if (selected) {
                setColor(
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer),
                )
                setStroke(
                    dp(0.8f).roundToInt().coerceAtLeast(1),
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary),
                )
            }
        }
        view.background = bg
    }

    fun updateQuickToolTouchDelegates() {
        val bottomBarCard: MaterialCardView = binding.bottomBarCard
        val root = binding.root
        val barRect = descendantRectInAncestor(bottomBarCard, root)
        val bottomBarTop = barRect?.top ?: root.height

        val composite = CompositeTouchDelegate(root)
        addQuickToolTouchDelegate(composite, binding.compactAddTextButton, bottomBarTop)
        addQuickToolTouchDelegate(composite, binding.compactBrushPanel, bottomBarTop)
        addQuickToolTouchDelegate(composite, binding.compactShapePanel, bottomBarTop)
        addQuickToolTouchDelegate(composite, binding.compactColorPanel, bottomBarTop)
        root.touchDelegate = if (composite.isEmpty()) null else composite
    }

    private fun addQuickToolTouchDelegate(
        composite: CompositeTouchDelegate,
        view: View,
        bottomBarTop: Int,
    ) {
        if (view.visibility != View.VISIBLE) return
        val rect = descendantRectInAncestor(view, binding.root) ?: return
        rect.left -= dpToPx(6)
        rect.right += dpToPx(6)
        rect.top -= dpToPx(16)
        rect.bottom = maxOf(rect.bottom + dpToPx(14), bottomBarTop)
        rect.left = maxOf(rect.left, 0)
        rect.top = maxOf(rect.top, 0)
        rect.right = minOf(rect.right, binding.root.width)
        rect.bottom = minOf(rect.bottom, binding.root.height)
        composite.add(TouchDelegate(rect, view))
    }

    private fun descendantRectInAncestor(descendant: View, ancestor: View): Rect? {
        val width = if (descendant.width > 0) descendant.width else descendant.measuredWidth
        val height = if (descendant.height > 0) descendant.height else descendant.measuredHeight
        val ancestorWidth = if (ancestor.width > 0) ancestor.width else ancestor.measuredWidth
        val ancestorHeight = if (ancestor.height > 0) ancestor.height else ancestor.measuredHeight
        if (width <= 0 || height <= 0 || ancestorWidth <= 0 || ancestorHeight <= 0) return null
        val descLoc = IntArray(2)
        val ancLoc = IntArray(2)
        descendant.getLocationOnScreen(descLoc)
        ancestor.getLocationOnScreen(ancLoc)
        val left = descLoc[0] - ancLoc[0]
        val top = descLoc[1] - ancLoc[1]
        return Rect(left, top, left + width, top + height)
    }

    private fun colorDescription(color: Int): String {
        val res = when (color) {
            Color.BLACK -> R.string.inspire_image_edit_color_black
            Color.RED -> R.string.inspire_image_edit_color_red
            Color.rgb(255, 152, 0) -> R.string.inspire_image_edit_color_orange
            Color.rgb(255, 235, 59) -> R.string.inspire_image_edit_color_yellow
            Color.rgb(76, 175, 80) -> R.string.inspire_image_edit_color_green
            Color.rgb(33, EditorViewMetrics.BLUE_RGB_G, 243) -> R.string.inspire_image_edit_color_blue
            Color.rgb(156, 39, 176) -> R.string.inspire_image_edit_color_purple
            else -> R.string.inspire_image_edit_color_white
        }
        return activity.getString(res)
    }

    private fun shapeDescription(shapeType: ShapeType): String {
        val res = when (shapeType) {
            ShapeType.RECTANGLE -> R.string.inspire_image_edit_shape_rect
            ShapeType.ROUNDED_RECTANGLE -> R.string.inspire_image_edit_shape_round_rect
            ShapeType.OVAL -> R.string.inspire_image_edit_shape_oval
            ShapeType.LINE -> R.string.inspire_image_edit_shape_line
            ShapeType.ARROW -> R.string.inspire_image_edit_shape_arrow
            ShapeType.DIAMOND -> R.string.inspire_image_edit_shape_diamond
            ShapeType.TRIANGLE -> R.string.inspire_image_edit_shape_triangle
        }
        return activity.getString(res)
    }

    private fun numberStyleDescription(style: NumberBadgeStyle): String {
        val res = when (style) {
            NumberBadgeStyle.FILLED_CIRCLE -> R.string.inspire_image_edit_number_style_filled_circle
            NumberBadgeStyle.OUTLINE_CIRCLE -> R.string.inspire_image_edit_number_style_outline_circle
            NumberBadgeStyle.FILLED_SQUARE -> R.string.inspire_image_edit_number_style_filled_square
        }
        return activity.getString(res)
    }

    private fun createNumberStyleScrubView(style: NumberBadgeStyle, tintColor: Int): View {
        return FrameLayout(activity).apply {
            layoutParams = ViewGroup.MarginLayoutParams(dpToPx(30), dpToPx(28)).apply {
                marginStart = dpToPx(2)
                marginEnd = dpToPx(2)
            }
            addView(
                NumberBadgeStylePreviewView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
                    setStylePreview(style, tintColor)
                },
            )
        }
    }

    private fun applyNumberStyleScrubSelection(
        view: View,
        style: NumberBadgeStyle,
        selected: Boolean,
        tintColor: Int,
    ) {
        (view as? FrameLayout)?.getChildAt(0)?.let { child ->
            (child as? NumberBadgeStylePreviewView)?.setStylePreview(style, tintColor)
        }
        view.alpha = if (selected) 1f else 0.72f
        view.scaleX = if (selected) 1.05f else 1f
        view.scaleY = if (selected) 1.05f else 1f
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12f)
            if (selected) {
                setColor(
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondaryContainer),
                )
                setStroke(
                    dp(0.8f).roundToInt().coerceAtLeast(1),
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary),
                )
            }
        }
        view.background = bg
    }

    private fun dp(value: Float): Float = EditorViewMetrics.dp(activity, value)

    private fun dpToPx(value: Int): Int = dp(value.toFloat()).roundToInt()
}
