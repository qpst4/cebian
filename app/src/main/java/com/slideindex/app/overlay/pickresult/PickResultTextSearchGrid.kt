package com.slideindex.app.overlay.pickresult

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val SearchIconSizeDefault = 40.dp

private fun searchGridContentHeight(rows: Int, showLabels: Boolean, columns: Int): Dp {
    val rowCount = rows.coerceIn(1, 4)
    val iconSize = searchIconSizeForColumns(columns)
    val labelHeight = if (showLabels) 14.dp else 0.dp
    val itemHeight = iconSize + labelHeight + 4.dp
    val rowGap = 10.dp * (rowCount - 1).coerceAtLeast(0)
    return itemHeight * rowCount + rowGap
}

internal fun pickResultSearchGridReservedHeight(rows: Int, showLabels: Boolean, columns: Int = 5): Dp {
    val padding = 8.dp
    val divider = 5.dp
    return padding + searchGridContentHeight(rows, showLabels, columns) + divider
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
    onEngineClick: (SearchEngineConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columnCount = columns.coerceIn(3, 7)
    val rowCount = rows.coerceIn(1, 4)
    val pageSize = columnCount * rowCount
    val panelEngines = remember(engines) {
        SearchEngineStore.textPickPanelEngines(engines)
    }
    if (panelEngines.isEmpty()) return
    val pages = remember(panelEngines, pageSize) {
        panelEngines.chunked(pageSize)
    }
    val iconSize = searchIconSizeForColumns(columnCount)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val gridHeight = searchGridContentHeight(rowCount, showLabels, columnCount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
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
    onEngineClick: (SearchEngineConfig) -> Unit,
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
                        onClick = { onEngineClick(engine) },
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

@Composable
private fun PickResultSearchEngineItem(
    engine: SearchEngineConfig,
    enabled: Boolean,
    showLabel: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick),
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
    var bitmap by remember(engine.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(engine) {
        bitmap = withContext(Dispatchers.IO) {
            resolveSearchEngineBitmap(context.filesDir, engine, context.packageManager)
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
