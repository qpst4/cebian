package com.slideindex.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import com.slideindex.app.widget.WidgetPanelLayoutMetrics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelGridLogic
import com.slideindex.app.widget.WidgetPanelItem
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.widget.WidgetPopupHost
import com.slideindex.app.widget.WidgetSpanUtil
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun WidgetPanelSettingsScreen(
  settings: AppSettings,
  onBack: () -> Unit,
  onSavePages: (List<WidgetPanelPage>) -> Unit,
  onBlurEnabledChange: (Boolean) -> Unit,
  onWidthFractionChange: (Float) -> Unit,
) {
  val context = LocalContext.current
  var pages by remember {
    mutableStateOf(
      WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
        .map { WidgetPanelGridLogic.fitPageToGrid(it) },
    )
  }
  var gridInteractionActive by remember { mutableStateOf(false) }
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  LaunchedEffect(Unit) {
    val raw = WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
      .map { WidgetPanelGridLogic.fitPageToGrid(it) }
    val repaired = raw.map { WidgetPanelGridLogic.repairAndFitPage(context, it) }
    if (repaired != raw) {
      pages = repaired
      onSavePages(repaired)
    }
  }

  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      MediumFlexibleTopAppBar(
        title = { SettingsAppBarTitle(stringResource(R.string.widget_panel_settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState(), enabled = !gridInteractionActive)
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(R.string.widget_panel_settings_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      SettingsCard {
        SettingSwitchRow(
          title = stringResource(R.string.widget_panel_blur),
          subtitle = stringResource(R.string.widget_panel_blur_desc),
          icon = { Icon(Icons.Default.Widgets, contentDescription = null) },
          checked = settings.widgetPanelBlurEnabled,
          enabled = true,
          onCheckedChange = onBlurEnabledChange,
        )
      }

      SettingsSectionTitle(stringResource(R.string.widget_panel_grid_section))

      HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
      ) { pageIndex ->
        val page = pages[pageIndex]
        WidgetPanelGridEditor(
          page = page,
          pageIndex = pageIndex,
          allPages = pages,
          gridScrollEnabled = !gridInteractionActive,
          onPagesChange = { updatedPages ->
            pages = updatedPages
            onSavePages(updatedPages)
          },
          onGridInteractionActiveChange = { gridInteractionActive = it },
        )
      }

      if (pages.size > 1) {
        Text(
          text = stringResource(R.string.widget_panel_page_indicator, pagerState.currentPage + 1, pages.size),
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetPanelGridEditor(
  page: WidgetPanelPage,
  pageIndex: Int,
  allPages: List<WidgetPanelPage>,
  gridScrollEnabled: Boolean,
  onPagesChange: (List<WidgetPanelPage>) -> Unit,
  onGridInteractionActiveChange: (Boolean) -> Unit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val latestPages by rememberUpdatedState(allPages)

  fun updatePage(newPage: WidgetPanelPage) {
    onPagesChange(
      latestPages.toMutableList().also { it[pageIndex] = newPage },
    )
  }

  fun launchWidgetPicker() {
    WidgetPickerTrampoline.launch(
      context = context,
      onAdded = { appWidgetId ->
        val updated = WidgetPanelMutator.addWidgetToPage(
          context,
          latestPages,
          pageIndex,
          appWidgetId,
        )
        if (updated != null) {
          onPagesChange(updated)
        }
      },
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    SettingsCard {
      SettingsSliderRow(
        title = stringResource(R.string.widget_panel_opacity),
        value = page.overlayAlpha,
        valueRange = 0.25f..0.9f,
        steps = 12,
        enabled = true,
        label = "${(page.overlayAlpha * 100).toInt()}%",
        onValueChange = { updatePage(page.copy(overlayAlpha = it)) },
      )
      SettingsSliderRow(
        title = "列数: ${page.columnCount}",
        value = page.columnCount.toFloat(),
        valueRange = 2f..20f,
        steps = 17,
        enabled = true,
        label = page.columnCount.toString(),
        onValueChange = { updatePage(WidgetPanelGridLogic.fitPageToGrid(page.copy(columnCount = it.toInt()))) },
      )
      SettingsSliderRow(
        title = "行数: ${page.rowCount}",
        value = page.rowCount.toFloat(),
        valueRange = 3f..40f,
        steps = 36,
        enabled = true,
        label = page.rowCount.toString(),
        onValueChange = { updatePage(WidgetPanelGridLogic.fitPageToGrid(page.copy(rowCount = it.toInt()))) },
      )
      SettingsSliderRow(
        title = "显示行数: ${page.visibleRowCount}",
        value = page.visibleRowCount.toFloat(),
        valueRange = 1f..40f,
        steps = 38,
        enabled = true,
        label = page.visibleRowCount.toString(),
        onValueChange = { updatePage(page.copy(visibleRowCount = it.toInt())) },
      )
      SettingsSliderRow(
        title = "容器上边距: ${page.marginTopDp}dp",
        value = page.marginTopDp.toFloat(),
        valueRange = 0f..500f,
        steps = 99,
        enabled = true,
        label = "${page.marginTopDp}dp",
        onValueChange = { updatePage(page.copy(marginTopDp = it.toInt())) },
      )
    }

    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
    ) {
      val layoutMetrics = WidgetPanelLayoutMetrics.compute(
        screenWidthPx = with(density) { maxWidth.roundToPx() },
        page = page,
        density = density.density,
        panelPaddingDp = 0f,
        panelInnerPaddingDp = 4f,
        horizontalInsetDp = 0f,
      )
      val viewportHeight = with(density) {
        layoutMetrics.viewportHeightPx.toDp().coerceAtLeast(200.dp)
      }
      val gridScrollState = rememberScrollState()

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(viewportHeight)
          .clip(RoundedCornerShape(20.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = page.overlayAlpha.coerceIn(0.25f, 0.9f))),
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(gridScrollState, enabled = gridScrollEnabled),
        ) {
          androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight()
              .nestedScroll(rememberNestedScrollInteropConnection()),
            factory = { ctx ->
              com.slideindex.app.widget.WidgetCanvasLayout(ctx).apply {
                val dm = ctx.resources.displayMetrics
                val pad = (4f * dm.density).roundToInt()
                setPadding(pad, pad, pad, pad)
                onPageCommitted = { committedPage ->
                  onPagesChange(WidgetPanelMutator.replacePage(latestPages, pageIndex, committedPage))
                }
                onItemRemoved = { widgetId ->
                  onPagesChange(
                    WidgetPanelMutator.removeWidgetFromPage(ctx, latestPages, pageIndex, widgetId),
                  )
                }
                onConfigureWidget = { widgetId ->
                  val intent = com.slideindex.app.service.WidgetConfigureTrampolineActivity.createIntent(ctx, widgetId)
                  runCatching { ctx.startActivity(intent) }
                }
                onAddWidgetRequested = { launchWidgetPicker() }
                onInteractionActiveChange = onGridInteractionActiveChange
                bindIfNeeded(page, ctx)
                editMode = true
              }
            },
            update = { view ->
              view.onPageCommitted = { committedPage ->
                onPagesChange(WidgetPanelMutator.replacePage(latestPages, pageIndex, committedPage))
              }
              view.onItemRemoved = { widgetId ->
                onPagesChange(
                  WidgetPanelMutator.removeWidgetFromPage(view.context, latestPages, pageIndex, widgetId),
                )
              }
              view.onConfigureWidget = { widgetId ->
                val intent = com.slideindex.app.service.WidgetConfigureTrampolineActivity.createIntent(view.context, widgetId)
                runCatching { view.context.startActivity(intent) }
              }
              view.onAddWidgetRequested = { launchWidgetPicker() }
              view.onInteractionActiveChange = onGridInteractionActiveChange
              view.bindIfNeeded(page, view.context)
              view.editMode = true
            },
          )
        }

        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { launchWidgetPicker() },
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.Add, contentDescription = "Add Widget")
        }
      }
    }
  }
}

@Composable
fun WidgetPanelEntryCard(
  settings: AppSettings,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val pages = WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
  val widgetCount = pages.sumOf { it.items.size }
  val subtitle = if (enabled) {
    stringResource(R.string.widget_panel_entry_summary, widgetCount, pages.size)
  } else {
    stringResource(R.string.widget_panel_entry_desc)
  }
  SettingNavigationRow(
    icon = { Icon(Icons.Default.Widgets, contentDescription = null) },
    title = stringResource(R.string.widget_panel_settings_title),
    subtitle = subtitle,
    enabled = enabled,
    onClick = onClick,
  )
}
