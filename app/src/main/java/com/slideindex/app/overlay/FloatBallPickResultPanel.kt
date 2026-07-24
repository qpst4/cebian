package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.barcode.joinDisplayText
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGridTopSpacing
import com.slideindex.app.overlay.pickresult.preloadPickResultSearchEngineIcons
import com.slideindex.app.overlay.pickresult.pickResultImageContentWidth
import com.slideindex.app.overlay.pickresult.PickResultImageDisplaySize
import com.slideindex.app.overlay.pickresult.pickResultImageDisplaySize
import com.slideindex.app.overlay.pickresult.pickResultImageMaxHeightDp
import com.slideindex.app.overlay.pickresult.pickResultImageSectionReservedHeight
import com.slideindex.app.overlay.pickresult.pickResultSearchGridReservedHeight
import com.slideindex.app.overlay.pickresult.searchGridContentHeight
import com.slideindex.app.overlay.pickresult.pickResultMinTextBodyAllocatedHeight
import com.slideindex.app.overlay.pickresult.pickResultTextBodyAllocatedHeight
import com.slideindex.app.overlay.pickresult.pickResultTextSectionChromeReservedHeight
import com.slideindex.app.overlay.pickresult.PickResultTextActionBarReservedHeight
import com.slideindex.app.overlay.pickresult.PickResultTextActionBarTopPadding
import com.slideindex.app.overlay.pickresult.PickResultTextActionBarBottomPaddingWhenAlone
import com.slideindex.app.overlay.pickresult.PickResultTextSectionToolbarReservedHeight
import com.slideindex.app.overlay.pickresult.PickResultTextToolbarBodySpacing
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.overlay.pickresult.PickResultImageSearchBar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.slideindex.app.overlay.pickresult.PickResultInteractiveTextSection
import com.slideindex.app.overlay.pickresult.pickResultBottomPanelCard
import com.slideindex.app.overlay.pickresult.PickResultSectionHeader
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.ShareImageOcrCoordinator
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.ocr.OcrDependencyAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope

private val PANEL_MAX_HEIGHT_FRACTION = 0.85f
private val PANEL_MIN_IMAGE_HEIGHT = 48.dp
private val PANEL_VERTICAL_PADDING = 12.dp
private val PANEL_ACTION_BAR_BOTTOM_GAP = 12.dp
private val TEXT_IMAGE_DIVIDER_HEIGHT = 25.dp
private const val AUXILIARY_COLLAPSE_ANIMATION_MS = 280
private const val AUXILIARY_COLLAPSE_DRAG_THRESHOLD = 0.35f
private const val EDIT_MODE_ANIMATION_MS = 280
private const val PANEL_ENTER_ANIMATION_MS = 360
private const val PANEL_EXIT_ANIMATION_MS = 260

@Stable
private class AuxiliaryCollapseController(
    private val totalCollapsiblePx: Float,
) {
    var collapseProgress by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set

    fun applyDrag(deltaPx: Float) {
        isDragging = true
        collapseProgress = (collapseProgress + deltaPx / totalCollapsiblePx).coerceIn(0f, 1f)
    }

    fun endDrag(onSettled: (expanded: Boolean) -> Unit) {
        isDragging = false
        val target = if (collapseProgress > AUXILIARY_COLLAPSE_DRAG_THRESHOLD) 1f else 0f
        collapseProgress = target
        onSettled(target < 0.5f)
    }

    fun setExpanded(expanded: Boolean) {
        if (isDragging) return
        val target = if (expanded) 0f else 1f
        if (abs(collapseProgress - target) < 0.001f) return
        collapseProgress = target
    }

    fun resetToExpanded() {
        isDragging = false
        collapseProgress = 0f
    }
}

private data class PickResultCollapseHeights(
    val imageSectionHeight: Dp,
    val textImageDividerHeight: Dp,
    val searchDividerHeight: Dp,
    val searchGridHeight: Dp,
    val textBodyHeight: Dp,
    val fillTextSpace: Boolean,
)

private fun computePickResultCollapseHeights(
    panelInnerHeight: Dp,
    expansionFraction: Float,
    normalLayoutFactor: Float,
    hasImageContent: Boolean,
    minImageSectionHeight: Dp,
    maxImageSectionHeight: Dp,
    textImageDividerBaseHeight: Dp,
    searchDividerBaseHeight: Dp,
    expandedSearchGridContentHeight: Dp,
    idealTextBodyHeight: Dp,
    minTextBodyHeight: Dp,
    actionBarBottomPadding: Dp = 0.dp,
): PickResultCollapseHeights {
    val layoutFactor = normalLayoutFactor.coerceIn(0f, 1f)
    val expansion = expansionFraction.coerceIn(0f, 1f)

    val imageSectionHeight = if (hasImageContent) {
        lerp(minImageSectionHeight, maxImageSectionHeight, expansion) * layoutFactor
    } else {
        0.dp
    }
    val textImageDividerHeight = textImageDividerBaseHeight * expansion * layoutFactor
    val searchDividerHeight = searchDividerBaseHeight * expansion * layoutFactor
    val searchGridHeight = expandedSearchGridContentHeight * expansion * layoutFactor

    val textToolbarReserved =
        PickResultTextSectionToolbarReservedHeight + PickResultTextToolbarBodySpacing
    val actionBarReserved =
        PickResultTextActionBarReservedHeight +
            PickResultTextActionBarTopPadding +
            actionBarBottomPadding

    val rawTextBodyHeight = (
        panelInnerHeight -
            imageSectionHeight -
            textImageDividerHeight -
            searchDividerHeight -
            searchGridHeight -
            textToolbarReserved -
            actionBarReserved
        ).coerceAtLeast(minTextBodyHeight)

    val compactTextBodyHeight =
        minOf(idealTextBodyHeight, rawTextBodyHeight).coerceAtLeast(minTextBodyHeight)
    val fillTextSpace = rawTextBodyHeight > compactTextBodyHeight + 0.5.dp
    val textBodyHeight = if (fillTextSpace) rawTextBodyHeight else compactTextBodyHeight

    return PickResultCollapseHeights(
        imageSectionHeight = imageSectionHeight.coerceAtLeast(0.dp),
        textImageDividerHeight = textImageDividerHeight.coerceAtLeast(0.dp),
        searchDividerHeight = searchDividerHeight.coerceAtLeast(0.dp),
        searchGridHeight = searchGridHeight.coerceAtLeast(0.dp),
        textBodyHeight = textBodyHeight,
        fillTextSpace = fillTextSpace,
    )
}

/** 完全展开时的面板外高度（短文本场景下与 wrapContent 测量值一致，避免拖动/松手跳变）。 */
private fun computePickResultExpandedPanelOuterHeight(
    panelContentHeight: Dp,
    hasSearchGrid: Boolean,
    hasImageContent: Boolean,
    minImageSectionHeight: Dp,
    maxImageSectionHeight: Dp,
    textImageDividerBaseHeight: Dp,
    searchGridSectionPrefixHeight: Dp,
    expandedSearchGridContentHeight: Dp,
    idealTextBodyHeight: Dp,
    minTextBodyHeight: Dp,
    actionBarBottomPadding: Dp = 0.dp,
): Dp {
    val expandedBottomPadding = if (hasSearchGrid) 0.dp else PANEL_ACTION_BAR_BOTTOM_GAP
    val panelInnerHeight = panelContentHeight - PANEL_VERTICAL_PADDING - expandedBottomPadding
    val heights = computePickResultCollapseHeights(
        panelInnerHeight = panelInnerHeight,
        expansionFraction = 1f,
        normalLayoutFactor = 1f,
        hasImageContent = hasImageContent,
        minImageSectionHeight = minImageSectionHeight,
        maxImageSectionHeight = maxImageSectionHeight,
        textImageDividerBaseHeight = textImageDividerBaseHeight,
        searchDividerBaseHeight = searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight = expandedSearchGridContentHeight,
        idealTextBodyHeight = idealTextBodyHeight,
        minTextBodyHeight = minTextBodyHeight,
        actionBarBottomPadding = actionBarBottomPadding,
    )
    val textToolbarReserved =
        PickResultTextSectionToolbarReservedHeight + PickResultTextToolbarBodySpacing
    val actionBarReserved =
        PickResultTextActionBarReservedHeight +
            PickResultTextActionBarTopPadding +
            actionBarBottomPadding

    return PANEL_VERTICAL_PADDING + expandedBottomPadding +
        heights.imageSectionHeight +
        heights.textImageDividerHeight +
        heights.searchDividerHeight +
        heights.searchGridHeight +
        textToolbarReserved +
        heights.textBodyHeight +
        actionBarReserved
}

