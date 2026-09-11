package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.slideindex.app.message.CNoticeHistoryLoader
import com.slideindex.app.message.MessageAction
import com.slideindex.app.notification.NotificationRemoteReply
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageReminderPreviewController
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.NotificationData
import com.slideindex.app.message.NotificationMessage
import com.slideindex.app.message.NotificationMessagingHistory
import com.slideindex.app.message.SideBubbleHorizontalEdge
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private fun cNoticePostKey(data: NotificationData): String = "${data.key}|${data.postTime}"

private fun preserveConversationIcon(
    previous: MessageDisplayPlan,
    incoming: MessageDisplayPlan,
): MessageDisplayPlan {
    if (incoming.data.conversationIcon != null) return incoming
    val preserved = previous.data.conversationIcon ?: return incoming
    return incoming.copy(data = incoming.data.copy(conversationIcon = preserved))
}

private data class CNoticeEntry(
    val conversationSourceKey: String,
    val planState: MutableState<MessageDisplayPlan>,
    val unreadCount: MutableIntState,
    val messageHistory: SnapshotStateList<NotificationMessage>,
    val visible: MutableState<Boolean>,
    val peekBannerTextState: MutableState<String?> = mutableStateOf(null),
    val displayedPostKeys: MutableSet<String>,
    val onAction: (MessageAction) -> Unit,
    val onDismiss: () -> Unit,
    var dismissRunnable: Runnable? = null,
    var peekBannerHideRunnable: Runnable? = null,
    var peekBannerShowRunnable: Runnable? = null,
    var pendingPeekBannerText: String? = null,
    var pendingPeekContentSignature: String? = null,
    var lastPeekPresentedUptimeMs: Long = 0L,
    var lastPeekContentSignature: String? = null,
    var readLocally: Boolean = false,
    var readBaselineBadgeCount: Int = 0,
    var readBaselineMessageCount: Int = 0,
    var readBaselinePostKeyCount: Int = 0,
) {
    val plan: MessageDisplayPlan
        get() = planState.value

    fun matchesNotification(data: NotificationData): Boolean =
        displayedPostKeys.contains(cNoticePostKey(data))

    fun containsConversation(data: NotificationData): Boolean =
        conversationSourceKey == NotificationData.conversationIdentityKey(data) ||
            conversationSourceKey == data.conversationSourceKey.ifBlank { data.packageName }

    fun matchesSourceKey(sourceKey: String): Boolean =
        conversationSourceKey == sourceKey ||
            NotificationData.conversationIdentityKey(plan.data) == sourceKey ||
            plan.data.conversationSourceKey == sourceKey

    fun syncMessagesFrom(data: NotificationData) {
        val merged = NotificationMessagingHistory.mergeHistory(
            existing = messageHistory.toList(),
            incoming = data.messages,
            latestContent = data.content,
            latestTimestamp = data.postTime.takeIf { it > 0L },
            latestSenderIcon = NotificationData.storedMessageSenderIcon(data),
        )
        messageHistory.clear()
        if (merged.isNotEmpty()) {
            messageHistory.addAll(
                merged.map { message ->
                    if (NotificationData.isDistinctMessageSenderIcon(message.senderIcon, data)) {
                        message
                    } else if (message.senderIcon != null) {
                        message.copy(senderIcon = null)
                    } else {
                        message
                    }
                },
            )
        } else if (data.content.isNotBlank()) {
            messageHistory.add(
                NotificationMessage(
                    text = data.content,
                    timestamp = data.postTime.takeIf { it > 0L },
                    senderIcon = NotificationData.storedMessageSenderIcon(data),
                ),
            )
        }
    }

    fun resolveUnreadCount(): Int {
        val data = plan.data
        return NotificationMessagingHistory.resolveUnreadCount(
            messages = messageHistory.toList(),
            badgeCount = data.badgeCount,
            textLineCount = data.textLineCount,
            fallback = displayedPostKeys.size,
            titleUnreadHint = NotificationMessagingHistory.parseTitleUnreadHint(data.title),
        )
    }

    fun resolveDisplayUnreadCount(): Int {
        if (readLocally) return 0
        val data = plan.data
        if (data.badgeCount > readBaselineBadgeCount) {
            return data.badgeCount - readBaselineBadgeCount
        }
        val messageDelta = messageHistory.size - readBaselineMessageCount
        if (messageDelta > 0) return messageDelta
        val postDelta = displayedPostKeys.size - readBaselinePostKeyCount
        if (postDelta > 0) return postDelta
        return resolveUnreadCount().coerceAtLeast(0)
    }

    fun captureReadBaseline() {
        readBaselineBadgeCount = plan.data.badgeCount
        readBaselineMessageCount = messageHistory.size
        readBaselinePostKeyCount = displayedPostKeys.size
        readLocally = true
        unreadCount.intValue = 0
    }

    fun latestActivityTime(): Long =
        NotificationMessagingHistory.latestConversationActivityTime(
            messages = messageHistory.toList(),
            postTime = plan.data.postTime,
        )

    fun contentSignature(): String {
        val latest = messageHistory.firstOrNull()
        return buildString {
            append(plan.data.key)
            append('|')
            append(plan.data.postTime)
            append('|')
            append(plan.data.content)
            append('|')
            append(latest?.text.orEmpty())
            append('|')
            append(messageHistory.size)
            append('|')
            append(plan.data.badgeCount)
            append('|')
            append(plan.data.textLineCount)
        }
    }
}

@SuppressLint("ViewConstructor")
private class CNoticeTouchHost(
    context: Context,
    private val onOutsideTouch: () -> Unit,
    private val onListInteraction: () -> Unit,
) : FrameLayout(context) {
    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        val handled = super.dispatchTouchEvent(event)
        if (handled) {
            onListInteraction()
        }
        return handled
    }
}

private fun isCNoticePreviewEntry(entry: CNoticeEntry): Boolean =
    entry.plan.data.key.startsWith(MessageReminderPreviewController.PREVIEW_KEY)

