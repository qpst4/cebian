package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.content.edit
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.overlayContainerHeightDp
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.overlay.resolvePinImageDisplaySizePx
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt

private val ImageSearchBarHeight = 60.dp
private val ImageSectionItemSpacing = 6.dp
private val ImageSearchBarBottomPadding = 0.dp

private const val LastUsedImageEnginePrefs = "pick_result_prefs"
private const val LastUsedImageEngineKey = "last_used_image_engine"

/** 图片区水平内容宽度（面板全宽减去左右 padding）。 */
@Composable
internal fun pickResultImageContentWidth(horizontalPadding: Dp = 40.dp): Dp {
    val density = LocalDensity.current
    val containerWidth = with(density) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return (containerWidth - horizontalPadding).coerceAtLeast(0.dp)
}

internal data class PickResultImageDisplaySize(
    val width: Dp,
    val height: Dp,
)

/** 面板图片预览高度上限：竖屏为屏宽；横屏取 min(屏宽, 屏高×45%)，避免抢垂直空间。 */
@Composable
internal fun pickResultImageMaxHeightDp(): Dp {
    val widthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    if (!overlayIsLandscape()) return widthDp
    return minOf(widthDp, overlayContainerHeightDp() * 0.45f)
}

private fun applyPickResultImageDisplayCaps(
    baseWidth: Dp,
    baseHeight: Dp,
    contentWidth: Dp,
    maxHeight: Dp,
): PickResultImageDisplaySize {
    if (baseWidth <= 0.dp || baseHeight <= 0.dp) {
        return PickResultImageDisplaySize(contentWidth, maxHeight)
    }
    if (baseWidth <= contentWidth && baseHeight <= maxHeight) {
        return PickResultImageDisplaySize(baseWidth, baseHeight)
    }
    val aspect = baseHeight / baseWidth
    val heightAtFullWidth = contentWidth * aspect
    if (heightAtFullWidth <= maxHeight) {
        return PickResultImageDisplaySize(contentWidth, heightAtFullWidth)
    }
    val widthAtMaxHeight = maxHeight / aspect
    return PickResultImageDisplaySize(widthAtMaxHeight, maxHeight)
}

/**
 * 计算图片展示尺寸：有 screenRect 时与 pin 一致（screenRect 屏幕像素→dp），
 * 仅当超出屏幕边界时等比缩小；无 screenRect 时保留 bitmap 自然 dp 尺寸不放大，
 * 仅在超出内容区宽度或 maxHeight 时等比缩小。
 */
internal fun pickResultImageDisplaySize(
    bitmap: Bitmap,
    contentWidth: Dp,
    maxHeight: Dp,
    density: Density,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?,
    screenWidthPx: Int,
    screenHeightPx: Int,
): PickResultImageDisplaySize {
    val hasScreenRect = screenRect != null && !screenRect.isEmpty
    val (baseWidth, baseHeight) = if (hasScreenRect) {
        val (widthPx, heightPx) = resolvePinImageDisplaySizePx(
            bitmap = bitmap,
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        with(density) { widthPx.toDp() to heightPx.toDp() }
    } else {
        with(density) { bitmap.width.toDp() to bitmap.height.toDp() }
    }
    return if (hasScreenRect) {
        val screenMaxWidth = with(density) { screenWidthPx.toDp() }
        val screenMaxHeight = with(density) { screenHeightPx.toDp() }
        applyPickResultImageDisplayCaps(
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            contentWidth = minOf(screenMaxWidth, contentWidth),
            maxHeight = minOf(screenMaxHeight, maxHeight),
        )
    } else {
        applyPickResultImageDisplayCaps(baseWidth, baseHeight, contentWidth, maxHeight)
    }
}

internal fun pickResultImageSectionReservedHeight(imageMaxHeight: Dp, isImageVisible: Boolean): Dp {
    val header = 44.dp
    if (!isImageVisible) return header
    val gaps = 8.dp
    return header + gaps + imageMaxHeight + ImageSectionItemSpacing + ImageSearchBarHeight
}

@Composable
fun PickResultImageSearchBar(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
) {
    val shareEngines = SearchEngineStore.imageSharePanelEngines(engines)
    val isDark = LocalAppDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ImageSearchBarBottomPadding)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isDark) Color.White else Color.Black,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF2A2A2C) else Color(0xFFF2F3F5))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (shareEngines.isNotEmpty()) {
            ImageShareEngineChip(
                engines = shareEngines,
                onShareEngineClick = onShareEngineClick,
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        PickResultImageSearchActions(
            onShare = onShare,
            onImageSearch = onImageSearch,
            onSave = onSave,
            onPinToScreen = onPinToScreen,
            onStash = onStash,
        )
    }
}

