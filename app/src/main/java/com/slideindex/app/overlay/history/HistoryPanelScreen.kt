package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.slideindex.app.ui.miuix.miuixAppBarBlur
import com.slideindex.app.ui.miuix.miuixAppBarColor
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardThumbnailCache
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashEntryType
import com.slideindex.app.stash.allImageFileNames
import com.slideindex.app.stash.combinedText
import com.slideindex.app.ui.miuix.MiuixExpandableSearchFieldStrip
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class HistorySearchBootstrap(
    val tabOrdinal: Int,
    val query: String,
)

@Composable
internal fun HistoryPanelScreen(
    gravityEnd: Boolean,
    panelTargetVisible: Boolean,
    panelBlurActive: Boolean = false,
    onDismiss: () -> Unit,
    onToggleSide: () -> Unit,
    requestedTabOrdinal: MutableIntState,
    searchBootstrapEpoch: MutableIntState,
    onSearchFocusChanged: (Boolean) -> Unit,
    onRegisterBackInterceptor: ((() -> Boolean)?) -> Unit,
) {
    val savedStateOwner = LocalSavedStateRegistryOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val viewModel: HistoryPanelViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = remember(savedStateOwner) { HistoryPanelViewModelFactory(savedStateOwner) },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stashRepo = StashAccess.repository
    val clipboardRepo = ClipboardAccess.repository

    val stashEntries by viewModel.stashEntries.collectAsStateWithLifecycle()
    val filteredStashEntries by viewModel.filteredStashEntries.collectAsStateWithLifecycle()
    val stashSearchQuery by viewModel.stashSearchQuery.collectAsStateWithLifecycle()
    val clipboardEntryCount by viewModel.clipboardEntryCount.collectAsStateWithLifecycle()
    val filteredClipboardEntries by viewModel.filteredClipboardEntries.collectAsStateWithLifecycle()
    val clipboardSearchQuery by viewModel.clipboardSearchQuery.collectAsStateWithLifecycle()
    val clipboardListLoading by viewModel.clipboardListLoading.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val expandedEntryIds by viewModel.expandedEntryIds.collectAsStateWithLifecycle()
    val selectedImageIndices by viewModel.selectedImageIndices.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val activeSearchQuery = when (selectedTab) {
        HistoryPanelTab.Stash -> stashSearchQuery
        HistoryPanelTab.Clipboard -> clipboardSearchQuery
    }
    val onActiveSearchQueryChange: (String) -> Unit = when (selectedTab) {
        HistoryPanelTab.Stash -> viewModel::setStashSearchQuery
        HistoryPanelTab.Clipboard -> viewModel::setClipboardSearchQuery
    }
    val searchHintResId = when (selectedTab) {
        HistoryPanelTab.Stash -> R.string.stash_search_hint
        HistoryPanelTab.Clipboard -> R.string.clipboard_search_hint
    }

    val pagerState = rememberPagerState(
        initialPage = requestedTabOrdinal.intValue,
        pageCount = { HistoryPanelTab.entries.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setSelectedTab(HistoryPanelTab.entries[pagerState.currentPage])
    }
    LaunchedEffect(requestedTabOrdinal.intValue) {
        val target = requestedTabOrdinal.intValue
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == HistoryPanelTab.Clipboard.ordinal) {
            viewModel.onClipboardTabActivated(context)
        }
    }
    LaunchedEffect(searchExpanded) {
        onSearchFocusChanged(searchExpanded)
    }
    LaunchedEffect(searchBootstrapEpoch.intValue, panelTargetVisible) {
        // 退出动画期间旧组合仍在：绝不能 consume，否则会偷走深链 ?q=。
        if (!panelTargetVisible) return@LaunchedEffect
        if (searchBootstrapEpoch.intValue == 0) return@LaunchedEffect
        val pending = StashPanelLaunchState.consumePendingSearch() ?: return@LaunchedEffect
        val tab = HistoryPanelTab.entries.getOrNull(pending.tabOrdinal) ?: HistoryPanelTab.Stash
        // 先落到目标 Tab，再写 query / 展开，避免搜索框短暂绑到错误 Tab 空串并把 VM 写空。
        requestedTabOrdinal.intValue = pending.tabOrdinal
        if (pagerState.currentPage != pending.tabOrdinal) {
            pagerState.scrollToPage(pending.tabOrdinal)
        }
        viewModel.setSelectedTab(tab)
        when (tab) {
            HistoryPanelTab.Stash -> viewModel.setStashSearchQuery(pending.query)
            HistoryPanelTab.Clipboard -> viewModel.setClipboardSearchQuery(pending.query)
        }
        searchExpanded = true
    }

    DisposableEffect(searchExpanded, activeSearchQuery, selectedTab) {
        onRegisterBackInterceptor {
            consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = activeSearchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = onActiveSearchQueryChange,
            )
        }
        onDispose { onRegisterBackInterceptor(null) }
    }

    val resources = androidx.compose.ui.platform.LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val tabLabels = listOf(
        stringResource(R.string.stash_panel_tab),
        stringResource(R.string.clipboard_panel_tab),
    )
    val showPanelMessage: (Int) -> Unit = { messageResId ->
        val message = resources.getString(messageResId)
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val panelWidth = historyPanelWidth()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = if (gravityEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(panelWidth)
                .shadow(12.dp, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(HistoryPanelColors.listBackground(panelBlurActive))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            val barBackdrop = rememberMiuixBlurBackdrop()
            val chromeBlurActive = barBackdrop != null
            var chromeHeightPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val listTopPadding = with(density) { chromeHeightPx.toDp() }

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                ) { page ->
                    when (HistoryPanelTab.entries[page]) {
                        HistoryPanelTab.Stash -> HistoryStashTabBody(
                            allEntries = stashEntries,
                            filteredEntries = filteredStashEntries,
                            searchQuery = stashSearchQuery,
                            isActive = selectedTab == HistoryPanelTab.Stash,
                            panelBlurActive = panelBlurActive,
                            listTopPadding = listTopPadding,
                            listBackdrop = barBackdrop,
                            repo = stashRepo,
                            expandedEntryIds = expandedEntryIds,
                            selectedImageIndices = selectedImageIndices,
                            onToggleExpanded = viewModel::toggleExpanded,
                            onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                            onShowMessage = showPanelMessage,
                        )
                        HistoryPanelTab.Clipboard -> HistoryClipboardTabBody(
                            entryCount = clipboardEntryCount,
                            filteredEntries = filteredClipboardEntries,
                            searchQuery = clipboardSearchQuery,
                            isActive = selectedTab == HistoryPanelTab.Clipboard,
                            panelBlurActive = panelBlurActive,
                            listTopPadding = listTopPadding,
                            listBackdrop = barBackdrop,
                            loading = clipboardListLoading,
                            clipboardRepo = clipboardRepo,
                            expandedEntryIds = expandedEntryIds,
                            selectedImageIndices = selectedImageIndices,
                            onToggleExpanded = viewModel::toggleExpanded,
                            onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                            onEnsureLoaded = viewModel::ensureClipboardPagesLoaded,
                            onLoadMore = viewModel::loadMoreClipboard,
                            onShowMessage = showPanelMessage,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onGloballyPositioned { chromeHeightPx = it.size.height }
                        .miuixAppBarBlur(barBackdrop)
                        .background(
                            if (chromeBlurActive) {
                                barBackdrop.miuixAppBarColor()
                            } else {
                                HistoryPanelColors.panelChrome(panelBlurActive)
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                when (selectedTab) {
                                    HistoryPanelTab.Clipboard -> R.string.clipboard_history_float_panel_title
                                    HistoryPanelTab.Stash -> R.string.floating_panel_title
                                },
                            ),
                            style = textStyles.title4,
                            color = scheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HistoryPanelToolbarIcon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.stash_toggle_side),
                            onClick = onToggleSide,
                        )
                        MiuixExpandableSearchIconAction(
                            expanded = searchExpanded,
                            query = activeSearchQuery,
                            onExpandedChange = { searchExpanded = it },
                            onQueryChange = onActiveSearchQueryChange,
                        )
                    }
                    MiuixExpandableSearchFieldStrip(
                        expanded = searchExpanded,
                        query = activeSearchQuery,
                        onQueryChange = onActiveSearchQueryChange,
                        focusRequester = searchFocusRequester,
                        hintResId = searchHintResId,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    MiuixTabRowWithContour(
                        tabs = tabLabels,
                        selectedTabIndex = selectedTab.ordinal,
                        onTabSelected = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        contourHost = MiuixTabRowContourHost.SurfaceContainer,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    HorizontalDivider(color = scheme.dividerLine)
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
}

@Composable
private fun HistoryStashTabBody(
    allEntries: List<com.slideindex.app.stash.StashEntry>,
    filteredEntries: List<com.slideindex.app.stash.StashEntry>,
    searchQuery: String,
    isActive: Boolean,
    panelBlurActive: Boolean,
    listTopPadding: Dp,
    listBackdrop: LayerBackdrop?,
    repo: com.slideindex.app.stash.StashRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onShowMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val topEntryId = allEntries.firstOrNull()?.id
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || searchQuery.isNotBlank()) return@LaunchedEffect
        listState.animateScrollToItem(0)
    }
    when {
        filteredEntries.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = listTopPadding)
                    .background(HistoryPanelColors.listBackground(panelBlurActive)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (allEntries.isEmpty()) R.string.stash_empty else R.string.stash_search_empty,
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        else -> {
            val scheme = MiuixTheme.colorScheme
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(HistoryPanelColors.listBackground(panelBlurActive))
                    .historyPanelListBackdrop(listBackdrop),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp + listTopPadding,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    val expanded = entry.id in expandedEntryIds
                    val selectedIndex = selectedImageIndices[entry.id] ?: 0
                    HistoryStashEntryCard(
                        entry = entry,
                        expanded = expanded,
                        onExpandedChange = { onToggleExpanded(entry.id) },
                        selectedImageIndex = selectedIndex,
                        onSelectedImageIndexChange = { onSelectedImageIndexChange(entry.id, it) },
                        onShowMessage = onShowMessage,
                        onPin = {
                            when (entry.type) {
                                StashEntryType.TEXT -> StashCoordinator.pinTextToScreen(context, entry.text.orEmpty())
                                StashEntryType.IMAGE -> {
                                    val bitmap = repo?.loadImage(entry) ?: return@HistoryStashEntryCard
                                    StashCoordinator.pinImageFromStash(context, entry, bitmap)
                                }
                                StashEntryType.RICH -> StashCoordinator.pinRichFromStash(context, entry)
                            }
                        },
                        onCopy = {
                            val ok = StashCoordinator.copyStashEntry(context, entry)
                            if (ok && entry.type != StashEntryType.IMAGE) {
                                onShowMessage(R.string.float_ball_text_copied)
                            }
                        },
                        onShare = {
                            when (entry.type) {
                                StashEntryType.TEXT -> FloatBallTextPick.shareText(context, entry.text.orEmpty())
                                StashEntryType.IMAGE -> {
                                    val bitmap = repo?.loadImage(entry) ?: return@HistoryStashEntryCard
                                    FloatBallTextPick.shareScreenshot(context, bitmap)
                                }
                                StashEntryType.RICH -> {
                                    val combined = entry.combinedText()
                                    if (combined.isNotBlank()) {
                                        FloatBallTextPick.shareText(context, combined)
                                    } else {
                                        val fileName = entry.allImageFileNames().firstOrNull()
                                        val bitmap = fileName?.let { repo?.loadBitmapByFileName(it) }
                                            ?: return@HistoryStashEntryCard
                                        FloatBallTextPick.shareScreenshot(context, bitmap)
                                    }
                                }
                            }
                        },
                        onToggleStar = { scope.launch { repo?.toggleStar(entry.id) } },
                        onDelete = { scope.launch { repo?.delete(entry.id) } },
                    )
                }
                item(key = "stash_list_footer") {
                    Spacer(modifier = Modifier.height(HistoryListFooterPadding))
                }
            }
        }
    }
}