object CNoticeOverlayWindow {
    private const val TAG = "CNoticeOverlay"
    const val ANIMATION_MS = 280
    internal const val HANDLE_HEIGHT_DP = 20f
    internal const val BALL_SPACING_DP = 8f
    internal const val REPOSITION_MS = 300
    internal const val PEEK_BANNER_GAP_DP = 6f
    private const val LIST_PLACEMENT_HEIGHT_EPSILON_DP = 4f
    private const val PEEK_BANNER_DURATION_MS = 3_000L
    private const val PEEK_BANNER_ANIM_MS = 200L
    private const val PEEK_BANNER_MAX_CHARS = 80
    private const val RECONCILE_REMOVE_DELAY_MS = 250L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val items = mutableStateListOf<CNoticeEntry>()
    private var windowManager: WindowManager? = null
    private var touchHost: CNoticeTouchHost? = null
    private var composeView: ComposeView? = null
    private var rootLayoutParams: WindowManager.LayoutParams? = null
    private var owner: OverlayComposeOwner? = null
    private var placementSettings: MessageSettings? = null
    private var appContext: Context? = null
    private val listPhaseState = mutableStateOf(CNoticeListPhase.ExpandedActive)
    private val selectedConversationKeyState = mutableStateOf<String?>(null)
    private val panelContentRefreshState = mutableIntStateOf(0)
    private val historyLoadingState = mutableStateOf(false)
    private val historyExhaustedKeys = mutableSetOf<String>()
    private val historyScanOffsets = mutableMapOf<String, Int>()
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val horizontalEdgeState = mutableStateOf(SideBubbleHorizontalEdge.Right)
    private val yFractionState = mutableFloatStateOf(0.5f)
    private val horizontalDragOffsetPxState = mutableIntStateOf(0)
    private val verticalDragOffsetPxState = mutableIntStateOf(0)
    private var listHeightPx = 0
    private var dimRunnable: Runnable? = null
    private var idleDimRunnable: Runnable? = null
    private var ghostRunnable: Runnable? = null
    private val pendingReconcileRemovals = mutableMapOf<String, Runnable>()

    var placementPersistHandler: ((SideBubbleHorizontalEdge, Float) -> Unit)? = null
    var historyLoader: CNoticeHistoryLoader? = null

    val isShowing: Boolean get() = touchHost != null
    val isPanelOpen: Boolean get() = CNoticePanelOverlayWindow.isShowing

    fun closePanel() {
        CNoticePanelOverlayWindow.dismiss()
    }

    private fun onPanelClosed() {
        selectedConversationKeyState.value = null
        historyLoadingState.value = false
    }

    private fun notifyPanelContentRefresh() {
        if (CNoticePanelOverlayWindow.isShowing) {
            panelContentRefreshState.intValue++
        }
    }

    private fun itemSnapshot(): List<CNoticeEntry> = items.toList()

    private fun conversationKeyFor(data: NotificationData): String =
        NotificationData.conversationIdentityKey(data)

    private fun findEntryForData(data: NotificationData): CNoticeEntry? {
        val identity = conversationKeyFor(data)
        return items.firstOrNull { it.conversationSourceKey == identity }
            ?: items.firstOrNull { conversationKeyFor(it.plan.data) == identity }
            ?: items.firstOrNull { it.containsConversation(data) }
    }

    private fun mergeDuplicateEntriesByIdentity() {
        val grouped = items.groupBy { conversationKeyFor(it.plan.data) }
        grouped.values.forEach { group ->
            if (group.size <= 1) return@forEach
            val primary = group.maxByOrNull { it.latestActivityTime() } ?: group.first()
            group.filter { it != primary }.forEach { duplicate ->
                primary.displayedPostKeys.addAll(duplicate.displayedPostKeys)
                primary.planState.value = preserveConversationIcon(primary.plan, duplicate.plan)
                primary.syncMessagesFrom(duplicate.plan.data)
                primary.unreadCount.intValue = primary.resolveUnreadCount()
                removeEntry(duplicate, animate = false)
            }
        }
    }

    fun containsNotification(data: NotificationData): Boolean =
        itemSnapshot().any { it.matchesNotification(data) }

    fun containsConversation(conversationSourceKey: String): Boolean =
        itemSnapshot().any {
            it.conversationSourceKey == conversationSourceKey ||
                NotificationData.conversationIdentityKey(it.plan.data) == conversationSourceKey
        }