@Composable
private fun PickResultAuxiliaryImageBlock(
    sectionHeight: Dp,
    alphaFactor: Float,
    sectionExpanded: Boolean,
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    panelImageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    onSaveScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
    onImageSearch: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    onImageIndexChange: (Int) -> Unit,
    onSectionExpandedChange: (Boolean) -> Unit,
) {
    if (sectionHeight <= 0.dp) return

    PickResultImageSection(
        screenshot = screenshot,
        panelImages = panelImages,
        currentImageIndex = currentImageIndex,
        imageDisplaySize = panelImageDisplaySize,
        searchEngines = searchEngines,
        modifier = Modifier
            .fillMaxWidth()
            .height(sectionHeight)
            .graphicsLayer { alpha = alphaFactor }
            .clipToBounds(),
        onSave = onSaveScreenshot,
        onShare = onShareScreenshot,
        onImageSearch = onImageSearch,
        onShareEngineClick = onImageShareEngineClick,
        onPinToScreen = onPinImageToScreen,
        onStash = onStashImage,
        onImageClick = onImageClick,
        onImageIndexChange = onImageIndexChange,
        sectionExpanded = sectionExpanded,
        onSectionExpandedChange = onSectionExpandedChange,
    )
}

@Composable
private fun PickResultTextImageDividerBlock(
    dividerHeight: Dp,
    alphaFactor: Float,
    hasAuxiliaryCollapse: Boolean,
    onDragEnd: () -> Unit,
    applyDrag: (Float) -> Unit,
) {
    if (dividerHeight <= 0.dp) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dividerHeight)
            .clipToBounds()
            .then(
                if (hasAuxiliaryCollapse) {
                    Modifier.pointerInput(onDragEnd, applyDrag) {
                        detectVerticalDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        ) { _, dragAmount ->
                            applyDrag(-dragAmount)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = alphaFactor },
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PickResultAuxiliarySearchBlock(
    searchDividerHeight: Dp,
    searchGridHeight: Dp,
    alphaFactor: Float,
    panelSearchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    activeText: String,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    appSettings: AppSettings,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig, Boolean) -> Unit,
    onDragEnd: () -> Unit,
    applyDrag: (Float) -> Unit,
) {
    if (searchDividerHeight > 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(searchDividerHeight)
                .clipToBounds()
                .pointerInput(onDragEnd, applyDrag) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    ) { _, dragAmount ->
                        applyDrag(dragAmount)
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(12.dp + PickResultTextSearchGridTopSpacing))
            }
        }
    }

    if (searchGridHeight <= 0.dp) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(searchGridHeight)
            .graphicsLayer { alpha = alphaFactor }
            .clipToBounds(),
    ) {
        PickResultTextSearchGrid(
            engines = panelSearchEngines,
            query = activeText,
            columns = searchEngineGridColumns,
            rows = searchEngineGridRows,
            showLabels = searchEngineShowLabels,
            longPressEnabled = appSettings.launchPolicyLongPressEligible(),
            onEngineClick = onSearchEngineClick,
        )
    }
}

@Composable
private fun PickResultPanelTextSlot(
    useExpandedLayout: Boolean,
    compactBodyMaxHeight: Dp,
    text: String,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    textSizeSp: Float,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    showBackgroundOcrAction: Boolean,
    auxiliaryDragEnabled: Boolean,
    activeText: String,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onTextChange: (String) -> Unit,
    onBackgroundOcr: () -> Unit,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onShareText: (String) -> Unit,
    onCopy: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, Boolean) -> Unit,
    onZoomText: (Boolean) -> Unit,
    onDragDelta: (Float) -> Unit,
    onActionBarDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
    actionBarBottomPadding: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        PickResultInteractiveTextSection(
            text = text,
            textMode = textMode,
            onTextModeChange = onTextModeChange,
            onTextChange = onTextChange,
            modifier = if (useExpandedLayout) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth()
            },
            textSizeSp = textSizeSp,
            textSource = textSource,
            ocrAvailable = ocrAvailable,
            a11yAvailable = a11yAvailable,
            ocrLoading = ocrLoading,
            barcodeResults = barcodeResults,
            showingTranslation = showingTranslation,
            translateLoading = translateLoading,
            showBackgroundOcrAction = showBackgroundOcrAction,
            onBackgroundOcr = onBackgroundOcr,
            onTextSourceChange = onTextSourceChange,
            pinActionBarOutside = true,
            expandTextBlock = useExpandedLayout,
            auxiliaryDragEnabled = auxiliaryDragEnabled,
            bodyMaxHeight = if (useExpandedLayout) null else compactBodyMaxHeight,
            showSearch = false,
            onActiveTextChange = onActiveTextChange,
            onShare = onShareText,
            onCopy = onCopy,
            onTranslate = onTranslate,
            onRemoveSpaces = onRemoveSpaces,
            onZoomText = onZoomText,
            onDragDelta = onDragDelta,
            onActionBarDragDelta = onActionBarDragDelta,
            onDragEnd = onDragEnd,
            onPinToScreen = { onPinTextToScreen(activeText) },
            onStash = { onStashText(activeText) },
            actionBarBottomPadding = actionBarBottomPadding,
        )
    }
}

