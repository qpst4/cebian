package com.slideindex.app.overlay.pickresult

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.resolvePinImageDisplaySizePx
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.util.HapticHelper

private val ImageSearchBarHeight = 60.dp
private val ImageSectionItemSpacing = 6.dp
private val ImageSearchBarBottomPadding = 0.dp // Already included in 60.dp

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

/** 面板图片预览高度上限：屏幕宽度（dp），与 pin 横图约束一致。 */
@Composable
internal fun pickResultImageMaxHeightDp(): Dp {
    val density = LocalDensity.current
    return with(density) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
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
 * 计算图片展示尺寸：有 screenRect 时与 pin 一致（screenRect 屏幕像素转 dp），
 * 仅当超出屏幕边界时等比缩小；无 screenRect 时保持 bitmap 自然 dp 尺寸不放大，
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
    val gaps = 8.dp // Gap from main Column's Arrangement.spacedBy(8.dp) between Header and Image column
    return header + gaps + imageMaxHeight + ImageSectionItemSpacing + ImageSearchBarHeight
}

@Composable
fun PickResultImageSearchBar(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    onThumbnailClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shareEngines = SearchEngineStore.imageSharePanelEngines(engines)

    val isDark = isSystemInDarkTheme()
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFDFE4EA))
                    .clickable(onClick = onThumbnailClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFFA0AAB5) else Color(0xFF747D8C),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (shareEngines.isNotEmpty()) {
                SwipeToSelectEngineMenu(
                    engines = shareEngines,
                    onShareEngineClick = onShareEngineClick
                )
            }
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
private fun SwipeToSelectEngineMenu(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var initialIndex by remember { mutableIntStateOf(0) }
    
    val itemWidth = 40.dp
    val itemSpacing = 6.dp
    val density = LocalDensity.current
    val totalItemStridePx = with(density) { (itemWidth + itemSpacing).toPx() }
    
    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        com.slideindex.app.di.OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.settings
            ?.collect { appSettings = it }
    }

    val prefs = remember(context) { context.getSharedPreferences("pick_result_prefs", android.content.Context.MODE_PRIVATE) }
    var lastUsedEngineId by remember { mutableStateOf(prefs.getString("last_used_image_engine", null)) }
    val displayEngine = remember(engines, lastUsedEngineId) {
        engines.find { it.id == lastUsedEngineId } ?: engines.first()
    }

    Box(
        modifier = modifier
            .pointerInput(engines, displayEngine) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        isDragging = true
                        dragOffset = Offset.Zero
                        initialIndex = engines.indexOf(displayEngine).coerceAtLeast(0)
                        hoveredIndex = initialIndex
                        HapticHelper.appTick(view, appSettings)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        
                        if (kotlin.math.abs(dragOffset.y) > with(density) { 60.dp.toPx() }) {
                            if (hoveredIndex != -1) {
                                hoveredIndex = -1
                                HapticHelper.appTick(view, appSettings)
                            }
                        } else {
                            val basePx = initialIndex * totalItemStridePx
                            val index = ((dragOffset.x + basePx + (totalItemStridePx / 2)) / totalItemStridePx)
                                .toInt()
                                .coerceIn(0, engines.size - 1)
                                
                            if (index != hoveredIndex) {
                                hoveredIndex = index
                                HapticHelper.appTick(view, appSettings)
                            }
                        }
                    },
                    onDragEnd = {
                        if (isDragging && hoveredIndex in engines.indices) {
                            val engine = engines[hoveredIndex]
                            prefs.edit().putString("last_used_image_engine", engine.id).apply()
                            lastUsedEngineId = engine.id
                            onShareEngineClick(engine)
                        }
                        isDragging = false
                        hoveredIndex = -1
                    },
                    onDragCancel = {
                        isDragging = false
                        hoveredIndex = -1
                    }
                )
            }
            .clickable { 
                prefs.edit().putString("last_used_image_engine", displayEngine.id).apply()
                lastUsedEngineId = displayEngine.id
                onShareEngineClick(displayEngine)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            SearchEngineIcon(
                engine = displayEngine,
                modifier = Modifier.size(24.dp),
            )
        }
        
        if (isDragging) {
            val offsetX = (-14).dp - (46.dp * initialIndex)
            Popup(
                alignment = Alignment.CenterStart,
                offset = IntOffset(with(density) { offsetX.roundToPx() }, 0)
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    engines.forEachIndexed { index, engine ->
                        val isHovered = index == hoveredIndex
                        val scale by animateFloatAsState(targetValue = if (isHovered) 1.2f else 1f, label = "scale")
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .background(
                                    color = if (isHovered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            SearchEngineIcon(
                                engine = engine,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
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
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .height(44.dp)
            .background(
                color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE4E5E8),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onPinToScreen?.let { 
            PickResultActionIcon(Icons.Outlined.PushPin, enabled = true, onClick = it) 
        }
        onStash?.let { 
            PickResultActionIcon(Icons.Outlined.Inventory2, enabled = true, onClick = it) 
        }
        
        Spacer(modifier = Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 16.dp)
                .background(if (isDark) Color(0xFF4A4A4C) else Color(0xFFCED6E0))
        )
        Spacer(modifier = Modifier.size(4.dp))
        
        PickResultActionIcon(Icons.Outlined.Share, enabled = true, onClick = onShare)
        PickResultActionIcon(
            icon = Icons.Outlined.ImageSearch,
            enabled = true,
            onClick = onImageSearch,
            tint = MaterialTheme.colorScheme.primary
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
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f)
        )
    }
}
