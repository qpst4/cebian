package com.slideindex.app.overlay.searchpanel

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.overlay.BlurredWallpaperCache
import com.slideindex.app.overlay.FloatBallImageSearchPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.OverlaySelectionToolbarActions
import com.slideindex.app.overlay.OverlaySelectionToolbarPopup
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.cutTextFieldValue
import com.slideindex.app.overlay.fieldModifier
import com.slideindex.app.overlay.overlayBottomPanelHeightCap
import com.slideindex.app.overlay.overlayBottomPanelMaxHeight
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.overlay.pasteIntoTextFieldValue
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.overlay.rememberOverlaySelectionToolbarState
import com.slideindex.app.overlay.suppressSystemTextContextMenu
import com.slideindex.app.overlay.viewportModifier
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.search.SearchHistoryAccess
import com.slideindex.app.search.SearchHistoryRecorder
import com.slideindex.app.search.calculator.CalculatorUtils
import com.slideindex.app.search.contacts.ContactSearchEntry
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.contacts.ContactSearchLauncher
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.files.FileSearchFilterOptions
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.search.files.FileSearchLauncher
import com.slideindex.app.search.files.FileType
import com.slideindex.app.search.files.FileTypeUtils
import com.slideindex.app.search.settings.SystemSettingsSearchEntry
import com.slideindex.app.search.settings.SystemSettingsSearchIndex
import com.slideindex.app.search.settings.SystemSettingsSearchLauncher
import com.slideindex.app.search.websuggestions.WebSuggestionsUtils
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelEnterAction
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.settings.SearchPanelPresentationMode
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.settings.shouldLaunchFullscreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

enum class SearchMode { TEXT, IMAGE }

private const val APP_CANDIDATE_LIMIT = 10
private const val SETTINGS_CANDIDATE_LIMIT = 6
private const val FILE_CANDIDATE_LIMIT = 8
private const val SEARCH_DEBOUNCE_MS = 200L
/** 呼出时抢输入框焦点的重试次数：窗口焦点可能早于输入框进入组合。 */
private const val SEARCH_PANEL_IME_FOCUS_ATTEMPTS = 3
/**
 * 面板呼出 / 退出的过渡时长，同时作为 [SearchPanelOverlayWindow] 退出后的收尾隐藏延迟。
 *
 * 超过 200ms 呼出会有明显「慢」的感觉，压到 0 又会像闪一下，这里取 140ms。
 */
internal const val SEARCH_PANEL_ANIM_MS = 140

/** 单行搜索框粘贴多行文本时，换行符会导致 TextField 内容不可见。 */
private fun normalizeSearchPanelQuery(input: String): String =
    input.replace('\r', ' ').replace('\n', ' ')

private fun textFieldValueForInputBehavior(
    behavior: SearchPanelInputBehavior,
    lastQuery: String,
): TextFieldValue = when (behavior) {
    SearchPanelInputBehavior.KEEP -> TextFieldValue(lastQuery)
    SearchPanelInputBehavior.CLEAR -> TextFieldValue("")
    SearchPanelInputBehavior.SELECT_ALL -> TextFieldValue(lastQuery, TextRange(0, lastQuery.length))
}

