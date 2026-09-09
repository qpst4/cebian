package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.FloatBallImageSearchPanel
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.overlayBottomPanelMaxHeightFraction
import com.slideindex.app.overlay.overlayBottomPanelMaxWidth
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.overlay.overlayContainerHeightDp
import com.slideindex.app.overlay.overlayContainerWidthDp
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.overlay.rememberOverlayImeBottomHeight
import com.slideindex.app.overlay.pickresult.detectPickResultDismissOutsidePanelTap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

@Composable
internal fun FloatBallPickResultContent(
    panelShowToken: Int,
    panelRevealed: Boolean,
    panelNotification: String?,
    onShowInPanelMessage: (String?) -> Unit,
    text: String?,
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    contentOrigin: PickResultContentOrigin,
    activeText: String,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    isShareImageOcr: Boolean,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    onBackgroundOcr: () -> Unit,
    imageSearchPickPanelTransparency: Float,
    textSizeSp: Float,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    appSettings: AppSettings,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShareText: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onSaveScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onImageSearch: () -> Unit,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig, Boolean) -> Unit,
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    onImageIndexChange: (Int) -> Unit,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?
) {
    LaunchedEffect(panelNotification) {
        if (panelNotification != null) {
            kotlinx.coroutines.delay(2000L)
            onShowInPanelMessage(null)
        }
    }

    val hasTextSection = ocrLoading || !text.isNullOrBlank() || screenshot != null ||
        panelImages.isNotEmpty() || ocrAvailable || barcodeResults.isNotEmpty() ||
        contentOrigin == PickResultContentOrigin.STASH_CLIPBOARD
    val isEditMode = textMode == PickResultTextMode.EDIT
    val showTextSection = hasTextSection || isEditMode
    val hasImageContent = panelImages.isNotEmpty() || screenshot != null
    val textFirstPanelEnabled = appSettings.floatBallPickTextFirstPanel
    val reserveImageSectionPlaceholder = textFirstPanelEnabled &&
        contentOrigin == PickResultContentOrigin.SCREEN_PICK &&
        !hasImageContent
    val showImageSection = hasImageContent || reserveImageSectionPlaceholder
    val imageSearchVisible by FloatBallImageSearchPanel.panelVisible
    val pickPanelAlpha = if (imageSearchVisible) {
        1f - imageSearchPickPanelTransparency.coerceIn(0f, 1f)
    } else {
        1f
    }

    val density = LocalDensity.current
    val displayMetrics = LocalContext.current.applicationContext.resources.displayMetrics
    val landscapeDualColumn = overlayIsLandscape() &&
        showImageSection &&
        showTextSection &&
        hasImageContent &&
        !isEditMode
    val maxPanelHeight = overlayContainerHeightDp() * overlayBottomPanelMaxHeightFraction()
    val panelMaxImageHeight = pickResultImageMaxHeightDp()
    val panelLayoutWidth = overlayBottomPanelMaxWidth() ?: overlayContainerWidthDp()
    val imageContentWidth = if (landscapeDualColumn) {
        (panelLayoutWidth * LANDSCAPE_DUAL_COLUMN_AUX_WEIGHT - 40.dp).coerceAtLeast(80.dp)
    } else {
        pickResultImageContentWidth()
    }

    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val panelSearchEngines = remember(searchEngines) {
        SearchEngineStore.textPickPanelEngines(searchEngines)
    }
    val hasSearchGrid = showTextSection && panelSearchEngines.isNotEmpty()
    val effectiveSearchGridColumns = if (overlayIsLandscape()) {
        val gridWidth = if (landscapeDualColumn) {
            panelLayoutWidth * LANDSCAPE_DUAL_COLUMN_AUX_WEIGHT
        } else {
            panelLayoutWidth
        }
        effectiveSearchGridColumns(searchEngineGridColumns, gridWidth, isLandscape = true)
    } else {
        searchEngineGridColumns
    }
    val searchGridReservedHeight = if (hasSearchGrid) {
        pickResultSearchGridReservedHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            effectiveSearchGridColumns
        )
    } else {
        0.dp
    }
    val textImageDividerBaseHeight = if (showImageSection && showTextSection) {
        TEXT_IMAGE_DIVIDER_HEIGHT
    } else {
        0.dp
    }
    val textSectionChromeHeight = if (showTextSection) {
        pickResultTextSectionChromeReservedHeight()
    } else {
        0.dp
    }
    val idealTextBodyHeight = if (showTextSection) {
        pickResultTextBodyAllocatedHeight(textSizeSp)
    } else {
        0.dp
    }
    val minTextBodyHeight = if (showTextSection) {
        pickResultMinTextBodyAllocatedHeight(
            textSizeSp = textSizeSp,
            lines = pickResultMinTextBodyLines()
        )
    } else {
        0.dp
    }
    val panelVerticalPadding = PANEL_VERTICAL_PADDING * 2
    val imageSectionFixedChrome = if (showImageSection) {
        pickResultImageSectionReservedHeight(0.dp, true) // Always use true for stable max height calculation
    } else {
        0.dp
    }
    val reservedForTextAndChrome = if (landscapeDualColumn) {
        panelVerticalPadding + searchGridReservedHeight + imageSectionFixedChrome
    } else {
        panelVerticalPadding +
            (if (showTextSection) {
                textSectionChromeHeight + textImageDividerBaseHeight + minTextBodyHeight
            } else {
                0.dp
            }) +
            searchGridReservedHeight
    }
    val affordableImageMaxHeight = when {
        !hasImageContent -> panelMaxImageHeight
        landscapeDualColumn -> {
            (maxPanelHeight - panelVerticalPadding - searchGridReservedHeight - imageSectionFixedChrome)
                .coerceAtLeast(PANEL_MIN_IMAGE_HEIGHT)
        }
        else -> {
            (maxPanelHeight - reservedForTextAndChrome - imageSectionFixedChrome)
                .coerceAtLeast(PANEL_MIN_IMAGE_HEIGHT)
        }
    }
    val effectivePanelMaxImageHeight = minOf(panelMaxImageHeight, affordableImageMaxHeight)
    val panelImageDisplaySize = screenshot?.let { bitmap ->
        pickResultImageDisplaySize(
            bitmap = bitmap,
            contentWidth = imageContentWidth,
            maxHeight = effectivePanelMaxImageHeight,
            density = density,
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels
        )
    } ?: PickResultImageDisplaySize(0.dp, 0.dp)

    val collapsedImageSectionHeight = pickResultImageSectionReservedHeight(0.dp, false)
    val minImageSectionHeight = if (showImageSection) collapsedImageSectionHeight else 0.dp
    val maxImageSectionHeight = when {
        hasImageContent -> pickResultImageSectionReservedHeight(panelImageDisplaySize.height, true)
        reserveImageSectionPlaceholder -> collapsedImageSectionHeight
        else -> 0.dp
    }
    val searchGridSectionPrefixHeight = if (showTextSection || showImageSection) {
        12.dp + 1.dp + 12.dp + PickResultTextSearchGridTopSpacing
    } else {
        0.dp
    }
    val expandedSearchGridContentHeight = if (hasSearchGrid) {
        searchGridContentHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            effectiveSearchGridColumns
        ) + 4.dp
    } else {
        0.dp
    }
    val maxSearchSectionHeight = if (hasSearchGrid) {
        searchGridSectionPrefixHeight + expandedSearchGridContentHeight
    } else {
        0.dp
    }
    val totalImageCollapsiblePx = remember(
        maxImageSectionHeight,
        minImageSectionHeight,
        density
    ) {
        with(density) {
            (maxImageSectionHeight - minImageSectionHeight).toPx().coerceAtLeast(1f)
        }
    }
    val totalSearchCollapsiblePx = remember(
        maxSearchSectionHeight,
        density
    ) {
        with(density) {
            maxSearchSectionHeight.toPx().coerceAtLeast(1f)
        }
    }
    val totalCollapsiblePx = remember(
        totalImageCollapsiblePx,
        totalSearchCollapsiblePx,
        textFirstPanelEnabled,
        hasSearchGrid
    ) {
        if (textFirstPanelEnabled && hasSearchGrid) {
            totalImageCollapsiblePx
        } else {
            totalImageCollapsiblePx + totalSearchCollapsiblePx
        }
    }

    val isImageVisible = remember(panelShowToken, textFirstPanelEnabled) {
        mutableStateOf(!textFirstPanelEnabled)
    }
    val isSearchGridVisible = remember(panelShowToken) {
        mutableStateOf(true)
    }
    val scopedCollapseController = remember(panelShowToken, textFirstPanelEnabled) {
        AuxiliaryCollapseController(
            initialCollapseProgress = if (textFirstPanelEnabled) 1f else 0f,
            initialSearchCollapseProgress = 0f
        )
    }
    SideEffect {
        scopedCollapseController.updateTotalCollapsiblePx(
            if (textFirstPanelEnabled && hasSearchGrid) {
                totalImageCollapsiblePx
            } else {
                totalCollapsiblePx
            }
        )
        scopedCollapseController.updateTotalSearchCollapsiblePx(totalSearchCollapsiblePx)
    }

    LaunchedEffect(isImageVisible.value, textFirstPanelEnabled, hasImageContent) {
        if (!textFirstPanelEnabled || !hasImageContent || scopedCollapseController.isDragging) return@LaunchedEffect
        scopedCollapseController.setExpanded(isImageVisible.value)
    }

    LaunchedEffect(isSearchGridVisible.value, textFirstPanelEnabled, hasSearchGrid) {
        if (!textFirstPanelEnabled || !hasSearchGrid || scopedCollapseController.isDragging) return@LaunchedEffect
        scopedCollapseController.setSearchExpanded(isSearchGridVisible.value)
    }

    fun applyAuxiliaryDrag(deltaPx: Float) {
        scopedCollapseController.applyDrag(deltaPx)
    }

    fun applySearchAuxiliaryDrag(deltaPx: Float) {
        scopedCollapseController.applySearchDrag(deltaPx)
    }

    fun endImageAuxiliaryDrag() {
        scopedCollapseController.endDrag { expanded ->
            isImageVisible.value = expanded
            if (!textFirstPanelEnabled) {
                isSearchGridVisible.value = expanded
            }
        }
    }

    fun endSearchAuxiliaryDrag() {
        scopedCollapseController.endSearchDrag { expanded ->
            isSearchGridVisible.value = expanded
        }
    }

    val overlayImeBottom = rememberOverlayImeBottomHeight()
    val hasAuxiliaryCollapse = showImageSection || hasSearchGrid
    val panelContentHeight = maxPanelHeight - overlayImeBottom

    val panelSlideDistance = remember(
        panelContentHeight,
        hasSearchGrid,
        showImageSection,
        minImageSectionHeight,
        maxImageSectionHeight,
        textImageDividerBaseHeight,
        searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight,
        idealTextBodyHeight,
        minTextBodyHeight,
        showTextSection,
        maxPanelHeight,
        landscapeDualColumn
    ) {
        if (showTextSection || showImageSection) {
            computePickResultExpandedPanelOuterHeight(
                panelContentHeight = panelContentHeight,
                hasSearchGrid = hasSearchGrid,
                hasImageContent = showImageSection,
                minImageSectionHeight = minImageSectionHeight,
                maxImageSectionHeight = maxImageSectionHeight,
                textImageDividerBaseHeight = textImageDividerBaseHeight,
                searchGridSectionPrefixHeight = searchGridSectionPrefixHeight,
                expandedSearchGridContentHeight = expandedSearchGridContentHeight,
                idealTextBodyHeight = idealTextBodyHeight,
                minTextBodyHeight = minTextBodyHeight,
                landscapeDualColumn = landscapeDualColumn
            )
        } else {
            maxPanelHeight * 0.35f
        }
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            FloatBallPickResultPanel.requestPanelFocus()
        }
    }

    var panelBoundsInRoot by remember { mutableStateOf(ComposeRect.Zero) }
    val panelBoundsState = rememberUpdatedState(panelBoundsInRoot)

    OverlayAwareModuleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectPickResultDismissOutsidePanelTap(
                        panelBoundsInRoot = { panelBoundsState.value },
                        onDismiss = onDismiss
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(modifier = Modifier.overlayBottomPanelWidth()) {
            PickResultPanelSlideHost(
                panelRevealed = panelRevealed,
                panelSlideDistance = panelSlideDistance,
                panelEnterAnimationMs = appSettings.floatBallPickPanelEnterAnimationMs,
                panelExitAnimationMs = appSettings.floatBallPickPanelExitAnimationMs,
                onPanelBoundsInRoot = { panelBoundsInRoot = it }
            ) { freezeCollapseAnimation ->
                PickResultCollapsePanelColumn(
                controller = scopedCollapseController,
                panelContentHeight = panelContentHeight,
                overlayImeBottom = overlayImeBottom,
                pickPanelAlpha = pickPanelAlpha,
                imageSearchVisible = imageSearchVisible,
                dismissInteraction = dismissInteraction,
                cardInteraction = cardInteraction,
                onDismiss = onDismiss,
                isEditMode = isEditMode,
                hasImageContent = showImageSection,
                hasSearchGrid = hasSearchGrid,
                hasAuxiliaryCollapse = hasAuxiliaryCollapse,
                showTextSection = showTextSection,
                minImageSectionHeight = minImageSectionHeight,
                maxImageSectionHeight = maxImageSectionHeight,
                textImageDividerBaseHeight = textImageDividerBaseHeight,
                searchGridSectionPrefixHeight = searchGridSectionPrefixHeight,
                expandedSearchGridContentHeight = expandedSearchGridContentHeight,
                idealTextBodyHeight = idealTextBodyHeight,
                minTextBodyHeight = minTextBodyHeight,
                screenshot = screenshot,
                panelImages = panelImages,
                currentImageIndex = currentImageIndex,
                panelImageDisplaySize = panelImageDisplaySize,
                searchEngines = searchEngines,
                onSaveScreenshot = onSaveScreenshot,
                onShareScreenshot = onShareScreenshot,
                onImageSearch = onImageSearch,
                onImageShareEngineClick = onImageShareEngineClick,
                onPinImageToScreen = onPinImageToScreen,
                onStashImage = onStashImage,
                onImageClick = onImageClick,
                onImageIndexChange = onImageIndexChange,
                onImageSectionExpandedChange = { expanded ->
                    isImageVisible.value = expanded
                    if (textFirstPanelEnabled) {
                        isSearchGridVisible.value = true
                        scopedCollapseController.setExpanded(expanded)
                    } else {
                        isSearchGridVisible.value = expanded
                    }
                },
                onDragEnd = ::endImageAuxiliaryDrag,
                onSearchDragEnd = ::endSearchAuxiliaryDrag,
                applyDrag = ::applyAuxiliaryDrag,
                applySearchDrag = if (textFirstPanelEnabled) {
                    ::applySearchAuxiliaryDrag
                } else {
                    ::applyAuxiliaryDrag
                },
                panelSearchEngines = panelSearchEngines,
                activeText = activeText,
                searchEngineGridColumns = effectiveSearchGridColumns,
                searchEngineGridRows = searchEngineGridRows,
                searchEngineShowLabels = searchEngineShowLabels,
                appSettings = appSettings,
                onSearchEngineClick = onSearchEngineClick,
                text = text,
                textMode = textMode,
                textSource = textSource,
                textSizeSp = textSizeSp,
                ocrAvailable = ocrAvailable,
                a11yAvailable = a11yAvailable,
                ocrLoading = ocrLoading,
                isShareImageOcr = isShareImageOcr,
                barcodeResults = barcodeResults,
                showingTranslation = showingTranslation,
                translateLoading = translateLoading,
                onBackgroundOcr = onBackgroundOcr,
                onTextSourceChange = onTextSourceChange,
                onActiveTextChange = onActiveTextChange,
                onTextModeChange = onTextModeChange,
                onTextChange = onTextChange,
                onShareText = onShareText,
                onCopy = onCopy,
                onTranslate = onTranslate,
                onRemoveSpaces = onRemoveSpaces,
                onZoomText = { expanded ->
                    val visible = !expanded
                    isImageVisible.value = visible
                    if (!textFirstPanelEnabled) {
                        isSearchGridVisible.value = visible
                    }
                },
                onPinTextToScreen = onPinTextToScreen,
                onStashText = onStashText,
                textFirstPanelEnabled = textFirstPanelEnabled,
                freezeCollapseAnimation = freezeCollapseAnimation,
                landscapeDualColumn = landscapeDualColumn
            )
            }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = panelNotification != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = panelNotification ?: "",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
