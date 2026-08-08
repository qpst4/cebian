package com.slideindex.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.toSafeImageBitmap
import com.slideindex.app.widget.WidgetAppGroup
import com.slideindex.app.widget.WidgetCatalog
import com.slideindex.app.widget.WidgetPreviewLoader
import com.slideindex.app.widget.WidgetProviderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.slideindex.app.widget.InstalledAppEntry
import com.slideindex.app.widget.ShortcutEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetPickerScreen(
  onBack: () -> Unit,
  onWidgetSelected: (WidgetProviderEntry) -> Unit,
  onAppSelected: ((InstalledAppEntry) -> Unit)? = null,
  onShortcutSelected: ((ShortcutEntry) -> Unit)? = null,
  enableBackHandler: Boolean = false,
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableStateOf(0) } // 0: 小组件, 1: 应用程序, 2: 快捷方式
  var groups by remember { mutableStateOf<List<WidgetAppGroup>>(emptyList()) }
  var installedApps by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
  var shortcuts by remember { mutableStateOf<List<ShortcutEntry>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var searchQuery by remember { mutableStateOf("") }
  var detailGroup by remember { mutableStateOf<WidgetAppGroup?>(null) }

  LaunchedEffect(Unit) {
    loading = true
    withContext(Dispatchers.IO) {
      groups = WidgetCatalog.loadGroups(context)
      installedApps = WidgetCatalog.loadInstalledApps(context)
      shortcuts = WidgetCatalog.loadShortcuts(context)
    }
    loading = false
  }

  val detail = detailGroup
  if (detail != null) {
    WidgetAppDetailScreen(
      group = detail,
      onBack = { detailGroup = null },
      onWidgetSelected = onWidgetSelected,
    )
    return
  }

  val filteredGroups = remember(groups, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) return@remember groups
    groups.mapNotNull { group ->
      val appMatches = PinyinHelper.sortKey(group.appLabel).contains(query)
      val widgets = group.widgets.filter { widget ->
        appMatches ||
          PinyinHelper.sortKey(widget.widgetLabel).contains(query) ||
          widget.packageName.contains(query)
      }
      if (widgets.isEmpty()) null else group.copy(widgets = widgets)
    }
  }

  val filteredApps = remember(installedApps, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) installedApps
    else installedApps.filter {
      it.sortKey.contains(query) || it.packageName.contains(query)
    }
  }

  val filteredShortcuts = remember(shortcuts, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) shortcuts
    else shortcuts.filter {
      it.sortKey.contains(query) || it.label.lowercase().contains(query) || it.packageName.contains(query)
    }
  }

  SettingsScreenScaffold(
    title = stringResource(R.string.widget_picker_title),
    onBack = onBack,
    enableBackHandler = enableBackHandler,
    modifier = Modifier.fillMaxSize(),
  ) {
    LazySettingsItem(key = "widget-picker-body", fillParentMaxSize = true) {
      Column(Modifier.fillMaxSize()) {
        PickerSearchListHeader(
          query = searchQuery,
          onQueryChange = { searchQuery = it },
          hintResId = R.string.widget_picker_search_hint,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PickerListHorizontalPadding, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            label = { Text(stringResource(R.string.widget_picker_title)) },
          )
          FilterChip(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            label = { Text("应用程序") },
          )
          FilterChip(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            label = { Text("快捷方式") },
          )
        }

        when {
          loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
          selectedTab == 0 && filteredGroups.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                text = stringResource(R.string.widget_picker_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          selectedTab == 1 && filteredApps.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                text = stringResource(R.string.widget_picker_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          selectedTab == 2 && filteredShortcuts.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                text = "未找到可用的应用快捷方式",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          selectedTab == 0 -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 24.dp),
            ) {
              items(filteredGroups, key = { it.packageName }) { group ->
                WidgetAppGroupSection(
                  group = group,
                  onOpenApp = { detailGroup = group },
                  onSelect = onWidgetSelected,
                )
                Spacer(modifier = Modifier.height(20.dp))
              }
            }
          }
          selectedTab == 1 -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 24.dp),
            ) {
              items(filteredApps, key = { it.packageName + "/" + it.className }) { app ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppSelected?.invoke(app) }
                    .padding(horizontal = PickerListHorizontalPadding, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (app.iconBitmap != null) {
                    Image(
                      bitmap = app.iconBitmap,
                      contentDescription = app.appLabel,
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    )
                  } else {
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                  }
                  Spacer(modifier = Modifier.width(14.dp))
                  Text(
                    text = app.appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }
          }
          else -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 24.dp),
            ) {
              items(filteredShortcuts, key = { it.packageName + "/" + it.shortcutId }) { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShortcutSelected?.invoke(item) }
                    .padding(horizontal = PickerListHorizontalPadding, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (item.iconBitmap != null) {
                    Image(
                      bitmap = item.iconBitmap,
                      contentDescription = item.label,
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    )
                  } else {
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                  }
                  Spacer(modifier = Modifier.width(14.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.label,
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = item.packageName,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WidgetAppDetailScreen(
  group: WidgetAppGroup,
  onBack: () -> Unit,
  onWidgetSelected: (WidgetProviderEntry) -> Unit,
) {
  SettingsScreenScaffold(
    title = group.appLabel,
    onBack = onBack,
    modifier = Modifier.fillMaxSize(),
  ) {
    LazySettingsItem(key = "widget-app-detail-grid", fillParentMaxSize = true) {
    LazyVerticalGrid(
      modifier = Modifier
        .fillMaxSize()
        .heightIn(min = 240.dp),
      columns = GridCells.Adaptive(minSize = 132.dp),
      contentPadding = PaddingValues(
        start = PickerListHorizontalPadding,
        end = PickerListHorizontalPadding,
        top = 8.dp,
        bottom = 24.dp,
      ),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(group.widgets, key = { it.provider.provider.flattenToString() }) { entry ->
        WidgetPreviewCard(entry = entry, onClick = { onWidgetSelected(entry) })
      }
    }
    }
  }
}

@Composable
private fun WidgetAppGroupSection(
  group: WidgetAppGroup,
  onOpenApp: (WidgetAppGroup) -> Unit,
  onSelect: (WidgetProviderEntry) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onOpenApp(group) }
        .padding(horizontal = PickerListHorizontalPadding, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val appIcon by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = group.packageName) {
        value = withContext(Dispatchers.IO) { group.appIcon?.toSafeImageBitmap() }
      }
      if (appIcon != null) {
        Image(
          bitmap = appIcon!!,
          contentDescription = stringResource(R.string.cd_app_icon),
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape),
          contentScale = ContentScale.Crop,
        )
      } else {
        Icon(
          Icons.Default.Widgets,
          contentDescription = stringResource(R.string.cd_widget_preview),
          modifier = Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
        text = group.appLabel,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
      ) {
        Text(
          text = group.widgets.size.toString(),
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = stringResource(R.string.widget_picker_open_app_widgets),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = PickerListHorizontalPadding),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(group.widgets, key = { it.provider.provider.flattenToString() }) { entry ->
        WidgetPreviewCard(entry = entry, onClick = { onSelect(entry) })
      }
    }
  }
}

@Composable
private fun WidgetPreviewCard(
  entry: WidgetProviderEntry,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val previewMaxPx = with(density) { 140.dp.roundToPx() }
  var preview by remember(entry.provider.provider) {
    mutableStateOf<Bitmap?>(null)
  }
  LaunchedEffect(entry.provider.provider) {
    preview = withContext(Dispatchers.IO) {
      WidgetPreviewLoader.loadPreviewBitmap(context, entry.provider, previewMaxPx)
    }
  }

  val cardWidth = (entry.spanX.coerceAtLeast(1) * 72 + (entry.spanX - 1) * 8)
    .coerceIn(96, 220)
    .dp

  Column(
    modifier = Modifier
      .width(cardWidth)
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onClick)
      .padding(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(88.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = preview
      if (bitmap != null) {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = stringResource(R.string.cd_widget_preview),
          modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
          contentScale = ContentScale.Fit,
        )
      } else {
        Icon(
          Icons.Default.Widgets,
          contentDescription = stringResource(R.string.cd_widget_preview),
          modifier = Modifier.size(32.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
      text = entry.widgetLabel,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
    )
    Text(
      text = stringResource(R.string.widget_picker_span_size, entry.spanX, entry.spanY),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp),
    )
  }
}
