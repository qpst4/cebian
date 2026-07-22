package com.slideindex.app.overlay.pickresult

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchIconType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.LruCache
import android.content.Context

private val searchEngineBitmapCache = LruCache<String, android.graphics.Bitmap>(48)

internal fun preloadPickResultSearchEngineIcons(
    context: Context,
    engines: List<SearchEngineConfig>,
) {
    val filesDir = context.filesDir
    val packageManager = context.packageManager
    engines.forEach { engine ->
        if (searchEngineBitmapCache.get(engine.id) != null) return@forEach
        resolveSearchEngineBitmap(filesDir, engine, packageManager)?.let { bitmap ->
            searchEngineBitmapCache.put(engine.id, bitmap)
        }
    }
}

private fun cachedSearchEngineBitmap(engineId: String): android.graphics.Bitmap? =
    searchEngineBitmapCache.get(engineId)

private val SearchIconSizeDefault = 40.dp

/** 文本区与搜索引擎网格之间、分隔线下方的额外间距。 */
internal val PickResultTextSearchGridTopSpacing = 8.dp

internal fun searchGridContentHeight(rows: Int, showLabels: Boolean, columns: Int): Dp {
    val rowCount = rows.coerceIn(1, 4)
    val iconSize = searchIconSizeForColumns(columns)
    val labelHeight = if (showLabels) 18.dp else 0.dp
    val itemHeight = iconSize + labelHeight + 4.dp
    val rowGap = 10.dp * (rowCount - 1).coerceAtLeast(0)
    return itemHeight * rowCount + rowGap + 6.dp
}

internal fun pickResultSearchGridReservedHeight(rows: Int, showLabels: Boolean, columns: Int = 5): Dp {
    val padding = 4.dp // 4.dp top, 0.dp bottom
    val divider = 5.dp // 2.dp padding * 2 + 1.dp
    val gaps = 16.dp // 2 gaps of 8.dp each (from spacedBy)
    return padding + searchGridContentHeight(rows, showLabels, columns) + divider + gaps +
        PickResultTextSearchGridTopSpacing
}

private fun searchIconSizeForColumns(columns: Int): Dp = when {
    columns >= 7 -> 32.dp
    columns >= 6 -> 36.dp
    else -> SearchIconSizeDefault
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickResultTextSearchGrid(
    engines: List<SearchEngineConfig>,
    query: String,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
    onEngineClick: (SearchEngineConfig, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    val columnCount = columns.coerceIn(3, 7)
    val rowCount = rows.coerceIn(1, 4)
    val pageSize = columnCount * rowCount
    if (engines.isEmpty()) return
    val pages = remember(engines, pageSize) {
        engines.chunked(pageSize)
    }
    val iconSize = searchIconSizeForColumns(columnCount)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val gridHeight = searchGridContentHeight(rowCount, showLabels, columnCount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
        ) { pageIndex ->
            PickResultSearchEngineGridPage(
                engines = pages[pageIndex],
                query = query,
                columnCount = columnCount,
                showLabels = showLabels,
                iconSize = iconSize,
                longPressEnabled = longPressEnabled,
                onEngineClick = onEngineClick,
            )
        }
    }
}

@Composable
private fun PickResultSearchEngineGridPage(
    engines: List<SearchEngineConfig>,
    query: String,
    columnCount: Int,
    showLabels: Boolean,
    iconSize: Dp,
    longPressEnabled: Boolean,
    onEngineClick: (SearchEngineConfig, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        engines.chunked(columnCount).forEach { rowEngines ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowEngines.forEach { engine ->
                    PickResultSearchEngineItem(
                        engine = engine,
                        enabled = query.isNotBlank(),
                        showLabel = showLabels,
                        iconSize = iconSize,
                        longPressEnabled = longPressEnabled,
                        onClick = { longPressTriggered ->
                            onEngineClick(engine, longPressTriggered)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columnCount - rowEngines.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickResultSearchEngineItem(
    engine: SearchEngineConfig,
    enabled: Boolean,
    showLabel: Boolean,
    iconSize: Dp,
    onClick: (longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    val clickModifier = if (longPressEnabled && enabled) {
        Modifier.combinedClickable(
            onClick = { onClick(false) },
            onLongClick = { onClick(true) },
        )
    } else {
        Modifier.clickable(enabled = enabled) { onClick(false) }
    }
    Column(
        modifier = modifier.then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchEngineIcon(
            engine = engine,
            modifier = Modifier.size(iconSize),
            alpha = if (enabled) 1f else 0.45f,
        )
        if (showLabel) {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.85f else 0.45f),
            )
        }
    }
}

private const val SearchEngineIconCornerFraction = 0.24f

@Composable
fun SearchEngineIcon(
    engine: SearchEngineConfig,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    var bitmap by remember(engine.id) {
        mutableStateOf(cachedSearchEngineBitmap(engine.id))
    }

    LaunchedEffect(engine) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            resolveSearchEngineBitmap(context.filesDir, engine, context.packageManager)
        }?.also { loaded ->
            searchEngineBitmapCache.put(engine.id, loaded)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val shape = RoundedCornerShape(maxWidth * SearchEngineIconCornerFraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = engine.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                    alpha = alpha,
                )
            } else {
                Text(
                    text = engine.textIcon?.take(1) ?: engine.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}

private fun resolveSearchEngineBitmap(
    filesDir: File,
    engine: SearchEngineConfig,
    packageManager: android.content.pm.PackageManager,
): android.graphics.Bitmap? {
    if (engine.iconType == SearchIconType.URI && !engine.iconPath.isNullOrBlank()) {
        val file = File(filesDir, engine.iconPath)
        if (file.exists()) {
            return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
    }
    val pkg = engine.targetPackage?.takeIf { it.isNotBlank() }
        ?: engine.externJumpPackage?.takeIf { it.isNotBlank() }
    if (pkg != null) {
        return runCatching {
            packageManager.getApplicationIcon(pkg).toBitmap(128, 128)
        }.getOrNull()
    }
    return null
}
