package com.slideindex.app.overlay.carousel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlin.math.roundToInt

data class AppCarouselItem(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap,
    val cardColors: AppCarouselCardColors,
)

/**
 * 全新独立应用切换器视图（MyGesture 风格）：
 * 1. 最多显示 8 个最新使用过的 app；
 * 2. 卡片行固定显示在屏幕纵向正中央；
 * 3. 手指在全屏任意位置滑动即可控制轮播，无需手指点在卡片上；
 * 4. 手指向左滑动时卡片行向右平移展现历史应用；
 * 5. 手指移至屏幕顶部或底部边缘区域时淡化并判定取消，松手不启动；
 * 6. 自适应 Palette 主色调 Squircle 卡片大图标 + 触感反馈。
 */
@SuppressLint("ViewConstructor")
class AppCarouselSwitcherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val onDismissRequest: () -> Unit,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val cardBaseWidth = 76f * density
    private val cardBaseHeight = 84f * density
    private val cardSpacing = 16f * density
    private val cornerRadius = 22f * density
    private val verticalCancelEdgePx = 72f * density

    private var appSettings: AppSettings? = null
    private val items = mutableListOf<AppCarouselItem>()
    private var selectedIndex = 0
    private var lastHapticIndex = 0

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var isVerticalCancelled = false
    private var scrollOffset = 0f
    private var targetScrollOffset = 0f
    private var scrollAnimator: ValueAnimator? = null

    private val bgDimPaint = Paint().apply { color = 0x88000000.toInt() }
    private val cardPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f * density, 0f, 2f * density, 0x88000000.toInt())
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFFFFFF.toInt()
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
    }

    private val cardRect = RectF()
    private val iconRect = RectF()
    private val clipPath = Path()

    init {
        isFocusable = true
        isClickable = true
        loadCandidateApps()
    }

    private var isRightTrigger = false

    fun configure(settings: AppSettings, anchorX: Float, anchorY: Float) {
        this.appSettings = settings
        this.initialTouchX = anchorX
        this.initialTouchY = anchorY
        this.currentTouchX = anchorX
        this.currentTouchY = anchorY
        this.isVerticalCancelled = false
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        this.isRightTrigger = anchorX > screenW / 2f
        alpha = 1.0f
        loadCandidateApps()
        // 默认预选上一应用（index 1）或当前应用（index 0）
        selectedIndex = if (items.size > 1) 1 else 0
        lastHapticIndex = selectedIndex
        targetScrollOffset = computeScrollOffsetForIndex(selectedIndex)
        scrollOffset = targetScrollOffset
        invalidate()
    }

    private fun loadCandidateApps() {
        val pm = context.packageManager
        val recentPackages = LinkedHashSet<String>()

        // 1. 优先通过 UsageStatsManager 查询最近使用的应用（最多 8 个）
        if (PermissionHelper.hasUsageAccess(context)) {
            val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            if (usageStatsManager != null) {
                val end = System.currentTimeMillis()
                val start = end - 1000L * 60 * 60 * 24 * 7
                val stats = runCatching {
                    usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                }.getOrNull()
                if (!stats.isNullOrEmpty()) {
                    stats.asSequence()
                        .filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
                        .sortedByDescending { it.lastTimeUsed }
                        .map { it.packageName }
                        .distinct()
                        .filter { pm.getLaunchIntentForPackage(it) != null }
                        .take(8)
                        .forEach { recentPackages.add(it) }
                }
            }
        }

        // 2. 尝试从最近任务中补充
        if (recentPackages.size < 8) {
            val taskRefs = runCatching { TaskManagerUtil.refreshRecentTasks() }.getOrNull().orEmpty()
            for (ref in taskRefs) {
                val pkg = ref.identifier.substringBefore('/').trim()
                if (pkg.isNotEmpty() && pkg != context.packageName && pm.getLaunchIntentForPackage(pkg) != null) {
                    recentPackages.add(pkg)
                    if (recentPackages.size >= 8) break
                }
            }
        }

        // 3. 回退至常用/已安装可启动应用补充至 8 个
        if (recentPackages.size < 8) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName && pm.getLaunchIntentForPackage(pkg) != null) {
                    recentPackages.add(pkg)
                    if (recentPackages.size >= 8) break
                }
            }
        }

        val loaded = mutableListOf<AppCarouselItem>()
        for (pkg in recentPackages.take(8)) {
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            val label = pm.getApplicationLabel(appInfo).toString()
            val iconDrawable: Drawable = try {
                pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                pm.defaultActivityIcon
            }
            val iconSizePx = (48f * density).roundToInt().coerceAtLeast(32)
            val bitmap = try {
                iconDrawable.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
            } catch (_: Exception) {
                Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.GRAY)
                }
            }
            val colors = AppCarouselPaletteResolver.resolveCardColors(pkg, bitmap)
            loaded.add(AppCarouselItem(pkg, label, bitmap, colors))
        }

        items.clear()
        items.addAll(loaded)
    }

    fun onExternalMove(rawX: Float, rawY: Float) {
        currentTouchX = rawX
        currentTouchY = rawY

        val shouldCancel = isInVerticalCancelZone(rawY)
        if (shouldCancel != isVerticalCancelled) {
            isVerticalCancelled = shouldCancel
            animate().alpha(if (isVerticalCancelled) 0.35f else 1.0f).setDuration(120L).start()
        }

        if (!isVerticalCancelled && items.isNotEmpty()) {
            val deltaX = rawX - initialTouchX
            val baseIndex = if (items.size > 1) 1 else 0
            val screenW = resources.displayMetrics.widthPixels.toFloat()
            val edgeMargin = 12f * density
            val maxComfortTravel = 130f * density
            val maxSteps = items.lastIndex - baseIndex
            val deadZone = 0.06f

            val newIndex = if (!isRightTrigger) {
                // 左侧触发：
                // 手指往左滑 (deltaX < 0)：浏览更早的历史应用 (卡片行向右平移展现历史)
                // 手指往右滑 (deltaX > 0)：回退至当前应用 (index 0)
                if (deltaX <= 0f) {
                    if (maxSteps <= 0) baseIndex else {
                        val availableTravel = (initialTouchX - edgeMargin).coerceIn(60f * density, maxComfortTravel)
                        val progress = (-deltaX / availableTravel).coerceIn(0f, 1f)
                        if (progress < deadZone) baseIndex else {
                            val activeProgress = (progress - deadZone) / (1f - deadZone)
                            val stepIndex = (activeProgress * maxSteps).roundToInt().coerceIn(0, maxSteps)
                            (baseIndex + stepIndex).coerceIn(0, items.lastIndex)
                        }
                    }
                } else {
                    val availableTravel = (screenW - edgeMargin - initialTouchX).coerceIn(60f * density, maxComfortTravel)
                    val progress = (deltaX / availableTravel).coerceIn(0f, 1f)
                    if (progress > 0.08f) 0 else baseIndex
                }
            } else {
                // 右侧触发对称：
                // 手指往右滑 (deltaX > 0)：浏览更早的历史应用 (卡片行向左平移展现历史)
                // 手指往左滑 (deltaX < 0)：回退至当前应用 (index 0)
                if (deltaX >= 0f) {
                    if (maxSteps <= 0) baseIndex else {
                        val availableTravel = (screenW - edgeMargin - initialTouchX).coerceIn(60f * density, maxComfortTravel)
                        val progress = (deltaX / availableTravel).coerceIn(0f, 1f)
                        if (progress < deadZone) baseIndex else {
                            val activeProgress = (progress - deadZone) / (1f - deadZone)
                            val stepIndex = (activeProgress * maxSteps).roundToInt().coerceIn(0, maxSteps)
                            (baseIndex + stepIndex).coerceIn(0, items.lastIndex)
                        }
                    }
                } else {
                    val availableTravel = (initialTouchX - edgeMargin).coerceIn(60f * density, maxComfortTravel)
                    val progress = (-deltaX / availableTravel).coerceIn(0f, 1f)
                    if (progress > 0.08f) 0 else baseIndex
                }
            }

            if (newIndex != selectedIndex) {
                selectedIndex = newIndex
                smoothScrollToIndex(selectedIndex)
                if (selectedIndex != lastHapticIndex) {
                    lastHapticIndex = selectedIndex
                    appSettings?.let { HapticHelper.appTick(this, it) }
                }
            }
        }
        invalidate()
    }

    fun onExternalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        if (cancelled || isVerticalCancelled) {
            onDismissRequest()
            return
        }
        val target = items.getOrNull(selectedIndex)
        if (target != null) {
            appSettings?.let { HapticHelper.confirmLaunch(this, it) }
            launchPackage(target.packageName)
        }
        onDismissRequest()
    }

    fun onExternalCancel() {
        onDismissRequest()
    }

    private fun smoothScrollToIndex(index: Int) {
        targetScrollOffset = computeScrollOffsetForIndex(index)
        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofFloat(scrollOffset, targetScrollOffset).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scrollOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun computeScrollOffsetForIndex(index: Int): Float {
        val centerX = width / 2f
        val itemStep = cardBaseWidth + cardSpacing
        return if (!isRightTrigger) {
            // 左侧触发：手指向左滑时卡片行向右平移展现历史
            centerX + index * itemStep - cardBaseWidth / 2f
        } else {
            // 右侧触发：手指向右滑时卡片行向左平移展现历史
            centerX - index * itemStep - cardBaseWidth / 2f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        targetScrollOffset = computeScrollOffsetForIndex(selectedIndex)
        scrollOffset = targetScrollOffset
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDimPaint)

        if (items.isEmpty()) return

        // 卡片行固定显示在屏幕垂直正中央
        val centerY = height / 2f

        val itemStep = cardBaseWidth + cardSpacing
        for (i in items.indices) {
            val item = items[i]
            val itemCenterX = if (!isRightTrigger) {
                scrollOffset - i * itemStep + cardBaseWidth / 2f
            } else {
                scrollOffset + i * itemStep + cardBaseWidth / 2f
            }
            if (itemCenterX < -cardBaseWidth || itemCenterX > width + cardBaseWidth) continue

            val isSelected = i == selectedIndex && !isVerticalCancelled
            val scale = if (isSelected) 1.22f else 1.0f
            val cardW = cardBaseWidth * scale
            val cardH = cardBaseHeight * scale
            val left = itemCenterX - cardW / 2f
            val top = centerY - cardH / 2f
            val right = left + cardW
            val bottom = top + cardH

            cardRect.set(left, top, right, bottom)
            val currentCorner = cornerRadius * scale

            // 卡片底色（调色板自适应主色）
            cardPlatePaint.color = if (isSelected) item.cardColors.highlightCardColor else item.cardColors.baseCardColor
            canvas.drawRoundRect(cardRect, currentCorner, currentCorner, cardPlatePaint)

            // 图标居中裁剪（保持 1:1 原比例不拉伸变形）
            clipPath.reset()
            clipPath.addRoundRect(cardRect, currentCorner, currentCorner, Path.Direction.CW)
            val saveCount = canvas.save()
            canvas.clipPath(clipPath)

            val iconPad = 13f * density * scale
            val iconSize = (minOf(cardW, cardH) - 2 * iconPad).coerceAtLeast(16f * density)
            val iconLeft = left + (cardW - iconSize) / 2f
            val iconTop = top + (cardH - iconSize) / 2f
            iconRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            canvas.drawBitmap(item.iconBitmap, null, iconRect, iconPaint)
            canvas.restoreToCount(saveCount)

            // 高亮外描边
            if (isSelected) {
                cardStrokePaint.color = item.cardColors.strokeColor
                cardStrokePaint.strokeWidth = 2.5f * density
                val strokePad = 1.5f * density
                val strokeRect = RectF(left - strokePad, top - strokePad, right + strokePad, bottom + strokePad)
                canvas.drawRoundRect(strokeRect, currentCorner + strokePad, currentCorner + strokePad, cardStrokePaint)
            } else {
                cardStrokePaint.color = 0x22FFFFFF.toInt()
                cardStrokePaint.strokeWidth = 1f * density
                canvas.drawRoundRect(cardRect, currentCorner, currentCorner, cardStrokePaint)
            }

            // 绘制选中的应用名称
            if (isSelected) {
                val labelY = bottom + 26f * density
                canvas.drawText(item.label, itemCenterX, labelY, textPaint)
            }
        }

        // 取消提示
        if (isVerticalCancelled) {
            val hintY = centerY - cardBaseHeight * 0.9f
            canvas.drawText("移至屏幕顶部或底部松手取消", width / 2f, hintY, hintPaint)
        }
    }

    private fun isInVerticalCancelZone(rawY: Float): Boolean {
        val screenH = if (height > 0) {
            height.toFloat()
        } else {
            resources.displayMetrics.heightPixels.toFloat()
        }
        return rawY <= verticalCancelEdgePx || rawY >= screenH - verticalCancelEdgePx
    }

    private fun launchPackage(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (launchIntent != null) {
            runCatching { context.startActivity(launchIntent) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                onExternalMove(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onExternalMove(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onExternalUp(event.rawX, event.rawY, false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                onExternalCancel()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

