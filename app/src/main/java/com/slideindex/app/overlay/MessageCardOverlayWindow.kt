package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.slideindex.app.message.NotificationData
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.messageGestureActions
import com.slideindex.app.message.messageThemeBackground
import com.slideindex.app.message.overlayAlpha
import com.slideindex.app.ui.theme.SlideIndexTheme

object MessageCardOverlayWindow {
    private const val TAG = "MessageCardOverlay"
    private const val ANIMATION_MS = 260
    private const val CARD_HORIZONTAL_MARGIN_DP = 16f
    private const val MAX_PER_KEY = 3

    private val mainHandler = Handler(Looper.getMainLooper())
    private val items = mutableStateListOf<MessageCardEntry>()
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

        while (items.count { it.plan.data.key == plan.data.key } >= MAX_PER_KEY) {
            items.filter { it.plan.data.key == plan.data.key }
                .minByOrNull { it.plan.data.postTime }
                ?.let { removeEntry(it, animate = false) }
                ?: break
        }

        val maxCount = plan.settings.sideMaxCount.coerceIn(1, 5)
        while (items.size >= maxCount) {
            removeEntry(items.last(), animate = false)
        }

        val entry = MessageCardEntry(
            id = ++nextEntryId,
            plan = plan,
            visible = mutableStateOf(false),
            onAction = onAction,
            onDismiss = onDismiss,
        )
        items.add(0, entry)
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
                MessageCardStackContent(
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

    private fun scheduleAutoDismiss(entry: MessageCardEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        val autoDismissMs = entry.plan.settings.autoDismissSeconds.coerceIn(0, 60) * 1000L
        if (autoDismissMs <= 0L) return
        val runnable = Runnable { removeEntry(entry, animate = true) }
        entry.dismissRunnable = runnable
        mainHandler.postDelayed(runnable, autoDismissMs)
    }

    private fun removeEntry(entry: MessageCardEntry, animate: Boolean) {
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

    private fun onEntryAction(entry: MessageCardEntry, action: MessageAction) {
        entry.onAction(action)
        removeEntry(entry, animate = true)
    }

    private fun onEntryDismiss(entry: MessageCardEntry) {
        entry.onDismiss()
        removeEntry(entry, animate = true)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val horizontalMarginPx = (CARD_HORIZONTAL_MARGIN_DP * density).toInt()
        val cardWidthPx = (context.resources.displayMetrics.widthPixels - horizontalMarginPx * 2)
            .coerceAtLeast((200 * density).toInt())

        return WindowManager.LayoutParams(
            cardWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
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

private data class MessageCardEntry(
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

private const val MESSAGE_CARD_ANIMATION_MS = 260

@Composable
private fun MessageCardStackContent(
    items: SnapshotStateList<MessageCardEntry>,
    onAction: (MessageCardEntry, MessageAction) -> Unit,
    onDismiss: (MessageCardEntry) -> Unit,
) {
    if (items.isEmpty()) return

    SlideIndexTheme {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items.forEach { entry ->
                    key(entry.id) {
                        MessageCardItem(
                            entry = entry,
                            onAction = { action -> onAction(entry, action) },
                            onDismiss = { onDismiss(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCardItem(
    entry: MessageCardEntry,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val plan = entry.plan
    val theme = plan.cardTheme ?: return
    val settings = plan.settings
    val data = plan.data
    val visible = entry.visible.value
    val presence by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(MESSAGE_CARD_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "messageCardPresence",
    )
    if (presence <= 0.001f && !visible) return

    Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(presence * theme.overlayAlpha(settings.cardOpacity))
                .clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
                .messageThemeBackground(theme, settings.cardOpacity)
                .messageGestureActions(
                    gestureKey = entry.id,
                    settings = settings,
                    onAction = onAction,
                )
                .padding(
                    horizontal = theme.paddingHorizontalDp.dp,
                    vertical = theme.paddingVerticalDp.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MessageNotificationIcon(
                iconBitmap = data.largeIcon,
                appIconBitmap = data.appIcon,
                sizeDp = 48.dp,
                startPaddingDp = theme.avatarStartPaddingDp.dp,
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = data.title.ifBlank { data.packageName },
                    color = Color(theme.titleColorArgb),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.content.isNotBlank()) {
                    Text(
                        text = data.content,
                        color = Color(theme.contentColorArgb),
                        fontSize = 14.sp,
                        maxLines = settings.cardMaxLines.coerceIn(1, 3),
                        overflow = TextOverflow.Ellipsis,
                    )
            }
        }
    }
}

@Composable
internal fun MessageNotificationIcon(
    iconBitmap: Bitmap?,
    appIconBitmap: Bitmap?,
    sizeDp: Dp,
    startPaddingDp: Dp = 0.dp,
) {
    val displayIcon = iconBitmap ?: appIconBitmap
    Box(
        modifier = Modifier.padding(start = startPaddingDp),
    ) {
        if (displayIcon != null) {
            Image(
                bitmap = displayIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(sizeDp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            if (iconBitmap != null && appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(sizeDp * 0.34f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                modifier = Modifier
                    .size(sizeDp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
