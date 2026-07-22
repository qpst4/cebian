package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.slideindex.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import androidx.compose.ui.platform.LocalLocale
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

private val PANEL_WIDTH = 300.dp

private enum class FloatingPanelTab {
    Stash,
    Clipboard,
}

enum class StashPanelInitialTab {
    Stash,
    Clipboard,
}

private fun StashPanelInitialTab.toFloatingPanelTab(): FloatingPanelTab = when (this) {
    StashPanelInitialTab.Stash -> FloatingPanelTab.Stash
    StashPanelInitialTab.Clipboard -> FloatingPanelTab.Clipboard
}

object FloatBallStashPanel {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var gravityEndState: MutableState<Boolean>? = null
    private var panelVisibilityState: androidx.compose.animation.core.MutableTransitionState<Boolean>? = null
    private var pendingInitialTab: FloatingPanelTab = FloatingPanelTab.Stash
    private var requestedTabOrdinalHolder: MutableState<Int>? = null

    val isShowing: Boolean get() = composeView != null

    fun show(context: Context, initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash): Boolean {
        pendingInitialTab = initialTab.toFloatingPanelTab()
        requestedTabOrdinalHolder?.value = pendingInitialTab.ordinal
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context, initialTab)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        if (isShowing) {
            composeView?.visibility = View.VISIBLE
            composeView?.post {
                panelVisibilityState?.targetState = true
            }
            FloatBallOverlay.bringChromeAbovePanels()
            return true
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }
        ensureWindow(hostContext)
        composeView?.visibility = View.VISIBLE
        composeView?.post {
            panelVisibilityState?.targetState = true
        }
        FloatBallOverlay.bringChromeAbovePanels()
        return composeView != null
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (!isShowing) return

        updateWindowInputActive(active = false)
        panelVisibilityState?.targetState = false
        val view = composeView
        val wm = windowManager
        val currentOwner = owner
        if (view != null && wm != null && currentOwner != null) {
            currentOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(300)
                if (panelVisibilityState?.targetState == true) return@launch // Abort if re-shown

                view.visibility = View.GONE
            }
        }
    }

    fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { destroy() }
            return
        }
        val currentOwner = owner
        val view = composeView
        val wm = windowManager
        if (currentOwner != null && view != null && wm != null) {
            updateWindowInputActive(active = false)
            runCatching { wm.removeView(view) }
            screenOffReceiver?.let { receiver ->
                appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
            }
            view.post { currentOwner.destroy() }

            composeView = null
            owner = null
            layoutParams = null
            windowManager = null
            gravityEndState = null
            screenOffReceiver = null
            appContext = null
            panelVisibilityState = null
            requestedTabOrdinalHolder = null
            pendingInitialTab = FloatingPanelTab.Stash
        }
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return
        val gravityEndHolder = mutableStateOf(true)
        gravityEndState = gravityEndHolder
        
        panelVisibilityState = androidx.compose.animation.core.MutableTransitionState(false)
        
        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setContent {
                val gravityEnd by gravityEndHolder
                val visibleState = panelVisibilityState!!
                
                SlideIndexTheme {
                    val springSpec = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = 0.8f,
                        stiffness = 300f
                    )
                    val fadeSpec = androidx.compose.animation.core.tween<Float>(250)
                    androidx.compose.animation.AnimatedVisibility(
                        visibleState = visibleState,
                        enter = androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { if (gravityEnd) it else -it },
                            animationSpec = springSpec
                        ) + androidx.compose.animation.fadeIn(animationSpec = fadeSpec),
                        exit = androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { if (gravityEnd) it else -it },
                            animationSpec = springSpec
                        ) + androidx.compose.animation.fadeOut(animationSpec = fadeSpec)
                    ) {
                        FloatBallStashPanelContent(
                            gravityEnd = gravityEnd,
                            onDismiss = { dismiss() },
                            onToggleSide = {
                                gravityEndHolder.value = !gravityEndHolder.value
                            },
                        )
                    }
                }
            }
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            dialogOwner.destroy()
            return
        }
        val params = buildLayoutParams(context)
        val added = runCatching { wm.addView(compose, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return
        }
        windowManager = wm
        composeView = compose
        owner = dialogOwner
        layoutParams = params
        appContext = context
        registerScreenOffReceiver(context)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /** 剪贴板 Tab 需要窗口可聚焦，才能读取剪贴板并弹出输入法。 */
    fun updateWindowInputActiveForClipboard(active: Boolean) {
        updateWindowInputActive(active)
    }

    private fun updateWindowInputActive(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateWindowInputActive(active) }
            return
        }
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = if (active) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
        if (active) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
        } else {
            view.clearFocus()
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private const val TAG = "FloatBallStashPanel"

    internal fun pendingInitialTabOrdinal(): Int = pendingInitialTab.ordinal

    internal fun consumePendingInitialTabOrdinal(): Int = pendingInitialTab.ordinal

    internal fun bindRequestedTabOrdinalHolder(holder: MutableState<Int>) {
        requestedTabOrdinalHolder = holder
    }
}

