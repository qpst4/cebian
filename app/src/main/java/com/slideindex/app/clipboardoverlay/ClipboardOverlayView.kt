package com.slideindex.app.clipboardoverlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Region
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.slideindex.app.R
import kotlin.math.max
import kotlin.math.min

/**
 * Port of AOSP `ClipboardOverlayView`: preview, chips, enter/exit/minimize animations.
 */
class ClipboardOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DraggableConstraintLayout(context, attrs, defStyleAttr) {

    interface ClipboardOverlayCallbacks : SwipeDismissCallbacks {
        fun onDismissButtonTapped()
        fun onRemoteCopyButtonTapped()
        fun onShareButtonTapped()
        fun onPreviewTapped()
        fun onMinimizedViewTapped()
        fun onTapOutside()
    }

    private val displayMetrics = context.resources.displayMetrics
    private val accessibilityManager =
        requireNotNull(context.getSystemService(AccessibilityManager::class.java))
    private val actionChips = ArrayList<View>()

    private lateinit var clipboardPreview: View
    private lateinit var imagePreview: ImageView
    private lateinit var textPreview: TextView
    private lateinit var hiddenPreview: TextView
    private lateinit var minimizedPreview: LinearLayout
    private lateinit var previewBorder: View
    private lateinit var shareChip: View
    private lateinit var remoteCopyChip: View
    private lateinit var actionContainerBackground: View
    private lateinit var indicationContainer: View
    private lateinit var indicationText: TextView
    private lateinit var dismissButton: View
    private lateinit var actionContainer: LinearLayout
    private var clipboardCallbacks: ClipboardOverlayCallbacks? = null
    private var chipBackgroundColor: Int? = null
    private var chipForegroundColor: Int? = null
    var previewSizePx: Int = 0
        private set

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            clipboardCallbacks?.onTapOutside()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onFinishInflate() {
        actionContainerBackground = requireViewById(R.id.actions_container_background)
        actionContainer = requireViewById(R.id.actions)
        clipboardPreview = requireViewById(R.id.clipboard_preview)
        previewBorder = requireViewById(R.id.preview_border)
        imagePreview = requireViewById(R.id.image_preview)
        textPreview = requireViewById(R.id.text_preview)
        hiddenPreview = requireViewById(R.id.hidden_preview)
        minimizedPreview = requireViewById(R.id.minimized_preview)
        shareChip = requireViewById(R.id.share_chip)
        remoteCopyChip = requireViewById(R.id.remote_copy_chip)
        dismissButton = requireViewById(R.id.dismiss_button)
        indicationContainer = requireViewById(R.id.indication_container)
        indicationText = indicationContainer.findViewById(R.id.indication_text)
        bindDefaultActionChips()
        previewSizePx = resources.getDimensionPixelSize(R.dimen.clipboard_preview_size)
        textPreview.viewTreeObserver.addOnPreDrawListener {
            val availableHeight = textPreview.height -
                (textPreview.paddingTop + textPreview.paddingBottom)
            textPreview.maxLines = max(availableHeight / textPreview.lineHeight, 1)
            true
        }
        super.onFinishInflate()
    }

    private fun bindDefaultActionChips() {
        ClipboardOverlayChipBinder.bind(
            shareChip,
            icon = context.getDrawable(R.drawable.ic_clipboard_overlay_share),
            label = null,
            description = context.getString(R.string.clipboard_overlay_share),
            tint = true,
        ) { clipboardCallbacks?.onShareButtonTapped() }
        ClipboardOverlayChipBinder.bind(
            remoteCopyChip,
            icon = context.getDrawable(R.drawable.ic_clipboard_overlay_devices),
            label = null,
            description = context.getString(R.string.clipboard_overlay_send_nearby_description),
            tint = true,
        ) { clipboardCallbacks?.onRemoteCopyButtonTapped() }
    }

