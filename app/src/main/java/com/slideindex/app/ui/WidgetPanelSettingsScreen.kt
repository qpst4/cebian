package com.slideindex.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import com.slideindex.app.widget.WidgetPanelLayoutMetrics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelUi
import com.slideindex.app.widget.WidgetPanelGridLogic
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardSegmentContent
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.ui.viewmodel.WidgetPanelEditorViewModel
import com.slideindex.app.ui.viewmodel.WidgetPanelUiState

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun WidgetPanelSettingsScreen(
  viewModel: WidgetPanelEditorViewModel,
  onBack: () -> Unit,
  onWidthFractionChange: (Float) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  WidgetPanelSettingsContent(
    uiState = uiState,
    onBack = onBack,
    onSavePages = viewModel::setPages,
    onBlurEnabledChange = viewModel::setBlurEnabled,
    onBlurRadiusChange = viewModel::setBlurRadius,
    onGridInteractionActiveChange = viewModel::setGridInteractionActive,
  )
}

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
  onBlurRadiusChange: (Int) -> Unit = {},
  onWidthFractionChange: (Float) -> Unit,
) {
  val pages = WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
    .map { WidgetPanelGridLogic.fitPageToGrid(it) }
  val uiState = WidgetPanelUiState(
    pages = pages,
    blurEnabled = settings.widgetPanelBlurEnabled,
    blurRadiusDp = settings.widgetPanelBlurRadiusDp,
  )
  WidgetPanelSettingsContent(
    uiState = uiState,
    onBack = onBack,
    onSavePages = onSavePages,
    onBlurEnabledChange = onBlurEnabledChange,
    onBlurRadiusChange = onBlurRadiusChange,
    onGridInteractionActiveChange = {},
  )
}

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun WidgetPanelSettingsContent(
  uiState: WidgetPanelUiState,
  onBack: () -> Unit,
  onSavePages: (List<WidgetPanelPage>) -> Unit,
  onBlurEnabledChange: (Boolean) -> Unit,
  onBlurRadiusChange: (Int) -> Unit = {},
  onGridInteractionActiveChange: (Boolean) -> Unit = {},
) {
  val pagerState = rememberPagerState(pageCount = { uiState.pages.size })
  val settingsDesc = stringResource(R.string.widget_panel_settings_desc)
  val blurTitle = stringResource(R.string.widget_panel_blur)
  val blurDesc = stringResource(R.string.widget_panel_blur_desc)
  val gridSectionTitle = stringResource(R.string.widget_panel_grid_section)

  SettingsScreenScaffold(
    title = stringResource(R.string.widget_panel_settings_title),
    onBack = onBack,
  ) {
    settingsLazyHint(
      key = "widget-panel-desc",
      text = settingsDesc,
    )
    groupedCardItems(
      keyPrefix = "widget-panel-blur",
      items = listOf(
        settingsCardScopeItem("widget-panel-blur-enabled") {
          SettingExpandableSwitchRow(
            title = blurTitle,
            subtitle = blurDesc,
            icon = { label -> Icon(HubLeadingIcons.widgetPanel(true), contentDescription = label) },
            checked = uiState.blurEnabled,
            enabled = true,
            onCheckedChange = onBlurEnabledChange,
          ) {
            SettingsSliderRow(
              title = stringResource(R.string.honeycomb_blur_strength),
              value = uiState.blurRadiusDp.toFloat(),
              valueRange = AppSettings.WIDGET_PANEL_BLUR_RADIUS_MIN_DP.toFloat()..
                AppSettings.WIDGET_PANEL_BLUR_RADIUS_MAX_DP.toFloat(),
              steps = AppSettings.WIDGET_PANEL_BLUR_RADIUS_MAX_DP -
                AppSettings.WIDGET_PANEL_BLUR_RADIUS_MIN_DP - 1,
              enabled = true,
              label = stringResource(
                R.string.corner_gesture_zone_dp_value,
                uiState.blurRadiusDp,
              ),
              onValueChange = { onBlurRadiusChange(it.roundToInt()) },
            )
          }
        },
      ),
    )
    settingsLazySmallTitle(
      key = "widget-panel-grid-section",
      title = gridSectionTitle,
      sectionTop = true,
    )
    LazySettingsItem(key = "widget-panel-pager") {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        HorizontalPager(
          state = pagerState,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        ) { pageIndex ->
          val page = uiState.pages.getOrElse(pageIndex) { WidgetPanelDefaults.defaultPage }
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            ) {
              WidgetPanelGridEditor(
                page = page,
                pageIndex = pageIndex,
                allPages = uiState.pages,
                widgetPanelBlurEnabled = uiState.blurEnabled,
                gridScrollEnabled = !uiState.isGridInteractionActive,
                onPagesChange = onSavePages,
                onGridInteractionActiveChange = onGridInteractionActiveChange,
              )
            }
          }
        }

        if (uiState.pages.size > 1) {
          Text(
            text = stringResource(R.string.widget_panel_page_indicator, pagerState.currentPage + 1, uiState.pages.size),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
          )
        }
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
  widgetPanelBlurEnabled: Boolean,
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
      pageIndex = pageIndex,
      pagesProvider = { latestPages },
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
      onAppAdded = { packageName, className, label ->
        val updated = WidgetPanelMutator.addAppToPage(
          context,
          latestPages,
          pageIndex,
          packageName,
          className,
          label,
        )
        if (updated != null) {
          onPagesChange(updated)
        }
      },
      onShortcutAdded = { packageName, shortcutId, label, intentUri ->
        val updated = WidgetPanelMutator.addShortcutToPage(
          context,
          latestPages,
          pageIndex,
          packageName,
          shortcutId,
          label,
          intentUri,
        )
        if (updated != null) {
          onPagesChange(updated)
        }
      },
      onActionAdded = { actionPayload, label ->
        val updated = WidgetPanelMutator.addActionToPage(
          context,
          latestPages,
          pageIndex,
          actionPayload,
          label,
        )
        if (updated != null) {
          onPagesChange(updated)
        }
      },
    )
  }

  val pageSettingsCard = settingsCardItems(page) {
    SettingsSliderRow(
      title = stringResource(R.string.widget_panel_opacity),
      value = page.overlayAlpha,
      valueRange = 0f..1f,
      enabled = true,
      label = "${(page.overlayAlpha * 100).toInt()}%",
      formatLabel = { "${(it * 100).toInt()}%" },
      keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
      onValueChange = { alpha ->
        updatePage(latestPages[pageIndex].copy(overlayAlpha = alpha))
      },
    )
    SettingsSliderRow(
      title = stringResource(R.string.widget_panel_columns_title, page.columnCount),
      value = page.columnCount.toFloat(),
      valueRange = 2f..20f,
      steps = 17,
      enabled = true,
      label = page.columnCount.toString(),
      onValueChange = { count ->
        updatePage(
          WidgetPanelGridLogic.fitPageToGrid(
            latestPages[pageIndex].copy(columnCount = count.toInt()),
          ),
        )
      },
    )
    SettingsSliderRow(
      title = stringResource(R.string.widget_panel_visible_rows_title, page.visibleRowCount),
      value = page.visibleRowCount.toFloat(),
      valueRange = 1f..40f,
      steps = 38,
      enabled = true,
      label = page.visibleRowCount.toString(),
      onValueChange = { count ->
        updatePage(latestPages[pageIndex].copy(visibleRowCount = count.toInt()))
      },
    )
    SettingsSliderRow(
      title = stringResource(R.string.widget_panel_cell_width_title, page.cellWidthDp),
      value = page.cellWidthDp.toFloat(),
      valueRange = 30f..120f,
      steps = 90,
      enabled = true,
      label = "${page.cellWidthDp}dp",
      onValueChange = { width ->
        updatePage(latestPages[pageIndex].copy(cellWidthDp = width.toInt()))
      },
    )
    SettingsSliderRow(
      title = stringResource(R.string.widget_panel_margin_top_title, page.marginTopDp),
      value = page.marginTopDp.toFloat(),
      valueRange = 0f..500f,
      steps = 99,
      enabled = true,
      label = "${page.marginTopDp}dp",
      onValueChange = { margin ->
        updatePage(latestPages[pageIndex].copy(marginTopDp = margin.toInt()))
      },
    )
  }

  val previewSectionTitle = stringResource(R.string.floating_pointer_preview_section)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    CardSegment(
      isFirst = true,
      isLast = true,
    ) {
      SettingsCardSegmentContent {
        pageSettingsCard.RenderRows()
      }
    }

    MiuixSmallTitle(
      text = previewSectionTitle,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = MiuixSmallTitleSectionTop),
    )

    CardSegment(
      isFirst = true,
      isLast = true,
      outerBottomPadding = 12.dp,
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
      ) {
      val screenWidthPx = LocalWindowInfo.current.containerSize.width
      val layoutMetrics = WidgetPanelLayoutMetrics.compute(
        screenWidthPx = screenWidthPx,
        page = page,
        density = density.density,
        panelPaddingDp = 12f,
        panelInnerPaddingDp = 4f,
        horizontalInsetDp = 16f,
      )
      val panelWidthDp = with(density) { layoutMetrics.panelWidthPx.toDp() }
      val viewportHeight = with(density) {
        layoutMetrics.viewportHeightPx.toDp().coerceAtLeast(200.dp)
      }
      val gridScrollState = rememberScrollState()

      Box(
        modifier = Modifier
          .width(panelWidthDp)
          .height(viewportHeight)
          .clip(RoundedCornerShape(20.dp))
          .background(
            WidgetPanelUi.panelSurfaceColor(
              overlayAlpha = page.overlayAlpha,
              editMode = true,
              blurEnabled = widgetPanelBlurEnabled,
            ),
          ),
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
}

@Composable
fun SettingsCardScope.WidgetPanelEntryCard(
  settings: ExtensionHubSettings,
  enabled: Boolean,
  outlinedLeadingIcons: Boolean = false,
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
    icon = { label ->
      Icon(HubLeadingIcons.widgetPanel(outlinedLeadingIcons), contentDescription = label)
    },
    title = stringResource(R.string.widget_panel_settings_title),
    subtitle = subtitle,
    enabled = enabled,
    onClick = onClick,
  )
}