@Composable
private fun PickResultCollapsePanelColumn(
    controller: AuxiliaryCollapseController,
    panelContentHeight: Dp,
    overlayImeBottom: Dp,
    pickPanelAlpha: Float,
    imageSearchVisible: Boolean,
    dismissInteraction: MutableInteractionSource,
    cardInteraction: MutableInteractionSource,
    onDismiss: () -> Unit,
    isEditMode: Boolean,
    hasImageContent: Boolean,
    hasSearchGrid: Boolean,
    hasAuxiliaryCollapse: Boolean,
    showTextSection: Boolean,
    minImageSectionHeight: Dp,
    maxImageSectionHeight: Dp,
    textImageDividerBaseHeight: Dp,
    searchGridSectionPrefixHeight: Dp,
    expandedSearchGridContentHeight: Dp,
    idealTextBodyHeight: Dp,
    minTextBodyHeight: Dp,
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    panelImageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    onSaveScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
    onImageSearch: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    onImageIndexChange: (Int) -> Unit,
    onImageSectionExpandedChange: (Boolean) -> Unit,
    onDragEnd: () -> Unit,
    applyDrag: (Float) -> Unit,
    panelSearchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    activeText: String,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    appSettings: AppSettings,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig, Boolean) -> Unit,
    text: String?,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    textSizeSp: Float,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    isShareImageOcr: Boolean,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    onBackgroundOcr: () -> Unit,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onTextChange: (String) -> Unit,
    onShareText: (String) -> Unit,
    onCopy: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, Boolean) -> Unit,
    onZoomText: (Boolean) -> Unit,
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
) {
    val editModeProgress by animateFloatAsState(
        targetValue = if (isEditMode) 1f else 0f,
        animationSpec = tween(
            durationMillis = EDIT_MODE_ANIMATION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "editMode",
    )
    val normalLayoutFactor = 1f - editModeProgress

    val animatedCollapseProgress by animateFloatAsState(
        targetValue = controller.collapseProgress,
        animationSpec = if (controller.isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = 0.92f,
                stiffness = 380f,
            )
        },
        label = "collapse",
    )
    val expansionFraction = if (controller.isDragging) {
        1f - controller.collapseProgress
    } else {
        1f - animatedCollapseProgress
    }

    val searchPresence = if (hasSearchGrid) {
        (expansionFraction * normalLayoutFactor).coerceIn(0f, 1f)
    } else {
        0f
    }
    val actionBarBottomInset = lerp(
        PickResultTextActionBarBottomPaddingWhenAlone,
        0.dp,
        searchPresence,
    ).coerceAtLeast(0.dp)

    val stablePanelHeight = remember(
        panelContentHeight,
        hasSearchGrid,
        hasImageContent,
        minImageSectionHeight,
        maxImageSectionHeight,
        textImageDividerBaseHeight,
        searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight,
        idealTextBodyHeight,
        minTextBodyHeight,
    ) {
        if (!hasAuxiliaryCollapse) {
            null
        } else {
            computePickResultExpandedPanelOuterHeight(
                panelContentHeight = panelContentHeight,
                hasSearchGrid = hasSearchGrid,
                hasImageContent = hasImageContent,
                minImageSectionHeight = minImageSectionHeight,
                maxImageSectionHeight = maxImageSectionHeight,
                textImageDividerBaseHeight = textImageDividerBaseHeight,
                searchGridSectionPrefixHeight = searchGridSectionPrefixHeight,
                expandedSearchGridContentHeight = expandedSearchGridContentHeight,
                idealTextBodyHeight = idealTextBodyHeight,
                minTextBodyHeight = minTextBodyHeight,
            )
        }
    }

    val useWeightedTextLayout = isEditMode || hasAuxiliaryCollapse

    val fixedPanelHeight = when {
        isEditMode -> panelContentHeight + overlayImeBottom
        stablePanelHeight != null -> stablePanelHeight
        else -> null
    }

    val effectivePanelHeight = fixedPanelHeight ?: panelContentHeight
    val panelInnerHeight = effectivePanelHeight - PANEL_VERTICAL_PADDING

    val collapseHeights = computePickResultCollapseHeights(
        panelInnerHeight = panelInnerHeight,
        expansionFraction = expansionFraction,
        normalLayoutFactor = normalLayoutFactor,
        hasImageContent = hasImageContent,
        minImageSectionHeight = minImageSectionHeight,
        maxImageSectionHeight = maxImageSectionHeight,
        textImageDividerBaseHeight = textImageDividerBaseHeight,
        searchDividerBaseHeight = searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight = expandedSearchGridContentHeight,
        idealTextBodyHeight = idealTextBodyHeight,
        minTextBodyHeight = minTextBodyHeight,
        actionBarBottomPadding = actionBarBottomInset,
    )

    val applyDragState = rememberUpdatedState(applyDrag)
    val wrappedApplyDrag: (Float) -> Unit = remember {
        { delta -> applyDragState.value(delta) }
    }
    val onTextDragDelta = remember {
        { delta: Float -> wrappedApplyDrag(-delta) }
    }
    val onTextActionBarDragDelta = remember {
        { delta: Float -> wrappedApplyDrag(delta) }
    }
    val onDragEndState = rememberUpdatedState(onDragEnd)
    val onTextDragEnd = remember {
        { onDragEndState.value() }
    }

    var imageHeaderExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(controller.isDragging, expansionFraction) {
        if (!controller.isDragging) {
            imageHeaderExpanded = expansionFraction > 0.5f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = overlayImeBottom)
            .then(
                if (fixedPanelHeight != null) {
                    Modifier.height(fixedPanelHeight)
                } else {
                    Modifier
                        .wrapContentHeight()
                        .heightIn(max = panelContentHeight)
                },
            )
            .graphicsLayer { alpha = pickPanelAlpha }
            .pickResultBottomPanelCard()
            .then(
                if (imageSearchVisible) {
                    Modifier.clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = onDismiss,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {},
                    )
                },
            )
            .padding(top = PANEL_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PickResultAuxiliaryImageBlock(
            sectionHeight = collapseHeights.imageSectionHeight,
            alphaFactor = normalLayoutFactor,
            sectionExpanded = imageHeaderExpanded,
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
            onSectionExpandedChange = onImageSectionExpandedChange,
        )

        if (showTextSection) {
            PickResultTextImageDividerBlock(
                dividerHeight = collapseHeights.textImageDividerHeight,
                alphaFactor = normalLayoutFactor,
                hasAuxiliaryCollapse = hasAuxiliaryCollapse,
                onDragEnd = onDragEnd,
                applyDrag = wrappedApplyDrag,
            )
            Box(
                modifier = if (useWeightedTextLayout) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                },
            ) {
                PickResultPanelTextSlot(
                    useExpandedLayout = useWeightedTextLayout,
                    compactBodyMaxHeight = if (useWeightedTextLayout) {
                        minTextBodyHeight
                    } else {
                        collapseHeights.textBodyHeight
                    },
                    text = text.orEmpty(),
                    textMode = textMode,
                    textSource = textSource,
                    textSizeSp = textSizeSp,
                    ocrAvailable = ocrAvailable,
                    a11yAvailable = a11yAvailable,
                    ocrLoading = ocrLoading,
                    barcodeResults = barcodeResults,
                    showingTranslation = showingTranslation,
                    translateLoading = translateLoading,
                    showBackgroundOcrAction = isShareImageOcr && ocrLoading,
                    auxiliaryDragEnabled = hasAuxiliaryCollapse && !isEditMode,
                    activeText = activeText,
                    onTextModeChange = onTextModeChange,
                    onTextChange = onTextChange,
                    onBackgroundOcr = onBackgroundOcr,
                    onTextSourceChange = onTextSourceChange,
                    onActiveTextChange = onActiveTextChange,
                    onShareText = onShareText,
                    onCopy = onCopy,
                    onTranslate = onTranslate,
                    onRemoveSpaces = onRemoveSpaces,
                    onZoomText = onZoomText,
                    onDragDelta = onTextDragDelta,
                    onActionBarDragDelta = onTextActionBarDragDelta,
                    onDragEnd = onTextDragEnd,
                    onPinTextToScreen = onPinTextToScreen,
                    onStashText = onStashText,
                    actionBarBottomPadding = actionBarBottomInset,
                )
            }
        }

        if (hasSearchGrid) {
            PickResultAuxiliarySearchBlock(
                searchDividerHeight = collapseHeights.searchDividerHeight,
                searchGridHeight = collapseHeights.searchGridHeight,
                alphaFactor = normalLayoutFactor,
                panelSearchEngines = panelSearchEngines,
                activeText = activeText,
                searchEngineGridColumns = searchEngineGridColumns,
                searchEngineGridRows = searchEngineGridRows,
                searchEngineShowLabels = searchEngineShowLabels,
                appSettings = appSettings,
                onSearchEngineClick = onSearchEngineClick,
                onDragEnd = onDragEnd,
                applyDrag = wrappedApplyDrag,
            )
        }
    }
}

/**
 * Bottom-anchored overlay pick-result panel after float-ball text pick / regional screenshot.
 */
object FloatBallPickResultPanel {
    private const val TAG = "FloatBallPickPanel"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val historyOcrScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var historyOcrJob: Job? = null
    private var historyOcrRequestId = 0

    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var appContext: Context? = null

