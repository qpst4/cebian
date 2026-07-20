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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.PickResultSearchEngineItem
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.di.OverlayDependencyAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchMode { TEXT, IMAGE }

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
    
    var mode by remember { mutableStateOf(SearchMode.TEXT) }
    var textQuery by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val engines = settings.searchEngines
    val textEngines = remember(engines) { SearchEngineStore.textPickPanelEngines(engines) }
    val imageEngines = remember(engines) { SearchEngineStore.imageSharePanelEngines(engines) }

    LaunchedEffect(visibilityState.targetState) {
        if (visibilityState.targetState && mode == SearchMode.TEXT) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) {
                onDismiss()
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
                    .clickable(interactionSource = null, indication = null) {}, // Consume clicks
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
                                    value = textQuery,
                                    onValueChange = { textQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .focusRequester(focusRequester),
                                    placeholder = { Text(stringResource(R.string.search_engine_settings_subtitle)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        Row {
                                            if (textQuery.isNotEmpty()) {
                                                IconButton(onClick = { textQuery = "" }) {
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
                                                SearchEngineLauncher.launch(context, engineToUse, textQuery)
                                                onDismiss()
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
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(50))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    val activeEngines = if (mode == SearchMode.TEXT) textEngines else imageEngines

                    com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid(
                        engines = activeEngines,
                        query = if (mode == SearchMode.TEXT) textQuery else if (imageBitmap != null) "image" else "",
                        columns = settings.searchEngineGridColumns,
                        rows = settings.searchEngineGridRows,
                        showLabels = settings.searchEngineShowLabels,
                        onEngineClick = { engine ->
                            if (mode == SearchMode.TEXT && textQuery.isNotBlank()) {
                                SearchEngineLauncher.launch(context, engine, textQuery)
                                onDismiss()
                            } else if (mode == SearchMode.IMAGE && imageBitmap != null) {
                                SearchEngineLauncher.launchImageShare(context, engine, imageBitmap!!)
                                onDismiss()
                            }
                        }
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