    fun refreshConversationIcon(conversationSourceKey: String, icon: android.graphics.Bitmap) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshConversationIcon(conversationSourceKey, icon) }
            return
        }
        val entry = items.firstOrNull {
            it.conversationSourceKey == conversationSourceKey ||
                NotificationData.conversationIdentityKey(it.plan.data) == conversationSourceKey
        } ?: return
        val current = entry.planState.value
        val safeIcon = icon.copy(icon.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
        val updatedData = current.data.copy(conversationIcon = safeIcon)
        entry.planState.value = current.copy(data = updatedData)
        notifyPanelContentRefresh()
    }

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

        val hostContext = resolveOverlayContext(context)
            ?: run {
                Log.w(TAG, "overlay permission not granted")
                return
            }

        ensureWindow(hostContext, plan.settings)
        if (touchHost == null) return
        applyWindowPlacement(hostContext, plan.settings)

        if (!plan.data.key.startsWith(MessageReminderPreviewController.PREVIEW_KEY)) {
            dismissPreview()
        }

        val conversationKey = conversationKeyFor(plan.data)
        val postKey = cNoticePostKey(plan.data)
        val existing = findEntryForData(plan.data)
        if (existing != null) {
            cancelDeferredReconcileRemoval(conversationKey)
            val previousSignature = existing.contentSignature()
            val hadExactPost = postKey in existing.displayedPostKeys
            val mergedPlan = preserveConversationIcon(existing.plan, plan)
            existing.planState.value = mergedPlan
            existing.syncMessagesFrom(mergedPlan.data)
            if (!hadExactPost) {
                existing.displayedPostKeys.add(postKey)
                existing.readLocally = false
            }
            existing.unreadCount.intValue = existing.resolveDisplayUnreadCount()
            if (hadExactPost && previousSignature == existing.contentSignature()) {
                notifyPanelContentRefresh()
                return
            }
            maybeShowPeekBanner(
                entry = existing,
                plan = mergedPlan,
                previousSignature = previousSignature,
                hadExactPost = hadExactPost,
            )
            reorderItemsByRecency()
            scheduleAutoDismiss(existing)
        } else {
            if (items.any { it.matchesNotification(plan.data) }) return
            if (findEntryForData(plan.data) != null) return
            if (items.isEmpty() && plan.settings.cNoticeDefaultCollapsed) {
                listPhaseState.value = CNoticeListPhase.Collapsed
            }
            val maxCount = plan.settings.cNoticeMaxCount.coerceIn(
                1,
                MessageSettings.C_NOTICE_MAX_RETAINED_CONVERSATIONS,
            )
            while (items.size >= maxCount) {
                removeEntry(items.last(), animate = false)
            }
            val messageHistory = mutableStateListOf<NotificationMessage>()
            val entry = CNoticeEntry(
                conversationSourceKey = conversationKey,
                planState = mutableStateOf(plan),
                unreadCount = mutableIntStateOf(1),
                messageHistory = messageHistory,
                visible = mutableStateOf(true),
                displayedPostKeys = mutableSetOf(postKey),
                onAction = onAction,
                onDismiss = onDismiss,
            )
            entry.syncMessagesFrom(plan.data)
            entry.unreadCount.intValue = entry.resolveUnreadCount()
            items.add(entry)
            maybeShowPeekBanner(
                entry = entry,
                plan = plan,
                previousSignature = "",
                hadExactPost = false,
            )
            reorderItemsByRecency()
            scheduleAutoDismiss(entry)
        }

        mergeDuplicateEntriesByIdentity()
        awakenListFromDimmed(plan.settings)
        notifyPanelContentRefresh()
        touchHost?.post { touchHost?.requestLayout() }
    }

    fun dismissEntry(key: String, postTime: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissEntry(key, postTime) }
            return
        }
        val postToken = "$key|$postTime"
        items.filter { postToken in it.displayedPostKeys }
            .toList()
            .forEach { entry ->
                entry.displayedPostKeys.remove(postToken)
                entry.unreadCount.intValue = persistentUnreadCount(entry)
            }
    }

    fun dismissEntriesForKey(key: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissEntriesForKey(key) }
            return
        }
        items.toList().forEach { entry ->
            val removed = entry.displayedPostKeys.removeAll { it.startsWith("$key|") }
            if (removed) {
                entry.unreadCount.intValue = persistentUnreadCount(entry)
            }
        }
    }

    /**
     * 系统通知被移除时同步 post token；贴边列表为常驻会话，不因通知栏清空而删球。
     */
    fun reconcileAfterRemoval(
        removedNotificationKey: String,
        removedConversationKey: String?,
        activeConversationKeys: Set<String>,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                reconcileAfterRemoval(
                    removedNotificationKey,
                    removedConversationKey,
                    activeConversationKeys,
                )
            }
            return
        }
        items.toList().forEach { entry ->
            val removedToken = entry.displayedPostKeys.removeAll { postToken ->
                postToken.startsWith("$removedNotificationKey|")
            }
            if (removedToken) {
                entry.unreadCount.intValue = persistentUnreadCount(entry)
            }
            cancelDeferredReconcileRemoval(entry.conversationSourceKey)
        }
    }

    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        CNoticePeekBannerOverlayWindow.dismissImmediate()
        CNoticePanelOverlayWindow.dismissImmediate()
        items.toList().forEach { removeEntry(it, animate = false) }
    }

    fun snapshotDisplayedKeys(): Set<String> =
        itemSnapshot().flatMap { entry -> entry.displayedPostKeys.map { it.substringBefore('|') } }.toSet()

    fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String> =
        itemSnapshot().filter { entry -> entry.matchesSourceKey(sourceKey) }
            .flatMap { entry -> entry.displayedPostKeys.map { it.substringBefore('|') } }
            .toSet()

    fun dismissSameSource(sourceKey: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissSameSource(sourceKey) }
            return
        }
        items.filter { it.matchesSourceKey(sourceKey) }
            .toList()
            .forEach { removeEntry(it, animate = true) }
    }

    fun dismissPreview() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissPreview() }
            return
        }
        items.filter {
            it.plan.data.key.startsWith(MessageReminderPreviewController.PREVIEW_KEY)
        }.toList().forEach { removeEntry(it, animate = false) }
    }

    fun updatePreviewPlan(plan: MessageDisplayPlan) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePreviewPlan(plan) }
            return
        }
        items.firstOrNull {
            it.plan.data.key.startsWith(MessageReminderPreviewController.PREVIEW_KEY)
        }?.let { it.planState.value = plan }
    }

    fun updateWindowPlacement(context: Context, settings: MessageSettings) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateWindowPlacement(context, settings) }
            return
        }
        horizontalEdgeState.value = settings.cNoticeHorizontalEdge
        yFractionState.floatValue = settings.cNoticeYFraction
        horizontalDragOffsetPxState.intValue = 0
        verticalDragOffsetPxState.intValue = 0
        applyWindowPlacement(
            hostContext = appContext ?: MessageOverlayHost.resolveHostContext(context) ?: return,
            settings = settings,
        )
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        closePanel()
        items.toList().forEach { removeEntry(it, animate = true) }
    }

    fun resumeAutoDismiss(key: String, postTime: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { resumeAutoDismiss(key, postTime) }
            return
        }
        val postToken = "$key|$postTime"
        items.firstOrNull { postToken in it.displayedPostKeys }?.let { scheduleAutoDismiss(it) }
    }

    fun pauseAutoDismiss(key: String, postTime: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { pauseAutoDismiss(key, postTime) }
            return
        }
        val postToken = "$key|$postTime"
        items.filter { postToken in it.displayedPostKeys }.forEach { cancelAutoDismiss(it) }
    }

    fun installPreview(context: Context, settings: MessageSettings) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { installPreview(context, settings) }
            return
        }
        val hostContext = resolveOverlayContext(context) ?: return
        dismissPreview()
        ensureWindow(hostContext, settings)
        applyWindowPlacement(hostContext, settings)
        if (itemSnapshot().any { !isCNoticePreviewEntry(it) }) {
            listPhaseState.value = CNoticeListPhase.ExpandedActive
            touchHost?.post { touchHost?.requestLayout() }
            return
        }
        listPhaseState.value = CNoticeListPhase.ExpandedActive
        val previewSettings = settings.copy(cNoticeAutoDismissSeconds = 0)
        repeat(3) { index ->
            val data = NotificationData(
                packageName = hostContext.packageName,
                key = "${MessageReminderPreviewController.PREVIEW_KEY}_$index",
                title = hostContext.getString(
                    com.slideindex.app.R.string.message_c_notice_preview_title,
                    index + 1
                ),
                content = hostContext.getString(com.slideindex.app.R.string.message_preview_content),
                largeIcon = null,
                appIcon = null,
                contentIntent = null,
                postTime = -index.toLong(),
                conversationSourceKey = "preview_$index",
            )
            val plan = MessageDisplayPlan(
                data = data,
                showFloatIcon = false,
                showSideBubble = false,
                showDanmaku = false,
                showCNotice = true,
                sideTheme = null,
                danmakuTheme = null,
                settings = previewSettings,
            )
            val previewHistory = mutableStateListOf(
                NotificationMessage(
                    text = data.content,
                    timestamp = data.postTime.takeIf { it > 0L },
                ),
            )
            items.add(
                CNoticeEntry(
                    conversationSourceKey = data.conversationSourceKey,
                    planState = mutableStateOf(plan),
                    unreadCount = mutableIntStateOf(index + 1),
                    messageHistory = previewHistory,
                    visible = mutableStateOf(true),
                    displayedPostKeys = mutableSetOf(cNoticePostKey(data)),
                    onAction = {},
                    onDismiss = {},
                )
            )
        }
    }

    private fun ensureWindow(hostContext: Context, settings: MessageSettings) {
        val existingHost = touchHost
        val existingView = composeView
        val existingOwner = owner
        if (existingHost != null && existingView != null && existingOwner != null) {
            val ownerAlive = existingOwner.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED
            if (existingHost.isAttachedToWindow && ownerAlive) {
                OverlayCompose.bindOwners(existingHost, existingOwner)
                OverlayCompose.bindOwners(existingView, existingOwner)
                return
            }
            cleanupWindow()
        } else if (existingHost != null) {
            cleanupWindow()
        }

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val dialogOwner = OverlayComposeOwner()
        val host = CNoticeTouchHost(
            context = overlayContext,
            onOutsideTouch = ::onOutsideTouch,
            onListInteraction = ::onListInteraction,
        )
        OverlayCompose.bindOwners(host, dialogOwner)
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                CNoticeListContent(
                    items = items,
                    listPhaseState = listPhaseState,
                    horizontalEdgeState = horizontalEdgeState,
                    onToggleCollapse = ::toggleCollapse,
                    onBallClick = ::onBallClick,
                    onListSized = ::onListSized,
                    onDragHandle = ::onDragHandle,
                    onPeekBallAnchored = ::reportPeekBallAnchor,
                )
            }
        }
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        placementSettings = settings
        horizontalEdgeState.value = settings.cNoticeHorizontalEdge
        yFractionState.floatValue = settings.cNoticeYFraction
        val params = buildEdgeLayoutParams(hostContext, settings)
        var wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            OverlayCompose.clearViewTreeOwners(host)
            dialogOwner.destroy()
            placementSettings = null
            return
        }
        fun tryAdd(windowManager: WindowManager): Boolean =
            runCatching { windowManager.addView(host, params) }
                .onFailure { Log.e(TAG, "addView failed", it) }
                .isSuccess
        var added = tryAdd(wm)
        if (!added &&
            params.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
            PermissionHelper.canDrawOverlays(hostContext.applicationContext)
        ) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            wm = hostContext.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            added = tryAdd(wm)
        }
        if (!added) {
            OverlayCompose.clearViewTreeOwners(host)
            dialogOwner.destroy()
            placementSettings = null
            return
        }

        windowManager = wm
        touchHost = host
        composeView = view
        rootLayoutParams = params
        owner = dialogOwner
        appContext = hostContext
        OverlayCompose.bindOwners(host, dialogOwner)
        OverlayCompose.bindOwners(view, dialogOwner)
        scheduleIdleDimTimer(settings)
    }

    private fun buildEdgeLayoutParams(
        hostContext: Context,
        settings: MessageSettings,
    ): WindowManager.LayoutParams {
        return MessageOverlayLayout.buildCNoticeLayoutParams(
            hostContext,
            settings,
            listHeightPx = listHeightPx,
            horizontalEdge = horizontalEdgeState.value,
            yFraction = yFractionState.floatValue,
            horizontalDragOffsetPx = horizontalDragOffsetPxState.intValue,
            verticalDragOffsetPx = verticalDragOffsetPxState.intValue,
        )
    }

    private fun applyWindowPlacement(hostContext: Context, settings: MessageSettings) {
        placementSettings = settings.copy(
            cNoticeHorizontalEdge = horizontalEdgeState.value,
            cNoticeYFraction = yFractionState.floatValue,
        )
        val wm = windowManager ?: return
        val host = touchHost ?: return
        if (!host.isAttachedToWindow) return
        val params = buildEdgeLayoutParams(hostContext, placementSettings ?: settings)
        rootLayoutParams = params
        runCatching { wm.updateViewLayout(host, params) }
            .onFailure { Log.w(TAG, "updateViewLayout failed", it) }
        refreshPeekBannerPlacement()
    }

    private fun onListSized(widthPx: Int, heightPx: Int) {
        if (heightPx <= 0) return
        val density = appContext?.resources?.displayMetrics?.density ?: return
        val epsilonPx = (LIST_PLACEMENT_HEIGHT_EPSILON_DP * density).roundToInt()
        if (kotlin.math.abs(heightPx - listHeightPx) <= epsilonPx) return
        listHeightPx = heightPx
        val hostContext = appContext ?: return
        val settings = placementSettings ?: return
        applyWindowPlacement(hostContext, settings)
    }

    private fun onOutsideTouch() {
        if (hasVisiblePeekBanner()) return
        val settings = placementSettings ?: return
        cancelIdleDimTimer()
        scheduleDimTimer(settings)
    }

    private fun hasVisiblePeekBanner(): Boolean =
        items.any { !it.peekBannerTextState.value.isNullOrBlank() }

    private fun reportPeekBallAnchor(
        entry: CNoticeEntry,
        anchor: CNoticePeekBannerOverlayWindow.BallAnchor,
    ) {
        val text = entry.peekBannerTextState.value?.trim()?.take(PEEK_BANNER_MAX_CHARS)
        if (text.isNullOrBlank()) return
        val host = touchHost ?: return
        val hostContext = appContext ?: return
        val hostLoc = IntArray(2)
        host.getLocationOnScreen(hostLoc)
        val screenAnchor = CNoticePeekBannerOverlayWindow.BallAnchor(
            ballLeft = anchor.ballLeft + hostLoc[0],
            ballTop = anchor.ballTop + hostLoc[1],
            ballRight = anchor.ballRight + hostLoc[0],
            ballBottom = anchor.ballBottom + hostLoc[1],
        )
        val gapPx = (PEEK_BANNER_GAP_DP * hostContext.resources.displayMetrics.density).roundToInt()
        CNoticePeekBannerOverlayWindow.update(
            context = hostContext,
            anchor = screenAnchor,
            text = text,
            horizontalEdge = horizontalEdgeState.value,
            gapPx = gapPx,
        )
    }

    private fun refreshPeekBannerPlacement() {
        if (!hasVisiblePeekBanner()) {
            CNoticePeekBannerOverlayWindow.dismiss()
            return
        }
        composeView?.invalidate()
    }

    private fun onListInteraction() {
        listPhaseState.value = CNoticeListUiState.onListInteraction(listPhaseState.value)
        resetTransparencyTimers()
    }

    private fun toggleCollapse() {
        listPhaseState.value = CNoticeListUiState.toggleCollapse(listPhaseState.value)
        resetTransparencyTimers()
    }

    private fun awakenListFromDimmed(settings: MessageSettings) {
        val awakened = CNoticeListUiState.onListInteraction(listPhaseState.value)
        listPhaseState.value = if (CNoticeListUiState.isCollapsedFamily(awakened)) {
            awakened
        } else {
            CNoticeListPhase.ExpandedActive
        }
        resetTransparencyTimers(settings)
    }

    private fun resetTransparencyTimers(settings: MessageSettings? = placementSettings) {
        cancelDimTimer()
        cancelGhostTimer()
        cancelIdleDimTimer()
        settings?.let { scheduleIdleDimTimer(it) }
    }

    private fun scheduleDimTimer(settings: MessageSettings) {
        cancelDimTimer()
        val phase = listPhaseState.value
        if (phase != CNoticeListPhase.ExpandedActive && phase != CNoticeListPhase.Collapsed) {
            return
        }
        val delayMs = settings.cNoticeDimDelayMs.coerceAtLeast(0)
        val runnable = Runnable {
            dimRunnable = null
            val current = listPhaseState.value
            val next = CNoticeListUiState.onOutsideTouch(current)
            if (next == current) return@Runnable
            listPhaseState.value = next
            cancelIdleDimTimer()
            scheduleGhostTimer(settings)
        }
        dimRunnable = runnable
        if (delayMs <= 0) {
            runnable.run()
        } else {
            mainHandler.postDelayed(runnable, delayMs.toLong())
        }
    }

    private fun cancelDimTimer() {
        dimRunnable?.let { mainHandler.removeCallbacks(it) }
        dimRunnable = null
    }

    private fun scheduleIdleDimTimer(settings: MessageSettings) {
        cancelIdleDimTimer()
        if (hasVisiblePeekBanner()) return
        val phase = listPhaseState.value
        if (phase != CNoticeListPhase.ExpandedActive && phase != CNoticeListPhase.Collapsed) {
            return
        }
        val delayMs = settings.cNoticeGhostDelayMs.coerceAtLeast(0)
        if (delayMs <= 0) return
        val runnable = Runnable {
            idleDimRunnable = null
            val current = listPhaseState.value
            if (current != CNoticeListPhase.ExpandedActive && current != CNoticeListPhase.Collapsed) {
                return@Runnable
            }
            val next = CNoticeListUiState.onOutsideTouch(current)
            if (next == current) return@Runnable
            listPhaseState.value = next
            scheduleGhostTimer(settings)
        }
        idleDimRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs.toLong())
    }

    private fun cancelIdleDimTimer() {
        idleDimRunnable?.let { mainHandler.removeCallbacks(it) }
        idleDimRunnable = null
    }

    private fun scheduleGhostTimer(settings: MessageSettings) {
        cancelGhostTimer()
        val phase = listPhaseState.value
        if (phase != CNoticeListPhase.ExpandedDimmed &&
            phase != CNoticeListPhase.CollapsedDimmed
        ) {
            return
        }
        val delayMs = settings.cNoticeGhostDelayMs.coerceAtLeast(0)
        if (delayMs <= 0) {
            listPhaseState.value = CNoticeListUiState.onGhostTimeout(listPhaseState.value)
            return
        }
        val runnable = Runnable {
            listPhaseState.value = CNoticeListUiState.onGhostTimeout(listPhaseState.value)
        }
        ghostRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs.toLong())
    }

    private fun cancelGhostTimer() {
        ghostRunnable?.let { mainHandler.removeCallbacks(it) }
        ghostRunnable = null
    }

    private fun onDragHandle(deltaY: Float, deltaX: Float, ended: Boolean) {
        val hostContext = appContext ?: return
        val settings = placementSettings ?: return
        val metrics = hostContext.resources.displayMetrics
        val screenHeight = metrics.heightPixels
        val screenWidth = metrics.widthPixels

        if (!ended) {
            verticalDragOffsetPxState.intValue += deltaY.roundToInt()
            horizontalDragOffsetPxState.intValue += deltaX.roundToInt()
            applyWindowPlacement(
                hostContext,
                settings.copy(
                    cNoticeHorizontalEdge = horizontalEdgeState.value,
                    cNoticeYFraction = yFractionState.floatValue,
                ),
            )
            return
        }

        val verticalOffsetPx = verticalDragOffsetPxState.intValue
        if (verticalOffsetPx != 0) {
            yFractionState.floatValue = MessagePlacementFractions.coerceY(
                yFractionState.floatValue + verticalOffsetPx / screenHeight.toFloat(),
            )
            verticalDragOffsetPxState.intValue = 0
        }

        val host = touchHost
        if (host != null && host.width > 0) {
            val location = IntArray(2)
            host.getLocationOnScreen(location)
            val centerX = location[0] + host.width / 2
            horizontalEdgeState.value = if (centerX < screenWidth / 2) {
                SideBubbleHorizontalEdge.Left
            } else {
                SideBubbleHorizontalEdge.Right
            }
        }
        horizontalDragOffsetPxState.intValue = 0
        applyWindowPlacement(
            hostContext,
            settings.copy(
                cNoticeHorizontalEdge = horizontalEdgeState.value,
                cNoticeYFraction = yFractionState.floatValue,
            ),
        )
        placementPersistHandler?.invoke(horizontalEdgeState.value, yFractionState.floatValue)
    }

    private fun sendQuickReply(entry: CNoticeEntry, text: String): Boolean {
        val context = appContext ?: return false
        var sent = false
        entry.displayedPostKeys.toList().forEach { postToken ->
            val key = postToken.substringBefore('|')
            val postTime = postToken.substringAfter('|', "").toLongOrNull() ?: 0L
            if (NotificationRemoteReply.sendReply(context, key, postTime, text)) {
                sent = true
            }
        }
        if (!sent) {
            val data = entry.plan.data
            sent = NotificationRemoteReply.sendReply(context, data.key, data.postTime, text)
        }
        return sent
    }

    private fun markConversationAsRead(entry: CNoticeEntry) {
        val context = appContext ?: return
        var marked = false
        entry.displayedPostKeys.toList().forEach { postToken ->
            val key = postToken.substringBefore('|')
            val postTime = postToken.substringAfter('|', "").toLongOrNull() ?: 0L
            if (NotificationRemoteReply.sendMarkAsRead(context, key, postTime)) {
                marked = true
            }
        }
        if (!marked) {
            val data = entry.plan.data
            marked = NotificationRemoteReply.sendMarkAsRead(context, data.key, data.postTime)
        }
        entry.captureReadBaseline()
        notifyPanelContentRefresh()
    }

    private fun persistentUnreadCount(entry: CNoticeEntry): Int {
        if (entry.readLocally) return 0
        if (entry.displayedPostKeys.isEmpty()) return 0
        return entry.resolveDisplayUnreadCount()
    }

    private fun reorderItemsByRecency() {
        if (items.size <= 1) return
        val sorted = items.sortedByDescending { it.latestActivityTime() }
        if (sorted.map { it.conversationSourceKey } == items.map { it.conversationSourceKey }) return
        items.clear()
        items.addAll(sorted)
    }

    private fun onBallClick(entry: CNoticeEntry) {
        if (entry.plan.data.key.startsWith(MessageReminderPreviewController.PREVIEW_KEY)) return
        onListInteraction()
        openPanel(entry.conversationSourceKey)
    }

    private fun openPanel(conversationKey: String) {
        val hostContext = appContext ?: return
        clearAllPeekBanners()
        selectedConversationKeyState.value = conversationKey
        CNoticePanelOverlayWindow.show(
            context = hostContext,
            horizontalEdge = horizontalEdgeState.value,
            edgeMarginDp = placementSettings?.cNoticeEdgeMarginDp ?: 8f,
            selectedKey = conversationKey,
            panelEntriesProvider = ::panelEntries,
            historyLoading = { historyLoadingState.value },
            historyExhausted = { it in historyExhaustedKeys },
            onSelectConversation = ::selectConversationInPanel,
            onOpenApp = ::openAppForConversation,
            onMarkRead = ::markConversationAsReadByKey,
            onSendReply = ::sendQuickReplyByKey,
            onLoadOlderHistory = ::loadOlderHistory,
            contentRefreshState = panelContentRefreshState,
            onPanelClosed = ::onPanelClosed,
        )
    }

    private fun selectConversationInPanel(conversationKey: String) {
        selectedConversationKeyState.value = conversationKey
        historyLoadingState.value = false
        CNoticePanelOverlayWindow.updateSelectedKey(conversationKey)
        notifyPanelContentRefresh()
    }

    private fun openAppForConversation(conversationKey: String) {
        val entry = items.firstOrNull { it.conversationSourceKey == conversationKey } ?: return
        closePanel()
        entry.onAction(MessageAction.Read)
    }

    private fun markConversationAsReadByKey(conversationKey: String) {
        val entry = items.firstOrNull { it.conversationSourceKey == conversationKey } ?: return
        markConversationAsRead(entry)
    }

    private fun sendQuickReplyByKey(conversationKey: String, text: String): Boolean {
        val entry = items.firstOrNull { it.conversationSourceKey == conversationKey } ?: return false
        return sendQuickReply(entry, text)
    }

    private fun panelEntries(): List<CNoticePanelEntryUi> {
        val snapshot = itemSnapshot()
        val hasRealEntries = snapshot.any { !isCNoticePreviewEntry(it) }
        val source = if (hasRealEntries) {
            snapshot.filter { !isCNoticePreviewEntry(it) }
        } else {
            snapshot
        }
        return source.sortedByDescending { it.latestActivityTime() }.map { entry ->
            val settings = entry.plan.settings
            val headerStyle = settings.cNoticeDetailHeaderStyle()
            CNoticePanelEntryUi(
                conversationSourceKey = entry.conversationSourceKey,
                data = entry.plan.data,
                unreadCount = entry.unreadCount.intValue,
                messages = entry.messageHistory.toList(),
                headerColor = headerStyle.background,
                headerTextColor = headerStyle.content,
            )
        }
    }

    private fun loadOlderHistory(conversationKey: String) {
        val loader = historyLoader ?: return
        if (historyLoadingState.value || conversationKey in historyExhaustedKeys) return
        val entry = items.firstOrNull { it.conversationSourceKey == conversationKey } ?: return
        val offset = historyScanOffsets[conversationKey] ?: 0
        historyLoadingState.value = true
        notifyPanelContentRefresh()
        historyScope.launch {
            val result = runCatching {
                loader.loadOlderMessages(
                    data = entry.plan.data,
                    currentMessages = entry.messageHistory.toList(),
                    historyScanOffset = offset,
                )
            }.getOrElse {
                historyLoadingState.value = false
                return@launch
            }
            historyScanOffsets[conversationKey] = result.nextScanOffset
            if (result.exhausted) {
                historyExhaustedKeys.add(conversationKey)
            }
            if (result.newMessages.isNotEmpty()) {
                val chronological = NotificationMessagingHistory.messagesOldestFirst(entry.messageHistory.toList())
                val combined = (result.newMessages + chronological).distinctBy { message ->
                    "${message.text}|${message.sender.orEmpty()}|${message.timestamp}"
                }
                entry.messageHistory.clear()
                entry.messageHistory.addAll(NotificationMessagingHistory.messagesNewestFirst(combined))
            }
            historyLoadingState.value = false
            notifyPanelContentRefresh()
        }
    }

    private fun cancelDeferredReconcileRemoval(conversationKey: String) {
        pendingReconcileRemovals.remove(conversationKey)?.let { mainHandler.removeCallbacks(it) }
    }

    private fun scheduleDeferredReconcileRemoval(entry: CNoticeEntry) {
        cancelDeferredReconcileRemoval(entry.conversationSourceKey)
    }

    private fun resolveOverlayContext(context: Context): Context? =
        MessageOverlayHost.resolveContentPanelContext(context)
            ?: MessageOverlayHost.resolveHostContext(context)


    private fun maybeShowPeekBanner(
        entry: CNoticeEntry,
        plan: MessageDisplayPlan,
        previousSignature: String,
        hadExactPost: Boolean,
    ) {
        if (!plan.settings.cNoticePeekBannerEnabled) return
        if (CNoticePanelOverlayWindow.isShowing) return
        if (hadExactPost && previousSignature == entry.contentSignature()) return
        val contentSignature = NotificationMessagingHistory.peekContentSignature(
            latestMessage = entry.messageHistory.firstOrNull(),
            data = plan.data,
        )
        if (contentSignature.isBlank() || contentSignature == entry.lastPeekContentSignature) return
        val preview = CNoticeListUiState.formatPeekBannerText(
            data = plan.data,
            message = entry.messageHistory.firstOrNull(),
        )
        if (preview.isBlank()) return
        entry.pendingPeekContentSignature = contentSignature
        items.forEach { other ->
            if (other !== entry) clearPeekBanner(other)
        }
        showPeekBanner(entry, preview)
    }

    private fun showPeekBanner(entry: CNoticeEntry, text: String) {
        val preview = text.trim().take(PEEK_BANNER_MAX_CHARS)
        if (preview.isBlank()) return
        entry.peekBannerHideRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.peekBannerHideRunnable = null
        entry.pendingPeekBannerText = preview
        ensurePeekBannerPresentScheduled(entry)
    }

    private fun ensurePeekBannerPresentScheduled(entry: CNoticeEntry) {
        if (entry.peekBannerShowRunnable != null) return
        val preview = entry.pendingPeekBannerText
            ?.trim()
            ?.take(PEEK_BANNER_MAX_CHARS)
            ?: return
        val current = entry.peekBannerTextState.value
            ?.trim()
            ?.take(PEEK_BANNER_MAX_CHARS)
        when {
            current.isNullOrBlank() -> {
                schedulePeekBannerAction(entry, 0L) {
                    flushPendingPeekBanner(entry)
                }
            }
            current == preview -> {
                entry.pendingPeekBannerText = null
                entry.pendingPeekContentSignature = null
                presentPeekBanner(entry, preview)
            }
            else -> {
                val enterRemaining = peekBannerEnterRemainingMs(entry)
                if (enterRemaining > 0L) {
                    schedulePeekBannerAction(entry, enterRemaining) {
                        ensurePeekBannerPresentScheduled(entry)
                    }
                } else {
                    entry.peekBannerTextState.value = null
                    schedulePeekBannerAction(entry, PEEK_BANNER_ANIM_MS) {
                        flushPendingPeekBanner(entry)
                    }
                }
            }
        }
    }

    private fun peekBannerEnterRemainingMs(entry: CNoticeEntry): Long {
        if (entry.lastPeekPresentedUptimeMs <= 0L) return 0L
        val elapsed = SystemClock.uptimeMillis() - entry.lastPeekPresentedUptimeMs
        return (PEEK_BANNER_ANIM_MS - elapsed).coerceAtLeast(0L)
    }

    private fun schedulePeekBannerAction(entry: CNoticeEntry, delayMs: Long, action: () -> Unit) {
        entry.peekBannerShowRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            entry.peekBannerShowRunnable = null
            action()
            if (!entry.pendingPeekBannerText.isNullOrBlank()) {
                ensurePeekBannerPresentScheduled(entry)
            }
        }
        entry.peekBannerShowRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun flushPendingPeekBanner(entry: CNoticeEntry) {
        val preview = entry.pendingPeekBannerText
            ?.trim()
            ?.take(PEEK_BANNER_MAX_CHARS)
        entry.pendingPeekBannerText = null
        if (!preview.isNullOrBlank()) {
            presentPeekBanner(entry, preview)
        }
    }

    private fun presentPeekBanner(entry: CNoticeEntry, preview: String) {
        entry.peekBannerShowRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.peekBannerShowRunnable = null
        entry.pendingPeekBannerText = null
        entry.pendingPeekContentSignature?.let { entry.lastPeekContentSignature = it }
        entry.pendingPeekContentSignature = null
        entry.peekBannerTextState.value = preview
        entry.lastPeekPresentedUptimeMs = SystemClock.uptimeMillis()
        cancelDimTimer()
        cancelIdleDimTimer()
        val runnable = Runnable {
            entry.peekBannerTextState.value = null
            entry.peekBannerHideRunnable = null
            onPeekBannerHidden()
        }
        entry.peekBannerHideRunnable = runnable
        mainHandler.postDelayed(runnable, PEEK_BANNER_DURATION_MS)
        composeView?.post { composeView?.invalidate() }
    }

    private fun clearPeekBanner(entry: CNoticeEntry) {
        entry.peekBannerHideRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.peekBannerHideRunnable = null
        entry.peekBannerShowRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.peekBannerShowRunnable = null
        entry.pendingPeekBannerText = null
        entry.pendingPeekContentSignature = null
        entry.peekBannerTextState.value = null
        onPeekBannerHidden()
    }

    private fun onPeekBannerHidden() {
        if (hasVisiblePeekBanner()) return
        CNoticePeekBannerOverlayWindow.dismiss()
        placementSettings?.let { resetTransparencyTimers(it) }
    }

    private fun clearAllPeekBanners() {
        items.forEach { clearPeekBanner(it) }
    }

    private fun scheduleAutoDismiss(entry: CNoticeEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.dismissRunnable = null
    }

    private fun cancelAutoDismiss(entry: CNoticeEntry) {
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.dismissRunnable = null
    }

    private fun removeEntry(entry: CNoticeEntry, animate: Boolean) {
        clearPeekBanner(entry)
        cancelDeferredReconcileRemoval(entry.conversationSourceKey)
        entry.dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        entry.dismissRunnable = null
        if (!items.contains(entry)) return
        items.remove(entry)
        if (items.isEmpty()) cleanupWindow()
    }

    private fun cleanupWindow() {
        cancelDimTimer()
        cancelIdleDimTimer()
        cancelGhostTimer()
        CNoticePeekBannerOverlayWindow.dismissImmediate()
        CNoticePanelOverlayWindow.dismissImmediate()
        pendingReconcileRemovals.values.forEach { mainHandler.removeCallbacks(it) }
        pendingReconcileRemovals.clear()
        selectedConversationKeyState.value = null
        historyLoadingState.value = false
        historyExhaustedKeys.clear()
        historyScanOffsets.clear()
        val wm = windowManager
        val host = touchHost
        val view = composeView
        val dialogOwner = owner
        windowManager = null
        touchHost = null
        composeView = null
        owner = null
        rootLayoutParams = null
        placementSettings = null
        appContext = null
        listHeightPx = 0
        horizontalDragOffsetPxState.intValue = 0
        verticalDragOffsetPxState.intValue = 0
        listPhaseState.value = CNoticeListPhase.ExpandedActive
        host?.let { h -> wm?.let { runCatching { it.removeView(h) } } }
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
    }
}

