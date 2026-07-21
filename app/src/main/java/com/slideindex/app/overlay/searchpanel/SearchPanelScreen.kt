package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.FloatBallImageSearchPanel
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.settings.shouldLaunchFullscreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode { TEXT, IMAGE }

private const val APP_CANDIDATE_LIMIT = 6
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

    var mode by remember { mutableStateOf(SearchMode.TEXT) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val textQuery = textFieldValue.text
    var debouncedQuery by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val engines = settings.searchEngines
    val textEngines = remember(engines) { SearchEngineStore.textPickPanelEngines(engines) }
    val imageEngines = remember(engines) { SearchEngineStore.imageSharePanelEngines(engines) }

    LaunchedEffect(appRepository) {
        val repository = appRepository ?: return@LaunchedEffect
        installedApps = repository.loadApps()
    }

    LaunchedEffect(textQuery) {
        if (textQuery.isBlank()) {
            debouncedQuery = ""
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = textQuery
    }

    val appCandidates = remember(debouncedQuery, installedApps, appRepository) {
        val repository = appRepository ?: return@remember emptyList()
        if (debouncedQuery.isBlank()) {
            emptyList()
        } else {
            repository.searchApps(installedApps, debouncedQuery).take(APP_CANDIDATE_LIMIT)
        }
    }

    val linkUrls = remember(textQuery) {
        PickResultUrl.extractOpenableUrls(textQuery).ifEmpty {
            PickResultUrl.normalizeOpenableUrl(textQuery.trim())?.let { listOf(it) } ?: emptyList()
        }
    }
    val hasCandidateSection = linkUrls.isNotEmpty() || appCandidates.isNotEmpty()

    var wasPanelVisible by remember { mutableStateOf(false) }

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
            onDismiss()
        } else if (mode == SearchMode.IMAGE && imageBitmap != null) {
            if (engine.id == "slideindex_aggregate_image_search") {
                FloatBallImageSearchPanel.show(context, imageBitmap!!)
            } else {
                SearchEngineLauncher.launchImageShare(context, engine, imageBitmap!!)
            }
            onDismiss()
        }
    }

    fun openUrl(url: String, longPressTriggered: Boolean) {
        persistTextQuery()
        FloatBallTextPick.openUrl(context, url, settings, longPressTriggered)
        onDismiss()
    }

    fun launchAppCandidate(app: AppInfo, longPressTriggered: Boolean) {
        val repository = appRepository ?: return
        val fullscreen = settings.shouldLaunchFullscreen(longPressTriggered)
        if (repository.launchApp(app, settings, fullscreen)) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) {
                dismissPanel()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) {},
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 10.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Crossfade(targetState = mode, label = "SearchModeCrossfade") { currentMode ->
                        when (currentMode) {
                            SearchMode.TEXT -> {
                                OutlinedTextField(
                                    value = textFieldValue,
                                    onValueChange = { updated ->
                                        textFieldValue = updated.copy(text = normalizeSearchPanelQuery(updated.text))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .focusRequester(focusRequester),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        Row {
                                            if (textQuery.isNotEmpty()) {
                                                IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
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
                                                Icon(Icons.Default.Image, contentDescription = null)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (textQuery.isNotBlank()) {
                                            val engineToUse = textEngines.find { it.id == settings.searchPanelDefaultEngineId }
                                            if (engineToUse != null) {
                                                launchSearchEngine(engineToUse, longPressTriggered = false)
                                            }
                                        }
                                    })
                                )
                            }
                            SearchMode.IMAGE -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .heightIn(min = 120.dp, max = 240.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = imageBitmap!!.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
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
                                            )
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = mode == SearchMode.TEXT && textQuery.isNotBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            if (hasCandidateSection) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            SearchPanelLinkCandidates(
                                urls = linkUrls,
                                onOpenUrl = ::openUrl,
                                longPressEnabled = longPressEnabled,
                            )
                            if (appCandidates.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                SearchPanelAppCandidates(
                                    apps = appCandidates,
                                    onLaunchApp = ::launchAppCandidate,
                                    longPressEnabled = longPressEnabled,
                                )
                            }
                            if (hasCandidateSection) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        query = if (mode == SearchMode.TEXT) textQuery else if (imageBitmap != null) "image" else "",
                        columns = settings.searchEngineGridColumns,
                        rows = settings.searchEngineGridRows,
                        showLabels = settings.searchEngineShowLabels,
                        longPressEnabled = longPressEnabled,
                        onEngineClick = { engine, longPressTriggered ->
                            launchSearchEngine(engine, longPressTriggered)
                        },
                    )
                }
            }
        }
    }
}

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }.getOrNull()
}
