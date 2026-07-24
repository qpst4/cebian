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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardEntryType
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardContentBlock
import com.slideindex.app.clipboard.displayTypeLabelKey
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.hasRichPinContent
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.clipboard.shouldOfferExpand
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.combinedText
import com.slideindex.app.stash.resolvedContentBlocks as stashResolvedContentBlocks
import com.slideindex.app.stash.shouldOfferExpand
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        repo?.entries?.collect { newEntries ->
            if (entries != newEntries) {
                entries = newEntries
            }
        }
    }
    LaunchedEffect(clipboardRepo) {
        clipboardRepo?.entries?.collect { newEntries ->
            if (clipboardEntries != newEntries) {
                clipboardEntries = newEntries
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val showPanelMessage: (Int) -> Unit = { messageResId ->
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(messageResId))
        }
    }
    val dismissInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = dismissInteraction, indication = null, onClick = onDismiss),
        contentAlignment = if (gravityEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(PANEL_WIDTH)
                .shadow(12.dp, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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
                beyondViewportPageCount = 0,
            ) { page ->
                when (FloatingPanelTab.entries[page]) {
                    FloatingPanelTab.Stash -> StashPanelBody(
                        entries = entries,
                        repo = repo,
                        context = context,
                        scope = scope,
                        onShowMessage = showPanelMessage,
                    )
                    FloatingPanelTab.Clipboard -> ClipboardPanelBody(
                        entries = clipboardEntries,
                        clipboardRepo = clipboardRepo,
                        isActive = selectedTab == FloatingPanelTab.Clipboard,
                        context = context,
                        scope = scope,
                        onShowMessage = showPanelMessage,
                        onSearchFocusChanged = { focused ->
                            FloatBallStashPanel.updateWindowInputActiveForClipboard(focused)
                        },
                    )
                }
            }
        }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun StashPanelBody(
    entries: List<StashEntry>,
    repo: com.slideindex.app.stash.StashRepository?,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onShowMessage: (Int) -> Unit,
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
                    onShowMessage = onShowMessage,
                    onPin = {
                        when (entry.type) {
                            StashEntryType.TEXT -> {
                                StashCoordinator.pinTextToScreen(context, entry.text.orEmpty())
                            }
                            StashEntryType.IMAGE -> {
                                val bitmap = repo?.loadImage(entry) ?: return@StashEntryCard
                                StashCoordinator.pinImageFromStash(context, entry, bitmap)
                            }
                            StashEntryType.RICH -> {
                                StashCoordinator.pinRichFromStash(context, entry)
                            }
                        }
                    },
                    onCopy = {
                        when (entry.type) {
                            StashEntryType.TEXT, StashEntryType.IMAGE, StashEntryType.RICH -> {
                                val ok = StashCoordinator.copyStashEntry(context, entry)
                                if (ok && entry.type != StashEntryType.IMAGE) {
                                    onShowMessage(R.string.float_ball_text_copied)
                                }
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
                            StashEntryType.RICH -> {
                                val combined = entry.combinedText()
                                if (combined.isNotBlank()) {
                                    FloatBallTextPick.shareText(context, combined)
                                } else {
                                    val fileName = entry.allImageFileNames().firstOrNull()
                                    val bitmap = fileName?.let { repo?.loadBitmapByFileName(it) }
                                        ?: return@StashEntryCard
                                    FloatBallTextPick.shareScreenshot(context, bitmap)
                                }
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
    onShowMessage: (Int) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val previewMaxSidePx = clipboardPreviewMaxSidePx()
    var searchQuery by remember { mutableStateOf("") }
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
                    items(
                        items = filteredEntries,
                        key = { it.id },
                        contentType = { "clipboard_entry" },
                    ) { entry ->
                        ClipboardEntryCard(
                            entry = entry,
                            previewMaxSidePx = previewMaxSidePx,
                            onShowMessage = onShowMessage,
                            onCopy = {
                                ClipboardWriter.write(context, entry)
                                onShowMessage(R.string.float_ball_text_copied)
                            },
                            onStash = {
                                StashCoordinator.addFromClipboard(context, entry) { success ->
                                    onShowMessage(
                                        if (success) R.string.stash_saved else R.string.stash_save_failed,
                                    )
                                }
                            },
                            onDelete = {
                                ClipboardThumbnailCache.evictEntry(entry)
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
    previewMaxSidePx: Int,
    onShowMessage: (Int) -> Unit,
    onCopy: () -> Unit,
    onStash: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasImageContent = entry.hasImageContent()
    var thumbnails by remember(entry.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedIndex by remember(entry.id) { mutableIntStateOf(0) }
    var imageLoadFailed by remember(entry.id) { mutableStateOf(false) }
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val contentBlocks = remember(entry.id, entry.contentBlocks, entry.text, entry.htmlText, entry.imageFileNames) {
        entry.resolvedContentBlocks()
    }
    val canExpand = remember(entry.id, contentBlocks) { entry.shouldOfferExpand() }
    LaunchedEffect(
        entry.id,
        entry.imageFileName,
        entry.imageFileNames,
        entry.uri,
        entry.mimeType,
        entry.htmlText,
        previewMaxSidePx,
        hasImageContent,
    ) {
        if (!hasImageContent) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            ClipboardThumbnailCache.loadEntryThumbnailsForPreview(context, entry, previewMaxSidePx)
        }
        thumbnails = loaded
        imageLoadFailed = loaded.isEmpty()
        selectedIndex = 0
    }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { thumbnails.size.coerceAtLeast(1) },
    )
    LaunchedEffect(pagerState.settledPage) {
        if (thumbnails.isNotEmpty()) {
            selectedIndex = pagerState.settledPage.coerceIn(0, thumbnails.lastIndex)
        }
    }
    LaunchedEffect(selectedIndex) {
        if (thumbnails.isNotEmpty() && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex.coerceIn(0, thumbnails.lastIndex))
        }
    }
    val selectedBitmap = thumbnails.getOrNull(selectedIndex)
    val hasImages = thumbnails.isNotEmpty()
    val bodyText = entry.text.trim()
    val showBodyText = bodyText.isNotEmpty() && bodyText != entry.uri
    val summaryText = when {
        showBodyText -> bodyText
        !hasImages && !imageLoadFailed -> entry.uri ?: entry.intentUri.orEmpty()
        else -> ""
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            PickResultFromHistoryCoordinator.openFromClipboard(
                                context,
                                entry,
                                selectedIndex,
                            )
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TextFields,
                            contentDescription = stringResource(R.string.stash_action_open_pick),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = clipboardEntryTypeLabel(entry.displayTypeLabelKey()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canExpand) {
                            Modifier
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { expanded = !expanded }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            if (!expanded && hasImages) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        val bitmap = thumbnails.getOrNull(page) ?: return@HorizontalPager
                        val imageBitmap = rememberCachedImageBitmap(bitmap) ?: return@HorizontalPager
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (thumbnails.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${thumbnails.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (thumbnails.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(thumbnails.size, key = { it }) { index ->
                            val selected = index == selectedIndex
                            val thumbBitmap = thumbnails[index]
                            val thumbImage = rememberCachedImageBitmap(thumbBitmap)
                            if (thumbImage != null) {
                                Image(
                                    bitmap = thumbImage,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable { selectedIndex = index },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            } else if (!expanded && imageLoadFailed) {
                Text(
                    text = stringResource(R.string.clipboard_image_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    contentBlocks.forEach { block ->
                        ClipboardContentBlockView(
                            block = block,
                            context = context,
                            previewMaxSidePx = previewMaxSidePx,
                            expanded = true,
                        )
                    }
                }
            } else if (summaryText.isNotBlank()) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (entry.hasRichPinContent() || hasImages || showBodyText) {
                    StashCardActionIcon(
                        icon = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.stash_action_pin),
                        onClick = {
                            when {
                                entry.hasRichPinContent() -> {
                                    StashCoordinator.pinRichFromClipboard(context, entry)
                                }
                                hasImages && selectedBitmap != null -> {
                                    StashCoordinator.pinImageToScreen(context, selectedBitmap)
                                }
                                showBodyText -> {
                                    StashCoordinator.pinTextToScreen(context, bodyText)
                                }
                            }
                        },
                    )
                }
                StashCardActionIcon(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = null,
                    onClick = onCopy,
                )
                StashCardActionIcon(
                    icon = Icons.Outlined.Inventory2,
                    contentDescription = stringResource(R.string.float_ball_action_stash),
                    onClick = onStash,
                )
                if (!expanded && hasImages && selectedBitmap != null) {
                    StashCardActionIcon(
                        icon = Icons.Default.Share,
                        contentDescription = null,
                        onClick = { FloatBallTextPick.shareScreenshot(context, selectedBitmap) },
                    )
                    StashCardActionIcon(
                        icon = Icons.Outlined.Save,
                        contentDescription = stringResource(R.string.clipboard_action_save_image),
                        onClick = {
                            val saved = FloatBallTextPick.saveScreenshot(context, selectedBitmap)
                            onShowMessage(
                                if (saved) {
                                    R.string.float_ball_screenshot_saved
                                } else {
                                    R.string.float_ball_action_failed
                                },
                            )
                        },
                    )
                } else if (!expanded && showBodyText) {
                    StashCardActionIcon(
                        icon = Icons.Default.Share,
                        contentDescription = null,
                        onClick = { FloatBallTextPick.shareText(context, bodyText) },
                    )
                }
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
private fun ClipboardContentBlockView(
    block: ClipboardContentBlock,
    context: Context,
    previewMaxSidePx: Int,
    expanded: Boolean = false,
) {
    when (block.kind) {
        ClipboardBlockKind.TEXT -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        ClipboardBlockKind.IMAGE -> {
            val decodeMaxSidePx = if (expanded) panelExpandedImageMaxSidePx() else previewMaxSidePx
            var bitmap by remember(block.fileName, decodeMaxSidePx) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(block.fileName, decodeMaxSidePx) {
                if (block.fileName.isBlank()) return@LaunchedEffect
                bitmap = withContext(Dispatchers.IO) {
                    ClipboardThumbnailCache.loadBlockThumbnail(context, block.fileName, decodeMaxSidePx)
                }
            }
            val imageBitmap = rememberCachedImageBitmap(bitmap)
            if (imageBitmap != null) {
                if (expanded) {
                    PanelScrollableExpandedImage(imageBitmap = imageBitmap)
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.clipboard_image_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun StashEntryCard(
    entry: StashEntry,
    onShowMessage: (Int) -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = StashAccess.repository
    val previewWidthPx = stashPreviewWidthPx()
    val previewHeightPx = stashPreviewHeightPx()
    val richPreviewMaxSidePx = clipboardPreviewMaxSidePx()
    var thumb by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val richBlocks = remember(entry.id, entry.contentBlocks, entry.type, entry.text, entry.imageFileName) {
        entry.stashResolvedContentBlocks()
    }
    val canExpand = remember(entry.id, entry.type, richBlocks) { entry.shouldOfferExpand() }
    val summaryText = remember(entry.id, entry.type, entry.text, richBlocks) {
        when (entry.type) {
            StashEntryType.TEXT -> entry.text.orEmpty()
            StashEntryType.RICH -> entry.combinedText()
            else -> ""
        }
    }
    val richImageFileNames = remember(entry.id, entry.contentBlocks, entry.imageFileName) {
        entry.allImageFileNames()
    }
    var richThumbnails by remember(entry.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var richSelectedIndex by remember(entry.id) { mutableIntStateOf(0) }
    var richImageLoadFailed by remember(entry.id) { mutableStateOf(false) }
    LaunchedEffect(entry.id, previewWidthPx, previewHeightPx) {
        if (entry.type != StashEntryType.IMAGE) return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            repo?.loadImageThumbnailForCard(entry, previewWidthPx, previewHeightPx)
        }
    }
    LaunchedEffect(entry.id, richImageFileNames, richPreviewMaxSidePx) {
        if (entry.type != StashEntryType.RICH || richImageFileNames.isEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            repo?.loadEntryThumbnailsForPreview(entry, richPreviewMaxSidePx).orEmpty()
        }
        richThumbnails = loaded
        richImageLoadFailed = loaded.isEmpty()
        richSelectedIndex = 0
    }
    val richPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { richThumbnails.size.coerceAtLeast(1) },
    )
    LaunchedEffect(richPagerState.settledPage) {
        if (richThumbnails.isNotEmpty()) {
            richSelectedIndex = richPagerState.settledPage.coerceIn(0, richThumbnails.lastIndex)
        }
    }
    LaunchedEffect(richSelectedIndex) {
        if (richThumbnails.isNotEmpty() && richPagerState.currentPage != richSelectedIndex) {
            richPagerState.scrollToPage(richSelectedIndex.coerceIn(0, richThumbnails.lastIndex))
        }
    }
    val richSelectedBitmap = richThumbnails.getOrNull(richSelectedIndex)
    val richHasImages = richThumbnails.isNotEmpty()
    val thumbImage = rememberCachedImageBitmap(thumb)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val imageIndex = when (entry.type) {
                                StashEntryType.RICH -> richSelectedIndex
                                else -> 0
                            }
                            PickResultFromHistoryCoordinator.openFromStash(context, entry, imageIndex)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TextFields,
                            contentDescription = stringResource(R.string.stash_action_open_pick),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canExpand) {
                            Modifier
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { expanded = !expanded }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (entry.type) {
                    StashEntryType.TEXT -> {
                        Text(
                            text = entry.text.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (canExpand && !expanded) 3 else Int.MAX_VALUE,
                            overflow = if (canExpand && !expanded) TextOverflow.Ellipsis else TextOverflow.Clip,
                        )
                    }
                    StashEntryType.IMAGE -> {
                        if (expanded) {
                            entry.stashResolvedContentBlocks().forEach { block ->
                                StashContentBlockView(
                                    entryId = entry.id,
                                    block = block,
                                    previewMaxSidePx = panelExpandedImageMaxSidePx(),
                                    expanded = true,
                                )
                            }
                        } else if (thumbImage != null) {
                            Image(
                                bitmap = thumbImage,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                    StashEntryType.RICH -> {
                        if (expanded) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                richBlocks.forEach { block ->
                                    StashContentBlockView(
                                        entryId = entry.id,
                                        block = block,
                                        previewMaxSidePx = panelExpandedImageMaxSidePx(),
                                        expanded = true,
                                    )
                                }
                            }
                        } else {
                            if (richHasImages) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                ) {
                                    HorizontalPager(
                                        state = richPagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondViewportPageCount = 0,
                                    ) { page ->
                                        val bitmap = richThumbnails.getOrNull(page) ?: return@HorizontalPager
                                        val imageBitmap = rememberCachedImageBitmap(bitmap) ?: return@HorizontalPager
                                        Image(
                                            bitmap = imageBitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    if (richThumbnails.size > 1) {
                                        Text(
                                            text = "${richPagerState.currentPage + 1}/${richThumbnails.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.55f),
                                                    shape = RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                if (richThumbnails.size > 1) {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        items(richThumbnails.size, key = { it }) { index ->
                                            val selected = index == richSelectedIndex
                                            val thumbBitmap = richThumbnails[index]
                                            val thumbImageBitmap = rememberCachedImageBitmap(thumbBitmap)
                                            if (thumbImageBitmap != null) {
                                                Image(
                                                    bitmap = thumbImageBitmap,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .border(
                                                            width = if (selected) 2.dp else 1.dp,
                                                            color = if (selected) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                                            },
                                                            shape = RoundedCornerShape(6.dp),
                                                        )
                                                        .clickable { richSelectedIndex = index },
                                                    contentScale = ContentScale.Crop,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (richImageLoadFailed) {
                                Text(
                                    text = stringResource(R.string.clipboard_image_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (summaryText.isNotBlank()) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
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
                if (entry.type == StashEntryType.RICH && !expanded && richHasImages && richSelectedBitmap != null) {
                    StashCardActionIcon(
                        icon = Icons.Default.Share,
                        contentDescription = null,
                        onClick = { FloatBallTextPick.shareScreenshot(context, richSelectedBitmap) },
                    )
                    StashCardActionIcon(
                        icon = Icons.Outlined.Save,
                        contentDescription = stringResource(R.string.clipboard_action_save_image),
                        onClick = {
                            val saved = FloatBallTextPick.saveScreenshot(context, richSelectedBitmap)
                            onShowMessage(
                                if (saved) {
                                    R.string.float_ball_screenshot_saved
                                } else {
                                    R.string.float_ball_action_failed
                                },
                            )
                        },
                    )
                } else if (entry.type == StashEntryType.RICH && !expanded && summaryText.isNotBlank()) {
                    StashCardActionIcon(
                        icon = Icons.Default.Share,
                        contentDescription = null,
                        onClick = { FloatBallTextPick.shareText(context, summaryText) },
                    )
                } else {
                    StashCardActionIcon(
                        icon = Icons.Default.Share,
                        contentDescription = null,
                        onClick = onShare,
                    )
                }
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
private fun StashContentBlockView(
    entryId: String,
    block: ClipboardContentBlock,
    previewMaxSidePx: Int,
    expanded: Boolean = false,
) {
    val repo = StashAccess.repository
    when (block.kind) {
        ClipboardBlockKind.TEXT -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
        ClipboardBlockKind.IMAGE -> {
            val decodeMaxSidePx = if (expanded) panelExpandedImageMaxSidePx() else previewMaxSidePx
            var bitmap by remember(entryId, block.fileName, decodeMaxSidePx) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(entryId, block.fileName, decodeMaxSidePx) {
                if (block.fileName.isBlank()) return@LaunchedEffect
                bitmap = withContext(Dispatchers.IO) {
                    repo?.loadThumbnailByFileName(entryId, block.fileName, decodeMaxSidePx)
                }
            }
            val imageBitmap = rememberCachedImageBitmap(bitmap)
            if (imageBitmap != null) {
                if (expanded) {
                    PanelScrollableExpandedImage(imageBitmap = imageBitmap)
                } else {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.clipboard_image_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun clipboardPreviewMaxSidePx(): Int {
    val density = LocalDensity.current
    return with(density) { (120.dp.roundToPx() * 2).coerceAtMost(384) }
}

@Composable
private fun stashPreviewWidthPx(): Int {
    val density = LocalDensity.current
    return with(density) { (PANEL_WIDTH - 24.dp).roundToPx().coerceAtMost(960) }
}

@Composable
private fun stashPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 150.dp.roundToPx() }
}

@Composable
private fun PanelScrollableExpandedImage(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = PANEL_EXPANDED_IMAGE_SCROLL_MAX)
            .verticalScroll(scrollState),
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@Composable
private fun panelExpandedImageMaxSidePx(): Int {
    val density = LocalDensity.current
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels
    return with(density) {
        maxOf(
            stashPreviewWidthPx(),
            (screenHeightPx * 0.75f).toInt(),
        ).coerceAtMost(2048)
    }
}

private val PANEL_EXPANDED_IMAGE_SCROLL_MAX = 420.dp

@Composable
private fun rememberCachedImageBitmap(bitmap: Bitmap?): ImageBitmap? =
    remember(bitmap) { bitmap?.asImageBitmap() }