    private var textState: MutableState<String?>? = null
    private var screenshotState: MutableState<Bitmap?>? = null
    private var panelImagesState: MutableState<List<Bitmap>>? = null
    private var currentImageIndexState: MutableState<Int>? = null
    private var contentOriginState: MutableState<PickResultContentOrigin>? = null
    private var ownsPanelImagesState: MutableState<Boolean>? = null
    private var ocrTextsByImageIndexState: MutableState<Map<Int, String>>? = null
    private var activeTextState: MutableState<String>? = null
    private var textModeState: MutableState<PickResultTextMode>? = null
    private var a11yTextState: MutableState<String?>? = null
    private var ocrTextState: MutableState<String?>? = null
    private var textSourceState: MutableState<PickResultTextSource>? = null
    private var ocrAvailableState: MutableState<Boolean>? = null
    private var ocrLoadingState: MutableState<Boolean>? = null
    private var a11ySourceEnabledState: MutableState<Boolean>? = null
    private var isShareImageOcrState: MutableState<Boolean>? = null
    private var screenRectState: MutableState<Rect?>? = null
    private var layoutMetaState: MutableState<ScreenshotLayoutMeta?>? = null
    private var barcodeResultsState: MutableState<List<BarcodeScanResult>>? = null
    private var showingTranslationState: MutableState<Boolean>? = null
    private var translateLoadingState: MutableState<Boolean>? = null
    private var ocrSwitchOnComplete = false
    private var captureSuppressed = false
    private var pickPanelVisible = false
    private var panelVisibilityState: androidx.compose.animation.core.MutableTransitionState<Boolean>? = null
    private var panelShowTokenState: androidx.compose.runtime.MutableIntState? = null
    private var settingsState: MutableState<AppSettings>? = null
    private var panelRevealedState: MutableState<Boolean>? = null
    private var panelRevealGeneration = 0

    val isShowing: Boolean get() = pickPanelVisible

    private fun readPanelSettings(context: Context): AppSettings =
        OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: AppSettings()

    private fun preparePanelReveal(context: Context, onReady: () -> Unit) {
        val settings = readPanelSettings(context)
        settingsState?.value = settings
        val engines = SearchEngineStore.textPickPanelEngines(settings.searchEngines)
        if (engines.isEmpty()) {
            onReady()
            return
        }
        val hostContext = appContext ?: context.applicationContext
        Thread {
            preloadPickResultSearchEngineIcons(hostContext, engines)
            mainHandler.post(onReady)
        }.start()
    }

    private fun preparePanelWhileLoading(context: Context) {
        panelVisibilityState?.targetState = false
        panelRevealedState?.value = false
        preparePanelReveal(context) { }
    }

    private fun revealPanelAnimated(context: Context) {
        panelRevealGeneration++
        val generation = panelRevealGeneration
        panelVisibilityState?.targetState = true
        panelRevealedState?.value = false
        val view = composeView ?: return
        view.post {
            if (generation != panelRevealGeneration) return@post
            view.post {
                if (generation != panelRevealGeneration) return@post
                preparePanelReveal(context) {
                    if (generation != panelRevealGeneration) return@preparePanelReveal
                    panelRevealedState?.value = true
                }
            }
        }
    }

