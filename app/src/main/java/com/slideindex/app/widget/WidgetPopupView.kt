package com.slideindex.app.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.service.WidgetConfigureTrampolineActivity
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.settings.AppSettings
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class WidgetPopupRootLayout(
    context: Context,
    private val onDismissOutside: () -> Unit,
) : FrameLayout(context) {

    internal var cardView: WidgetPopupCardLayout? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val card = cardView ?: return super.onTouchEvent(event)
        val x = event.x
        val y = event.y
        if (x < card.left || x > card.right || y < card.top || y > card.bottom) {
            if (event.action == MotionEvent.ACTION_UP) {
                onDismissOutside()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}

@SuppressLint("ViewConstructor")
class WidgetPopupCardLayout(
    context: Context,
    private val hostContext: Context,
    private val deps: OverlayDependencies,
    private var settings: AppSettings,
    private val onDismiss: () -> Unit,
    private val onSavePages: (List<WidgetPanelPage>) -> Unit,
) : FrameLayout(context) {

    private var pages: MutableList<WidgetPanelPage> =
        WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
            .map { WidgetPanelGridLogic.fitPageToGrid(it) }
            .toMutableList()

    private var editMode = false
    private var currentPageIndex = 0

    private val headerTextView: TextView
    private val recyclerView: RecyclerView
    private val adapter: WidgetPageAdapter
    private val dotsLayout: LinearLayout
    private val fabContainer: LinearLayout
    private val addFab: ImageView
    private val closeFab: ImageView

    private val density = resources.displayMetrics.density
    private val cornerRadiusPx = 24f * density

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx)
            }
        }
        clipToOutline = true

        applyCardBackground()

        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = (PANEL_PADDING_DP * density).roundToInt()
            setPadding(pad, pad, pad, pad)
        }
        addView(
            contentContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        headerTextView = TextView(context).apply {
            textSize = 12f
            setTextColor(0x8CFFFFFF.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            val bottomPad = (4f * density).roundToInt()
            setPadding(0, 0, 0, bottomPad)
        }
        contentContainer.addView(headerTextView)

        val layoutMetrics = WidgetPanelLayoutMetrics.compute(
            screenWidthPx = resources.displayMetrics.widthPixels,
            page = pages.getOrElse(0) { WidgetPanelPage() },
            density = density,
        )

        val recyclerContainer = FrameLayout(context).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    outline.setRoundRect(0, 0, v.width, v.height, 12f * density)
                }
            }
            clipToOutline = true
        }
        contentContainer.addView(
            recyclerContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                layoutMetrics.viewportHeightPx,
            ),
        )

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            PagerSnapHelper().attachToRecyclerView(this)
            overScrollMode = OVER_SCROLL_NEVER
            setHasFixedSize(true)
        }
        recyclerContainer.addView(
            recyclerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        adapter = WidgetPageAdapter()
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val pos = lm.findFirstCompletelyVisibleItemPosition()
                    if (pos >= 0 && pos < pages.size) {
                        currentPageIndex = pos
                        updateDots()
                        updateHeader()
                    }
                }
            }
        })

        dotsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val topPad = (8f * density).roundToInt()
            setPadding(0, topPad, 0, 0)
        }
        contentContainer.addView(dotsLayout)

        fabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        val fabSize = (44f * density).roundToInt()
        val fabMargin = (8f * density).roundToInt()
        val fabSpacing = (10f * density).roundToInt()

        addFab = createFabButton(R.drawable.ic_widget_add, 0xFF3B82F6.toInt()) {
            launchWidgetPicker(currentPageIndex)
        }
        val addLp = LinearLayout.LayoutParams(fabSize, fabSize).apply {
            bottomMargin = fabSpacing
        }
        fabContainer.addView(addFab, addLp)

        closeFab = createFabButton(R.drawable.ic_widget_close, 0xFF374151.toInt()) {
            setEditMode(false)
        }
        fabContainer.addView(closeFab, LinearLayout.LayoutParams(fabSize, fabSize))

        val fabContainerLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = fabMargin
            bottomMargin = fabMargin
        }
        addView(fabContainer, fabContainerLp)

        updateHeader()
        updateDots()
    }

    fun applyCardBackground() {
        val page = pages.getOrElse(currentPageIndex) { WidgetPanelPage() }
        val surfaceColor = WidgetPanelUi.panelSurfaceColorInt(
            overlayAlpha = page.overlayAlpha,
            editMode = editMode,
            blurEnabled = settings.widgetPanelBlurEnabled,
        )
        val strokeColor = if (settings.widgetPanelBlurEnabled) 0x29FFFFFF.toInt() else 0x14FFFFFF

        if (settings.widgetPanelBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setupBackgroundBlur(
                cornerRadiusPx = cornerRadiusPx,
                blurRadiusPx = (settings.widgetPanelBlurRadiusDp * density).roundToInt(),
                tintColor = surfaceColor,
            )
            val borderDrawable = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = cornerRadiusPx
                setStroke((1f * density).roundToInt().coerceAtLeast(1), strokeColor)
            }
            foreground = borderDrawable
        } else {
            val cardDrawable = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = cornerRadiusPx
                setStroke((1f * density).roundToInt().coerceAtLeast(1), strokeColor)
            }
            background = cardDrawable
            foreground = null
        }
    }

    private fun setupBackgroundBlur(cornerRadiusPx: Float, blurRadiusPx: Int, tintColor: Int) {
        runCatching {
            val getViewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
            getViewRootImplMethod.isAccessible = true
            val viewRootImpl = getViewRootImplMethod.invoke(this) ?: return
            val createMethod = viewRootImpl.javaClass.getMethod("createBackgroundBlurDrawable")
            val blurDrawable = createMethod.invoke(viewRootImpl) as? android.graphics.drawable.Drawable ?: return
            val setBlurRadius = blurDrawable.javaClass.getMethod("setBlurRadius", java.lang.Integer.TYPE)
            val setCornerRadius = blurDrawable.javaClass.getMethod("setCornerRadius", java.lang.Float.TYPE)
            val setColor = blurDrawable.javaClass.getMethod("setColor", java.lang.Integer.TYPE)
            setBlurRadius.invoke(blurDrawable, blurRadiusPx)
            setCornerRadius.invoke(blurDrawable, cornerRadiusPx)
            setColor.invoke(blurDrawable, tintColor)
            background = blurDrawable
        }.onFailure { Log.w(TAG, "setupBackgroundBlur failed", it) }
    }

    fun ensureBackgroundBlurAttached() {
        applyCardBackground()
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        applyCardBackground()
    }

    private fun createFabButton(iconRes: Int, bgColor: Int, onClick: () -> Unit): ImageView {
        val fab = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (10f * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            elevation = 6f * density
            setOnClickListener { onClick() }
        }
        return fab
    }

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        if (enabled) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        updateHeader()
        fabContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun updateHeader() {
        val page = pages.getOrElse(currentPageIndex) { WidgetPanelPage() }
        when {
            editMode -> {
                headerTextView.text = context.getString(R.string.widget_panel_edit_mode)
                headerTextView.visibility = View.VISIBLE
            }
            page.items.isEmpty() -> {
                headerTextView.text = context.getString(R.string.widget_panel_edit_hint)
                headerTextView.visibility = View.VISIBLE
            }
            else -> {
                headerTextView.visibility = View.GONE
            }
        }
    }

    private fun updateDots() {
        dotsLayout.removeAllViews()
        if (pages.size <= 1) {
            dotsLayout.visibility = View.GONE
            return
        }
        dotsLayout.visibility = View.VISIBLE
        for (i in 0 until pages.size) {
            val dot = View(context).apply {
                val isSelected = i == currentPageIndex
                val w = (if (isSelected) 12f else 6f) * density
                val h = 6f * density
                val dotColor = if (isSelected) 0xB3FFFFFF.toInt() else 0x40FFFFFF
                background = GradientDrawable().apply {
                    cornerRadius = 3f * density
                    setColor(dotColor)
                }
                val lp = LinearLayout.LayoutParams(w.roundToInt(), h.roundToInt()).apply {
                    marginStart = (3f * density).roundToInt()
                    marginEnd = (3f * density).roundToInt()
                }
                layoutParams = lp
            }
            dotsLayout.addView(dot)
        }
    }

    fun updatePages(updated: List<WidgetPanelPage>) {
        pages = WidgetPanelDefaults.effectivePages(updated)
            .map { WidgetPanelGridLogic.fitPageToGrid(it) }
            .toMutableList()
        if (currentPageIndex >= pages.size) {
            currentPageIndex = (pages.size - 1).coerceAtLeast(0)
        }
        adapter.notifyDataSetChanged()
        updateDots()
        updateHeader()
        applyCardBackground()
    }

    private fun persist(updated: List<WidgetPanelPage>) {
        pages = updated.toMutableList()
        onSavePages(updated)
        updateDots()
        updateHeader()
    }

    private fun launchWidgetPicker(pageIndex: Int) {
        WidgetPickerTrampoline.launch(
            context = hostContext,
            pageIndex = pageIndex,
            pagesProvider = { pages },
            onAdded = { appWidgetId ->
                val updated = WidgetPanelMutator.addWidgetToPage(
                    hostContext,
                    pages,
                    pageIndex,
                    appWidgetId,
                )
                if (updated != null) {
                    persist(updated)
                    adapter.notifyDataSetChanged()
                }
            },
            onAppAdded = { packageName, className, label ->
                val updated = WidgetPanelMutator.addAppToPage(
                    hostContext,
                    pages,
                    pageIndex,
                    packageName,
                    className,
                    label,
                )
                if (updated != null) {
                    persist(updated)
                    adapter.notifyDataSetChanged()
                }
            },
            onShortcutAdded = { packageName, shortcutId, label, intentUri ->
                val updated = WidgetPanelMutator.addShortcutToPage(
                    hostContext,
                    pages,
                    pageIndex,
                    packageName,
                    shortcutId,
                    label,
                    intentUri,
                )
                if (updated != null) {
                    persist(updated)
                    adapter.notifyDataSetChanged()
                }
            },
            onActionAdded = { actionPayload, label ->
                val updated = WidgetPanelMutator.addActionToPage(
                    hostContext,
                    pages,
                    pageIndex,
                    actionPayload,
                    label,
                )
                if (updated != null) {
                    persist(updated)
                    adapter.notifyDataSetChanged()
                }
            },
            onPagesChanged = { updatedPages ->
                this.pages = updatedPages.toMutableList()
                persist(updatedPages)
                adapter.notifyDataSetChanged()
            },
        )
    }

    private inner class WidgetPageAdapter : RecyclerView.Adapter<WidgetPageViewHolder>() {

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetPageViewHolder {
            val scrollView = NestedScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val canvasLayout = WidgetCanvasLayout(context).apply {
                val pad = (PANEL_INNER_PADDING_DP * density).roundToInt()
                setPadding(pad, pad, pad, pad)
            }
            scrollView.addView(
                canvasLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            return WidgetPageViewHolder(scrollView, canvasLayout)
        }

        override fun onBindViewHolder(holder: WidgetPageViewHolder, position: Int) {
            val page = pages[position]
            val canvas = holder.canvasLayout

            canvas.onLongPressBlank = { setEditMode(true) }
            canvas.onTapBlank = { if (!editMode) onDismiss() }
            canvas.onPageCommitted = { committedPage ->
                persist(WidgetPanelMutator.replacePage(pages, position, committedPage))
            }
            canvas.onItemRemoved = { widgetId ->
                persist(WidgetPanelMutator.removeWidgetFromPage(hostContext, pages, position, widgetId))
                notifyDataSetChanged()
            }
            canvas.onConfigureWidget = { widgetId ->
                val intent = WidgetConfigureTrampolineActivity.createIntent(hostContext, widgetId)
                runCatching { hostContext.startActivity(intent) }
                onDismiss()
            }
            canvas.onAddWidgetRequested = { launchWidgetPicker(position) }
            canvas.editMode = editMode
            canvas.bind(page, hostContext)
        }
    }

    private class WidgetPageViewHolder(
        val scrollView: NestedScrollView,
        val canvasLayout: WidgetCanvasLayout,
    ) : RecyclerView.ViewHolder(scrollView)

    companion object {
        private const val PANEL_PADDING_DP = 12f
        private const val PANEL_INNER_PADDING_DP = 4f
        private const val TAG = "WidgetPopupCard"
    }
}