private data class CNoticeDetailHeaderStyle(
    val background: Color,
    val content: Color,
)

private fun MessageSettings.cNoticeDetailHeaderStyle(): CNoticeDetailHeaderStyle {
    val theme = com.slideindex.app.message.MessageThemeCatalog.themeFor(
        com.slideindex.app.message.MessageStyle.SideBubble,
        sideThemeId,
    )
    val accent = Color(theme.titleColorArgb)
    return if (accent.luminance() > 0.6f) {
        CNoticeDetailHeaderStyle(
            background = Color(0xFF37474F),
            content = accent,
        )
    } else {
        CNoticeDetailHeaderStyle(
            background = accent,
            content = Color.White,
        )
    }
}

@Composable
private fun CNoticeListContent(
    items: SnapshotStateList<CNoticeEntry>,
    listPhaseState: MutableState<CNoticeListPhase>,
    horizontalEdgeState: MutableState<SideBubbleHorizontalEdge>,
    onToggleCollapse: () -> Unit,
    onBallClick: (CNoticeEntry) -> Unit,
    onListSized: (Int, Int) -> Unit,
    onDragHandle: (deltaY: Float, deltaX: Float, ended: Boolean) -> Unit,
    onPeekBallAnchored: (CNoticeEntry, CNoticePeekBannerOverlayWindow.BallAnchor) -> Unit,
) {
    val phase by listPhaseState
    val itemEntries = items.toList().sortedByDescending { it.latestActivityTime() }
    val settings = itemEntries.firstOrNull()?.plan?.settings ?: return
    val listAlpha = CNoticeListUiState.listAlpha(phase, settings)
    val handleAlpha = CNoticeListUiState.handleAlpha(phase)
    val iconSizeDp = settings.cNoticeIconSizeDp.coerceIn(32f, 64f).dp
    val hasRealEntries = itemEntries.any { !isCNoticePreviewEntry(it) }
    val collapsedFamily = CNoticeListUiState.isCollapsedFamily(phase)
    val visibleEntries = if (collapsedFamily) {
        val source = if (hasRealEntries) {
            itemEntries.filter { !isCNoticePreviewEntry(it) }
        } else {
            itemEntries
        }
        source.take(1)
    } else if (hasRealEntries) {
        itemEntries.filter { !isCNoticePreviewEntry(it) }
    } else {
        itemEntries
    }

    val horizontalAlignment = when (horizontalEdgeState.value) {
        SideBubbleHorizontalEdge.Left -> Alignment.Start
        SideBubbleHorizontalEdge.Right -> Alignment.End
    }
    val horizontalEdge = horizontalEdgeState.value
    val peekEntry = if (settings.cNoticePeekBannerEnabled) {
        visibleEntries.firstOrNull { !it.peekBannerTextState.value.isNullOrBlank() }
    } else {
        null
    }
    OverlayAwareModuleTheme {
        LazyColumn(
            modifier = Modifier
                .wrapContentWidth()
                .alpha(listAlpha)
                .graphicsLayer { clip = false }
                .onSizeChanged { size -> onListSized(size.width, size.height) },
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(CNoticeOverlayWindow.BALL_SPACING_DP.dp),
        ) {
            item(key = "c_notice_handle") {
                CNoticeDragHandle(
                    itemWidth = iconSizeDp,
                    alpha = handleAlpha,
                    onToggleCollapse = onToggleCollapse,
                    onDrag = onDragHandle,
                )
            }
            items(
                items = visibleEntries,
                key = { it.conversationSourceKey },
            ) { entry ->
                if (!entry.visible.value) return@items
                val isPeekTarget = peekEntry?.conversationSourceKey == entry.conversationSourceKey
                CNoticeBallRow(
                    entry = entry,
                    iconSizeDp = iconSizeDp,
                    horizontalEdge = horizontalEdge,
                    onClick = { onBallClick(entry) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tween(0),
                            placementSpec = tween(
                                durationMillis = CNoticeOverlayWindow.REPOSITION_MS,
                                easing = FastOutLinearInEasing,
                            ),
                            fadeOutSpec = tween(0),
                        )
                        .then(
                            if (isPeekTarget) {
                                Modifier.onGloballyPositioned { coordinates ->
                                    if (!coordinates.isAttached) return@onGloballyPositioned
                                    if (entry.peekBannerTextState.value.isNullOrBlank()) {
                                        return@onGloballyPositioned
                                    }
                                    val bounds = coordinates.boundsInWindow()
                                    onPeekBallAnchored(
                                        entry,
                                        CNoticePeekBannerOverlayWindow.BallAnchor(
                                            ballLeft = bounds.left.roundToInt(),
                                            ballTop = bounds.top.roundToInt(),
                                            ballRight = bounds.right.roundToInt(),
                                            ballBottom = bounds.bottom.roundToInt(),
                                        ),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun CNoticeDragHandle(
    itemWidth: androidx.compose.ui.unit.Dp,
    alpha: Float,
    onToggleCollapse: () -> Unit,
    onDrag: (deltaY: Float, deltaX: Float, ended: Boolean) -> Unit,
) {
    var dragTotalX by remember { mutableFloatStateOf(0f) }
    var dragTotalY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .size(width = itemWidth, height = CNoticeOverlayWindow.HANDLE_HEIGHT_DP.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleCollapse() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragTotalX = 0f
                        dragTotalY = 0f
                    },
                    onDragEnd = { onDrag(dragTotalY, dragTotalX, true) },
                    onDragCancel = { onDrag(dragTotalY, dragTotalX, true) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragTotalX += dragAmount.x
                        dragTotalY += dragAmount.y
                        onDrag(dragAmount.y, dragAmount.x, false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
private fun CNoticeBallRow(
    entry: CNoticeEntry,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    horizontalEdge: SideBubbleHorizontalEdge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.graphicsLayer { clip = false }) {
        CNoticeBallItem(
            entry = entry,
            iconSizeDp = iconSizeDp,
            horizontalEdge = horizontalEdge,
            onClick = onClick,
        )
    }
}

@Composable
private fun CNoticeBallItem(
    entry: CNoticeEntry,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    horizontalEdge: SideBubbleHorizontalEdge,
    onClick: () -> Unit,
) {
    val plan = entry.planState.value
    val data = plan.data
    val unread = entry.unreadCount.intValue
    if (!entry.visible.value) return

    val badgeLabel = if (unread > 99) "99+" else unread.toString()
    val appBadgeSize = iconSizeDp * 0.34f
    val ballAlignment = when (horizontalEdge) {
        SideBubbleHorizontalEdge.Left -> Alignment.TopStart
        SideBubbleHorizontalEdge.Right -> Alignment.TopEnd
    }
    Box(
        modifier = Modifier.graphicsLayer { clip = false },
        contentAlignment = ballAlignment,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .padding(top = if (unread > 1) 4.dp else 0.dp)
                .size(iconSizeDp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.95f))
                    .clickable(onClick = onClick)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                MessageNotificationIcon(
                    iconBitmap = data.conversationIcon,
                    appIconBitmap = null,
                    sizeDp = iconSizeDp - 4.dp,
                    allowAppIconFallback = false,
                    fallbackLabel = NotificationData.normalizeConversationTitle(data.title),
                )
            }
            data.appIcon?.let { appIcon ->
                Image(
                    bitmap = appIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(appBadgeSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            if (unread > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(Color(0xFFE53935), CircleShape)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