    fun warmUp(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { warmUp(context) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        preparePanelReveal(hostContext) { }
    }

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (composeView == null || captureSuppressed) return
        captureSuppressed = true
        composeView?.visibility = View.GONE
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!captureSuppressed) return
        captureSuppressed = false
        if (pickPanelVisible) {
            composeView?.visibility = View.VISIBLE
        }
    }

    private fun updateWindowFocusableForMode(mode: PickResultTextMode) {
        updateWindowFocusable(focusable = true)
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        @Suppress("DEPRECATION")
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun isPanelImeVisible(): Boolean {
        val view = composeView ?: return false
        val insets = ViewCompat.getRootWindowInsets(view) ?: return false
        return insets.isVisible(WindowInsetsCompat.Type.ime())
    }

    private fun hidePanelKeyboard() {
        val view = composeView ?: return
        val imm = appContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
        view.post { view.requestFocus() }
    }

    private fun exitEditModeFromBack() {
        hidePanelKeyboard()
        textModeState?.value = PickResultTextMode.WORD_TAP
    }

    private fun handlePanelBack() {
        if (textModeState?.value == PickResultTextMode.EDIT) {
            if (isPanelImeVisible()) {
                hidePanelKeyboard()
                return
            }
            exitEditModeFromBack()
            return
        }
        when {
            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
            else -> dismiss()
        }
    }

    internal fun requestPanelFocus() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestPanelFocus() }
            return
        }
        composeView?.requestFocus()
    }

    fun showResult(
        context: Context,
        anchorX: Float = 0f,
        anchorY: Float = 0f,
        result: FloatBallPickResult,
        initialTextMode: PickResultTextMode? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showResult(context, anchorX, anchorY, result, initialTextMode) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        captureSuppressed = false
        pickPanelVisible = true
        composeView?.visibility = View.VISIBLE
        FloatBallOverlay.bringChromeAbovePanels()
        composeView?.requestFocus()
        a11yTextState?.value = result.a11yText
        ocrTextState?.value = result.ocrText
        textSourceState?.value = result.activeSource
        ocrAvailableState?.value = result.ocrAvailable
        ocrLoadingState?.value = result.ocrPending
        a11ySourceEnabledState?.value = result.a11ySourceEnabled
        isShareImageOcrState?.value = result.isShareImageOcr
        ocrSwitchOnComplete = result.ocrPreferSwitchOnComplete
        val resolvedImages = result.resolvedImages()
        val safeImageIndex = result.initialImageIndex.coerceIn(0, (resolvedImages.size - 1).coerceAtLeast(0))
        panelImagesState?.value = resolvedImages
        currentImageIndexState?.value = safeImageIndex
        contentOriginState?.value = result.contentOrigin
        ownsPanelImagesState?.value = result.ownsImages
        ocrTextsByImageIndexState?.value = result.ocrText
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf(safeImageIndex to it) }
            ?: emptyMap()
        textState?.value = result.text
        activeTextState?.value = result.text.orEmpty()
        screenshotState?.value?.takeIf { it !in resolvedImages }?.recycle()
        screenshotState?.value = resolvedImages.getOrNull(safeImageIndex) ?: result.screenshot
        screenRectState?.value = result.screenRect?.let { Rect(it) }
        layoutMetaState?.value = result.layoutMeta
        barcodeResultsState?.value = result.barcodeResults
        clearTranslateState()
        textModeState?.value = initialTextMode ?: defaultTextModeFor(result.text)
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
        panelShowTokenState?.let { it.intValue++ }
        if (panelRevealedState?.value == true) {
            panelVisibilityState?.targetState = true
        } else {
            revealPanelAnimated(hostContext)
        }
        if (result.text.isNullOrBlank() && resolvedImages.isEmpty()) {
            Toast.makeText(hostContext, R.string.float_ball_text_not_found, Toast.LENGTH_SHORT).show()
            dismiss()
        }
        PickPerf.mark("panel_showResult_done", "source=${result.activeSource}")
    }

    fun showLoading(
        context: Context,
        anchorX: Float = 0f,
        anchorY: Float = 0f,
        loadingSource: PickResultTextSource = PickResultTextSource.OCR,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showLoading(context, anchorX, anchorY, loadingSource) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        captureSuppressed = false
        pickPanelVisible = true
        composeView?.visibility = View.GONE
        a11yTextState?.value = null
        ocrTextState?.value = null
        textSourceState?.value = loadingSource
        isShareImageOcrState?.value = false
        textState?.value = null
        activeTextState?.value = ""
        screenshotState?.value?.let { current ->
            val owned = panelImagesState?.value.orEmpty()
            if (current !in owned) {
                current.recycle()
            }
        }
        screenshotState?.value = null
        recycleOwnedPanelImages()
        historyOcrJob?.cancel()
        historyOcrRequestId++
        ocrTextsByImageIndexState?.value = emptyMap()
        contentOriginState?.value = PickResultContentOrigin.SCREEN_PICK
        currentImageIndexState?.value = 0
        screenRectState?.value = null
        layoutMetaState?.value = null
        barcodeResultsState?.value = emptyList()
        clearTranslateState()
        textModeState?.value = PickResultTextMode.WORD_TAP
        when (loadingSource) {
            PickResultTextSource.A11Y -> {
                a11ySourceEnabledState?.value = true
                ocrAvailableState?.value = false
                ocrLoadingState?.value = false
                ocrSwitchOnComplete = false
            }
            PickResultTextSource.OCR -> {
                a11ySourceEnabledState?.value = false
                ocrAvailableState?.value = false
                ocrLoadingState?.value = true
                ocrSwitchOnComplete = true
            }
            PickResultTextSource.BARCODE -> {
                a11ySourceEnabledState?.value = false
                ocrAvailableState?.value = false
                ocrLoadingState?.value = false
                ocrSwitchOnComplete = false
            }
        }
        updateWindowFocusableForMode(PickResultTextMode.WORD_TAP)
        preparePanelWhileLoading(hostContext)
        PickPerf.mark("panel_showLoading", "source=$loadingSource")
    }

    fun updateOcrText(
        ocrText: String,
        switchToOcr: Boolean = ocrSwitchOnComplete,
        initialTextMode: PickResultTextMode? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateOcrText(ocrText, switchToOcr, initialTextMode) }
            return
        }
        ocrLoadingState?.value = false
        ocrTextState?.value = ocrText
        ocrAvailableState?.value = true
        if (switchToOcr || textSourceState?.value == PickResultTextSource.OCR) {
            clearTranslateState()
            textSourceState?.value = PickResultTextSource.OCR
            textState?.value = ocrText
            activeTextState?.value = ocrText
        }
        initialTextMode?.let { mode ->
            textModeState?.value = mode
            updateWindowFocusableForMode(mode)
        }
        PickPerf.mark("panel_ocr_updated", "len=${ocrText.length}")
    }

    fun updateBarcodeResults(barcodeResults: List<BarcodeScanResult>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateBarcodeResults(barcodeResults) }
            return
        }
        if (!isShowing || barcodeResults.isEmpty()) return
        barcodeResultsState?.value = barcodeResults
        PickPerf.mark("panel_barcode_updated", "count=${barcodeResults.size}")
    }

    fun isShowingTranslation(): Boolean = showingTranslationState?.value == true

    fun showTranslateLoading() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateLoading() }
            return
        }
        if (!isShowing) return
        showingTranslationState?.value = false
        translateLoadingState?.value = true
    }

    fun showTranslateResult(translatedText: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateResult(translatedText) }
            return
        }
        if (!isShowing) return
        translateLoadingState?.value = false
        showingTranslationState?.value = true
        textState?.value = translatedText
        activeTextState?.value = translatedText
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    fun showTranslateError(context: Context, message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateError(context, message) }
            return
        }
        if (!isShowing) return
        translateLoadingState?.value = false
        showingTranslationState?.value = false
        val hostContext = appContext ?: context.applicationContext
        Toast.makeText(
            hostContext,
            translateErrorMessage(hostContext, message),
            Toast.LENGTH_SHORT,
        ).show()
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    fun restoreFromTranslation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreFromTranslation() }
            return
        }
        if (!isShowing) return
        showingTranslationState?.value = false
        translateLoadingState?.value = false
        applyTextForCurrentSource()
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    private fun applyTextForCurrentSource() {
        val source = textSourceState?.value ?: PickResultTextSource.A11Y
        val text = textForSource(
            source = source,
            a11yText = a11yTextState?.value,
            ocrText = ocrTextState?.value,
            barcodeResults = barcodeResultsState?.value.orEmpty(),
        )
        textState?.value = text
        activeTextState?.value = text
    }

    private fun textForSource(
        source: PickResultTextSource,
        a11yText: String?,
        ocrText: String?,
        barcodeResults: List<BarcodeScanResult>,
    ): String = when (source) {
        PickResultTextSource.A11Y -> a11yText.orEmpty()
        PickResultTextSource.OCR -> ocrText.orEmpty()
        PickResultTextSource.BARCODE -> barcodeResults.joinDisplayText()
    }

    private fun clearTranslateState() {
        showingTranslationState?.value = false
        translateLoadingState?.value = false
    }

    fun finishOcrPending() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { finishOcrPending() }
            return
        }
        ocrLoadingState?.value = false
        PickPerf.mark("panel_ocr_pending_done", "empty=true")
    }

    fun requestHistoryImageOcr(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestHistoryImageOcr(context) }
            return
        }
        requestOcrForImageIndex(context, currentImageIndexState?.value ?: 0)
    }

    internal fun setCurrentImageIndex(context: Context, index: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setCurrentImageIndex(context, index) }
            return
        }
        val images = panelImagesState?.value.orEmpty()
        if (images.isEmpty()) return
        val safeIndex = index.coerceIn(0, images.lastIndex)
        if (currentImageIndexState?.value == safeIndex && screenshotState?.value === images[safeIndex]) {
            return
        }
        currentImageIndexState?.value = safeIndex
        screenshotState?.value = images[safeIndex]
        if (contentOriginState?.value == PickResultContentOrigin.STASH_CLIPBOARD &&
            textSourceState?.value == PickResultTextSource.OCR
        ) {
            applyHistoryOcrForIndex(context, safeIndex)
        }
    }

    private fun requestOcrForImageIndex(context: Context, index: Int) {
        if (contentOriginState?.value != PickResultContentOrigin.STASH_CLIPBOARD) return
        applyHistoryOcrForIndex(context, index)
    }

    private fun applyHistoryOcrForIndex(context: Context, index: Int) {
        val images = panelImagesState?.value.orEmpty()
        val bitmap = images.getOrNull(index) ?: return
        ocrTextsByImageIndexState?.value?.get(index)?.let { cached ->
            ocrLoadingState?.value = false
            ocrAvailableState?.value = true
            ocrTextState?.value = cached
            if (textSourceState?.value == PickResultTextSource.OCR) {
                clearTranslateState()
                textState?.value = cached
                activeTextState?.value = cached
            }
            return
        }
        val appContext = appContext ?: context.applicationContext
        val settings = OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return
        val modelId = settings.floatBallOcrModelId
        if (modelId.isBlank() || !settings.floatBallOcrFallbackEnabled) return
        if (OcrDependencyAccess.modelRepository(appContext)?.isInstalled(modelId) != true) return

        val requestId = ++historyOcrRequestId
        historyOcrJob?.cancel()
        ocrLoadingState?.value = true
        historyOcrJob = historyOcrScope.launch(Dispatchers.IO) {
            val recognized = runCatching {
                RegionalScreenshotOcr.recognizeBitmapPublic(appContext, modelId, bitmap)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                if (requestId != historyOcrRequestId) return@withContext
                ocrLoadingState?.value = false
                if (!recognized.isNullOrBlank()) {
                    ocrTextsByImageIndexState?.value =
                        (ocrTextsByImageIndexState?.value ?: emptyMap()) + (index to recognized)
                    ocrAvailableState?.value = true
                    ocrTextState?.value = recognized
                    if (textSourceState?.value == PickResultTextSource.OCR) {
                        clearTranslateState()
                        textState?.value = recognized
                        activeTextState?.value = recognized
                    }
                }
            }
        }
    }

    private fun recycleOwnedPanelImages() {
        if (ownsPanelImagesState?.value != true) return
        val images = panelImagesState?.value.orEmpty()
        images.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        panelImagesState?.value = emptyList()
        ownsPanelImagesState?.value = false
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (!pickPanelVisible) return
        pickPanelVisible = false
        panelRevealGeneration++
        panelRevealedState?.value = false

        val currentOwner = owner
        val view = composeView
        val wm = windowManager
        if (currentOwner != null && view != null && wm != null) {
            currentOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(PANEL_EXIT_ANIMATION_MS.toLong())
                if (pickPanelVisible) return@launch // Abort if re-shown

                panelVisibilityState?.targetState = false
                if (pickPanelVisible) return@launch // Abort if re-shown

                screenshotState?.value?.let { current ->
                    val owned = panelImagesState?.value.orEmpty()
                    if (current !in owned) {
                        current.recycle()
                    }
                }
                screenshotState?.value = null
                recycleOwnedPanelImages()
                historyOcrJob?.cancel()
                historyOcrRequestId++
                ocrTextsByImageIndexState?.value = emptyMap()
                contentOriginState?.value = PickResultContentOrigin.SCREEN_PICK
                view.visibility = View.GONE
                if (FloatBallImageSearchPanel.isShowing) {
                    FloatBallImageSearchPanel.dismiss()
                }
                clearTranslateState()
                updateWindowFocusable(focusable = false)
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
            runCatching { wm.removeView(view) }
            screenOffReceiver?.let { receiver ->
                appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
            }
            view.post { currentOwner.destroy() }

            backHandler?.detach()
            backHandler = null
            owner = null
            composeView = null
            layoutParams = null
            windowManager = null
            textState = null
            recycleOwnedPanelImages()
            screenshotState = null
            panelImagesState = null
            currentImageIndexState = null
            contentOriginState = null
            ownsPanelImagesState = null
            ocrTextsByImageIndexState = null
            historyOcrJob?.cancel()
            historyOcrRequestId = 0
            activeTextState = null
            textModeState = null
            a11yTextState = null
            ocrTextState = null
            textSourceState = null
            ocrAvailableState = null
            ocrLoadingState = null
            a11ySourceEnabledState = null
            isShareImageOcrState = null
            screenRectState = null
            layoutMetaState = null
            barcodeResultsState = null
            showingTranslationState = null
            translateLoadingState = null
            panelVisibilityState = null
            panelShowTokenState = null
            settingsState = null
            panelRevealedState = null
            ocrSwitchOnComplete = false
            screenOffReceiver = null
            appContext = null
            pickPanelVisible = false
        }
    }

    private fun updateWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return

        val textHolder = mutableStateOf<String?>(null)
        val screenshotHolder = mutableStateOf<Bitmap?>(null)
        val panelImagesHolder = mutableStateOf<List<Bitmap>>(emptyList())
        val currentImageIndexHolder = mutableIntStateOf(0)
        val contentOriginHolder = mutableStateOf(PickResultContentOrigin.SCREEN_PICK)
        val ownsPanelImagesHolder = mutableStateOf(false)
        val ocrTextsByImageIndexHolder = mutableStateOf<Map<Int, String>>(emptyMap())
        val activeTextHolder = mutableStateOf("")
        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)
        val a11yTextHolder = mutableStateOf<String?>(null)
        val ocrTextHolder = mutableStateOf<String?>(null)
        val textSourceHolder = mutableStateOf(PickResultTextSource.A11Y)
        val ocrAvailableHolder = mutableStateOf(false)
        val ocrLoadingHolder = mutableStateOf(false)
        val a11ySourceEnabledHolder = mutableStateOf(true)
        val isShareImageOcrHolder = mutableStateOf(false)
        val screenRectHolder = mutableStateOf<Rect?>(null)
        val layoutMetaHolder = mutableStateOf<ScreenshotLayoutMeta?>(null)
        val barcodeResultsHolder = mutableStateOf<List<BarcodeScanResult>>(emptyList())
        val showingTranslationHolder = mutableStateOf(false)
        val translateLoadingHolder = mutableStateOf(false)
        textState = textHolder
        screenshotState = screenshotHolder
        panelImagesState = panelImagesHolder
        currentImageIndexState = currentImageIndexHolder
        contentOriginState = contentOriginHolder
        ownsPanelImagesState = ownsPanelImagesHolder
        ocrTextsByImageIndexState = ocrTextsByImageIndexHolder
        activeTextState = activeTextHolder
        textModeState = textModeHolder
        a11yTextState = a11yTextHolder
        ocrTextState = ocrTextHolder
        textSourceState = textSourceHolder
        ocrAvailableState = ocrAvailableHolder
        ocrLoadingState = ocrLoadingHolder
        a11ySourceEnabledState = a11ySourceEnabledHolder
        isShareImageOcrState = isShareImageOcrHolder
        screenRectState = screenRectHolder
        layoutMetaState = layoutMetaHolder
        barcodeResultsState = barcodeResultsHolder
        showingTranslationState = showingTranslationHolder
        translateLoadingState = translateLoadingHolder
        val initialSettings = readPanelSettings(context)
        val settingsHolder = mutableStateOf(initialSettings)
        settingsState = settingsHolder
        val panelRevealedHolder = mutableStateOf(false)
        panelRevealedState = panelRevealedHolder

        panelVisibilityState = androidx.compose.animation.core.MutableTransitionState(false)
        val panelShowTokenHolder = mutableIntStateOf(0)
        panelShowTokenState = panelShowTokenHolder
        
        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val visibleState = panelVisibilityState!!
                val panelShowToken = panelShowTokenHolder.intValue
                val panelRevealed by panelRevealedHolder
                if (!visibleState.currentState && !visibleState.targetState) return@setContent
                val text by textHolder
                val screenshot by screenshotHolder
                val panelImages by panelImagesHolder
                val currentImageIndex by currentImageIndexHolder
                val contentOrigin by contentOriginHolder
                val activeText by activeTextHolder
                val textMode by textModeHolder
                val ocrText by ocrTextHolder
                val textSource by textSourceHolder
                val ocrAvailable by ocrAvailableHolder
                val ocrLoading by ocrLoadingHolder
                val a11ySourceEnabled by a11ySourceEnabledHolder
                val isShareImageOcr by isShareImageOcrHolder
                val screenRect by screenRectHolder
                val layoutMeta by layoutMetaHolder
                val barcodeResults by barcodeResultsHolder
                val showingTranslation by showingTranslationHolder
                val translateLoading by translateLoadingHolder
                val settings by settingsHolder
                LaunchedEffect(overlayContext) {
                    val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)
                        ?.settingsRepository
                        ?.settings
                        ?: return@LaunchedEffect
                    flow.collect { settingsHolder.value = it }
                }
                LaunchedEffect(text) {
                    if (text != null) {
                        activeTextHolder.value = text.orEmpty()
                    }
                }
                FloatBallPickResultContent(
                    panelShowToken = panelShowToken,
                    panelRevealed = panelRevealed,
                    text = text,
                    screenshot = screenshot,
                    panelImages = panelImages,
                    currentImageIndex = currentImageIndex,
                    contentOrigin = contentOrigin,
                    activeText = activeText,
                    textMode = textMode,
                    textSource = textSource,
                    ocrAvailable = ocrAvailable,
                    a11yAvailable = a11ySourceEnabled,
                    ocrLoading = ocrLoading,
                    isShareImageOcr = isShareImageOcr,
                    barcodeResults = barcodeResults,
                    showingTranslation = showingTranslation,
                    translateLoading = translateLoading,
                    onBackgroundOcr = {
                        ShareImageOcrCoordinator.moveToBackground(overlayContext)
                    },
                    imageSearchPickPanelTransparency = settings.floatBallImageSearchPickPanelTransparency,
                    textSizeSp = settings.floatBallPickTextSizeSp,
                    searchEngines = settings.searchEngines,
                    searchEngineGridColumns = settings.searchEngineGridColumns,
                    searchEngineGridRows = settings.searchEngineGridRows,
                    searchEngineShowLabels = settings.searchEngineShowLabels,
                    appSettings = settings,
                    onImageClick = {
                        screenshot?.let {
                            val opened = FloatBallTextPick.viewScreenshot(
                                appContext ?: overlayContext,
                                it,
                                settings.defaultImageViewerPackage,
                            )
                            if (opened) {
                                dismiss()
                            }
                        }
                    },
                    onImageIndexChange = { index ->
                        setCurrentImageIndex(overlayContext, index)
                    },
                    onTextSourceChange = { source ->
                        if (source == PickResultTextSource.A11Y && !a11ySourceEnabledHolder.value) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.OCR && !ocrAvailable) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.BARCODE && barcodeResultsHolder.value.isEmpty()) {
                            return@FloatBallPickResultContent
                        }
                        clearTranslateState()
                        textSourceHolder.value = source
                        val switched = textForSource(
                            source = source,
                            a11yText = a11yTextHolder.value,
                            ocrText = ocrTextHolder.value,
                            barcodeResults = barcodeResultsHolder.value,
                        )
                        textHolder.value = switched
                        activeTextHolder.value = switched
                        if (source == PickResultTextSource.OCR &&
                            contentOriginHolder.value == PickResultContentOrigin.STASH_CLIPBOARD
                        ) {
                            requestOcrForImageIndex(overlayContext, currentImageIndexHolder.intValue)
                        }
                    },
                    onActiveTextChange = { activeTextHolder.value = it },
                    onTextModeChange = { mode ->
                        val previousMode = textModeHolder.value
                        textModeHolder.value = mode
                        updateWindowFocusableForMode(mode)
                        if (previousMode == PickResultTextMode.EDIT && mode != PickResultTextMode.EDIT) {
                            requestPanelFocus()
                        }
                    },
                    onDismiss = {
                        when {
                            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
                            else -> dismiss()
                        }
                    },
                    onTextChange = { textHolder.value = it },
                    onCopy = { value ->
                        FloatBallTextPick.copyText(context, value)
                        Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                    },
                    onShareText = { FloatBallTextPick.shareText(context, it) },
                    onTranslate = { FloatBallTranslateCoordinator.translate(context, it) },
                    onRemoveSpaces = { value, removeAll ->
                        textHolder.value = if (removeAll) {
                            value.replace(Regex("\\s+"), "")
                        } else {
                            value.trim()
                        }
                    },
                    onSaveScreenshot = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val saved = FloatBallTextPick.saveScreenshot(context, bitmap)
                        Toast.makeText(
                            context,
                            if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onShareScreenshot = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        FloatBallTextPick.shareScreenshot(context, bitmap)
                    },
                    onImageShareEngineClick = { engine ->
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val launched = SearchEngineLauncher.launchImageShare(context, engine, bitmap)
                        if (launched) {
                            dismiss()
                        }
                    },
                    onImageSearch = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        FloatBallImageSearchPanel.show(context, bitmap)
                    },
                    onSearchEngineClick = { engine, longPressTriggered ->
                        val launched = SearchEngineLauncher.launch(
                            context,
                            engine,
                            activeTextHolder.value,
                            settings,
                            longPressTriggered,
                        )
                        if (launched) {
                            dismiss()
                        }
                    },
                    onPinTextToScreen = { value ->
                        StashCoordinator.pinTextToScreen(overlayContext, value)
                        dismiss()
                    },
                    onStashText = { value ->
                        StashCoordinator.addText(value) { success ->
                            if (success) {
                                Toast.makeText(overlayContext, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(overlayContext)
                                dismiss()
                            } else {
                                Toast.makeText(overlayContext, R.string.stash_save_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPinImageToScreen = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val meta = layoutMeta ?: buildScreenshotLayoutMeta(
                            bitmap = bitmap,
                            screenWidthPx = overlayContext.resources.displayMetrics.widthPixels,
                            screenHeightPx = overlayContext.resources.displayMetrics.heightPixels,
                        )
                        StashCoordinator.pinImageToScreen(
                            overlayContext,
                            bitmap,
                            screenRect,
                            meta,
                        )
                        dismiss()
                    },
                    onStashImage = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val metrics = overlayContext.resources.displayMetrics
                        val (displayW, displayH) = resolvePinImageDisplaySizePx(
                            bitmap = bitmap,
                            screenRect = screenRect,
                            layoutMeta = layoutMeta ?: buildScreenshotLayoutMeta(
                                bitmap = bitmap,
                                screenWidthPx = metrics.widthPixels,
                                screenHeightPx = metrics.heightPixels,
                            ),
                            screenWidthPx = metrics.widthPixels,
                            screenHeightPx = metrics.heightPixels,
                        )
                        StashCoordinator.addImage(
                            bitmap = bitmap,
                            pinDisplayWidthPx = displayW,
                            pinDisplayHeightPx = displayH,
                        ) { success ->
                            if (success) {
                                Toast.makeText(overlayContext, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(overlayContext)
                                dismiss()
                            } else {
                                Toast.makeText(overlayContext, R.string.stash_save_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    screenRect = screenRect,
                    layoutMeta = layoutMeta,
                )
            }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                dialogOwner.destroy()
                return
            }
        val params = buildLayoutParams(context)
        val added = runCatching { wm.addView(compose, params) }.isSuccess
        if (!added) {
            dialogOwner.destroy()
            Log.e(TAG, "failed to add pick result panel")
            return
        }

        windowManager = wm
        composeView = compose
        owner = dialogOwner
        layoutParams = params
        appContext = context
        backHandler = OverlayViewBackHandler(compose, ::handlePanelBack).also { it.attach() }
        registerScreenOffReceiver(context)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun defaultTextModeFor(@Suppress("UNUSED_PARAMETER") text: String?): PickResultTextMode {
        return PickResultTextMode.WORD_TAP
    }
}

@Composable
private fun FloatBallPickResultContent(
    panelShowToken: Int,
    panelRevealed: Boolean,
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
    layoutMeta: ScreenshotLayoutMeta?,
) {
    val hasTextSection = ocrLoading || !text.isNullOrBlank() || screenshot != null ||
        panelImages.isNotEmpty() || ocrAvailable || barcodeResults.isNotEmpty() ||
        contentOrigin == PickResultContentOrigin.STASH_CLIPBOARD
    val isEditMode = textMode == PickResultTextMode.EDIT
    val showTextSection = hasTextSection || isEditMode
    val hasImageContent = panelImages.isNotEmpty() || screenshot != null
    val imageSearchVisible by FloatBallImageSearchPanel.panelVisible
    val pickPanelAlpha = if (imageSearchVisible) {
        1f - imageSearchPickPanelTransparency.coerceIn(0f, 1f)
    } else {
        1f
    }

    val density = LocalDensity.current
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val maxPanelHeight = remember(displayMetrics, density) {
        with(density) { displayMetrics.heightPixels.toDp() } * PANEL_MAX_HEIGHT_FRACTION
    }
    val panelMaxImageHeight = pickResultImageMaxHeightDp()
    val imageContentWidth = pickResultImageContentWidth()

    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val panelSearchEngines = remember(searchEngines) {
        SearchEngineStore.textPickPanelEngines(searchEngines)
    }
    val hasSearchGrid = showTextSection && panelSearchEngines.isNotEmpty()
    val searchGridReservedHeight = if (hasSearchGrid) {
        pickResultSearchGridReservedHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            searchEngineGridColumns,
        )
    } else {
        0.dp
    }
    val textImageDividerBaseHeight = if (hasImageContent && showTextSection) {
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
        pickResultMinTextBodyAllocatedHeight(textSizeSp = textSizeSp, lines = 6)
    } else {
        0.dp
    }
    val panelVerticalPadding = PANEL_VERTICAL_PADDING * 2
    val imageSectionFixedChrome = if (hasImageContent) {
        pickResultImageSectionReservedHeight(0.dp, true) // Always use true for stable max height calculation
    } else {
        0.dp
    }
    val reservedForTextAndChrome = panelVerticalPadding +
        (if (showTextSection) {
            textSectionChromeHeight + textImageDividerBaseHeight + minTextBodyHeight
        } else {
            0.dp
        }) +
        searchGridReservedHeight
    val affordableImageMaxHeight = if (hasImageContent) {
        (maxPanelHeight - reservedForTextAndChrome - imageSectionFixedChrome)
            .coerceAtLeast(PANEL_MIN_IMAGE_HEIGHT)
    } else {
        panelMaxImageHeight
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
            screenHeightPx = displayMetrics.heightPixels,
        )
    } ?: PickResultImageDisplaySize(0.dp, 0.dp)

    val maxImageSectionHeight = if (hasImageContent) {
        pickResultImageSectionReservedHeight(panelImageDisplaySize.height, true)
    } else {
        0.dp
    }
    val minImageSectionHeight = if (hasImageContent) {
        pickResultImageSectionReservedHeight(0.dp, false)
    } else {
        0.dp
    }
    val searchGridSectionPrefixHeight = if (showTextSection || hasImageContent) {
        12.dp + 1.dp + 12.dp + PickResultTextSearchGridTopSpacing
    } else {
        0.dp
    }
    val expandedSearchGridContentHeight = if (hasSearchGrid) {
        searchGridContentHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            searchEngineGridColumns,
        ) + 4.dp
    } else {
        0.dp
    }
    val maxSearchSectionHeight = if (hasSearchGrid) {
        searchGridSectionPrefixHeight + expandedSearchGridContentHeight
    } else {
        0.dp
    }
    val totalCollapsiblePx = remember(
        maxImageSectionHeight,
        minImageSectionHeight,
        maxSearchSectionHeight,
        density,
    ) {
        with(density) {
            (maxImageSectionHeight - minImageSectionHeight + maxSearchSectionHeight)
                .toPx()
                .coerceAtLeast(1f)
        }
    }

    val isImageVisible = remember { mutableStateOf(true) }
    val isSearchGridVisible = remember { mutableStateOf(true) }
    val scopedCollapseController = remember(totalCollapsiblePx) {
        AuxiliaryCollapseController(totalCollapsiblePx)
    }

    LaunchedEffect(panelShowToken) {
        isImageVisible.value = true
        isSearchGridVisible.value = true
        scopedCollapseController.resetToExpanded()
    }

    LaunchedEffect(isImageVisible.value, isSearchGridVisible.value, hasSearchGrid) {
        if (scopedCollapseController.isDragging) return@LaunchedEffect
        val expanded = isImageVisible.value && (!hasSearchGrid || isSearchGridVisible.value)
        scopedCollapseController.setExpanded(expanded)
    }

    fun applyAuxiliaryDrag(deltaPx: Float) {
        scopedCollapseController.applyDrag(deltaPx)
    }

    fun endAuxiliaryDrag() {
        scopedCollapseController.endDrag { expanded ->
            isImageVisible.value = expanded
            isSearchGridVisible.value = expanded
        }
    }

    val overlayImeBottom = rememberOverlayImeBottomHeight()
    val hasAuxiliaryCollapse = hasImageContent || hasSearchGrid
    val panelContentHeight = maxPanelHeight - overlayImeBottom

    val panelSlideDistance = remember(
        panelContentHeight,
        hasSearchGrid,
        hasImageContent,
        minImageSectionHeight,
        maxImageSectionHeight,
        textImageDividerBaseHeight,
        searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight,
        idealTextBodyHeight,
        minTextBodyHeight,
        showTextSection,
        maxPanelHeight,
    ) {
        if (showTextSection || hasImageContent) {
            computePickResultExpandedPanelOuterHeight(
                panelContentHeight = panelContentHeight,
                hasSearchGrid = hasSearchGrid,
                hasImageContent = hasImageContent,
                minImageSectionHeight = minImageSectionHeight,
                maxImageSectionHeight = maxImageSectionHeight,
                textImageDividerBaseHeight = textImageDividerBaseHeight,
                searchGridSectionPrefixHeight = searchGridSectionPrefixHeight,
                expandedSearchGridContentHeight = expandedSearchGridContentHeight,
                idealTextBodyHeight = idealTextBodyHeight,
                minTextBodyHeight = minTextBodyHeight,
            )
        } else {
            maxPanelHeight * 0.35f
        }
    }
    var hiddenSlideDistance by remember { mutableStateOf(panelSlideDistance) }
    if (!panelRevealed) {
        hiddenSlideDistance = panelSlideDistance
    }
    val panelSlideOffset by animateDpAsState(
        targetValue = if (panelRevealed) 0.dp else hiddenSlideDistance,
        animationSpec = tween(
            durationMillis = if (panelRevealed) PANEL_ENTER_ANIMATION_MS else PANEL_EXIT_ANIMATION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "pickPanelSlide",
    )

    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            FloatBallPickResultPanel.requestPanelFocus()
        }
    }

    SlideIndexTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.offset(y = panelSlideOffset)) {
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
                hasImageContent = hasImageContent,
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
                    isSearchGridVisible.value = expanded
                },
                onDragEnd = ::endAuxiliaryDrag,
                applyDrag = ::applyAuxiliaryDrag,
                panelSearchEngines = panelSearchEngines,
                activeText = activeText,
                searchEngineGridColumns = searchEngineGridColumns,
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
                    isSearchGridVisible.value = visible
                },
                onPinTextToScreen = onPinTextToScreen,
                onStashText = onStashText,
            )
            }
        }
    }
}