@Composable
private fun rememberLastUsedImageShareEngine(
    engines: List<SearchEngineConfig>,
): Pair<SearchEngineConfig, (SearchEngineConfig) -> Unit> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences(LastUsedImageEnginePrefs, android.content.Context.MODE_PRIVATE)
    }
    var lastUsedEngineId by remember {
        mutableStateOf(prefs.getString(LastUsedImageEngineKey, null))
    }
    val displayEngine = remember(engines, lastUsedEngineId) {
        engines.find { it.id == lastUsedEngineId } ?: engines.first()
    }
    val rememberEngine: (SearchEngineConfig) -> Unit = { engine ->
        prefs.edit { putString(LastUsedImageEngineKey, engine.id) }
        lastUsedEngineId = engine.id
    }
    return displayEngine to rememberEngine
}

@Composable
private fun ImageShareEngineChip(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    val chipBackground = if (isDark) Color(0xFF3A3A3C) else Color(0xFFDFE4EA)
    val dividerColor = if (isDark) Color(0xFF4A4A4C) else Color(0xFFCED6E0)
    val labelColor = if (isDark) Color(0xFFE8EAED) else Color(0xFF2F3542)

    val (displayEngine, rememberEngine) = rememberLastUsedImageShareEngine(engines)
    var menuExpanded by remember { mutableStateOf(false) }
    val showEnginePicker = engines.size > 1
    val shareLabel = stringResource(R.string.pick_result_image_share_to_app)
    val pickEngineLabel = stringResource(R.string.pick_result_image_share_pick_engine)

    fun shareWith(engine: SearchEngineConfig) {
        rememberEngine(engine)
        onShareEngineClick(engine)
    }

    Box {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(chipBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImageShareEngineChipMain(
                engines = engines,
                displayEngine = displayEngine,
                labelColor = labelColor,
                shareLabel = shareLabel,
                onShare = { shareWith(displayEngine) },
                onQuickSwitchShare = { shareWith(it) },
            )
            if (showEnginePicker) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(dividerColor),
                )
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = pickEngineLabel,
                        modifier = Modifier.size(20.dp),
                        tint = if (isDark) Color(0xFFA0AAB5) else Color(0xFF747D8C),
                    )
                }
            }
        }

        if (showEnginePicker) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                engines.forEach { engine ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = engine.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            SearchEngineIcon(
                                engine = engine,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            shareWith(engine)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageShareEngineChipMain(
    engines: List<SearchEngineConfig>,
    displayEngine: SearchEngineConfig,
    labelColor: Color,
    shareLabel: String,
    onShare: () -> Unit,
    onQuickSwitchShare: (SearchEngineConfig) -> Unit,
) {
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var rowAnchorLeftInWindow by remember { mutableFloatStateOf(0f) }
    var dragStartFingerY by remember { mutableFloatStateOf(0f) }

    val quickSwitchItemWidth = 40.dp
    val quickSwitchItemSpacing = 6.dp
    val quickSwitchRowPaddingStart = 12.dp
    val quickSwitchRowPaddingEnd = 6.dp
    val quickSwitchRowVerticalPadding = 6.dp
    val quickSwitchPopupGapAboveChip = 8.dp
    val quickSwitchYCancelThreshold = 60.dp
    val quickSwitchScreenMargin = 8.dp

    val density = LocalDensity.current
    val itemWidthPx = with(density) { quickSwitchItemWidth.toPx() }
    val itemSpacingPx = with(density) { quickSwitchItemSpacing.toPx() }
    val rowPaddingStartPx = with(density) { quickSwitchRowPaddingStart.toPx() }
    val rowPaddingEndPx = with(density) { quickSwitchRowPaddingEnd.toPx() }
    val popupOffsetAboveChipPx = with(density) {
        (quickSwitchItemWidth + quickSwitchRowVerticalPadding * 2 + quickSwitchPopupGapAboveChip).toPx()
    }
    val yCancelThresholdPx = with(density) { quickSwitchYCancelThreshold.toPx() }
    val screenMarginPx = with(density) { quickSwitchScreenMargin.toPx() }
    val totalItemStridePx = itemWidthPx + itemSpacingPx
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()

    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.settings
            ?.collect { appSettings = it }
    }

    val quickSwitchEnabled = engines.size > 1

    fun quickSwitchRowWidthPx(): Float {
        val itemCount = engines.size
        return rowPaddingStartPx + rowPaddingEndPx +
            itemCount * itemWidthPx +
            (itemCount - 1).coerceAtLeast(0) * itemSpacingPx
    }

    fun clampRowAnchorLeft(left: Float): Float {
        val rowWidth = quickSwitchRowWidthPx()
        return left.coerceIn(screenMarginPx, (screenWidthPx - rowWidth - screenMarginPx).coerceAtLeast(screenMarginPx))
    }

    fun indexAtFingerX(fingerX: Float): Int {
        val localX = fingerX - rowAnchorLeftInWindow - rowPaddingStartPx
        return (localX / totalItemStridePx)
            .toInt()
            .coerceIn(0, engines.lastIndex)
    }

    fun anchorRowUnderFinger(fingerX: Float, underIndex: Int) {
        val desiredLeft = fingerX - rowPaddingStartPx - underIndex * totalItemStridePx - itemWidthPx / 2f
        rowAnchorLeftInWindow = clampRowAnchorLeft(desiredLeft)
    }

    Box(modifier = Modifier.onGloballyPositioned { anchorCoordinates = it }) {
        Row(
            modifier = Modifier
                .then(
                    if (quickSwitchEnabled) {
                        Modifier.pointerInput(engines, displayEngine, anchorCoordinates) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                                    val finger = coords.localToWindow(startOffset)
                                    dragStartFingerY = finger.y
                                    val startIndex = engines.indexOf(displayEngine).coerceAtLeast(0)
                                    anchorRowUnderFinger(finger.x, startIndex)
                                    hoveredIndex = indexAtFingerX(finger.x)
                                    isDragging = true
                                    HapticHelper.appTick(view, appSettings)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                                    val finger = coords.localToWindow(change.position)
                                    if (kotlin.math.abs(finger.y - dragStartFingerY) > yCancelThresholdPx) {
                                        if (hoveredIndex != -1) {
                                            hoveredIndex = -1
                                            HapticHelper.appTick(view, appSettings)
                                        }
                                    } else {
                                        val index = indexAtFingerX(finger.x)
                                        if (index != hoveredIndex) {
                                            hoveredIndex = index
                                            HapticHelper.appTick(view, appSettings)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isDragging && hoveredIndex in engines.indices) {
                                        onQuickSwitchShare(engines[hoveredIndex])
                                    }
                                    isDragging = false
                                    hoveredIndex = -1
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hoveredIndex = -1
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    contentDescription = "$shareLabel：${displayEngine.name}"
                }
                .clickable(onClick = onShare)
                .padding(start = 8.dp, end = if (quickSwitchEnabled) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchEngineIcon(
                engine = displayEngine,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = displayEngine.name,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 88.dp),
            )
        }

        if (isDragging && quickSwitchEnabled) {
            val coords = anchorCoordinates
            if (coords != null && coords.isAttached) {
                val anchorTopLeft = coords.boundsInWindow().topLeft
                val popupOffsetX = (rowAnchorLeftInWindow - anchorTopLeft.x).roundToInt()
                val popupOffsetY = -popupOffsetAboveChipPx.roundToInt()
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, popupOffsetY),
                ) {
                    ImageShareEngineQuickSwitchRow(
                        engines = engines,
                        hoveredIndex = hoveredIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageShareEngineQuickSwitchRow(
    engines: List<SearchEngineConfig>,
    hoveredIndex: Int,
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        engines.forEachIndexed { index, engine ->
            val isHovered = index == hoveredIndex
            val scale by animateFloatAsState(
                targetValue = if (isHovered) 1.2f else 1f,
                label = "engineQuickSwitchScale",
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .background(
                        color = if (isHovered) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun PickResultImageSearchActions(
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
) {
    val isDark = LocalAppDarkTheme.current
    val systemShareLabel = stringResource(R.string.pick_result_image_share_system)
    Row(
        modifier = Modifier
            .height(44.dp)
            .background(
                color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE4E5E8),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onPinToScreen?.let {
            PickResultActionIcon(Icons.Outlined.PushPin, enabled = true, onClick = it)
        }
        onStash?.let {
            PickResultActionIcon(Icons.Outlined.Archive, enabled = true, onClick = it)
        }

        Spacer(modifier = Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 16.dp)
                .background(if (isDark) Color(0xFF4A4A4C) else Color(0xFFCED6E0)),
        )
        Spacer(modifier = Modifier.size(4.dp))

        PickResultActionIcon(
            icon = Icons.Outlined.Share,
            enabled = true,
            onClick = onShare,
            contentDescription = systemShareLabel,
        )
        PickResultActionIcon(
            icon = Icons.Outlined.ImageSearch,
            enabled = true,
            onClick = onImageSearch,
            tint = MaterialTheme.colorScheme.primary,
        )
        PickResultActionIcon(Icons.Outlined.Save, enabled = true, onClick = onSave)
    }
}

@Composable
private fun PickResultActionIcon(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}
