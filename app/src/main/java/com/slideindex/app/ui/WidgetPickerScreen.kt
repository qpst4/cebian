package com.slideindex.app.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.widget.InstalledAppEntry
import com.slideindex.app.widget.ShortcutEntry
import com.slideindex.app.widget.WidgetAppGroup
import com.slideindex.app.widget.WidgetCatalog
import com.slideindex.app.widget.WidgetPreviewLoader
import com.slideindex.app.widget.WidgetProviderEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface WidgetPickerSubScreen {
  data object Main : WidgetPickerSubScreen
  data class WidgetAppDetail(val group: WidgetAppGroup) : WidgetPickerSubScreen
  data object MyShortcuts : WidgetPickerSubScreen
  data object PresetShortcuts : WidgetPickerSubScreen
  data object PickApp : WidgetPickerSubScreen
  data class PickActivity(val packageName: String) : WidgetPickerSubScreen
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetPickerScreen(
  onBack: () -> Unit,
  onWidgetSelected: ((WidgetProviderEntry) -> Unit)? = null,
  onAppSelected: ((InstalledAppEntry) -> Unit)? = null,
  onShortcutSelected: ((ShortcutEntry) -> Unit)? = null,
  launchCreateShortcut: ((AppShortcutLoader.CreateShortcutHost) -> Unit)? = null,
  enableBackHandler: Boolean = true,
  overlayMode: Boolean = false,
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableIntStateOf(0) }
  val initialGroups = WidgetCatalog.cachedGroups ?: emptyList()
  val initialInstalledApps = WidgetCatalog.cachedInstalledApps ?: emptyList()
  var groups by remember { mutableStateOf(initialGroups) }
  var installedApps by remember { mutableStateOf(initialInstalledApps) }
  var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
  var loading by remember { mutableStateOf(initialGroups.isEmpty() && initialInstalledApps.isEmpty()) }
  var searchQuery by remember { mutableStateOf("") }
  var searchExpanded by remember { mutableStateOf(false) }
  val searchFocusRequester = remember { FocusRequester() }
  var subScreen by remember { mutableStateOf<WidgetPickerSubScreen>(WidgetPickerSubScreen.Main) }

  val settingsRepository = com.slideindex.app.ui.compose.rememberSettingsRepository()
  val appSettings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = com.slideindex.app.settings.AppSettings())
  val activityShortcuts = appSettings.activityShortcuts
  val appRepository = rememberAppRepository()

  LaunchedEffect(Unit) {
    if (groups.isEmpty() || installedApps.isEmpty()) {
      loading = true
    }
    withContext(Dispatchers.IO) {
      val loadedGroups = WidgetCatalog.loadGroups(context)
      val loadedApps = WidgetCatalog.loadInstalledApps(context)
      val loadedAll = appRepository.loadApps(force = false)
      groups = loadedGroups
      installedApps = loadedApps
      allApps = loadedAll
    }
    loading = false
  }

  val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
  var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
  val createLauncher = if (activityResultRegistryOwner != null) {
    rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
    ) { result ->
      val host = pendingCreateHost
      pendingCreateHost = null
      if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
      val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
        ?: return@rememberLauncherForActivityResult
      onShortcutSelected?.invoke(
        ShortcutEntry(
          packageName = created.hostPackageName,
          shortcutId = "created_${created.label.hashCode()}",
          label = created.label,
          sortKey = created.label,
          initialKey = "",
          iconBitmap = null,
          intentUri = created.intentUri.orEmpty(),
        )
      )
    }
  } else null

  when (val currentSub = subScreen) {
    is WidgetPickerSubScreen.WidgetAppDetail -> {
      WidgetAppDetailScreen(
        group = currentSub.group,
        onBack = { subScreen = WidgetPickerSubScreen.Main },
        onWidgetSelected = onWidgetSelected,
        enableBackHandler = enableBackHandler,
        overlayMode = overlayMode,
      )
      return
    }
    WidgetPickerSubScreen.MyShortcuts -> {
      MyShortcutsFolderScreen(
        activityShortcuts = activityShortcuts,
        onBack = { subScreen = WidgetPickerSubScreen.Main },
        onBrowseNewShortcut = { subScreen = WidgetPickerSubScreen.PickApp },
        onSelectShortcutEntry = { entry ->
          onShortcutSelected?.invoke(entry)
        },
        enableBackHandler = enableBackHandler,
        overlayMode = overlayMode,
      )
      return
    }
    WidgetPickerSubScreen.PresetShortcuts -> {
      PresetShortcutsFolderScreen(
        onBack = { subScreen = WidgetPickerSubScreen.Main },
        onSelectShortcutEntry = { entry ->
          onShortcutSelected?.invoke(entry)
        },
        enableBackHandler = enableBackHandler,
        overlayMode = overlayMode,
      )
      return
    }
    WidgetPickerSubScreen.PickApp -> {
      ActivityShortcutPickAppScreen(
        onBack = { subScreen = WidgetPickerSubScreen.MyShortcuts },
        onSelectApp = { app ->
          subScreen = WidgetPickerSubScreen.PickActivity(app.packageName)
        },
        enableBackHandler = enableBackHandler,
      )
      return
    }
    is WidgetPickerSubScreen.PickActivity -> {
      ActivityShortcutPickActivityScreen(
        packageName = currentSub.packageName,
        onBack = { subScreen = WidgetPickerSubScreen.PickApp },
        onSelectActivity = { activity ->
          onShortcutSelected?.invoke(
            ShortcutEntry(
              packageName = activity.packageName,
              shortcutId = "${activity.packageName}/${activity.className}",
              label = activity.label,
              sortKey = activity.label,
              initialKey = "",
              iconBitmap = null,
              intentUri = "",
            )
          )
        },
        enableBackHandler = enableBackHandler,
      )
      return
    }
    WidgetPickerSubScreen.Main -> { /* Proceed to main picker */ }
  }

  val filteredGroups = remember(groups, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) return@remember groups
    groups.mapNotNull { group ->
      val appMatches = group.appLabel.lowercase().contains(query) ||
        PinyinHelper.sortKey(group.appLabel).contains(query) ||
        PinyinHelper.initialKey(group.appLabel).contains(query) ||
        group.packageName.lowercase().contains(query)
      val widgets = group.widgets.filter { widget ->
        appMatches ||
          widget.widgetLabel.lowercase().contains(query) ||
          PinyinHelper.sortKey(widget.widgetLabel).contains(query) ||
          PinyinHelper.initialKey(widget.widgetLabel).contains(query) ||
          widget.packageName.lowercase().contains(query)
      }
      if (widgets.isEmpty()) null else group.copy(widgets = widgets)
    }
  }

  val filteredApps = remember(installedApps, searchQuery) {
    val query = searchQuery.trim().lowercase()
    if (query.isEmpty()) installedApps
    else installedApps.filter {
      it.appLabel.lowercase().contains(query) ||
        it.sortKey.contains(query) ||
        it.initialKey.contains(query) ||
        it.packageName.lowercase().contains(query)
    }
  }

  val loadedCatalog = rememberLoadedShortcutCatalog(allApps)
  val filteredCatalog = remember(loadedCatalog.catalog, searchQuery) {
    filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
  }
  val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

  val handleBack: () -> Unit = {
    if (
      !consumeExpandableSearchBack(
        expanded = searchExpanded,
        query = searchQuery,
        onExpandedChange = { searchExpanded = it },
        onQueryChange = { searchQuery = it },
      )
    ) {
      onBack()
    }
  }

  val listHPadding = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding

  SettingsLazyScreenScaffold(
    title = stringResource(R.string.widget_picker_title),
    onBack = handleBack,
    enableBackHandler = enableBackHandler,
    modifier = Modifier.fillMaxSize(),
    actions = {
      MiuixExpandableSearchIconAction(
        expanded = searchExpanded,
        query = searchQuery,
        onExpandedChange = { searchExpanded = it },
        onQueryChange = { searchQuery = it },
      )
    },
    bottomContent = {
      MiuixScaffoldSearchTabBottomContent(
        searchExpanded = searchExpanded,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        focusRequester = searchFocusRequester,
        hintResId = R.string.widget_picker_search_hint,
        tabContent = {
          MiuixTabRowWithContour(
            tabs = listOf(
              stringResource(R.string.widget_picker_title),
              "应用程序",
              "快捷方式",
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
          )
        },
      )
    },
  ) {
    when {
      loading -> {
        item(key = "widget_loading") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      }
      selectedTab == 0 && filteredGroups.isEmpty() -> {
        item(key = "widget_empty_groups") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.widget_picker_empty),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      selectedTab == 1 && filteredApps.isEmpty() -> {
        item(key = "widget_empty_apps") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(240.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.widget_picker_empty),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      selectedTab == 0 -> {
        items(filteredGroups, key = { it.packageName }) { group ->
          WidgetAppGroupSection(
            group = group,
            onOpenApp = { subScreen = WidgetPickerSubScreen.WidgetAppDetail(group) },
            onSelect = onWidgetSelected,
            horizontalPadding = listHPadding,
          )
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
      selectedTab == 1 -> {
        val segmentCount = filteredApps.size
        items(
          count = filteredApps.size,
          key = { filteredApps[it].packageName + "/" + filteredApps[it].className },
        ) { index ->
          val app = filteredApps[index]
          Md3PickerListRow(
            segmentIndex = index,
            segmentCount = segmentCount,
            title = app.appLabel,
            subtitle = app.packageName,
            selected = false,
            onClick = { onAppSelected?.invoke(app) },
            leadingContent = {
              if (app.iconBitmap != null) {
                Surface(
                  modifier = Modifier.size(40.dp),
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                  Image(
                    bitmap = app.iconBitmap,
                    contentDescription = app.appLabel,
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(4.dp)
                      .clip(MaterialTheme.shapes.extraSmall),
                    contentScale = ContentScale.Crop,
                  )
                }
              } else {
                Md3PickerPackageLeading(packageName = app.packageName, contentDescription = app.appLabel)
              }
            },
          )
        }
      }
      selectedTab == 2 -> {
        if (searchQuery.isBlank()) {
          shortcutFolderCardsSection(
            activityShortcutsCount = activityShortcuts.size,
            onOpenMyShortcuts = { subScreen = WidgetPickerSubScreen.MyShortcuts },
            onOpenPresetShortcuts = { subScreen = WidgetPickerSubScreen.PresetShortcuts },
            horizontalPadding = listHPadding,
          )
        }
        systemShortcutCatalogItems(
          filtered = filteredCatalog,
          appsByPackage = appsByPackage,
          loading = loadedCatalog.loading,
          scanProgress = loadedCatalog.scanProgress,
          loadingItemKey = "widget-shortcut-loading",
          emptyItemKey = "widget-shortcut-empty",
          onCreateHostClick = { host ->
            if (launchCreateShortcut != null) {
              launchCreateShortcut(host)
            } else if (createLauncher != null) {
              pendingCreateHost = host
              runCatching { createLauncher.launch(host.createIntent()) }
                .onFailure { pendingCreateHost = null }
            } else {
              com.slideindex.app.service.CreateShortcutTrampoline.launch(
                context = context,
                host = host,
                onPrepare = {
                  onBack()
                },
                onResult = { created ->
                  if (created != null) {
                    onShortcutSelected?.invoke(
                      ShortcutEntry(
                        packageName = created.hostPackageName,
                        shortcutId = "created_${created.label.hashCode()}",
                        label = created.label,
                        sortKey = created.label,
                        initialKey = "",
                        iconBitmap = null,
                        intentUri = created.intentUri.orEmpty(),
                      )
                    )
                  }
                },
              )
            }
          },
          shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
            Md3PickerListRow(
              segmentIndex = segmentIndex,
              segmentCount = segmentCount,
              title = shortcut.label,
              subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
              selected = false,
              onClick = {
                val intentUri = shortcut.intentUris?.firstOrNull().orEmpty()
                onShortcutSelected?.invoke(
                  ShortcutEntry(
                    packageName = group.app.packageName,
                    shortcutId = shortcut.shortcutId ?: shortcut.label,
                    label = shortcut.label,
                    sortKey = shortcut.label,
                    initialKey = "",
                    iconBitmap = null,
                    intentUri = intentUri,
                  )
                )
              },
              leadingContent = { Md3PickerAppLeading(group.app) },
            )
          },
        )
      }
    }
  }
}

@Composable
private fun WidgetAppGroupSection(
  group: WidgetAppGroup,
  onOpenApp: () -> Unit,
  onSelect: ((WidgetProviderEntry) -> Unit)? = null,
  horizontalPadding: androidx.compose.ui.unit.Dp = PickerListHorizontalPadding,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = horizontalPadding),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onOpenApp)
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (group.appIcon != null) {
        Md3PickerDrawableLeading(
          drawable = group.appIcon,
          contentDescription = group.appLabel,
          cacheKey = group.packageName,
        )
      } else {
        Md3PickerPackageLeading(
          packageName = group.packageName,
          contentDescription = group.appLabel,
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = group.appLabel,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "${group.widgets.size} 个小组件",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "查看全部",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
      items(
        count = group.widgets.size,
        key = { index -> "${group.widgets[index].provider.provider.flattenToString()}_${group.widgets[index].widgetLabel}_${group.widgets[index].spanX}x${group.widgets[index].spanY}_$index" },
      ) { index ->
        val widget = group.widgets[index]
        WidgetPreviewCard(
          widget = widget,
          onClick = { onSelect?.invoke(widget) },
        )
      }
    }
  }
}