@Composable
private fun PickResultImageSection(
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    imageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinToScreen: () -> Unit,
    onStash: () -> Unit,
    onImageClick: () -> Unit,
    onImageIndexChange: (Int) -> Unit,
    sectionExpanded: Boolean,
    onSectionExpandedChange: (Boolean) -> Unit,
) {
    val images = panelImages.ifEmpty { listOfNotNull(screenshot) }
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = currentImageIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    LaunchedEffect(currentImageIndex) {
        if (images.isNotEmpty() && pagerState.currentPage != currentImageIndex) {
            pagerState.scrollToPage(currentImageIndex.coerceIn(0, images.lastIndex))
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (images.isNotEmpty() && pagerState.settledPage != currentImageIndex) {
            onImageIndexChange(pagerState.settledPage)
        }
    }
    Column(modifier = modifier) {
        PickResultSectionHeader(
            title = stringResource(R.string.float_ball_pick_result_image_section),
            expanded = sectionExpanded,
            onToggle = { onSectionExpandedChange(!sectionExpanded) },
            collapsible = true,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (images.size == 1) {
                    val image = images.first()
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(imageDisplaySize.width)
                            .height(imageDisplaySize.height)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onImageClick),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                    ) { page ->
                        val image = images[page]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(imageDisplaySize.width)
                                    .height(imageDisplaySize.height)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onImageClick),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Text(
                        text = "${pagerState.currentPage + 1}/${images.size}",
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
            if (images.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(images.size, key = { it }) { index ->
                        val selected = index == pagerState.currentPage
                        val thumb = images[index]
                        Image(
                            bitmap = thumb.asImageBitmap(),
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
                                .clickable { onImageIndexChange(index) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            PickResultImageSearchBar(
                engines = searchEngines,
                onShareEngineClick = onShareEngineClick,
                onShare = onShare,
                onImageSearch = onImageSearch,
                onSave = onSave,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
                onThumbnailClick = { onSectionExpandedChange(!sectionExpanded) },
            )
        }
    }
}

private fun translateErrorMessage(context: Context, code: String): String = when (code) {
    "mlkit_model_not_installed" -> context.getString(R.string.float_ball_translate_error_model_missing)
    "wifi_required" -> context.getString(R.string.float_ball_translate_error_wifi_required)
    "unsupported_language" -> context.getString(R.string.float_ball_translate_error_unsupported_language)
    "translate_unavailable" -> context.getString(R.string.float_ball_translate_error_unavailable)
    "network_error", "http_403", "http_429", "http_500" ->
        context.getString(R.string.float_ball_translate_error_network)
    else -> code
}
