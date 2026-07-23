package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.message.SideBubbleVerticalAnchor
import com.slideindex.app.message.effectiveSideBackgroundResId
import com.slideindex.app.message.MessageGestureHaptics
import com.slideindex.app.message.messageGestureActions
import com.slideindex.app.message.messageThemeBackground
import com.slideindex.app.ui.theme.SlideIndexTheme

object SideBubbleOverlayWindow {
    private const val TAG = "SideBubbleOverlay"
    internal const val ENTRANCE_DELAY_MS = 100L
    internal const val ENTRANCE_MS = 300
    internal const val REPOSITION_MS = 300
    internal const val EXIT_MS = 100
    private const val MAX_PER_KEY = 3
    private const val BOTTOM_OFFSET_DP = 72f
    const val EDGE_MARGIN_DP = 12

    private val mainHandler = Handler(Looper.getMainLooper())
    private val items = mutableStateListOf<SideBubbleEntry>()
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var rootLayoutParams: WindowManager.LayoutParams? = null
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var nextEntryId = 0L
    private var horizontalEdge = SideBubbleHorizontalEdge.Right
    private var verticalAnchor = SideBubbleVerticalAnchor.Middle

    val isShowing: Boolean get() = composeView != null

    fun containsNotification(data: NotificationData): Boolean =
        items.any { entry -> entry.matches(data) }

