package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessageGestureHaptics
import com.slideindex.app.message.MessageReminderPreviewController
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.messageGestureActions
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

private data class FloatIconEntry(
    val id: Long,
    val planState: MutableState<MessageDisplayPlan>,
    val visible: MutableState<Boolean>,
    val onAction: (MessageAction) -> Unit,
    val onDismiss: () -> Unit,
    var dismissRunnable: Runnable? = null,
) {
    val plan: MessageDisplayPlan
        get() = planState.value

    fun matches(data: NotificationData): Boolean {
        val shown = plan.data
        return shown.key == data.key && shown.postTime == data.postTime
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
    private var rootLayoutParams: WindowManager.LayoutParams? = null
    private var owner: OverlayComposeOwner? = null
    private var placementSettings: MessageSettings? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
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

        ensureWindow(hostContext, plan.settings)
        updateWindowPlacement(hostContext, plan.settings)

        if (items.any { it.matches(plan.data) }) return

        while (items.isNotEmpty()) {
            removeEntry(items.last(), animate = false)
        }

        val entry = FloatIconEntry(
            id = ++nextEntryId,
            planState = mutableStateOf(plan),
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

    fun dismissEntriesForKey(key: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissEntriesForKey(key) }
            return
        }
        items.filter { it.plan.data.key == key }
            .toList()
            .forEach { removeEntry(it, animate = true) }
    }

    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        items.toList().forEach { removeEntry(it, animate = false) }
    }

    fun snapshotDisplayedKeys(): Set<String> = items.map { it.plan.data.key }.toSet()

    fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String> =
        items.filter { it.plan.data.conversationSourceKey == sourceKey }
            .map { it.plan.data.key }
            .toSet()

    fun dismissSameSource(sourceKey: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissSameSource(sourceKey) }
            return
        }
        items.filter { it.plan.data.conversationSourceKey == sourceKey }
            .toList()
            .forEach { removeEntry(it, animate = true) }
    }

    fun dismissPreview() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissPreview() }
            return
        }
        items.filter { it.plan.data.key == MessageReminderPreviewController.PREVIEW_KEY }
            .toList()
            .forEach { removeEntry(it, animate = false) }
    }

    fun updatePreviewPlan(plan: MessageDisplayPlan) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePreviewPlan(plan) }
            return
        }
        val entry = items.firstOrNull {
            it.plan.data.key == MessageReminderPreviewController.PREVIEW_KEY
        } ?: return
        entry.planState.value = plan
    }

    fun updateWindowPlacement(context: Context, settings: MessageSettings) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateWindowPlacement(context, settings) }
            return
        }
        if (placementMatches(placementSettings, settings)) return
        placementSettings = settings
        val hostContext = appContext ?: MessageOverlayHost.resolveHostContext(context) ?: return
        val wm = windowManager ?: return
        val view = composeView ?: return
        if (!view.isAttachedToWindow) return
        val params = MessageOverlayLayout.buildFloatIconLayoutParams(hostContext, settings)
        rootLayoutParams = params
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "updateViewLayout failed", it) }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        items.toList().forEach { removeEntry(it, animate = true) }
    }

    private fun ensureWindow(hostContext: Context, settings: MessageSettings) {
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

        placementSettings = settings
        val params = MessageOverlayLayout.buildFloatIconLayoutParams(hostContext, settings)
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            placementSettings = null
            return
        }

        windowManager = wm
        composeView = view
        rootLayoutParams = params
        owner = dialogOwner
        appContext = hostContext
        screenOffDismissReceiver.register(hostContext)
    }

    private fun scheduleAutoDismiss(entry: FloatIconEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        val autoDismissMs = entry.plan.settings.autoDismissSeconds.coerceIn(0, 60) * 1000L
        if (autoDismissMs <= 0L) return
        val runnable = Runnable { removeEntry(entry, animate = true) }
        entry.dismissRunnable = runnable
        mainHandler.postDelayed(runnable, autoDismissMs)
    }

    private fun cancelAutoDismiss(entry: FloatIconEntry) {
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
        if (entry.plan.data.key == MessageReminderPreviewController.PREVIEW_KEY) return
        if (action.opensQuickReply) {
            cancelAutoDismiss(entry)
        }
        entry.onAction(action)
        if (action.opensQuickReply || action.affectsAllDisplayed || action.affectsSameSource) return
        removeEntry(entry, animate = true)
    }

    private fun onEntryDismiss(entry: FloatIconEntry) {
        if (entry.plan.data.key == MessageReminderPreviewController.PREVIEW_KEY) return
        entry.onDismiss()
        removeEntry(entry, animate = true)
    }

    private fun placementMatches(current: MessageSettings?, next: MessageSettings): Boolean {
        if (current == null) return false
        return current.floatIconCorner == next.floatIconCorner &&
            current.floatIconYFraction == next.floatIconYFraction &&
            current.floatIconSizeDp == next.floatIconSizeDp
    }

    private fun cleanupWindow() {
        val wm = windowManager
        val view = composeView
        val dialogOwner = owner
        view?.let { v -> wm?.let { runCatching { it.removeView(v) } } }
        screenOffDismissReceiver.unregister()
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
        owner = null
        composeView = null
        rootLayoutParams = null
        placementSettings = null
        windowManager = null
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

    OverlayAwareModuleTheme {
        Box(contentAlignment = Alignment.Center) {
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
    val plan = entry.planState.value
    val settings = plan.settings
    val data = plan.data
    val isPreview = data.key == MessageReminderPreviewController.PREVIEW_KEY
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
    val view = LocalView.current

    var iconModifier = Modifier
        .padding(end = (slideOffset * (1f - presence)).dp)
        .alpha(presence * settings.floatIconOpacity.coerceIn(0f, 1f))
        .scale(scalePulse)
        .shadow(6.dp, CircleShape)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.95f))
    if (!isPreview) {
        iconModifier = iconModifier.messageGestureActions(
            gestureKey = entry.id,
            settings = settings,
            onAction = onAction,
            onLongPressMenu = onDismiss,
            onLongPressHaptic = { MessageGestureHaptics.longPress(view) },
        )
    }
    Box(
        modifier = iconModifier
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
