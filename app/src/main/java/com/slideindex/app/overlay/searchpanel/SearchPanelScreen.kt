package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
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
import com.slideindex.app.overlay.pickresult.searchGridContentHeight
import com.slideindex.app.overlay.rememberOverlaySelectionToolbarState
import com.slideindex.app.overlay.suppressSystemTextContextMenu
import com.slideindex.app.overlay.viewportModifier
import com.slideindex.app.search.SearchEngineLauncher
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
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.settings.SearchPanelPresentationMode
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.settings.shouldLaunchFullscreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode { TEXT, IMAGE }

private const val APP_CANDIDATE_LIMIT = 10
private const val SETTINGS_CANDIDATE_LIMIT = 6
private const val FILE_CANDIDATE_LIMIT = 8
private const val SEARCH_DEBOUNCE_MS = 200L

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
    val wallpaperBlurEnabled = settings.searchPanelWallpaperBlurEnabled

    var mode by remember { mutableStateOf(SearchMode.TEXT) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(SearchPanelSessionState.lastTextQuery))
    }
    val textQuery = textFieldValue.text
    var debouncedQuery by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var settingsCandidates by remember { mutableStateOf<List<SystemSettingsSearchEntry>>(emptyList()) }
    var contactCandidates by remember { mutableStateOf<List<ContactSearchEntry>>(emptyList()) }
    var fileCandidates by remember { mutableStateOf<List<DeviceFileEntry>>(emptyList()) }
    var webSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var contactsExpanded by remember { mutableStateOf(false) }
    var filesExpanded by remember { mutableStateOf(false) }
    var manuallySwitchedToNumberKeyboard by remember { mutableStateOf(false) }
    var wallpaperBlurBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewFile by remember { mutableStateOf<DeviceFileEntry?>(null) }

    var hasContactPermission by remember(context, debouncedQuery) {
        mutableStateOf(ContactSearchIndex.hasPermission(context))
    }
    var hasFilePermission by remember(context, debouncedQuery) {
        mutableStateOf(FileSearchIndex.hasPermission(context))
    }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun hideSearchKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val engines = settings.searchEngines
    val textEngines = remember(engines) { SearchEngineStore.textPickPanelEngines(engines) }
    val imageEngines = remember(engines) { SearchEngineStore.imageSharePanelEngines(engines) }

    LaunchedEffect(appRepository) {
        val repository = appRepository ?: return@LaunchedEffect
        installedApps = repository.loadApps()
        withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context)
        }
    }

    LaunchedEffect(wallpaperBlurEnabled, settings.searchPanelBlurRadiusDp) {
        if (!wallpaperBlurEnabled) {
            wallpaperBlurBitmap = null
            return@LaunchedEffect
        }
        wallpaperBlurBitmap = SystemWallpaperBlurHelper.loadBlurred(
            context,
            settings.searchPanelBlurRadiusDp,
        )
    }

    LaunchedEffect(textQuery) {
        if (textQuery.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = textQuery
    }

    LaunchedEffect(debouncedQuery) {
        contactsExpanded = false
        filesExpanded = false
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
        settings.searchPanelContactSearchEnabled,
        settings.searchPanelFileSearchEnabled,
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
            return@LaunchedEffect
        }
        hasContactPermission = ContactSearchIndex.hasPermission(context)
        hasFilePermission = FileSearchIndex.hasPermission(context)
        settingsCandidates = withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.search(context, debouncedQuery, SETTINGS_CANDIDATE_LIMIT)
        }
        if (settings.searchPanelContactSearchEnabled) {
            contactCandidates = withContext(Dispatchers.IO) {
                ContactSearchIndex.search(context, debouncedQuery, 5)
            }
        } else {
            contactCandidates = emptyList()
        }
        if (settings.searchPanelFileSearchEnabled) {
            fileCandidates = if (hasFilePermission) {
                FileSearchIndex.search(
                    context = context,
                    query = debouncedQuery,
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
        } else {
            fileCandidates = emptyList()
        }
    }

    val appCandidates = remember(debouncedQuery, installedApps, appRepository) {
        val repository = appRepository ?: return@remember emptyList()
        if (debouncedQuery.isBlank()) {
            emptyList()
        } else {
            repository.searchApps(installedApps, debouncedQuery, APP_CANDIDATE_LIMIT)
        }
    }

    val linkUrls = remember(textQuery) {
        PickResultUrl.extractOpenableUrls(textQuery).ifEmpty {
            PickResultUrl.normalizeOpenableUrl(textQuery.trim())?.let { listOf(it) } ?: emptyList()
        }
    }
    val calculatorResult = remember(textQuery, settings.searchPanelCalculatorEnabled) {
        if (!settings.searchPanelCalculatorEnabled || !CalculatorUtils.isMathExpression(textQuery)) {
            null
        } else {
            CalculatorUtils.evaluateExpression(textQuery)
        }
    }
    val showCalculator = calculatorResult != null
    val showContactPermissionPrompt = settings.searchPanelContactSearchEnabled &&
        !hasContactPermission &&
        textQuery.isNotBlank()
    val showFilePermissionPrompt = settings.searchPanelFileSearchEnabled &&
        !hasFilePermission &&
        textQuery.isNotBlank()
    // Match Quick Search: Text <-> Number only (Phone can force a heavier IME restart).
    val searchKeyboardType = when {
        showCalculator || manuallySwitchedToNumberKeyboard -> KeyboardType.Number
        else -> KeyboardType.Text
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
    val hasCandidateSection = showCalculator ||
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

    DisposableEffect(previewFile != null) {
        if (previewFile != null) {
            SearchPanelSessionState.onBackPressed = {
                previewFile = null
                true
            }
        } else {
            SearchPanelSessionState.onBackPressed = null
        }
        onDispose {
            SearchPanelSessionState.onBackPressed = null
        }
    }

    LaunchedEffect(visibilityState.targetState, settings.searchPanelInputBehavior) {
        val visible = visibilityState.targetState
        if (visible && !wasPanelVisible && mode == SearchMode.TEXT) {
            textFieldValue = textFieldValueForInputBehavior(
                behavior = settings.searchPanelInputBehavior,
                lastQuery = SearchPanelSessionState.lastTextQuery,
            )
            debouncedQuery = if (textFieldValue.text.isBlank()) "" else textFieldValue.text
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        wasPanelVisible = visible
    }

    fun persistTextQuery() {
        SearchPanelSessionState.lastTextQuery = textFieldValue.text
    }

    fun dismissPanel() {
        persistTextQuery()
        onDismiss()
    }

    fun launchSearchEngine(engine: SearchEngineConfig, longPressTriggered: Boolean) {
        if (mode == SearchMode.TEXT && textQuery.isNotBlank()) {
            persistTextQuery()
            SearchEngineLauncher.launch(context, engine, textQuery, settings, longPressTriggered)
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
        FloatBallTextPick.openUrl(context, url, settings, longPressTriggered)
        dismissPanel()
    }

    fun launchAppCandidate(app: AppInfo, longPressTriggered: Boolean) {
        val repository = appRepository ?: return
        val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
        if (repository.launchApp(app, settings, fullscreen)) {
            dismissPanel()
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
            return
        }
        if (FileSearchLauncher.open(context, file)) {
            dismissPanel()
        }
    }

    val dismissInteraction = remember { MutableInteractionSource() }
    val isLandscape = overlayIsLandscape()
    val maxPanelHeight = overlayBottomPanelMaxHeight()
    val landscapeImagePreviewMaxHeight = 160.dp
    val showBlurBackground = wallpaperBlurEnabled && wallpaperBlurBitmap != null
    val panelShape = if (isFullscreen) {
        RectangleShape
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }
    val panelSurfaceColor = if (showBlurBackground) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val enterTransition = if (isFullscreen) {
        fadeIn() + slideInVertically(initialOffsetY = { -it / 10 })
    } else {
        slideInVertically(initialOffsetY = { it })
    }
    val exitTransition = if (isFullscreen) {
        fadeOut() + slideOutVertically(targetOffsetY = { -it / 10 })
    } else {
        slideOutVertically(targetOffsetY = { it })
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = ::dismissPanel,
                ),
        )
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
                val hasQueryCandidates = mode == SearchMode.TEXT && textQuery.isNotBlank()
                val useFlexLayout = isFullscreen
                val gridHeight = searchGridContentHeight(
                    rows = settings.searchEngineGridRows,
                    showLabels = settings.searchEngineShowLabels,
                    columns = settings.searchEngineGridColumns,
                )
                val topSectionHeight = if (mode == SearchMode.IMAGE) {
                    if (isLandscape) landscapeImagePreviewMaxHeight else 240.dp
                } else {
                    72.dp
                }
                val verticalPadding = 26.dp
                val candidateScrollMaxHeight = (maxHeight - topSectionHeight - gridHeight - verticalPadding)
                    .coerceAtLeast(0.dp)
                val forceTallPanel = isFullscreen || barAtBottom || textQuery.isNotBlank() || mode == SearchMode.IMAGE

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) {
                                Modifier.fillMaxSize()
                            } else if (forceTallPanel) {
                                Modifier.height(maxHeight)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (showBlurBackground) {
                        val blurRadiusDp = settings.searchPanelBlurRadiusDp.dp
                        Image(
                            bitmap = wallpaperBlurBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(panelShape)
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(blurRadiusDp)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isFullscreen || forceTallPanel) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        shape = panelShape,
                        color = panelSurfaceColor,
                        tonalElevation = if (showBlurBackground) 0.dp else 8.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFullscreen || forceTallPanel) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(top = 16.dp, bottom = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val searchFieldBlock: @Composable () -> Unit = {
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
                                                OutlinedTextField(
                                                    value = textFieldValue,
                                                    onValueChange = { updated ->
                                                        textFieldValue = updated.copy(
                                                            text = normalizeSearchPanelQuery(updated.text),
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp)
                                                        .focusRequester(focusRequester)
                                                        .suppressSystemTextContextMenu()
                                                        .then(Modifier.fieldModifier(toolbarState)),
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Search, contentDescription = null)
                                                    },
                                                    trailingIcon = {
                                                        Row {
                                                            if (textQuery.isNotEmpty()) {
                                                                IconButton(onClick = {
                                                                    textFieldValue = TextFieldValue("")
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
                                                    shape = RoundedCornerShape(24.dp),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = searchKeyboardType,
                                                        imeAction = ImeAction.Search,
                                                    ),
                                                    keyboardActions = KeyboardActions(onSearch = {
                                                        if (textQuery.isNotBlank()) {
                                                            val engineToUse = textEngines.find {
                                                                it.id == settings.searchPanelDefaultEngineId
                                                            }
                                                            if (engineToUse != null) {
                                                                launchSearchEngine(
                                                                    engineToUse,
                                                                    longPressTriggered = false,
                                                                )
                                                            }
                                                        }
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
                                                    .padding(horizontal = 16.dp)
                                                    .heightIn(min = 120.dp, max = imagePreviewMaxHeight)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
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


                            val candidatesContent: @Composable () -> Unit = {
                                val expandEdge = if (bottomUpListOrder) {
                                    Alignment.Bottom
                                } else {
                                    Alignment.Top
                                }
                                AnimatedVisibility(
                                    visible = hasQueryCandidates,
                                    enter = expandVertically(expandFrom = expandEdge) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = expandEdge) + fadeOut(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val candidateScrollState = rememberScrollState()
                                    // Base order is top-down (apps first). Bottom-up reverses sections.
                                    val topDownSections: List<@Composable () -> Unit> = buildList {
                                        if (appCandidates.isNotEmpty()) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                SearchPanelAppCandidates(
                                                    apps = appCandidates,
                                                    onLaunchApp = ::launchAppCandidate,
                                                    longPressEnabled = longPressEnabled,
                                                )
                                            }
                                        }
                                        if (showFilePermissionPrompt) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
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
                                        }
                                        if (fileCandidates.isNotEmpty()) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
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
                                        }
                                        if (showContactPermissionPrompt) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
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
                                        }
                                        if (contactCandidates.isNotEmpty()) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
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
                                        }
                                        if (settingsCandidates.isNotEmpty()) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                SearchPanelSettingsResultCards(
                                                    entries = settingsCandidates,
                                                    onLaunchEntry = ::launchSettingsCandidate,
                                                    longPressEnabled = longPressEnabled,
                                                )
                                            }
                                        }
                                        if (webSuggestions.isNotEmpty()) {
                                            add {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                SearchPanelWebSuggestionsCard(
                                                    suggestions = webSuggestions,
                                                    onSuggestionClick = { suggestion ->
                                                        textFieldValue = textFieldValue.copy(
                                                            text = suggestion,
                                                            selection = TextRange(suggestion.length),
                                                        )
                                                        debouncedQuery = suggestion
                                                    },
                                                )
                                            }
                                        }
                                        if (linkUrls.isNotEmpty()) {
                                            add {
                                                SearchPanelLinkResultCards(
                                                    urls = linkUrls,
                                                    onOpenUrl = ::openUrl,
                                                    longPressEnabled = longPressEnabled,
                                                )
                                            }
                                        }
                                        if (showCalculator && calculatorResult != null) {
                                            add {
                                                SearchPanelCalculatorCard(
                                                    expression = textQuery.trim(),
                                                    result = calculatorResult,
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                    val orderedSections = if (bottomUpListOrder) {
                                        topDownSections.asReversed()
                                    } else {
                                        topDownSections
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(
                                                candidateScrollState,
                                                reverseScrolling = bottomUpListOrder,
                                            ),
                                    ) {
                                        if (hasCandidateSection) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                        orderedSections.forEach { section -> section() }
                                        if (hasCandidateSection) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                    alpha = 0.45f,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            val candidatesSlot: @Composable ColumnScope.(flex: Boolean) -> Unit = { flex ->
                                Box(
                                    modifier = when {
                                        flex -> Modifier.weight(1f, fill = true).fillMaxWidth()
                                        hasQueryCandidates -> Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = candidateScrollMaxHeight)
                                        else -> Modifier.fillMaxWidth()
                                    },
                                    contentAlignment = if (bottomUpListOrder) {
                                        Alignment.BottomCenter
                                    } else {
                                        Alignment.TopCenter
                                    },
                                ) {
                                    candidatesContent()
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
                                val aggregateSearchEngine = remember {
                                    SearchEngineConfig(
                                        id = "slideindex_aggregate_image_search",
                                        name = "聚合搜图",
                                        engineType = SearchEngineType.SHARE_IMAGE_TO_APP,
                                        iconType = SearchIconType.TEXT,
                                        textIcon = "聚",
                                    )
                                }
                                val activeEngines = if (mode == SearchMode.TEXT) {
                                    textEngines
                                } else {
                                    listOf(aggregateSearchEngine) + imageEngines
                                }
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
                                    onEngineClick = { engine, longPressTriggered ->
                                        launchSearchEngine(engine, longPressTriggered)
                                    },
                                )
                            }

                            if (!barAtBottom) {
                                searchFieldBlock()
                                candidatesSlot(useFlexLayout)
                                actionPillsBlock()
                                Spacer(modifier = Modifier.height(16.dp))
                                engineGridBlock()
                            } else {
                                candidatesSlot(useFlexLayout)
                                actionPillsBlock()
                                Spacer(modifier = Modifier.height(16.dp))
                                engineGridBlock()
                                searchFieldBlock()
                            }
                        }
                    }
                }
            }
        }

        previewFile?.let { previewTarget ->
            FilePreviewBottomSheet(
                deviceFile = previewTarget,
                onDismiss = { previewFile = null },
                onOpen = {
                    previewFile = null
                    if (FileSearchLauncher.open(context, previewTarget)) {
                        dismissPanel()
                    }
                },
                onShare = {
                    FileSearchLauncher.share(context, previewTarget)
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