    fun show(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, plan, onAction, onDismiss) }
            return
        }

        val hostContext = MessageOverlayHost.resolveHostContext(context)
            ?: run {
                Log.w(TAG, "overlay permission not granted")
                return
            }

        ensureWindow(hostContext, plan.settings)
        updateWindowPlacement(hostContext, plan.settings)

        if (items.any { it.matches(plan.data) }) return

        while (items.count { it.plan.data.key == plan.data.key } >= MAX_PER_KEY) {
            items.filter { it.plan.data.key == plan.data.key }
                .minByOrNull { it.plan.data.postTime }
                ?.let { removeEntry(it, animate = false) }
                ?: break
        }

        val maxCount = plan.settings.sideMaxCount.coerceIn(1, 9)
        if (items.size >= maxCount) {
            removeEntry(
                entry = items.first(),
                animate = true,
                exitDirection = SideBubbleExitDirection.Top,
            )
        }

        val entry = SideBubbleEntry(
            id = ++nextEntryId,
            plan = plan,
            visible = mutableStateOf(true),
            entranceStarted = mutableStateOf(false),
            onAction = onAction,
            onDismiss = onDismiss,
        )
        items.add(entry)
        scheduleAutoDismiss(entry)
    }

    fun dismissEntry(key: String, postTime: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissEntry(key, postTime) }
            return
        }
        val entry = items.firstOrNull {
            it.plan.data.key == key && it.plan.data.postTime == postTime
        } ?: return
        removeEntry(entry, animate = true)
    }

    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        items.toList().forEach { removeEntry(it, animate = false) }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        items.toList().forEach { removeEntry(it, animate = true) }
    }

    private fun ensureWindow(hostContext: Context, settings: com.slideindex.app.message.MessageSettings) {
        if (composeView != null) return

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dialogOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                SideBubbleStackContent(
                    items = items,
                    horizontalEdge = horizontalEdge,
                    onAction = { entry, action -> onEntryAction(entry, action) },
                    onDismiss = { entry -> onEntryDismiss(entry) },
                    onClearAll = { dismiss() },
                    cancelAutoDismiss = ::cancelAutoDismiss,
                    scheduleAutoDismiss = ::scheduleAutoDismiss,
                )
            }
        }

        horizontalEdge = settings.sideBubbleHorizontalEdge
        verticalAnchor = settings.sideBubbleVerticalAnchor
        val params = buildLayoutParams(hostContext, settings)
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return
        }

        windowManager = wm
        composeView = view
        rootLayoutParams = params
        owner = dialogOwner
        appContext = hostContext
        registerScreenOffReceiver(hostContext)
    }

    private fun updateWindowPlacement(
        hostContext: Context,
        settings: com.slideindex.app.message.MessageSettings,
    ) {
        if (settings.sideBubbleHorizontalEdge == horizontalEdge &&
            settings.sideBubbleVerticalAnchor == verticalAnchor
        ) {
            return
        }
        horizontalEdge = settings.sideBubbleHorizontalEdge
        verticalAnchor = settings.sideBubbleVerticalAnchor
        val wm = windowManager ?: return
        val view = composeView ?: return
        if (!view.isAttachedToWindow) return
        val params = buildLayoutParams(hostContext, settings)
        rootLayoutParams = params
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "updateViewLayout failed", it) }
    }

    private fun scheduleAutoDismiss(entry: SideBubbleEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        val autoDismissMs = entry.plan.settings.autoDismissSeconds.coerceIn(0, 60) * 1000L
        if (autoDismissMs <= 0L) return
        val runnable = Runnable { removeEntry(entry, animate = true) }
        entry.dismissRunnable = runnable
        mainHandler.postDelayed(runnable, autoDismissMs)
    }

    private fun cancelAutoDismiss(entry: SideBubbleEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.dismissRunnable = null
    }

    fun resumeAutoDismiss(key: String, postTime: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { resumeAutoDismiss(key, postTime) }
            return
        }
        val entry = items.firstOrNull {
            it.plan.data.key == key && it.plan.data.postTime == postTime
        } ?: return
        scheduleAutoDismiss(entry)
    }

    fun pauseAutoDismiss(key: String, postTime: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { pauseAutoDismiss(key, postTime) }
            return
        }
        items.filter {
            it.plan.data.key == key && it.plan.data.postTime == postTime
        }.forEach { cancelAutoDismiss(it) }
    }

    private fun removeEntry(
        entry: SideBubbleEntry,
        animate: Boolean,
        exitDirection: SideBubbleExitDirection = SideBubbleExitDirection.Side,
    ) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.dismissRunnable = null
        if (!items.contains(entry)) return
        if (!animate) {
            items.remove(entry)
            if (items.isEmpty()) cleanupWindow()
            return
        }
        if (!entry.visible.value) {
            items.remove(entry)
            if (items.isEmpty()) cleanupWindow()
            return
        }
        entry.exitDirection = exitDirection
        entry.entranceStarted.value = true
        entry.visible.value = false
        mainHandler.postDelayed({
            items.remove(entry)
            if (items.isEmpty()) cleanupWindow()
        }, EXIT_MS.toLong())
    }

    private fun onEntryAction(entry: SideBubbleEntry, action: MessageAction) {
        if (action == MessageAction.QuickReply) {
            cancelAutoDismiss(entry)
        }
        entry.onAction(action)
        if (action == MessageAction.QuickReply) return
        removeEntry(entry, animate = true)
    }

    private fun onEntryDismiss(entry: SideBubbleEntry) {
        entry.onDismiss()
        removeEntry(entry, animate = true)
    }

    private fun buildLayoutParams(
        context: Context,
        settings: com.slideindex.app.message.MessageSettings,
    ): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val edgeMarginPx = (EDGE_MARGIN_DP * density).toInt()
        val horizontalGravity = when (settings.sideBubbleHorizontalEdge) {
            SideBubbleHorizontalEdge.Left -> Gravity.START
            SideBubbleHorizontalEdge.Right -> Gravity.END
        }
        val verticalGravity = when (settings.sideBubbleVerticalAnchor) {
            SideBubbleVerticalAnchor.Bottom -> Gravity.BOTTOM
            SideBubbleVerticalAnchor.Middle -> Gravity.CENTER_VERTICAL
        }
        val yOffsetPx = when (settings.sideBubbleVerticalAnchor) {
            SideBubbleVerticalAnchor.Bottom -> (BOTTOM_OFFSET_DP * density).toInt()
            SideBubbleVerticalAnchor.Middle -> 0
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = verticalGravity or horizontalGravity
            x = edgeMarginPx
            y = yOffsetPx
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun cleanupWindow() {
        val wm = windowManager
        composeView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(composeView)
        owner?.destroy()
        owner = null
        composeView = null
        rootLayoutParams = null
        windowManager = null
        screenOffReceiver = null
        appContext = null
    }
}

