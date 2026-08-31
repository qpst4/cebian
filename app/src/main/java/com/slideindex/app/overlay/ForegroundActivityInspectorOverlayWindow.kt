package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 历史记录条目数据结构
 */
data class ActivityHistoryRecord(
    val timeFormatted: String,
    val packageName: String,
    val className: String,
    var appLabel: String,
)

/**
 * 前台活动（Activity）探测悬浮窗单例控制器。
 * 具备 0 延迟即时刷新、应用信息内存缓存以及可折叠的历史跳转轨迹面板。
 */
@SuppressLint("StaticFieldLeak")
object ForegroundActivityInspectorOverlayWindow {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var appContext: Context? = null
    private var scope: CoroutineScope? = null

    // 应用名称与图标内存缓存（容量 64，避免每次跨进程 IPC 造成视觉延迟）
    private val appInfoCache = LruCache<String, Pair<String, Drawable?>>(64)

    // 历史跳转记录队列（上限 25 条）
    private val historyRecords = ArrayDeque<ActivityHistoryRecord>(25)
    private var isHistoryExpanded = false

    // UI View 引用
    private var appIconView: ImageView? = null
    private var appLabelView: TextView? = null
    private var packageNameView: TextView? = null
    private var activityNameView: TextView? = null
    private var historyContainer: LinearLayout? = null
    private var historyItemsLayout: LinearLayout? = null
    private var historyToggleBtn: TextView? = null

    // 当前探测到的数据缓存
    private var currentPackage: String = ""
    private var currentActivity: String = ""
    private var currentLabel: String = ""

    val isShowing: Boolean
        get() = rootView != null

    fun toggle(context: Context) {
        if (isShowing) {
            dismiss()
        } else {
            show(context)
        }
    }