@Composable
private fun WidgetPreviewCard(
  widget: WidgetProviderEntry,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  var previewBitmap by remember(widget.provider) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(widget.provider) {
    withContext(Dispatchers.IO) {
      previewBitmap = WidgetPreviewLoader.loadPreviewBitmap(context, widget.provider, 200)
    }
  }

  Surface(
    modifier = Modifier
      .width(160.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        val b = previewBitmap
        if (b != null) {
          Image(
            bitmap = b.asImageBitmap(),
            contentDescription = widget.widgetLabel,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = widget.widgetLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )

      Text(
        text = "${widget.spanX}×${widget.spanY}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetAppDetailScreen(
  group: WidgetAppGroup,
  onBack: () -> Unit,
  onWidgetSelected: ((WidgetProviderEntry) -> Unit)? = null,
  enableBackHandler: Boolean = true,
  overlayMode: Boolean = false,
) {
  val listHPadding = if (overlayMode) PickerListOverlayHorizontalPadding else PickerListHorizontalPadding

  SettingsScreenScaffold(
    title = group.appLabel,
    subtitle = "${group.widgets.size} 个小组件",
    onBack = onBack,
    enableBackHandler = enableBackHandler,
    overlayMode = overlayMode,
    scrollContent = false,
    modifier = Modifier.fillMaxSize(),
  ) {
    LazySettingsItem(key = "app-detail-body", fillParentMaxSize = true) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          start = listHPadding,
          end = listHPadding,
          top = 8.dp,
          bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        itemsIndexed(
          items = group.widgets,
          key = { index, item -> "${item.provider.provider.flattenToString()}_${item.widgetLabel}_${item.spanX}x${item.spanY}_$index" },
        ) { _, widget ->
          WidgetDetailCard(
            widget = widget,
            onClick = { onWidgetSelected?.invoke(widget) },
          )
        }
      }
    }
  }
}

@Composable
private fun WidgetDetailCard(
  widget: WidgetProviderEntry,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  var previewBitmap by remember(widget.provider) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(widget.provider) {
    withContext(Dispatchers.IO) {
      previewBitmap = WidgetPreviewLoader.loadPreviewBitmap(context, widget.provider, 300)
    }
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        val b = previewBitmap
        if (b != null) {
          Image(
            bitmap = b.asImageBitmap(),
            contentDescription = widget.widgetLabel,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
          )
        } else {
          Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = widget.widgetLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )

      Text(
        text = "${widget.spanX}×${widget.spanY}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