@Composable
private fun FloatBallStashPanelContent(
    gravityEnd: Boolean,
    onDismiss: () -> Unit,
    onToggleSide: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = FloatBallStashPanel.pendingInitialTabOrdinal(),
        pageCount = { FloatingPanelTab.entries.size },
    )
    val requestedTabState = remember {
        mutableStateOf(FloatBallStashPanel.consumePendingInitialTabOrdinal()).also {
            FloatBallStashPanel.bindRequestedTabOrdinalHolder(it)
        }
    }
    var selectedTab by remember {
        mutableStateOf(FloatingPanelTab.entries[requestedTabState.value])
    }
    var entries by remember { mutableStateOf<List<StashEntry>>(emptyList()) }
    var clipboardEntries by remember { mutableStateOf<List<ClipboardEntry>>(emptyList()) }
    val repo = StashAccess.repository
    val clipboardRepo = ClipboardAccess.repository
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = FloatingPanelTab.entries[pagerState.currentPage]
    }
    LaunchedEffect(requestedTabState.value) {
        val target = requestedTabState.value
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        val onClipboard = pagerState.settledPage == FloatingPanelTab.Clipboard.ordinal
        if (onClipboard) {
            clipboardRepo?.refreshClipboardWithFocus(context, force = true)
        } else {
            FloatBallStashPanel.updateWindowInputActiveForClipboard(false)
        }
    }
    LaunchedEffect(repo) {
        repo?.entries?.collect { entries = it }
    }
    LaunchedEffect(clipboardRepo) {
        clipboardRepo?.entries?.collect { clipboardEntries = it }
    }
    val dismissInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss),
        contentAlignment = if (gravityEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(PANEL_WIDTH)
                .shadow(12.dp, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.floating_panel_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Row {
                    IconButton(onClick = onToggleSide) {
                        Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.stash_toggle_side))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTab == FloatingPanelTab.Stash,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(FloatingPanelTab.Stash.ordinal) }
                    },
                    label = { Text(stringResource(R.string.stash_panel_tab)) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
                FilterChip(
                    selected = selectedTab == FloatingPanelTab.Clipboard,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(FloatingPanelTab.Clipboard.ordinal) }
                    },
                    label = { Text(stringResource(R.string.clipboard_panel_tab)) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
            HorizontalDivider()
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                when (FloatingPanelTab.entries[page]) {
                    FloatingPanelTab.Stash -> StashPanelBody(
                        entries = entries,
                        repo = repo,
                        context = context,
                        scope = scope,
                    )
                    FloatingPanelTab.Clipboard -> ClipboardPanelBody(
                        entries = clipboardEntries,
                        clipboardRepo = clipboardRepo,
                        isActive = selectedTab == FloatingPanelTab.Clipboard,
                        context = context,
                        scope = scope,
                        onSearchFocusChanged = { focused ->
                            FloatBallStashPanel.updateWindowInputActiveForClipboard(focused)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StashPanelBody(
    entries: List<StashEntry>,
    repo: com.slideindex.app.stash.StashRepository?,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.stash_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                StashEntryCard(
                    entry = entry,
                    onPin = {
                        when (entry.type) {
                            StashEntryType.TEXT -> {
                                StashCoordinator.pinTextToScreen(context, entry.text.orEmpty())
                            }
                            StashEntryType.IMAGE -> {
                                val bitmap = repo?.loadImage(entry) ?: return@StashEntryCard
                                StashCoordinator.pinImageFromStash(context, entry, bitmap)
                            }
                        }
                    },
                    onCopy = {
                        when (entry.type) {
                            StashEntryType.TEXT -> {
                                FloatBallTextPick.copyText(context, entry.text.orEmpty())
                                Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                            }
                            StashEntryType.IMAGE -> {
                                val bitmap = repo?.loadImage(entry) ?: return@StashEntryCard
                                FloatBallTextPick.copyImage(context, bitmap)
                            }
                        }
                    },
                    onShare = {
                        when (entry.type) {
                            StashEntryType.TEXT -> FloatBallTextPick.shareText(context, entry.text.orEmpty())
                            StashEntryType.IMAGE -> {
                                val bitmap = repo?.loadImage(entry) ?: return@StashEntryCard
                                FloatBallTextPick.shareScreenshot(context, bitmap)
                            }
                        }
                    },
                    onToggleStar = {
                        scope.launch { repo?.toggleStar(entry.id) }
                    },
                    onDelete = {
                        scope.launch { repo?.delete(entry.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ClipboardPanelBody(
    entries: List<ClipboardEntry>,
    clipboardRepo: com.slideindex.app.clipboard.ClipboardHistoryRepository?,
    isActive: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onSearchFocusChanged: (Boolean) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val filteredEntries = remember(entries, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            entries
        } else {
            entries.filter { it.matchesQuery(query) }
        }
    }
    val topEntryId = entries.firstOrNull()?.id
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || searchQuery.isNotBlank()) return@LaunchedEffect
        listState.animateScrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            hintResId = R.string.clipboard_search_hint,
            onFocusChanged = onSearchFocusChanged,
        )
        when {
            filteredEntries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (entries.isEmpty()) {
                                R.string.clipboard_empty
                            } else {
                                R.string.clipboard_search_empty
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        ClipboardEntryCard(
                            entry = entry,
                            onCopy = {
                                ClipboardWriter.write(context, entry)
                                Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                scope.launch { clipboardRepo?.delete(entry.id) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardEntryCard(
    entry: ClipboardEntry,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var imageThumb by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id, entry.uri, entry.mimeType) {
        imageThumb = if (entry.type == ClipboardEntryType.URI && entry.mimeType?.startsWith("image/") == true) {
            loadClipboardUriThumbnail(context, entry.uri)
        } else {
            null
        }
    }
    val cardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatStashRelativeTime(entry.createdAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = clipboardEntryTypeLabel(entry.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when {
                imageThumb != null -> {
                    Image(
                        bitmap = imageThumb!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                else -> {
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StashCardActionIcon(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = null,
                    onClick = onCopy,
                )
                Spacer(modifier = Modifier.weight(1f))
                StashCardActionIcon(
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.stash_action_delete),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun clipboardEntryTypeLabel(type: ClipboardEntryType): String = when (type) {
    ClipboardEntryType.TEXT -> stringResource(R.string.clipboard_entry_type_text)
    ClipboardEntryType.URI -> stringResource(R.string.clipboard_entry_type_uri)
    ClipboardEntryType.INTENT -> stringResource(R.string.clipboard_entry_type_intent)
    ClipboardEntryType.HTML -> stringResource(R.string.clipboard_entry_type_html)
}

private fun loadClipboardUriThumbnail(context: Context, uri: String?): Bitmap? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(uri.toUri())?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}

@Composable
private fun StashEntryCard(
    entry: StashEntry,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val repo = StashAccess.repository
    var thumb by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        thumb = if (entry.type == StashEntryType.IMAGE) repo?.loadImage(entry) else null
    }
    val cardShape = RoundedCornerShape(12.dp)
    val containerColor = if (entry.starred) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entry.starred) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatStashRelativeTime(entry.createdAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = onToggleStar,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (entry.starred) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (entry.starred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            when (entry.type) {
                StashEntryType.TEXT -> {
                    Text(
                        text = entry.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StashEntryType.IMAGE -> {
                    val image = thumb
                    if (image != null) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StashCardActionIcon(
                    icon = Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.stash_action_pin),
                    onClick = onPin,
                )
                StashCardActionIcon(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = null,
                    onClick = onCopy,
                )
                StashCardActionIcon(
                    icon = Icons.Default.Share,
                    contentDescription = null,
                    onClick = onShare,
                )
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    StashCardActionIcon(
                        icon = Icons.Default.MoreVert,
                        contentDescription = null,
                        onClick = { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.stash_action_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StashCardActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatStashRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L -> stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L -> stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalLocale.current.platformLocale
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMs }
            val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                if (locale.language == "zh") "M月d日" else "MMM d"
            } else {
                "yyyy/M/d"
            }
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
}
