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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import kotlinx.coroutines.launch

data class HistorySearchBootstrap(
    val tabOrdinal: Int,
    val query: String,
)

@Composable
internal fun HistoryPanelScreen(
    gravityEnd: Boolean,
    onDismiss: () -> Unit,
    onToggleSide: () -> Unit,
    requestedTabOrdinal: MutableIntState,
    pendingSearchBootstrap: MutableState<HistorySearchBootstrap?>,
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
    val clipboardEntries by viewModel.clipboardEntries.collectAsStateWithLifecycle()
    val filteredClipboardEntries by viewModel.filteredClipboardEntries.collectAsStateWithLifecycle()
    val clipboardSearchQuery by viewModel.clipboardSearchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val expandedEntryIds by viewModel.expandedEntryIds.collectAsStateWithLifecycle()
    val selectedImageIndices by viewModel.selectedImageIndices.collectAsStateWithLifecycle()
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val bootstrap = pendingSearchBootstrap.value

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
    LaunchedEffect(bootstrap) {
        val pending = bootstrap ?: return@LaunchedEffect
        pendingSearchBootstrap.value = null
        when (HistoryPanelTab.entries.getOrNull(pending.tabOrdinal) ?: HistoryPanelTab.Stash) {
            HistoryPanelTab.Stash -> viewModel.setStashSearchQuery(pending.query)
            HistoryPanelTab.Clipboard -> viewModel.setClipboardSearchQuery(pending.query)
        }
        if (pagerState.currentPage != pending.tabOrdinal) {
            pagerState.scrollToPage(pending.tabOrdinal)
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
    val showPanelMessage: (Int) -> Unit = { messageResId ->
        val message = resources.getString(messageResId)
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

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
                .width(HISTORY_PANEL_WIDTH)
                .shadow(12.dp, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.floating_panel_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (searchExpanded) {
                                    if (activeSearchQuery.isNotBlank()) {
                                        onActiveSearchQueryChange("")
                                    } else {
                                        searchExpanded = false
                                    }
                                } else {
                                    searchExpanded = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search_hint),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = onToggleSide) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = stringResource(R.string.stash_toggle_side),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                MiuixExpandableSearchFieldStrip(
                    expanded = searchExpanded,
                    query = activeSearchQuery,
                    onQueryChange = onActiveSearchQueryChange,
                    focusRequester = searchFocusRequester,
                    hintResId = searchHintResId,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTab == HistoryPanelTab.Stash,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(HistoryPanelTab.Stash.ordinal) }
                        },
                        label = { Text(stringResource(R.string.stash_panel_tab)) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                    FilterChip(
                        selected = selectedTab == HistoryPanelTab.Clipboard,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(HistoryPanelTab.Clipboard.ordinal) }
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
                    when (HistoryPanelTab.entries[page]) {
                        HistoryPanelTab.Stash -> HistoryStashTabBody(
                            allEntries = stashEntries,
                            filteredEntries = filteredStashEntries,
                            searchQuery = stashSearchQuery,
                            isActive = selectedTab == HistoryPanelTab.Stash,
                            repo = stashRepo,
                            expandedEntryIds = expandedEntryIds,
                            selectedImageIndices = selectedImageIndices,
                            onToggleExpanded = viewModel::toggleExpanded,
                            onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                            onShowMessage = showPanelMessage,
                        )
                        HistoryPanelTab.Clipboard -> HistoryClipboardTabBody(
                            allEntries = clipboardEntries,
                            filteredEntries = filteredClipboardEntries,
                            searchQuery = clipboardSearchQuery,
                            isActive = selectedTab == HistoryPanelTab.Clipboard,
                            clipboardRepo = clipboardRepo,
                            expandedEntryIds = expandedEntryIds,
                            selectedImageIndices = selectedImageIndices,
                            onToggleExpanded = viewModel::toggleExpanded,
                            onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                            onShowMessage = showPanelMessage,
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
private fun HistoryStashTabBody(
    allEntries: List<com.slideindex.app.stash.StashEntry>,
    filteredEntries: List<com.slideindex.app.stash.StashEntry>,
    searchQuery: String,
    isActive: Boolean,
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (allEntries.isEmpty()) R.string.stash_empty else R.string.stash_search_empty,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
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
            }
        }
    }
}

@Composable
private fun HistoryClipboardTabBody(
    allEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    filteredEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    searchQuery: String,
    isActive: Boolean,
    clipboardRepo: com.slideindex.app.clipboard.ClipboardHistoryRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onShowMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyClipboardCardPreviewHeightPx()
    val topEntryId = allEntries.firstOrNull()?.id
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || searchQuery.isNotBlank()) return@LaunchedEffect
        listState.animateScrollToItem(0)
    }
    when {
        filteredEntries.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (allEntries.isEmpty()) R.string.clipboard_empty else R.string.clipboard_search_empty,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
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
            }
        }
    }
}