    override fun setCallbacks(callbacks: SwipeDismissCallbacks) {
        super.setCallbacks(callbacks)
        val clipboard = callbacks as ClipboardOverlayCallbacks
        dismissButton.setOnClickListener { clipboard.onDismissButtonTapped() }
        clipboardPreview.setOnClickListener { clipboard.onPreviewTapped() }
        minimizedPreview.setOnClickListener { clipboard.onMinimizedViewTapped() }
        clipboardCallbacks = clipboard
    }

    fun applyAppColorScheme(scheme: ColorScheme) {
        val secondaryContainer = scheme.secondaryContainer.toArgb()
        val onSecondaryContainer = scheme.onSecondaryContainer.toArgb()
        val surfaceBright = scheme.surfaceBright.toArgb()
        chipBackgroundColor = scheme.secondary.toArgb()
        chipForegroundColor = scheme.onSecondary.toArgb()
        textPreview.setBackgroundColor(secondaryContainer)
        textPreview.setTextColor(onSecondaryContainer)
        hiddenPreview.setBackgroundColor(secondaryContainer)
        hiddenPreview.setTextColor(onSecondaryContainer)
        previewBorder.backgroundTintList = ColorStateList.valueOf(surfaceBright)
        actionContainerBackground.backgroundTintList = ColorStateList.valueOf(surfaceBright)
        minimizedPreview.backgroundTintList = ColorStateList.valueOf(secondaryContainer)
        indicationContainer.backgroundTintList = ColorStateList.valueOf(scheme.inverseSurface.toArgb())
        indicationText.setTextColor(scheme.inverseOnSurface.toArgb())
        val dismissImage = dismissButton.findViewById<ImageView>(R.id.dismiss_image)
        dismissImage?.backgroundTintList = ColorStateList.valueOf(scheme.primary.toArgb())
        dismissImage?.imageTintList = ColorStateList.valueOf(scheme.onPrimary.toArgb())
        for (i in 0 until minimizedPreview.childCount) {
            (minimizedPreview.getChildAt(i) as? ImageView)?.imageTintList =
                ColorStateList.valueOf(onSecondaryContainer)
        }
        tintChip(shareChip)
        tintChip(remoteCopyChip)
    }

    private fun tintChip(chip: View) {
        val background = chipBackgroundColor ?: return
        val foreground = chipForegroundColor ?: return
        chip.backgroundTintList = ColorStateList.valueOf(background)
        chip.findViewById<ImageView>(R.id.overlay_action_chip_icon)
            ?.imageTintList = ColorStateList.valueOf(foreground)
        chip.findViewById<TextView>(R.id.overlay_action_chip_text)?.setTextColor(foreground)
    }