@Composable
fun SearchPanelScreen(
    visibilityState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settingsHolder = remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        val flow = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.settings
            ?: return@LaunchedEffect
        flow.collect { settingsHolder.value = it }
    }
    val settings = settingsHolder.value
    val longPressEnabled = settings.launchPolicyLongPressEligible()
    val appRepository = remember(context) {
        OverlayDependencyAccess.overlayDependencies(context)?.appRepository
    }

    val isFullscreen = settings.searchPanelPresentationMode == SearchPanelPresentationMode.FULLSCREEN
    val barAtBottom = settings.searchPanelBarPosition == SearchPanelBarPosition.BOTTOM
    val bottomUpListOrder = settings.searchPanelListOrder == SearchPanelListOrder.BOTTOM_UP
    val backgroundStyle = settings.searchPanelBackgroundStyle
    val blurRadiusDp = settings.searchPanelBlurRadiusDp
    val dimPercent = settings.searchPanelDimPercent

    var mode by remember { mutableStateOf(SearchMode.TEXT) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(SearchPanelSessionState.lastTextQuery))
    }
    val textQuery = textFieldValue.text
    var showSearchHistory by remember { mutableStateOf(false) }
    val emptySearchHistoryFlow = remember {
        kotlinx.coroutines.flow.flowOf(emptyList<com.slideindex.app.search.SearchHistoryEntry>())
    }
    val searchHistoryEntries by (
        SearchHistoryAccess.repository?.entries ?: emptySearchHistoryFlow
        ).collectAsState(initial = emptyList())
    val searchHistoryQueries = remember(searchHistoryEntries) {
        searchHistoryEntries.map { it.query }
    }
    var debouncedQuery by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val appsForSearch by (appRepository?.appsForSearch ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = appRepository?.getCachedAppsForSearch().orEmpty())
    val appsForSearchRevision by (appRepository?.appsForSearchRevision ?: kotlinx.coroutines.flow.flowOf(0L))
        .collectAsState(initial = 0L)
    var settingsCandidates by remember { mutableStateOf<List<SystemSettingsSearchEntry>>(emptyList()) }
    var contactCandidates by remember { mutableStateOf<List<ContactSearchEntry>>(emptyList()) }
    var fileCandidates by remember { mutableStateOf<List<DeviceFileEntry>>(emptyList()) }
    var appCandidates by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var webSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var contactsExpanded by remember { mutableStateOf(false) }
    var filesExpanded by remember { mutableStateOf(false) }
    var appsExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    var manuallySwitchedToNumberKeyboard by remember { mutableStateOf(false) }
    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var usesNativeWindowBlur by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<DeviceFileEntry?>(null) }
    val previewVisibilityState = remember { MutableTransitionState(false) }

    var hasContactPermission by remember(context, debouncedQuery) {
        mutableStateOf(ContactSearchIndex.hasPermission(context))
    }
    var hasFilePermission by remember(context, debouncedQuery) {
        mutableStateOf(FileSearchIndex.hasPermission(context))
    }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    var lockedSection by remember { mutableStateOf(SearchPanelResultSection.ALL) }
    var lockedEngineId by remember { mutableStateOf<String?>(null) }
    var showSectionMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun hideSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun clearSearchLocks() {
        lockedSection = SearchPanelResultSection.ALL
        lockedEngineId = null
    }

    fun allowsResultSection(section: SearchPanelResultSection): Boolean =
        lockedSection == SearchPanelResultSection.ALL || lockedSection == section

    /** 开关只控制「全部」下的自动候选；图标/别名锁定到某类时仍主动搜索。 */
    fun shouldFetchCandidateSection(section: SearchPanelResultSection): Boolean {
        if (lockedSection == section) return true
        if (lockedSection != SearchPanelResultSection.ALL) return false
        return when (section) {
            SearchPanelResultSection.APPS -> settings.searchPanelAppSearchEnabled
            SearchPanelResultSection.CONTACTS -> settings.searchPanelContactSearchEnabled
            SearchPanelResultSection.FILES -> settings.searchPanelFileSearchEnabled
            SearchPanelResultSection.SETTINGS -> settings.searchPanelSettingsSearchEnabled
            SearchPanelResultSection.ALL -> false
        }
    }

    val engines = settings.searchEngines
    val textEngines = remember(engines) { SearchEngineStore.textPickPanelEngines(engines) }
    val imageEngines = remember(engines) { SearchEngineStore.imageSharePanelEngines(engines) }

    fun resolveTextSearchEngine(): SearchEngineConfig? {
        lockedEngineId?.let { id -> textEngines.find { it.id == id } }?.let { return it }
        // 默认引擎允许是已隐藏的引擎，故按全量文本引擎解析；解析不到再回退首个可见引擎。
        return SearchEngineStore.findTextEngineById(engines, settings.searchPanelDefaultEngineId)
            ?: textEngines.firstOrNull()
    }

    fun applyTextFieldInput(updated: TextFieldValue) {
        val normalized = normalizeSearchPanelQuery(updated.text)
        if (textQuery.isEmpty() && normalized.isNotEmpty() && normalized.all { it.isWhitespace() }) {
            textFieldValue = TextFieldValue("")
            showSearchHistory = true
            return
        }
        val aliasMatch = SearchPanelAliasResolver.detectPrefixAlias(
            normalized,
            textEngines,
            settings.searchPanelSectionAliases,
        )
        when (aliasMatch) {
            is SearchPanelAliasMatch.Engine -> {
                lockedEngineId = aliasMatch.engine.id
                lockedSection = SearchPanelResultSection.ALL
                val stripped = aliasMatch.queryWithoutAlias
                if (stripped.isNotEmpty()) {
                    showSearchHistory = false
                }
                textFieldValue = TextFieldValue(
                    text = stripped,
                    selection = TextRange(stripped.length),
                )
            }
            is SearchPanelAliasMatch.Section -> {
                lockedSection = aliasMatch.section
                lockedEngineId = null
                val stripped = aliasMatch.queryWithoutAlias
                if (stripped.isNotEmpty()) {
                    showSearchHistory = false
                }
                textFieldValue = TextFieldValue(
                    text = stripped,
                    selection = TextRange(stripped.length),
                )
            }
            null -> {
                // 历史模式下继续输入用于过滤历史，不退出；退出靠空框删除键等既有路径
                textFieldValue = updated.copy(text = normalized)
            }
        }
    }

    LaunchedEffect(appRepository) {
        val repository = appRepository ?: return@LaunchedEffect
        repository.loadAppsForSearch()
        withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context)
        }
    }

    LaunchedEffect(appRepository, visibilityState.targetState) {
        val repository = appRepository ?: return@LaunchedEffect
        if (!visibilityState.targetState) return@LaunchedEffect
        repository.loadAppsForSearch(force = true)
        SearchPanelCandidateCache.clear()
    }

    LaunchedEffect(backgroundStyle, blurRadiusDp, visibilityState.targetState) {
        if (!visibilityState.targetState) {
            backgroundBitmap = null
            usesNativeWindowBlur = SearchPanelOverlayWindow.updateBackgroundBlur(
                context,
                SearchPanelBackgroundStyle.BLACK,
                0,
            )
            return@LaunchedEffect
        }
        usesNativeWindowBlur = SearchPanelOverlayWindow.updateBackgroundBlur(
            context,
            backgroundStyle,
            blurRadiusDp,
        )
        when (backgroundStyle) {
            SearchPanelBackgroundStyle.WALLPAPER_BLUR -> {
                backgroundBitmap = SystemWallpaperBlurHelper.loadBlurred(context, blurRadiusDp)
            }
            SearchPanelBackgroundStyle.BLUR -> {
                if (usesNativeWindowBlur || blurRadiusDp <= 0) {
                    backgroundBitmap = null
                    return@LaunchedEffect
                }
                val service = OverlayDependencyAccess.overlayHostContext() as? AccessibilityService
                if (service == null) {
                    backgroundBitmap = null
                    return@LaunchedEffect
                }
                val deferred = CompletableDeferred<Bitmap?>()
                val cached = BlurredWallpaperCache.captureFromDisplay(
                    service,
                    context,
                    blurRadiusDp,
                ) { bitmap ->
                    deferred.complete(bitmap)
                }
                backgroundBitmap = cached ?: deferred.await()
            }
            else -> {
                backgroundBitmap = null
            }
        }
    }

    LaunchedEffect(textQuery, showSearchHistory) {
        // 历史模式只做历史过滤，不触发普通搜索候选
        if (showSearchHistory || textQuery.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = textQuery
    }

    LaunchedEffect(debouncedQuery) {
        contactsExpanded = false
        filesExpanded = false
        appsExpanded = false
        settingsExpanded = false
        historyExpanded = false
    }

    LaunchedEffect(textQuery) {
        if (textQuery.any { it.isLetter() }) {
            manuallySwitchedToNumberKeyboard = false
        }
    }

    LaunchedEffect(
        debouncedQuery,
        settings.searchPanelWebSuggestionsEnabled,
        settings.searchPanelWebSuggestionsCount,
    ) {
        if (!settings.searchPanelWebSuggestionsEnabled || debouncedQuery.length < 2) {
            webSuggestions = emptyList()
            return@LaunchedEffect
        }
        val query = debouncedQuery
        val results = withContext(Dispatchers.IO) {
            WebSuggestionsUtils.getSuggestions(query)
                .take(settings.searchPanelWebSuggestionsCount)
        }
        if (debouncedQuery == query) {
            webSuggestions = results
        }
    }

    LaunchedEffect(
        debouncedQuery,
        lockedSection,
        appsForSearch,
        appsForSearchRevision,
        appRepository,
        settings.searchPanelContactSearchEnabled,
        settings.searchPanelFileSearchEnabled,
        settings.searchPanelAppSearchEnabled,
        settings.searchPanelSettingsSearchEnabled,
        settings.searchPanelFileTypesEnabled,
        settings.searchPanelFileShowFolders,
        settings.searchPanelFileShowSystemFiles,
        settings.searchPanelFileFolderWhitelist,
        settings.searchPanelFileFolderBlacklist,
        permissionRefreshKey,
    ) {
        if (debouncedQuery.isBlank()) {
            settingsCandidates = emptyList()
            contactCandidates = emptyList()
            fileCandidates = emptyList()
            appCandidates = emptyList()
            return@LaunchedEffect
        }
        val query = debouncedQuery
        val cacheKey = buildString {
            append(query)
            append('|')
            append(lockedSection.name)
            append('|')
            append(settings.searchPanelAppSearchEnabled)
            append('|')
            append(settings.searchPanelContactSearchEnabled)
            append('|')
            append(settings.searchPanelFileSearchEnabled)
            append('|')
            append(settings.searchPanelSettingsSearchEnabled)
            append('|')
            append(settings.searchPanelFileTypesEnabled.joinToString(","))
            append('|')
            append(settings.searchPanelFileShowFolders)
            append('|')
            append(settings.searchPanelFileShowSystemFiles)
            append('|')
            append(appsForSearchRevision)
        }
        val cached = SearchPanelCandidateCache.get(cacheKey)
        if (cached != null) {
            settingsCandidates = cached.settings
            contactCandidates = cached.contacts
            fileCandidates = cached.files
            appCandidates = cached.apps
            return@LaunchedEffect
        }
        hasContactPermission = withContext(Dispatchers.IO) { ContactSearchIndex.hasPermission(context) }
        hasFilePermission = withContext(Dispatchers.IO) { FileSearchIndex.hasPermission(context) }
        val fetchApps = shouldFetchCandidateSection(SearchPanelResultSection.APPS)
        val fetchSettings = shouldFetchCandidateSection(SearchPanelResultSection.SETTINGS)
        val fetchContacts = shouldFetchCandidateSection(SearchPanelResultSection.CONTACTS) && hasContactPermission
        val fetchFiles = shouldFetchCandidateSection(SearchPanelResultSection.FILES) && hasFilePermission
        coroutineScope {
            val settingsDeferred = async(Dispatchers.IO) {
                if (fetchSettings) {
                    SystemSettingsSearchIndex.search(context, query, SETTINGS_CANDIDATE_LIMIT)
                } else {
                    emptyList()
                }
            }
            val contactsDeferred = async(Dispatchers.IO) {
                if (fetchContacts) {
                    ContactSearchIndex.search(context, query, 5)
                } else {
                    emptyList()
                }
            }
            val filesDeferred = async(Dispatchers.IO) {
                if (fetchFiles) {
                    FileSearchIndex.search(
                        context = context,
                        query = query,
                        limit = FILE_CANDIDATE_LIMIT,
                        filterOptions = FileSearchFilterOptions(
                            enabledFileTypes = FileType.fromNames(settings.searchPanelFileTypesEnabled),
                            showFolders = settings.searchPanelFileShowFolders,
                            showSystemFiles = settings.searchPanelFileShowSystemFiles,
                            folderWhitelistPatterns = settings.searchPanelFileFolderWhitelist,
                            folderBlacklistPatterns = settings.searchPanelFileFolderBlacklist,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            val appsDeferred = async(Dispatchers.Default) {
                val repository = appRepository
                if (!fetchApps || repository == null) {
                    emptyList()
                } else {
                    repository.searchApps(appsForSearch, query, APP_CANDIDATE_LIMIT)
                }
            }
            if (!currentCoroutineContext().isActive || debouncedQuery != query) return@coroutineScope
            val settingsResults = settingsDeferred.await()
            val contactsResults = contactsDeferred.await()
            val filesResults = filesDeferred.await()
            val appsResults = appsDeferred.await()
            if (!currentCoroutineContext().isActive || debouncedQuery != query) return@coroutineScope
            settingsCandidates = settingsResults
            contactCandidates = contactsResults
            fileCandidates = filesResults
            appCandidates = appsResults
            SearchPanelCandidateCache.put(
                cacheKey,
                SearchPanelCandidateCacheEntry(
                    settings = settingsResults,
                    contacts = contactsResults,
                    files = filesResults,
                    apps = appsResults,
                ),
            )
        }
    }

    val linkUrls = remember(textQuery, showSearchHistory) {
        if (showSearchHistory) {
            emptyList()
        } else {
            PickResultUrl.extractOpenableUrls(textQuery).ifEmpty {
                PickResultUrl.normalizeOpenableUrl(textQuery.trim())?.let { listOf(it) } ?: emptyList()
            }
        }
    }
    val calculatorResult = remember(textQuery, settings.searchPanelCalculatorEnabled, showSearchHistory) {
        if (showSearchHistory ||
            !settings.searchPanelCalculatorEnabled ||
            !CalculatorUtils.isMathExpression(textQuery)
        ) {
            null
        } else {
            CalculatorUtils.evaluateExpression(textQuery)
        }
    }
    val showCalculator = calculatorResult != null
    val showContactPermissionPrompt =
        shouldFetchCandidateSection(SearchPanelResultSection.CONTACTS) &&
            !showSearchHistory &&
            !hasContactPermission &&
            textQuery.isNotBlank()
    val showFilePermissionPrompt =
        shouldFetchCandidateSection(SearchPanelResultSection.FILES) &&
            !showSearchHistory &&
            !hasFilePermission &&
            textQuery.isNotBlank()
    // Only manual pill toggles IME type. Binding to showCalculator restarts the overlay keyboard.
    val searchKeyboardType = if (manuallySwitchedToNumberKeyboard) {
        KeyboardType.Number
    } else {
        KeyboardType.Text
    }
    val keyboardSwitchText = when {
        showCalculator -> null
        manuallySwitchedToNumberKeyboard -> stringResource(R.string.keyboard_switch_back)
        textQuery.isNotEmpty() &&
            textQuery.none { it.isLetter() } &&
            linkUrls.isEmpty() -> stringResource(R.string.keyboard_switch_to_number)
        else -> null
    }
    val shouldShowPhoneCallAction = keyboardSwitchText != null && textQuery.isPhoneNumberQuery()
    val filteredSearchHistoryQueries = remember(searchHistoryQueries, textQuery, showSearchHistory) {
        if (!showSearchHistory) {
            emptyList()
        } else {
            val needle = textQuery.trim()
            if (needle.isEmpty()) {
                searchHistoryQueries
            } else {
                searchHistoryQueries.filter { it.contains(needle, ignoreCase = true) }
            }
        }
    }
    val showHistoryPanel = mode == SearchMode.TEXT &&
        showSearchHistory &&
        filteredSearchHistoryQueries.isNotEmpty()
    val hasCandidateSection = showCalculator ||
        showHistoryPanel ||
        linkUrls.isNotEmpty() ||
        webSuggestions.isNotEmpty() ||
        appCandidates.isNotEmpty() ||
        settingsCandidates.isNotEmpty() ||
        contactCandidates.isNotEmpty() ||
        fileCandidates.isNotEmpty() ||
        showContactPermissionPrompt ||
        showFilePermissionPrompt

    var wasPanelVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        SearchPanelSessionState.persistBeforeDismiss = {
            SearchPanelSessionState.lastTextQuery = textFieldValue.text
        }
        onDispose {
            SearchPanelSessionState.persistBeforeDismiss = null
        }
    }

    fun clearPreviewState() {
        previewVisibilityState.targetState = false
        previewFile = null
    }

    /** @return true if a visible preview was dismissed (consumes back). */
    fun dismissPreview(): Boolean {
        // Use targetState: once dismiss starts, further backs must reach the panel.
        if (previewFile == null || !previewVisibilityState.targetState) return false
        previewVisibilityState.targetState = false
        return true
    }

    LaunchedEffect(previewVisibilityState.isIdle, previewVisibilityState.currentState) {
        if (previewVisibilityState.isIdle && !previewVisibilityState.currentState) {
            previewFile = null
        }
    }

    val dismissPreviewForBack by rememberUpdatedState(newValue = { dismissPreview() })
    DisposableEffect(Unit) {
        SearchPanelSessionState.onBackPressed = { dismissPreviewForBack() }
        // 供浮层窗口在「窗口获得焦点」的第一时间抢焦点，绕开 Compose 重组延迟。
        SearchPanelSessionState.focusSearchTextField = {
            runCatching { focusRequester.requestFocus() }.isSuccess
        }
        onDispose {
            SearchPanelSessionState.onBackPressed = null
            SearchPanelSessionState.focusSearchTextField = null
        }
    }

    LaunchedEffect(visibilityState.targetState, settings.searchPanelInputBehavior) {
        val visible = visibilityState.targetState
        if (!visible) {
            clearPreviewState()
        } else if (!wasPanelVisible && mode == SearchMode.TEXT) {
            textFieldValue = textFieldValueForInputBehavior(
                behavior = settings.searchPanelInputBehavior,
                lastQuery = SearchPanelSessionState.lastTextQuery,
            )
            debouncedQuery = if (textFieldValue.text.isBlank()) "" else textFieldValue.text
        }
        wasPanelVisible = visible
    }

    // 文本搜索时让窗口保持「获得焦点即弹键盘」（窗口隐藏时不生效），图片模式关掉。
    // 提前常驻这个标志，窗口真正获得焦点的那一刻系统就会弹键盘，不用等我们后面再请求。
    LaunchedEffect(mode) {
        SearchPanelOverlayWindow.updateSoftInputAlwaysVisible(
            visible = mode == SearchMode.TEXT,
        )
    }

    // 焦点与输入法：呼出即抢输入框焦点，并按固定节奏补发几次输入法请求（覆盖窗口焦点稍晚到的情况）。
    LaunchedEffect(visibilityState.targetState, mode) {
        if (!visibilityState.targetState || mode != SearchMode.TEXT) return@LaunchedEffect
        var focused = false
        repeat(SEARCH_PANEL_IME_FOCUS_ATTEMPTS) { attempt ->
            if (focused) return@repeat
            focused = runCatching { focusRequester.requestFocus() }.isSuccess
            if (!focused && attempt < SEARCH_PANEL_IME_FOCUS_ATTEMPTS - 1) {
                withFrameNanos { }
            }
        }
        SearchPanelOverlayWindow.requestImeShow()
    }

    fun persistTextQuery() {
        SearchPanelSessionState.lastTextQuery = textFieldValue.text
    }

    fun dismissPanel() {
        persistTextQuery()
        clearPreviewState()
        onDismiss()
    }

    fun launchSearchEngine(
        engine: SearchEngineConfig,
        longPressTriggered: Boolean,
        queryOverride: String? = null,
    ) {
        val query = (queryOverride ?: textQuery).trim()
        if (mode == SearchMode.TEXT && query.isNotBlank()) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            )
            SearchPanelSessionState.lastTextQuery = query
            if (engine.engineType == SearchEngineType.SHARE_TO_APP) {
                SearchEngineLauncher.launchTextShare(context, engine, query)
            } else {
                SearchHistoryRecorder.record(context, query)
                showSearchHistory = false
                SearchEngineLauncher.launch(context, engine, query, settings, longPressTriggered)
            }
            dismissPanel()
        } else if (mode == SearchMode.IMAGE && imageBitmap != null) {
            if (engine.id == "slideindex_aggregate_image_search") {
                FloatBallImageSearchPanel.show(context, imageBitmap!!)
            } else {
                SearchEngineLauncher.launchImageShare(context, engine, imageBitmap!!)
            }
            dismissPanel()
        }
    }

    fun openUrl(url: String, longPressTriggered: Boolean) {
        persistTextQuery()
        SearchHistoryRecorder.record(context, url)
        showSearchHistory = false
        FloatBallTextPick.openUrl(context, url, settings, longPressTriggered)
        dismissPanel()
    }

    fun launchAppCandidate(app: AppInfo, longPressTriggered: Boolean) {
        val repository = appRepository ?: return
        val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
        coroutineScope.launch {
            val wasFrozen = FreezerOperations.isFrozen(context, app.packageName)
            if (FreezerOperations.launchAndUnfreeze(context, repository, settings, app, fullscreen)) {
                if (wasFrozen) {
                    repository.loadAppsForSearch(force = true)
                    SearchPanelCandidateCache.clear()
                }
                dismissPanel()
            }
        }
    }

    fun handleAppQuickAction(app: AppInfo, action: SearchPanelAppQuickAction) {
        when (action) {
            SearchPanelAppQuickAction.FREE_WINDOW -> {
                val repository = appRepository ?: return
                coroutineScope.launch {
                    val wasFrozen = FreezerOperations.isFrozen(context, app.packageName)
                    if (FreezerOperations.launchAndUnfreeze(context, repository, settings, app, fullscreen = false)) {
                        if (wasFrozen) {
                            repository.loadAppsForSearch(force = true)
                            SearchPanelCandidateCache.clear()
                        }
                        dismissPanel()
                    }
                }
            }
            SearchPanelAppQuickAction.SHARE -> {
                runCatching { SearchPanelAppShare.shareApk(context, app.packageName) }
                dismissPanel()
            }
            SearchPanelAppQuickAction.FREEZE -> {
                coroutineScope.launch {
                    val frozen = FreezerOperations.isFrozen(context, app.packageName)
                    val ok = FreezerOperations.setFrozen(context, app.packageName, frozen = !frozen)
                    if (ok) {
                        appRepository?.loadAppsForSearch(force = true)
                        SearchPanelCandidateCache.clear()
                    }
                }
                dismissPanel()
            }
            SearchPanelAppQuickAction.DETAILS -> {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${app.packageName}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
                dismissPanel()
            }
        }
    }

    fun launchSettingsCandidate(entry: SystemSettingsSearchEntry, longPressTriggered: Boolean) {
        if (SystemSettingsSearchLauncher.launch(context, entry, settings, longPressTriggered)) {
            dismissPanel()
        }
    }

    fun launchContactCandidate(contact: ContactSearchEntry, @Suppress("UNUSED_PARAMETER") longPressTriggered: Boolean) {
        hideSearchKeyboard()
        if (ContactSearchLauncher.openContact(context, contact)) {
            dismissPanel()
        }
    }

    fun callContact(contact: ContactSearchEntry) {
        hideSearchKeyboard()
        if (ContactSearchLauncher.dial(context, contact.phoneNumber)) {
            dismissPanel()
        }
    }

    fun smsContact(contact: ContactSearchEntry) {
        hideSearchKeyboard()
        if (ContactSearchLauncher.sms(context, contact.phoneNumber)) {
            dismissPanel()
        }
    }

    fun launchFileCandidate(file: DeviceFileEntry, @Suppress("UNUSED_PARAMETER") longPressTriggered: Boolean) {
        hideSearchKeyboard()
        if (
            settings.searchPanelFilePreviewsEnabled &&
            (FileTypeUtils.isImage(file) || FileTypeUtils.isPdf(file))
        ) {
            previewFile = file
            previewVisibilityState.targetState = true
            return
        }
        if (FileSearchLauncher.open(context, file)) {
            dismissPanel()
        }
    }

    fun performSearchEnterKey() {
        if (textQuery.isBlank()) return
        if (
            settings.searchPanelEnterAction == SearchPanelEnterAction.FIRST_CANDIDATE &&
            !showSearchHistory
        ) {
            val calc = calculatorResult
            if (showCalculator && calc != null) {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipData.newPlainText("calculator", calc).toClipEntry())
                }
                dismissPanel()
                return
            }
            if (appCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.APPS)) {
                launchAppCandidate(appCandidates.first(), longPressTriggered = false)
                return
            }
            if (linkUrls.isNotEmpty()) {
                openUrl(linkUrls.first(), longPressTriggered = false)
                return
            }
            if (fileCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.FILES)) {
                launchFileCandidate(fileCandidates.first(), longPressTriggered = false)
                return
            }
            if (contactCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.CONTACTS)) {
                launchContactCandidate(contactCandidates.first(), longPressTriggered = false)
                return
            }
            if (settingsCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.SETTINGS)) {
                launchSettingsCandidate(settingsCandidates.first(), longPressTriggered = false)
                return
            }
        }
        val engineToUse = resolveTextSearchEngine()
        if (engineToUse != null) {
            launchSearchEngine(engineToUse, longPressTriggered = false)
        }
    }

    val dismissInteraction = remember { MutableInteractionSource() }
    val isLandscape = overlayIsLandscape()
    val maxPanelHeight = overlayBottomPanelMaxHeight()
    val landscapeImagePreviewMaxHeight = 160.dp
    val dimAlpha = (dimPercent.coerceIn(
        AppSettings.SEARCH_PANEL_DIM_MIN_PERCENT,
        AppSettings.SEARCH_PANEL_DIM_MAX_PERCENT,
    ) / 100f)
    // Blur follows background mode; dim only controls the black veil.
    val showBitmapBackground = backgroundBitmap != null &&
        (backgroundStyle == SearchPanelBackgroundStyle.BLUR
            || backgroundStyle == SearchPanelBackgroundStyle.WALLPAPER_BLUR)
    val hasBlurBackground = showBitmapBackground || usesNativeWindowBlur
    val showDimMask = dimAlpha > 0f
    val panelAnimSpec = tween<Float>(SEARCH_PANEL_ANIM_MS, easing = FastOutSlowInEasing)
    val panelSlideSpec = tween<IntOffset>(SEARCH_PANEL_ANIM_MS, easing = FastOutSlowInEasing)
    val enterTransition = if (isFullscreen) {
        fadeIn(panelAnimSpec) + slideInVertically(panelSlideSpec) { it / 8 }
    } else {
        fadeIn(panelAnimSpec) + slideInVertically(panelSlideSpec) { it }
    }
    val exitTransition = if (isFullscreen) {
        fadeOut(panelAnimSpec) + slideOutVertically(panelSlideSpec) { it / 8 }
    } else {
        fadeOut(panelAnimSpec) + slideOutVertically(panelSlideSpec) { it }
    }
    val rootAlignment = when {
        isFullscreen && barAtBottom -> Alignment.BottomCenter
        isFullscreen -> Alignment.TopCenter
        else -> Alignment.BottomCenter
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = rootAlignment,
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(panelAnimSpec),
            exit = fadeOut(panelAnimSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showDimMask) {
                            Modifier.background(Color.Black.copy(alpha = dimAlpha))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClick = ::dismissPanel,
                    ),
            ) {
                if (showBitmapBackground) {
                    Image(
                        bitmap = backgroundBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = enterTransition,
            exit = exitTransition,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // imePadding first so BoxWithConstraints.maxHeight excludes keyboard — engines stay above IME.
            val panelModifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            } else {
                Modifier
                    .overlayBottomPanelWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .overlayBottomPanelHeightCap()
            }
            BoxWithConstraints(modifier = panelModifier) {
                val panelMaxHeight = maxHeight
                val hasQueryCandidates = mode == SearchMode.TEXT && textQuery.isNotBlank()
                val hasCandidatePanel = hasQueryCandidates || showHistoryPanel
                val forceTallPanel = isFullscreen || mode == SearchMode.IMAGE

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) {
                                Modifier.fillMaxSize()
                            } else if (forceTallPanel) {
                                Modifier.height(panelMaxHeight)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isFullscreen) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier.height(panelMaxHeight)
                                },
                            )
                            .padding(
                                horizontal = SearchPanelCardHorizontalPadding,
                                vertical = 12.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(SearchPanelCardVerticalSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                            val searchFieldBlock: @Composable () -> Unit = {
                                SearchPanelFrostedCard {
                                    Crossfade(targetState = mode, label = "SearchModeCrossfade") { currentMode ->
                                    when (currentMode) {
                                        SearchMode.TEXT -> {
                                            val toolbarState = rememberOverlaySelectionToolbarState()
                                            val searchToolbarActions = OverlaySelectionToolbarActions(
                                                editable = true,
                                                onCopy = { copied ->
                                                    FloatBallTextPick.copyText(context, copied)
                                                },
                                                onCut = {
                                                    textFieldValue = cutTextFieldValue(textFieldValue) { copied ->
                                                        FloatBallTextPick.copyText(context, copied)
                                                    }
                                                },
                                                onPaste = {
                                                    textFieldValue = pasteIntoTextFieldValue(context, textFieldValue)
                                                },
                                                onSelectAll = {
                                                    textFieldValue = textFieldValue.copy(
                                                        selection = TextRange(0, textFieldValue.text.length),
                                                    )
                                                },
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(Modifier.viewportModifier(toolbarState)),
                                            ) {
                                                TextField(
                                                    value = textFieldValue,
                                                    onValueChange = { updated ->
                                                        applyTextFieldInput(updated)
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp)
                                                        .focusRequester(focusRequester)
                                                        .suppressSystemTextContextMenu()
                                                        .onPreviewKeyEvent { event ->
                                                            if (
                                                                event.type != KeyEventType.KeyDown ||
                                                                (event.key != Key.Backspace && event.key != Key.Delete)
                                                            ) {
                                                                return@onPreviewKeyEvent false
                                                            }
                                                            if (textQuery.isNotEmpty()) {
                                                                return@onPreviewKeyEvent false
                                                            }
                                                            if (showSearchHistory) {
                                                                showSearchHistory = false
                                                                return@onPreviewKeyEvent true
                                                            }
                                                            if (
                                                                lockedSection != SearchPanelResultSection.ALL ||
                                                                lockedEngineId != null
                                                            ) {
                                                                clearSearchLocks()
                                                                return@onPreviewKeyEvent true
                                                            }
                                                            false
                                                        }
                                                        .then(Modifier.fieldModifier(toolbarState)),
                                                    leadingIcon = {
                                                        Box {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(48.dp)
                                                                    .focusProperties { canFocus = false }
                                                                    .clickable(
                                                                        interactionSource = remember {
                                                                            MutableInteractionSource()
                                                                        },
                                                                        indication = ripple(
                                                                            bounded = false,
                                                                            radius = 24.dp,
                                                                        ),
                                                                        onClick = { showSectionMenu = true },
                                                                    ),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Icon(
                                                                    imageVector = lockedSection.icon(),
                                                                    contentDescription = stringResource(
                                                                        R.string.search_panel_section_menu,
                                                                    ),
                                                                )
                                                            }
                                                            DropdownMenu(
                                                                expanded = showSectionMenu,
                                                                onDismissRequest = { showSectionMenu = false },
                                                                properties = PopupProperties(focusable = false),
                                                            ) {
                                                                SearchPanelResultSection.entries.forEach { section ->
                                                                    DropdownMenuItem(
                                                                        text = {
                                                                            Text(stringResource(section.labelResId()))
                                                                        },
                                                                        onClick = {
                                                                            lockedSection = section
                                                                            if (section != SearchPanelResultSection.ALL) {
                                                                                lockedEngineId = null
                                                                            }
                                                                            showSectionMenu = false
                                                                        },
                                                                        leadingIcon = {
                                                                            Icon(
                                                                                imageVector = section.icon(),
                                                                                contentDescription = null,
                                                                            )
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    },
                                                    trailingIcon = {
                                                        Row {
                                                            if (textQuery.isNotEmpty()) {
                                                                IconButton(onClick = {
                                                                    textFieldValue = TextFieldValue("")
                                                                    clearSearchLocks()
                                                                }) {
                                                                    Icon(
                                                                        Icons.Default.Close,
                                                                        contentDescription = null,
                                                                    )
                                                                }
                                                            }
                                                            IconButton(onClick = {
                                                                SearchPanelOverlayWindow.hide()
                                                                SearchPanelImagePickerActivity.launch(context) { uri ->
                                                                    SearchPanelOverlayWindow.restore()
                                                                    if (uri != null) {
                                                                        imageUri = uri
                                                                        mode = SearchMode.IMAGE
                                                                        coroutineScope.launch {
                                                                            val bitmap = loadBitmapFromUri(context, uri)
                                                                            imageBitmap = bitmap
                                                                        }
                                                                    }
                                                                }
                                                            }) {
                                                                Icon(
                                                                    Icons.Default.Image,
                                                                    contentDescription = null,
                                                                )
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(28.dp),
                                                    singleLine = true,
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        disabledContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        disabledIndicatorColor = Color.Transparent,
                                                    ),
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = searchKeyboardType,
                                                        imeAction = ImeAction.Search,
                                                    ),
                                                    keyboardActions = KeyboardActions(onSearch = {
                                                        performSearchEnterKey()
                                                    }),
                                                )
                                                OverlaySelectionToolbarPopup(
                                                    visible = !textFieldValue.selection.collapsed,
                                                    selection = textFieldValue.selection,
                                                    text = textFieldValue.text,
                                                    textLayoutResult = toolbarState.textLayoutResult,
                                                    fieldCoordinates = toolbarState.fieldCoordinates,
                                                    viewportCoordinates = toolbarState.viewportCoordinates,
                                                    actions = searchToolbarActions,
                                                )
                                            }
                                        }
                                        SearchMode.IMAGE -> {
                                            val imagePreviewMaxHeight =
                                                if (isLandscape) landscapeImagePreviewMaxHeight else 240.dp
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                                    .heightIn(min = 120.dp, max = imagePreviewMaxHeight)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (imageBitmap != null) {
                                                    Image(
                                                        bitmap = imageBitmap!!.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        mode = SearchMode.TEXT
                                                        imageUri = null
                                                        imageBitmap = null
                                                        focusRequester.requestFocus()
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(8.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                            RoundedCornerShape(50),
                                                        ),
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }


                            // 悬浮 chrome 的高度：胶囊 / 引擎 dock / 搜索框叠在列表之上、不占行高，
                            // 列表底部按实测高度留内边距，滚到底时最后一张卡片仍完整可见。
                            val actionPillsDensity = LocalDensity.current
                            var actionPillsHeight by remember { mutableStateOf(0.dp) }
                            var bottomChromeHeight by remember { mutableStateOf(0.dp) }

                            val candidatesContent: @Composable () -> Unit = {
                                val expandEdge = if (bottomUpListOrder) {
                                    Alignment.Bottom
                                } else {
                                    Alignment.Top
                                }
                                AnimatedVisibility(
                                    visible = hasCandidatePanel,
                                    enter = expandVertically(expandFrom = expandEdge) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = expandEdge) + fadeOut(),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    val candidateListState = rememberLazyListState()
                                    val candidateSectionKeys = remember(
                                        linkUrls.isNotEmpty(),
                                        showCalculator,
                                        showHistoryPanel,
                                        appCandidates.isNotEmpty(),
                                        showFilePermissionPrompt,
                                        fileCandidates.isNotEmpty(),
                                        showContactPermissionPrompt,
                                        contactCandidates.isNotEmpty(),
                                        settingsCandidates.isNotEmpty(),
                                        webSuggestions.isNotEmpty(),
                                        lockedSection,
                                        bottomUpListOrder,
                                    ) {
                                        val list = mutableListOf<String>()
                                        if (linkUrls.isNotEmpty() && lockedSection == SearchPanelResultSection.ALL) list.add("links")
                                        if (showCalculator && lockedSection == SearchPanelResultSection.ALL) list.add("calculator")
                                        if (showHistoryPanel && lockedSection == SearchPanelResultSection.ALL) list.add("history")
                                        if (appCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.APPS)) list.add("apps")
                                        if (showFilePermissionPrompt && allowsResultSection(SearchPanelResultSection.FILES)) list.add("file_permission")
                                        if (fileCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.FILES)) list.add("files")
                                        if (showContactPermissionPrompt && allowsResultSection(SearchPanelResultSection.CONTACTS)) list.add("contact_permission")
                                        if (contactCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.CONTACTS)) list.add("contacts")
                                        if (settingsCandidates.isNotEmpty() && allowsResultSection(SearchPanelResultSection.SETTINGS)) list.add("settings")
                                        if (webSuggestions.isNotEmpty() && lockedSection == SearchPanelResultSection.ALL) list.add("web_suggestions")
                                        if (bottomUpListOrder) list.asReversed() else list
                                    }
                                    LazyColumn(
                                        state = candidateListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            bottom = SearchPanelCardVerticalSpacing +
                                                if (barAtBottom) bottomChromeHeight else actionPillsHeight,
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(SearchPanelCardVerticalSpacing),
                                    ) {
                                        items(
                                            count = candidateSectionKeys.size,
                                            key = { candidateSectionKeys[it] },
                                        ) { index ->
                                            when (candidateSectionKeys[index]) {
                                                "links" -> {
                                                    SearchPanelLinkResultCards(
                                                        urls = linkUrls,
                                                        onOpenUrl = ::openUrl,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "calculator" -> {
                                                    val calcResult = calculatorResult
                                                    if (calcResult != null) {
                                                        SearchPanelCalculatorCard(
                                                            expression = textQuery.trim(),
                                                            result = calcResult,
                                                        )
                                                    }
                                                }
                                                "history" -> {
                                                    SearchPanelSearchHistoryCard(
                                                        queries = filteredSearchHistoryQueries,
                                                        expanded = historyExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            historyExpanded = expanded
                                                        },
                                                        onQueryClick = { query ->
                                                            textFieldValue = TextFieldValue(
                                                                text = query,
                                                                selection = TextRange(query.length),
                                                            )
                                                            debouncedQuery = query
                                                            showSearchHistory = false
                                                        },
                                                    )
                                                }
                                                "apps" -> {
                                                    SearchPanelAppResultCards(
                                                        apps = appCandidates,
                                                        style = settings.searchPanelAppDisplayStyle,
                                                        settings = settings,
                                                        onLaunchApp = ::launchAppCandidate,
                                                        onAppQuickAction = ::handleAppQuickAction,
                                                        expanded = appsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            appsExpanded = expanded
                                                        },
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "file_permission" -> {
                                                    SearchPanelPermissionResultCard(
                                                        label = stringResource(
                                                            R.string.search_panel_file_permission_prompt,
                                                        ),
                                                        leadingIcon = Icons.Default.Folder,
                                                        onRequestPermission = {
                                                            SearchPanelOverlayWindow.hide()
                                                            FilePermissionTrampolineActivity.launch(context) { granted ->
                                                                hasFilePermission = granted
                                                                permissionRefreshKey++
                                                                SearchPanelOverlayWindow.restore()
                                                            }
                                                        },
                                                    )
                                                }
                                                "files" -> {
                                                    SearchPanelFileResultCards(
                                                        files = fileCandidates,
                                                        expanded = filesExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            filesExpanded = expanded
                                                        },
                                                        onOpenFile = ::launchFileCandidate,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "contact_permission" -> {
                                                    SearchPanelPermissionResultCard(
                                                        label = stringResource(
                                                            R.string.search_panel_contact_permission_prompt,
                                                        ),
                                                        leadingIcon = Icons.Default.Person,
                                                        onRequestPermission = {
                                                            SearchPanelOverlayWindow.hide()
                                                            ContactPermissionTrampolineActivity.launch(context) { granted ->
                                                                hasContactPermission = granted
                                                                permissionRefreshKey++
                                                                SearchPanelOverlayWindow.restore()
                                                            }
                                                        },
                                                    )
                                                }
                                                "contacts" -> {
                                                    SearchPanelContactResultCards(
                                                        contacts = contactCandidates,
                                                        expanded = contactsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            contactsExpanded = expanded
                                                        },
                                                        onLaunchContact = ::launchContactCandidate,
                                                        onCallContact = ::callContact,
                                                        onSmsContact = ::smsContact,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "settings" -> {
                                                    SearchPanelSettingsResultCards(
                                                        entries = settingsCandidates,
                                                        expanded = settingsExpanded,
                                                        onExpandedChange = { expanded ->
                                                            if (expanded) hideSearchKeyboard()
                                                            settingsExpanded = expanded
                                                        },
                                                        onLaunchEntry = ::launchSettingsCandidate,
                                                        longPressEnabled = longPressEnabled,
                                                    )
                                                }
                                                "web_suggestions" -> {
                                                    SearchPanelWebSuggestionsCard(
                                                        suggestions = webSuggestions,
                                                        onSuggestionClick = { suggestion ->
                                                            val engineToUse = resolveTextSearchEngine()
                                                            if (engineToUse != null) {
                                                                launchSearchEngine(
                                                                    engine = engineToUse,
                                                                    longPressTriggered = false,
                                                                    queryOverride = suggestion,
                                                                )
                                                            } else {
                                                                textFieldValue = textFieldValue.copy(
                                                                    text = suggestion,
                                                                    selection = TextRange(suggestion.length),
                                                                )
                                                                debouncedQuery = suggestion
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val actionPillsBlock: @Composable () -> Unit = {
                                if (mode == SearchMode.TEXT && textQuery.isNotBlank()) {
                                    SearchPanelActionPillsRow(
                                        visible = keyboardSwitchText != null || shouldShowPhoneCallAction,
                                        keyboardSwitchText = keyboardSwitchText,
                                        showPhoneCallAction = shouldShowPhoneCallAction,
                                        phoneQuery = textQuery,
                                        onKeyboardSwitchToggle = {
                                            manuallySwitchedToNumberKeyboard = !manuallySwitchedToNumberKeyboard
                                        },
                                    )
                                }
                            }

                            val engineGridBlock: @Composable () -> Unit = {
                                val aggregateName = stringResource(R.string.search_panel_aggregated_image_search)
                                val aggregateIcon = stringResource(R.string.search_panel_aggregated_image_icon)
                                val aggregateSearchEngine = remember(aggregateName, aggregateIcon) {
                                    SearchEngineConfig(
                                        id = "slideindex_aggregate_image_search",
                                        name = aggregateName,
                                        engineType = SearchEngineType.SHARE_IMAGE_TO_APP,
                                        iconType = SearchIconType.TEXT,
                                        textIcon = aggregateIcon,
                                    )
                                }
                                val activeEngines = if (mode == SearchMode.TEXT) {
                                    textEngines
                                } else {
                                    listOf(aggregateSearchEngine) + imageEngines
                                }
                                SearchPanelEngineDockCard(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    PickResultTextSearchGrid(
                                        engines = activeEngines,
                                        query = if (mode == SearchMode.TEXT) {
                                            textQuery
                                        } else if (imageBitmap != null) {
                                            "image"
                                        } else {
                                            ""
                                        },
                                        columns = settings.searchEngineGridColumns,
                                        rows = settings.searchEngineGridRows,
                                        showLabels = settings.searchEngineShowLabels,
                                        longPressEnabled = longPressEnabled,
                                        showPageIndicator = true,
                                        pageIndicatorTopPadding = SearchPanelPageIndicatorTopPadding,
                                        pageIndicatorBottomPadding = SearchPanelPageIndicatorBottomPadding,
                                        onEngineClick = { engine, longPressTriggered ->
                                            launchSearchEngine(engine, longPressTriggered)
                                        },
                                    )
                                }
                            }

                            // 搜索框在底部时：引擎 dock 与搜索框一起做真悬浮，叠在列表之上（列表内容从它们后面滑过）。
                            // 搜索框在顶部时保持原来的布局流，只让操作胶囊悬浮。
                            val floatingBottomChrome = barAtBottom
                            val candidatesAndDockBlock: @Composable ColumnScope.() -> Unit = {
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .fillMaxWidth(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = true)
                                            .fillMaxWidth(),
                                    ) {
                                        // 候选列表：底边按卡片圆角裁切，切口被悬浮卡片盖住。
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(SearchPanelCardCorner)),
                                            contentAlignment = if (bottomUpListOrder) {
                                                Alignment.BottomCenter
                                            } else {
                                                Alignment.TopCenter
                                            },
                                        ) {
                                            candidatesContent()
                                        }
                                        if (floatingBottomChrome) {
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .onSizeChanged {
                                                        bottomChromeHeight =
                                                            with(actionPillsDensity) { it.height.toDp() }
                                                    },
                                                verticalArrangement = Arrangement.spacedBy(
                                                    SearchPanelCardVerticalSpacing,
                                                ),
                                            ) {
                                                actionPillsBlock()
                                                engineGridBlock()
                                                searchFieldBlock()
                                            }
                                        } else {
                                            // 搜索框在顶部时，只有操作胶囊悬浮在列表底边。
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .onSizeChanged {
                                                        actionPillsHeight =
                                                            with(actionPillsDensity) { it.height.toDp() }
                                                    },
                                            ) {
                                                actionPillsBlock()
                                            }
                                        }
                                    }
                                    if (!floatingBottomChrome) {
                                        engineGridBlock()
                                    }
                                }
                            }

                            if (floatingBottomChrome) {
                                candidatesAndDockBlock()
                            } else {
                                searchFieldBlock()
                                candidatesAndDockBlock()
                            }
                        }
                    }
                }
            }

        previewFile?.let { previewTarget ->
            FilePreviewBottomSheet(
                deviceFile = previewTarget,
                visibleState = previewVisibilityState,
                onDismissRequest = { dismissPreview() },
                onOpen = {
                    clearPreviewState()
                    if (FileSearchLauncher.open(context, previewTarget)) {
                        dismissPanel()
                    }
                },
                onShare = {
                    if (FileSearchLauncher.share(context, previewTarget)) {
                        clearPreviewState()
                        dismissPanel()
                    }
                },
            )
        }
    }
}

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    }.getOrNull()
}


private fun SearchPanelResultSection.icon(): ImageVector = when (this) {
    SearchPanelResultSection.ALL -> Icons.Default.Search
    SearchPanelResultSection.APPS -> Icons.Default.Apps
    SearchPanelResultSection.CONTACTS -> Icons.Default.Person
    SearchPanelResultSection.FILES -> Icons.Default.Folder
    SearchPanelResultSection.SETTINGS -> Icons.Default.Settings
}

private fun SearchPanelResultSection.labelResId(): Int = when (this) {
    SearchPanelResultSection.ALL -> R.string.search_panel_section_all
    SearchPanelResultSection.APPS -> R.string.search_panel_section_apps
    SearchPanelResultSection.CONTACTS -> R.string.search_panel_section_contacts
    SearchPanelResultSection.FILES -> R.string.search_panel_section_files
    SearchPanelResultSection.SETTINGS -> R.string.search_panel_section_settings
}
