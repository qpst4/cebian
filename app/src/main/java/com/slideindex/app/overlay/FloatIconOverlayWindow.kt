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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.messageGestureActions
import com.slideindex.app.ui.theme.SlideIndexTheme

private data class FloatIconEntry(
    val id: Long,
    val plan: MessageDisplayPlan,
    val visible: MutableState<Boolean>,
    val onAction: (MessageAction) -> Unit,
    val onDismiss: () -> Unit,
    var dismissRunnable: Runnable? = null,
) {
    fun matches(data: NotificationData): Boolean {
        val shown = plan.data
        return (shown.key == data.key && shown.postTime == data.postTime) ||
            (shown.key == data.key && shown.title == data.title && shown.content == data.content)
    }
}

object FloatIconOverlayWindow {
    private const val TAG = "FloatIconOverlay"
    const val ANIMATION_MS = 280
    const val EDGE_MARGIN_DP = 16f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val items = mutableStateListOf<FloatIconEntry>()
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var nextEntryId = 0L

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

        ensureWindow(hostContext)

        if (items.any { it.matches(plan.data) }) return

        while (items.isNotEmpty()) {
            removeEntry(items.last(), animate = false)
        }

        val entry = FloatIconEntry(
            id = ++nextEntryId,
            plan = plan,
            visible = mutableStateOf(false),
            onAction = onAction,
            onDismiss = onDismiss,
        )
        items.add(entry)
        scheduleAutoDismiss(entry)

        composeView?.post { entry.visible.value = true }
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

    private fun ensureWindow(hostContext: Context) {
        if (composeView != null) return

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dialogOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                FloatIconStackContent(
                    items = items,
                    onAction = { entry, action -> onEntryAction(entry, action) },
                    onDismiss = { entry -> onEntryDismiss(entry) },
                )
            }
        }

        val params = buildLayoutParams(hostContext)
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return
        }

        windowManager = wm
        composeView = view
        owner = dialogOwner
        appContext = hostContext
        registerScreenOffReceiver(hostContext)
    }

    private fun scheduleAutoDismiss(entry: FloatIconEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        val autoDismissMs = entry.plan.settings.autoDismissSeconds.coerceIn(0, 60) * 1000L
        if (autoDismissMs <= 0L) return
        val runnable = Runnable { removeEntry(entry, animate = true) }
        entry.dismissRunnable = runnable
        mainHandler.postDelayed(runnable, autoDismissMs)
    }

    private fun removeEntry(entry: FloatIconEntry, animate: Boolean) {
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
        entry.visible.value = false
        mainHandler.postDelayed({
            items.remove(entry)
            if (items.isEmpty()) cleanupWindow()
        }, ANIMATION_MS.toLong())
    }

    private fun onEntryAction(entry: FloatIconEntry, action: MessageAction) {
        entry.onAction(action)
        removeEntry(entry, animate = true)
    }

    private fun onEntryDismiss(entry: FloatIconEntry) {
        entry.onDismiss()
        removeEntry(entry, animate = true)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
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
            gravity = Gravity.BOTTOM or Gravity.END
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
        windowManager = null
        screenOffReceiver = null
        appContext = null
    }
}

@Composable
private fun FloatIconStackContent(
    items: SnapshotStateList<FloatIconEntry>,
    onAction: (FloatIconEntry, MessageAction) -> Unit,
    onDismiss: (FloatIconEntry) -> Unit,
) {
    val entry = items.firstOrNull() ?: return

    SlideIndexTheme {
        Box(
            modifier = Modifier.padding(
                end = FloatIconOverlayWindow.EDGE_MARGIN_DP.dp,
                bottom = FloatIconOverlayWindow.EDGE_MARGIN_DP.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            FloatIconItem(
                entry = entry,
                onAction = { action -> onAction(entry, action) },
                onDismiss = { onDismiss(entry) },
            )
        }
    }
}

@Composable
private fun FloatIconItem(
    entry: FloatIconEntry,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val plan = entry.plan
    val settings = plan.settings
    val data = plan.data
    val visible = entry.visible.value
    val presence by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(FloatIconOverlayWindow.ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "floatIconPresence",
    )
    val scalePulse by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = tween(FloatIconOverlayWindow.ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "floatIconScale",
    )

    if (presence <= 0.001f && !visible) return

    val sizeDp = settings.floatIconSizeDp.coerceIn(32f, 64f).dp
    val slideOffset = if (visible) 0f else 24f

    Box(
        modifier = Modifier
            .padding(end = (slideOffset * (1f - presence)).dp)
            .alpha(presence * settings.floatIconOpacity.coerceIn(0f, 1f))
            .scale(scalePulse)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.95f))
            .messageGestureActions(
                gestureKey = entry.id,
                settings = settings,
                onAction = onAction,
                onLongPress = onDismiss,
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        MessageNotificationIcon(
            iconBitmap = data.largeIcon,
            appIconBitmap = data.appIcon,
            sizeDp = sizeDp - 4.dp,
        )
    }
}