@Composable
private fun HistoryClipboardTabBody(
    entryCount: Int,
    filteredEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    searchQuery: String,
    isActive: Boolean,
    panelBlurActive: Boolean,
    listTopPadding: Dp,
    listBackdrop: LayerBackdrop?,
    loading: Boolean,
    clipboardRepo: com.slideindex.app.clipboard.ClipboardHistoryRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onEnsureLoaded: () -> Unit,
    onLoadMore: () -> Unit,
    onShowMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scheme = MiuixTheme.colorScheme
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyClipboardCardPreviewHeightPx()
    val isSearching = searchQuery.isNotBlank()
    val topEntryId = filteredEntries.firstOrNull()?.id
    val shouldLoadMore by remember {
        derivedStateOf {
            if (isSearching || loading) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            filteredEntries.isNotEmpty() && lastVisible >= filteredEntries.lastIndex - 2
        }
    }
    LaunchedEffect(isActive) {
        if (isActive && !isSearching) {
            onEnsureLoaded()
        }
    }
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || isSearching) return@LaunchedEffect
        listState.animateScrollToItem(0)
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }
    when {
        filteredEntries.isEmpty() && !loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = listTopPadding)
                    .background(HistoryPanelColors.listBackground(panelBlurActive)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (entryCount == 0) R.string.clipboard_empty else R.string.clipboard_search_empty,
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }
        filteredEntries.isEmpty() && loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = listTopPadding)
                    .background(HistoryPanelColors.listBackground(panelBlurActive)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary,
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(HistoryPanelColors.listBackground(panelBlurActive))
                    .historyPanelListBackdrop(listBackdrop),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp + listTopPadding,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = filteredEntries,
                    key = { it.id },
                    contentType = { "clipboard_entry" },
                ) { entry ->
                    val expanded = entry.id in expandedEntryIds
                    val selectedIndex = selectedImageIndices[entry.id] ?: 0
                    HistoryClipboardEntryCard(
                        entry = entry,
                        expanded = expanded,
                        onExpandedChange = { onToggleExpanded(entry.id) },
                        selectedImageIndex = selectedIndex,
                        onSelectedImageIndexChange = { onSelectedImageIndexChange(entry.id, it) },
                        previewWidthPx = previewWidthPx,
                        previewHeightPx = previewHeightPx,
                        onShowMessage = onShowMessage,
                        onCopy = {
                            ClipboardWriter.write(context, entry)
                            onShowMessage(R.string.float_ball_text_copied)
                        },
                        onStash = {
                            StashCoordinator.addFromClipboard(context, entry) { success ->
                                onShowMessage(if (success) R.string.stash_saved else R.string.stash_save_failed)
                            }
                        },
                        onDelete = {
                            ClipboardThumbnailCache.evictEntry(entry)
                            scope.launch { clipboardRepo?.delete(entry.id) }
                        },
                    )
                }
                if (!isSearching && entryCount > 0) {
                    item(key = "clipboard_record_count") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.clipboard_history_float_record_count,
                                entryCount,
                                entryCount,
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = scheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                if (!isSearching) {
                    item(key = "clipboard_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