private enum class SideBubbleExitDirection {
    Side,
    Top,
}

private data class SideBubbleEntry(
    val id: Long,
    val plan: MessageDisplayPlan,
    val visible: MutableState<Boolean>,
    val entranceStarted: MutableState<Boolean>,
    val onAction: (MessageAction) -> Unit,
    val onDismiss: () -> Unit,
    var exitDirection: SideBubbleExitDirection = SideBubbleExitDirection.Side,
    var dismissRunnable: Runnable? = null,
) {
    fun matches(data: NotificationData): Boolean {
        val shown = plan.data
        return (shown.key == data.key && shown.postTime == data.postTime) ||
            (shown.key == data.key && shown.title == data.title && shown.content == data.content)
    }
}

@Composable
private fun SideBubbleStackContent(
    items: SnapshotStateList<SideBubbleEntry>,
    horizontalEdge: SideBubbleHorizontalEdge,
    onAction: (SideBubbleEntry, MessageAction) -> Unit,
    onDismiss: (SideBubbleEntry) -> Unit,
    onClearAll: () -> Unit,
    cancelAutoDismiss: (SideBubbleEntry) -> Unit,
    scheduleAutoDismiss: (SideBubbleEntry) -> Unit,
) {
    if (items.isEmpty()) return

    val stackAlignment = when (horizontalEdge) {
        SideBubbleHorizontalEdge.Left -> Alignment.TopStart
        SideBubbleHorizontalEdge.Right -> Alignment.TopEnd
    }
    val itemAlignment = when (horizontalEdge) {
        SideBubbleHorizontalEdge.Left -> Alignment.Start
        SideBubbleHorizontalEdge.Right -> Alignment.End
    }

    SlideIndexTheme {
        LazyColumn(
            modifier = Modifier.wrapContentSize(stackAlignment),
            horizontalAlignment = itemAlignment,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false,
        ) {
            items(items, key = { it.id }) { entry ->
                SideBubbleItem(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(0),
                        placementSpec = tween(
                            durationMillis = SideBubbleOverlayWindow.REPOSITION_MS,
                            easing = FastOutLinearInEasing,
                        ),
                        fadeOutSpec = tween(0),
                    ),
                    entry = entry,
                    onAction = { action -> onAction(entry, action) },
                    onDismiss = { onDismiss(entry) },
                    onClearAll = onClearAll,
                    onAutoDismissHoldChanged = { hold ->
                        if (hold) cancelAutoDismiss(entry) else scheduleAutoDismiss(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun SideBubbleItem(
    modifier: Modifier = Modifier,
    entry: SideBubbleEntry,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onAutoDismissHoldChanged: (Boolean) -> Unit,
) {
    val plan = entry.plan
    val theme = plan.sideTheme ?: return
    val settings = plan.settings
    val data = plan.data
    val visible = entry.visible.value
    val entranceStarted = entry.entranceStarted.value
    val progress = remember { Animatable(0f) }

    LaunchedEffect(entry.id) {
        delay(SideBubbleOverlayWindow.ENTRANCE_DELAY_MS)
        entry.entranceStarted.value = true
    }

    LaunchedEffect(visible, entranceStarted) {
        when {
            !visible -> progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SideBubbleOverlayWindow.EXIT_MS,
                    easing = LinearEasing,
                ),
            )
            entranceStarted -> progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SideBubbleOverlayWindow.ENTRANCE_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    if (progress.value <= 0.001f && !visible) return

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(menuExpanded) {
        onAutoDismissHoldChanged(menuExpanded)
    }

    var bubbleWidthPx by remember { mutableFloatStateOf(0f) }
    val maxWidth = settings.sideMaxWidthDp.coerceIn(100f, 280f).dp
    val slideDistancePx = bubbleWidthPx.takeIf { it > 0f }
        ?: with(density) { 100.dp.toPx() }
    val exitDistancePx = with(density) { 100.dp.toPx() }
    val slideSign = when (settings.sideBubbleHorizontalEdge) {
        SideBubbleHorizontalEdge.Left -> -1f
        SideBubbleHorizontalEdge.Right -> 1f
    }
    val presence = progress.value
    val useTopExit = !visible && entry.exitDirection == SideBubbleExitDirection.Top

    var bubbleModifier = modifier
        .onSizeChanged { bubbleWidthPx = it.width.toFloat() }
        .graphicsLayer {
            alpha = presence
            scaleX = presence
            scaleY = presence
            if (useTopExit) {
                translationX = 0f
                translationY = (1f - presence) * -exitDistancePx
            } else {
                translationX = (1f - presence) * slideSign * slideDistancePx
                translationY = 0f
            }
        }
        .widthIn(max = maxWidth)
    if (theme.cornerRadiusDp > 0f) {
        bubbleModifier = bubbleModifier.clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
    }

    val avatarOnRight = settings.sideBubbleHorizontalEdge == SideBubbleHorizontalEdge.Right
    val fontMetrics = sideBubbleFontMetrics(settings.sideBubbleFontSizeLevel)

    Box(modifier = Modifier.wrapContentSize()) {
        Row(
            modifier = bubbleModifier
                .messageThemeBackground(
                    theme = theme.copy(backgroundResId = theme.effectiveSideBackgroundResId()),
                    opacity = settings.sideBubbleOpacity,
                )
                .messageGestureActions(
                    gestureKey = entry.id,
                    settings = settings,
                    onAction = onAction,
                    onLongPressMenu = { menuExpanded = true },
                    onLongPressHaptic = { MessageGestureHaptics.longPress(view) },
                )
                .padding(
                    horizontal = theme.paddingHorizontalDp.dp,
                    vertical = theme.paddingVerticalDp.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!avatarOnRight) {
                MessageNotificationIcon(
                    iconBitmap = data.largeIcon,
                    appIconBitmap = data.appIcon,
                    sizeDp = 28.dp,
                    badgeAlignment = Alignment.BottomStart,
                )
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                SideBubbleTitleText(
                    text = data.title.ifBlank { data.packageName },
                    color = Color(theme.contentColorArgb),
                    fontSize = fontMetrics.titleSize,
                    lineHeight = fontMetrics.titleLineHeight,
                )
                if (data.content.isNotBlank()) {
                    SideBubbleContentText(
                        title = data.title.ifBlank { data.packageName },
                        text = data.content,
                        titleColor = Color(theme.titleColorArgb),
                        contentColor = Color(theme.contentColorArgb),
                        maxLines = settings.sideMaxLines.coerceIn(1, 3),
                        fontSize = fontMetrics.contentSize,
                        lineHeight = fontMetrics.contentLineHeight,
                    )
                }
            }
            if (avatarOnRight) {
                MessageNotificationIcon(
                    iconBitmap = data.largeIcon,
                    appIconBitmap = data.appIcon,
                    sizeDp = 28.dp,
                    badgeAlignment = Alignment.BottomEnd,
                )
            }
        }
        MessageOverlayContextMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onCopy = {
                copyNotificationText(context, data)
                menuExpanded = false
            },
            onClose = {
                menuExpanded = false
                onDismiss()
            },
            onClearAll = {
                menuExpanded = false
                onClearAll()
            },
        )
    }
}
