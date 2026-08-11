package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
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
import com.slideindex.app.ui.SearchBar
import kotlinx.coroutines.launch

@Composable
internal fun HistoryPanelScreen(
    gravityEnd: Boolean,
    onDismiss: () -> Unit,
    onToggleSide: () -> Unit,
    requestedTabOrdinal: MutableIntState,
    onClipboardSearchFocusChanged: (Boolean) -> Unit,
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
    val clipboardEntries by viewModel.clipboardEntries.collectAsStateWithLifecycle()
    val filteredClipboardEntries by viewModel.filteredClipboardEntries.collectAsStateWithLifecycle()
    val clipboardSearchQuery by viewModel.clipboardSearchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val expandedEntryIds by viewModel.expandedEntryIds.collectAsStateWithLifecycle()
    val selectedImageIndices by viewModel.selectedImageIndices.collectAsStateWithLifecycle()

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
        } else {
            // 离开剪贴板 Tab 时清掉搜索焦点标记；进入时不再默认 true，避免「一下返回关不掉」。
            onClipboardSearchFocusChanged(false)
        }
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
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = stringResource(R.string.stash_toggle_side),
                            )
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
                            entries = stashEntries,
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
                            onSearchQueryChange = viewModel::setClipboardSearchQuery,
                            isActive = selectedTab == HistoryPanelTab.Clipboard,
                            clipboardRepo = clipboardRepo,
                            expandedEntryIds = expandedEntryIds,
                            selectedImageIndices = selectedImageIndices,
                            onToggleExpanded = viewModel::toggleExpanded,
                            onSelectedImageIndexChange = viewModel::setSelectedImageIndex,
                            onShowMessage = showPanelMessage,
                            onSearchFocusChanged = onClipboardSearchFocusChanged,
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
    entries: List<com.slideindex.app.stash.StashEntry>,
    repo: com.slideindex.app.stash.StashRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onShowMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

@Composable
private fun HistoryClipboardTabBody(
    allEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    filteredEntries: List<com.slideindex.app.clipboard.ClipboardEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isActive: Boolean,
    clipboardRepo: com.slideindex.app.clipboard.ClipboardHistoryRepository?,
    expandedEntryIds: Set<String>,
    selectedImageIndices: Map<String, Int>,
    onToggleExpanded: (String) -> Unit,
    onSelectedImageIndexChange: (String, Int) -> Unit,
    onShowMessage: (Int) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val previewWidthPx = historyPreviewWidthPx()
    val previewHeightPx = historyClipboardCardPreviewHeightPx()
    val topEntryId = allEntries.firstOrNull()?.id
    val focusSearchField = remember(view, keyboardController) {
        {
            view.post {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
    LaunchedEffect(isActive, topEntryId, searchQuery) {
        if (!isActive || topEntryId == null || searchQuery.isNotBlank()) return@LaunchedEffect
        listState.animateScrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .pointerInput(focusSearchField) {
                    detectTapGestures(onTap = { focusSearchField() })
                },
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hintResId = R.string.clipboard_search_hint,
                focusRequester = searchFocusRequester,
                onFocusChanged = onSearchFocusChanged,
            )
        }
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
}