    fun show(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context) }
            return
        }

        if (isShowing) return

        val app = context.applicationContext
        appContext = app
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val dm = app.resources.displayMetrics
        val density = dm.density
        val dp = { value: Float -> (value * density).toInt() }

        val params = WindowManager.LayoutParams().apply {
            width = dp(330f).coerceAtMost((dm.widthPixels * 0.92f).toInt())
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(100f)
        }
        layoutParams = params

        val root = FrameLayout(app)
        rootView = root

        // 构建内部卡片 UI
        val cardLayout = buildCardView(app, dp)
        root.addView(cardLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))

        // 绑定平滑拖拽
        setupDraggable(root, wm, params)

        try {
            wm.addView(root, params)
        } catch (e: Exception) {
            rootView = null
            return
        }

        // 初始化当前前台信息
        val initialPkg = SlideIndexAccessibilityService.currentForegroundPackageName() ?: ""
        val initialCls = SlideIndexAccessibilityService.currentForegroundClassName().orEmpty()
        if (initialPkg.isNotBlank()) {
            onForegroundWindowStateChanged(initialPkg, initialCls)
        } else {
            updateInfoUI(
                pkg = app.getString(R.string.foreground_activity_inspector_waiting),
                cls = "",
                label = app.getString(R.string.foreground_activity_inspector_title),
                icon = null,
            )
        }
    }

    /**
     * 无障碍服务监测到前台窗口变化时调用（实现极致响应速度与历史追踪）。
     */
    fun onForegroundWindowStateChanged(packageName: String, className: String) {
        if (!isShowing) return
        if (packageName.isBlank()) return
        val app = appContext ?: return
        if (packageName == app.packageName) return // 忽略应用自身

        // 过滤输入法与弹窗
        if (className.contains("inputmethod", ignoreCase = true) ||
            className.startsWith("android.inputmethodservice.") ||
            className == "android.widget.PopupWindow" ||
            className == "android.widget.Toast"
        ) {
            return
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onForegroundWindowStateChanged(packageName, className) }
            return
        }

        // 1. 检查去重：若与最后一次完全一致则不重复入栈
        val isSameAsCurrent = (packageName == currentPackage && className == currentActivity)

        currentPackage = packageName
        currentActivity = className

        // 2. 尝试从内存缓存命中应用名称与图标（0ms 瞬间响应）
        val cached = appInfoCache.get(packageName)
        val initialLabel = cached?.first ?: packageName
        val cachedIcon = cached?.second
        currentLabel = initialLabel

        // 3. 同步即时刷新当前卡片 UI（文本 0 延迟响应）
        updateInfoUI(
            pkg = packageName,
            cls = className,
            label = initialLabel,
            icon = cachedIcon,
        )

        // 4. 记录到历史队列中
        if (!isSameAsCurrent) {
            val record = ActivityHistoryRecord(
                timeFormatted = timeFormat.format(Date()),
                packageName = packageName,
                className = className,
                appLabel = initialLabel,
            )
            if (historyRecords.size >= 25) {
                historyRecords.removeFirst()
            }
            historyRecords.addLast(record)
            if (isHistoryExpanded) {
                renderHistoryList()
            }
        }

        // 5. 若未命中缓存，异步在后台加载应用信息并补齐
        if (cached == null) {
            scope?.launch(Dispatchers.IO) {
                val pm = app.packageManager
                var resolvedLabel = packageName
                var resolvedIcon: Drawable? = null
                runCatching {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    resolvedLabel = pm.getApplicationLabel(appInfo).toString()
                    resolvedIcon = pm.getApplicationIcon(appInfo)
                }
                appInfoCache.put(packageName, Pair(resolvedLabel, resolvedIcon))

                withContext(Dispatchers.Main) {
                    if (currentPackage == packageName) {
                        currentLabel = resolvedLabel
                        updateInfoUI(packageName, currentActivity, resolvedLabel, resolvedIcon)
                    }
                    // 更新历史记录中的标签
                    historyRecords.forEach {
                        if (it.packageName == packageName) {
                            it.appLabel = resolvedLabel
                        }
                    }
                    if (isHistoryExpanded) {
                        renderHistoryList()
                    }
                }
            }
        }
    }

    private fun updateInfoUI(pkg: String, cls: String, label: String, icon: Drawable?) {
        appLabelView?.text = label
        packageNameView?.text = pkg
        activityNameView?.text = if (cls.isNotBlank()) cls else "—"
        if (icon != null) {
            appIconView?.setImageDrawable(icon)
            appIconView?.visibility = View.VISIBLE
        } else {
            appIconView?.visibility = View.GONE
        }
    }

    private fun buildCardView(context: Context, dp: (Float) -> Int): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(0xEE1A1B20.toInt()) // 高质感深色毛玻璃背景
                cornerRadius = dp(14f).toFloat()
                setStroke(dp(1f), 0x33FFFFFF)
            }
            background = bg
            setPadding(dp(12f), dp(10f), dp(12f), dp(12f))
            elevation = dp(8f).toFloat()
        }

        // 1. 顶部标题栏（图标 + 应用名 + 右侧按钮组）
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconIv = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                marginEnd = dp(6f)
            }
            visibility = View.GONE
        }
        appIconView = iconIv
        header.addView(iconIv)

        val titleTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = context.getString(R.string.foreground_activity_inspector_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        appLabelView = titleTv
        header.addView(titleTv)

        // 历史记录折叠/展开按钮
        val historyBtn = createIconButton(context, dp, "📜") {
            toggleHistoryPanel()
        }
        historyToggleBtn = historyBtn
        header.addView(historyBtn)

        // 复制全部按钮
        val copyAllBtn = createIconButton(context, dp, "📋") {
            copyAllInfo()
        }
        header.addView(copyAllBtn)

        // 打开应用信息详情设置按钮
        val settingsBtn = createIconButton(context, dp, "⚙️") {
            openAppDetails()
        }
        header.addView(settingsBtn)

        // 关闭按钮
        val closeBtn = createIconButton(context, dp, "✖️") {
            dismiss()
        }
        header.addView(closeBtn)

        card.addView(header)

        // 分割线
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
                topMargin = dp(6f)
                bottomMargin = dp(6f)
            }
            setBackgroundColor(0x22FFFFFF)
        }
        card.addView(divider)

        // 2. 包名模块
        val pkgLabel = TextView(context).apply {
            text = "PACKAGE (点击复制):"
            setTextColor(0x88FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.MONOSPACE
        }
        card.addView(pkgLabel)

        val pkgTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
                bottomMargin = dp(5f)
            }
            text = "..."
            setTextColor(0xFF4ADE80.toInt()) // 荧光淡绿
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            maxLines = 2
            setOnClickListener {
                if (currentPackage.isNotBlank()) {
                    copyToClipboard(context, currentPackage, context.getString(R.string.foreground_activity_inspector_pkg_copied))
                }
            }
        }
        packageNameView = pkgTv
        card.addView(pkgTv)

        // 3. Activity 模块
        val actLabel = TextView(context).apply {
            text = "ACTIVITY (点击复制):"
            setTextColor(0x88FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.MONOSPACE
        }
        card.addView(actLabel)

        val actTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f)
            }
            text = "..."
            setTextColor(0xFF38BDF8.toInt()) // 明亮天蓝
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            maxLines = 3
            setOnClickListener {
                if (currentActivity.isNotBlank()) {
                    copyToClipboard(context, currentActivity, context.getString(R.string.foreground_activity_inspector_act_copied))
                }
            }
        }
        activityNameView = actTv
        card.addView(actTv)

        // 4. 可折叠历史记录面板
        val historyPanel = buildHistorySection(context, dp)
        historyContainer = historyPanel
        card.addView(historyPanel)

        return card
    }

    private fun buildHistorySection(context: Context, dp: (Float) -> Int): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6f)
            }
        }

        // 分割线
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
                bottomMargin = dp(6f)
            }
            setBackgroundColor(0x22FFFFFF)
        }
        container.addView(divider)

        // 历史标题栏（“活动跳转历史” + “清空”按钮）
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleTv = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = context.getString(R.string.foreground_activity_inspector_history_title)
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleBar.addView(titleTv)

        val clearTv = TextView(context).apply {
            text = context.getString(R.string.foreground_activity_inspector_history_clear)
            setTextColor(0xFFEF4444.toInt()) // 浅红
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
            setOnClickListener {
                historyRecords.clear()
                renderHistoryList()
            }
        }
        titleBar.addView(clearTv)
        container.addView(titleBar)

        // 滚动列表（最大高度限制为 240dp）
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220f))
        }

        val itemsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        historyItemsLayout = itemsLayout
        scrollView.addView(itemsLayout)
        container.addView(scrollView)

        return container
    }

    private fun toggleHistoryPanel() {
        isHistoryExpanded = !isHistoryExpanded
        historyContainer?.visibility = if (isHistoryExpanded) View.VISIBLE else View.GONE
        if (isHistoryExpanded) {
            renderHistoryList()
        }
    }

    private fun renderHistoryList() {
        val container = historyItemsLayout ?: return
        val context = appContext ?: return
        container.removeAllViews()

        val dm = context.resources.displayMetrics
        val density = dm.density
        val dp = { value: Float -> (value * density).toInt() }

        if (historyRecords.isEmpty()) {
            val emptyTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(16f)
                    bottomMargin = dp(16f)
                }
                text = context.getString(R.string.foreground_activity_inspector_history_empty)
                setTextColor(0x66FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
            }
            container.addView(emptyTv)
            return
        }

        // 从最新到最旧反向遍历
        historyRecords.reversed().forEachIndexed { index, record ->
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val itemBg = GradientDrawable().apply {
                    setColor(if (index == 0) 0x334ADE80.toInt() else 0x1AFFFFFF)
                    cornerRadius = dp(6f).toFloat()
                }
                background = itemBg
                setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f)
                }
                setOnClickListener {
                    val fullInfo = "${record.appLabel}\n${record.packageName}\n${record.className}"
                    copyToClipboard(context, fullInfo, context.getString(R.string.foreground_activity_inspector_all_copied))
                }
            }

            // 顶部时间 + 应用名
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val timeTv = TextView(context).apply {
                text = record.timeFormatted
                setTextColor(0x99FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                typeface = Typeface.MONOSPACE
            }
            topRow.addView(timeTv)

            val labelTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(6f)
                }
                text = record.appLabel
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            topRow.addView(labelTv)
            itemLayout.addView(topRow)

            // 底部 Activity 类名（高亮简短类名）
            val actTv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2f)
                }
                text = record.className
                setTextColor(0xFF38BDF8.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.MONOSPACE
                maxLines = 2
            }
            itemLayout.addView(actTv)

            container.addView(itemLayout)
        }
    }

    private fun createIconButton(context: Context, dp: (Float) -> Int, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26f), dp(26f)).apply {
                marginStart = dp(4f)
            }
            gravity = Gravity.CENTER
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.WHITE)
            val btnBg = GradientDrawable().apply {
                setColor(0x22FFFFFF)
                cornerRadius = dp(6f).toFloat()
            }
            background = btnBg
            setOnClickListener { onClick() }
        }
    }

    private fun setupDraggable(view: View, wm: WindowManager, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, params) }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = isDragging
                    isDragging = false
                    wasDragging
                }
                else -> false
            }
        }
    }

    private fun copyAllInfo() {
        val context = appContext ?: return
        if (currentPackage.isBlank()) return
        val text = buildString {
            if (currentLabel.isNotBlank()) append("应用名称: $currentLabel\n")
            append("包名: $currentPackage\n")
            if (currentActivity.isNotBlank()) append("Activity: $currentActivity")
        }
        copyToClipboard(context, text, context.getString(R.string.foreground_activity_inspector_all_copied))
    }

    private fun openAppDetails() {
        val context = appContext ?: return
        if (currentPackage.isBlank()) return
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", currentPackage, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun copyToClipboard(context: Context, text: String, toastMsg: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        scope?.cancel()
        scope = null
        rootView?.let { root ->
            runCatching { windowManager?.removeView(root) }
            rootView = null
        }
        appIconView = null
        appLabelView = null
        packageNameView = null
        activityNameView = null
        historyContainer = null
        historyItemsLayout = null
        historyToggleBtn = null
        isHistoryExpanded = false
    }
}