    fun setEditAccessibilityAction(editable: Boolean) {
        if (editable) {
            ViewCompat.replaceAccessibilityAction(
                clipboardPreview,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.clipboard_overlay_open_pick),
                null,
            )
        } else {
            ViewCompat.replaceAccessibilityAction(
                clipboardPreview,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                null,
                null,
            )
        }
    }

    fun setIndicationText(text: CharSequence) {
        indicationText.text = text
        indicationContainer.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    fun setMinimized(minimized: Boolean) {
        if (minimized) {
            minimizedPreview.visibility = View.VISIBLE
            clipboardPreview.visibility = View.GONE
            previewBorder.visibility = View.GONE
            actionContainer.visibility = View.GONE
            actionContainerBackground.visibility = View.GONE
        } else {
            minimizedPreview.visibility = View.GONE
            clipboardPreview.visibility = View.VISIBLE
            previewBorder.visibility = View.VISIBLE
            actionContainer.visibility = View.VISIBLE
        }
    }

    fun setInsets(insets: WindowInsets, orientation: Int) {
        val margins = computeMargins(insets, orientation)
        if (paddingLeft == margins.left &&
            paddingTop == margins.top &&
            paddingRight == margins.right &&
            paddingBottom == margins.bottom
        ) {
            return
        }
        setPadding(margins.left, margins.top, margins.right, margins.bottom)
        requestLayout()
    }

    fun isInTouchRegion(x: Int, y: Int): Boolean {
        val touchRegion = Region()
        val tmpRect = Rect()
        previewBorder.copyBoundsOnScreen(tmpRect)
        insetSwipePadding(tmpRect)
        touchRegion.op(tmpRect, Region.Op.UNION)
        actionContainerBackground.copyBoundsOnScreen(tmpRect)
        insetSwipePadding(tmpRect)
        touchRegion.op(tmpRect, Region.Op.UNION)
        minimizedPreview.copyBoundsOnScreen(tmpRect)
        insetSwipePadding(tmpRect)
        touchRegion.op(tmpRect, Region.Op.UNION)
        dismissButton.copyBoundsOnScreen(tmpRect)
        touchRegion.op(tmpRect, Region.Op.UNION)
        return touchRegion.contains(x, y)
    }

    fun setRemoteCopyVisibility(visible: Boolean) {
        if (visible) {
            remoteCopyChip.visibility = View.VISIBLE
            actionContainerBackground.visibility = View.VISIBLE
        } else {
            remoteCopyChip.visibility = View.GONE
        }
    }

    fun showDefaultTextPreview() {
        showTextPreview(context.getString(R.string.clipboard_overlay_text_copied), hidden = false)
    }

    fun showTextPreview(text: CharSequence, hidden: Boolean) {
        val textView = if (hidden) hiddenPreview else textPreview
        showSinglePreview(textView)
        textView.text = text.subSequence(0, min(500, text.length))
        updateTextSize(text, textView)
        textView.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                updateTextSize(text, textView)
            }
        }
    }

    fun getPreview(): View = clipboardPreview

    fun showImagePreview(thumbnail: Bitmap?) {
        if (thumbnail == null) {
            hiddenPreview.text = context.getString(R.string.clipboard_overlay_text_hidden)
            showSinglePreview(hiddenPreview)
        } else {
            imagePreview.setImageBitmap(thumbnail)
            showSinglePreview(imagePreview)
        }
    }

    fun showShareChip() {
        shareChip.visibility = View.VISIBLE
        actionContainerBackground.visibility = View.VISIBLE
    }

    fun reset() {
        translationX = 0f
        alpha = 0f
        actionContainerBackground.visibility = View.GONE
        indicationContainer.visibility = View.GONE
        dismissButton.visibility = View.GONE
        shareChip.visibility = View.GONE
        remoteCopyChip.visibility = View.GONE
        setEditAccessibilityAction(false)
        resetActionChips()
    }

    fun resetActionChips() {
        for (chip in actionChips) {
            actionContainer.removeView(chip)
        }
        actionChips.clear()
    }

    fun getMinimizedFadeoutAnimation(): Animator {
        val anim = ObjectAnimator.ofFloat(minimizedPreview, "alpha", 1f, 0f)
        anim.duration = 66
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                minimizedPreview.visibility = View.GONE
                minimizedPreview.alpha = 1f
            }
        })
        return anim
    }

    fun getEnterAnimation(): Animator {
        if (accessibilityManager.isEnabled) {
            dismissButton.visibility = View.VISIBLE
        }
        val linearInterpolator = LinearInterpolator()
        val scaleInterpolator = PathInterpolator(0f, 0f, 0f, 1f)
        val enterAnim = AnimatorSet()
        val rootAnim = ValueAnimator.ofFloat(0f, 1f)
        rootAnim.interpolator = linearInterpolator
        rootAnim.duration = 66
        rootAnim.addUpdateListener { animation -> alpha = animation.animatedFraction }
        val scaleAnim = ValueAnimator.ofFloat(0f, 1f)
        scaleAnim.interpolator = scaleInterpolator
        scaleAnim.duration = 333
        scaleAnim.addUpdateListener { animation ->
            val previewScale = lerp(0.9f, 1f, animation.animatedFraction)
            minimizedPreview.scaleX = previewScale
            minimizedPreview.scaleY = previewScale
            clipboardPreview.scaleX = previewScale
            clipboardPreview.scaleY = previewScale
            previewBorder.scaleX = previewScale
            previewBorder.scaleY = previewScale
            val pivotX = clipboardPreview.width / 2f + clipboardPreview.x
            actionContainerBackground.pivotX = pivotX - actionContainerBackground.x
            actionContainer.pivotX = pivotX - (actionContainer.parent as View).x
            val actionsScaleX = lerp(0.7f, 1f, animation.animatedFraction)
            val actionsScaleY = lerp(0.9f, 1f, animation.animatedFraction)
            actionContainer.scaleX = actionsScaleX
            actionContainer.scaleY = actionsScaleY
            actionContainerBackground.scaleX = actionsScaleX
            actionContainerBackground.scaleY = actionsScaleY
        }
        val alphaAnim = ValueAnimator.ofFloat(0f, 1f)
        alphaAnim.interpolator = linearInterpolator
        alphaAnim.duration = 283
        alphaAnim.addUpdateListener { animation ->
            val a = animation.animatedFraction
            minimizedPreview.alpha = a
            clipboardPreview.alpha = a
            previewBorder.alpha = a
            dismissButton.alpha = a
            actionContainer.alpha = a
        }
        minimizedPreview.alpha = 0f
        actionContainer.alpha = 0f
        previewBorder.alpha = 0f
        clipboardPreview.alpha = 0f
        enterAnim.play(rootAnim).with(scaleAnim)
        enterAnim.play(alphaAnim).after(50).after(rootAnim)
        enterAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                alpha = 1f
            }
        })
        return enterAnim
    }

    fun getFadeOutAnimation(): Animator {
        val alphaAnim = ValueAnimator.ofFloat(1f, 0f)
        alphaAnim.addUpdateListener { animation ->
            val a = animation.animatedValue as Float
            actionContainer.alpha = a
            actionContainerBackground.alpha = a
            previewBorder.alpha = a
            dismissButton.alpha = a
        }
        alphaAnim.duration = 300
        return alphaAnim
    }

    fun getExitAnimation(): Animator {
        val linearInterpolator = LinearInterpolator()
        val scaleInterpolator = PathInterpolator(0.3f, 0f, 1f, 1f)
        val exitAnim = AnimatorSet()
        val rootAnim = ValueAnimator.ofFloat(0f, 1f)
        rootAnim.interpolator = linearInterpolator
        rootAnim.duration = 100
        rootAnim.addUpdateListener { anim -> alpha = 1f - anim.animatedFraction }
        val scaleAnim = ValueAnimator.ofFloat(0f, 1f)
        scaleAnim.interpolator = scaleInterpolator
        scaleAnim.duration = 250
        scaleAnim.addUpdateListener { animation ->
            val previewScale = lerp(1f, 0.9f, animation.animatedFraction)
            minimizedPreview.scaleX = previewScale
            minimizedPreview.scaleY = previewScale
            clipboardPreview.scaleX = previewScale
            clipboardPreview.scaleY = previewScale
            previewBorder.scaleX = previewScale
            previewBorder.scaleY = previewScale
            val pivotX = clipboardPreview.width / 2f + clipboardPreview.x
            actionContainerBackground.pivotX = pivotX - actionContainerBackground.x
            actionContainer.pivotX = pivotX - (actionContainer.parent as View).x
            val actionScaleX = lerp(1f, 0.8f, animation.animatedFraction)
            val actionScaleY = lerp(1f, 0.9f, animation.animatedFraction)
            actionContainer.scaleX = actionScaleX
            actionContainer.scaleY = actionScaleY
            actionContainerBackground.scaleX = actionScaleX
            actionContainerBackground.scaleY = actionScaleY
        }
        val alphaAnim = ValueAnimator.ofFloat(0f, 1f)
        alphaAnim.interpolator = linearInterpolator
        alphaAnim.duration = 166
        alphaAnim.addUpdateListener { animation ->
            val a = 1f - animation.animatedFraction
            minimizedPreview.alpha = a
            clipboardPreview.alpha = a
            previewBorder.alpha = a
            dismissButton.alpha = a
            actionContainer.alpha = a
        }
        exitAnim.play(alphaAnim).with(scaleAnim)
        exitAnim.play(rootAnim).after(150).after(alphaAnim)
        return exitAnim
    }

    fun setActionChip(
        icon: android.graphics.drawable.Drawable?,
        label: CharSequence,
        description: CharSequence,
        onClick: () -> Unit,
    ) {
        actionContainerBackground.visibility = View.VISIBLE
        val chip = constructShelfActionChip(icon, label, description, onClick)
        actionContainer.addView(chip)
        actionChips.add(chip)
    }

    private fun showSinglePreview(v: View) {
        textPreview.visibility = View.GONE
        imagePreview.visibility = View.GONE
        hiddenPreview.visibility = View.GONE
        minimizedPreview.visibility = View.GONE
        v.visibility = View.VISIBLE
    }

    private fun constructShelfActionChip(
        icon: android.graphics.drawable.Drawable?,
        label: CharSequence,
        description: CharSequence,
        onClick: () -> Unit,
    ): View {
        val chip = LayoutInflater.from(context).inflate(R.layout.shelf_action_chip, actionContainer, false)
        ClipboardOverlayChipBinder.bind(
            chip,
            icon = icon,
            label = label,
            description = description,
            tint = true,
        ) { onClick() }
        tintChip(chip)
        return chip
    }

    private fun insetSwipePadding(rect: Rect) {
        val pad = dpToPx(-SWIPE_PADDING_DP).toInt()
        rect.inset(pad, pad)
    }

    private fun dpToPx(dp: Float): Float =
        dp * displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()

    companion object {
        private const val FONT_SEARCH_STEP_PX = 4

        private fun updateTextSize(text: CharSequence, textView: TextView) {
            val paint = Paint(textView.paint)
            val res = textView.resources
            val minFontSize = res.getDimensionPixelSize(R.dimen.clipboard_overlay_min_font).toFloat()
            val maxFontSize = res.getDimensionPixelSize(R.dimen.clipboard_overlay_max_font).toFloat()
            if (isOneWord(text) && fitsInView(text, textView, paint, minFontSize)) {
                var fontSizePx = minFontSize
                while (fontSizePx + FONT_SEARCH_STEP_PX < maxFontSize &&
                    fitsInView(text, textView, paint, fontSizePx + FONT_SEARCH_STEP_PX)
                ) {
                    fontSizePx += FONT_SEARCH_STEP_PX
                }
                textView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                textView.gravity = Gravity.CENTER
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx.toInt().toFloat())
            } else {
                textView.setAutoSizeTextTypeUniformWithConfiguration(
                    minFontSize.toInt(),
                    maxFontSize.toInt(),
                    FONT_SEARCH_STEP_PX,
                    TypedValue.COMPLEX_UNIT_PX,
                )
                textView.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
        }

        private fun fitsInView(
            text: CharSequence,
            textView: TextView,
            paint: Paint,
            fontSizePx: Float,
        ): Boolean {
            paint.textSize = fontSizePx
            val size = paint.measureText(text.toString())
            val availableWidth = textView.width - textView.paddingLeft - textView.paddingRight
            return size < availableWidth
        }

        private fun isOneWord(text: CharSequence): Boolean =
            text.toString().split("\\s+".toRegex(), limit = 2).size == 1

        private fun computeMargins(insets: WindowInsets, orientation: Int): Rect {
            val cutout = insets.displayCutout
            val navBarInsets = insets.getInsets(WindowInsets.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsets.Type.ime())
            if (cutout == null) {
                return Rect(0, 0, 0, max(imeInsets.bottom, navBarInsets.bottom))
            }
            val waterfall = cutout.waterfallInsets
            return if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                Rect(
                    waterfall.left,
                    max(cutout.safeInsetTop, waterfall.top),
                    waterfall.right,
                    max(
                        imeInsets.bottom,
                        max(cutout.safeInsetBottom, max(navBarInsets.bottom, waterfall.bottom)),
                    ),
                )
            } else {
                Rect(
                    waterfall.left,
                    waterfall.top,
                    waterfall.right,
                    max(imeInsets.bottom, max(navBarInsets.bottom, waterfall.bottom)),
                )
            }
        }

        private fun lerp(start: Float, stop: Float, amount: Float): Float =
            start + (stop - start) * amount
    }
}
