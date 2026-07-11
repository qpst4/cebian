package com.slideindex.app.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.animateFloatAsState
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.service.WidgetConfigureTrampolineActivity
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.widget.WidgetCanvasLayout
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelGridLogic
import com.slideindex.app.widget.WidgetPanelLayoutMetrics
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WidgetPopupContentRenderer(
    settings: AppSettings,
    visible: Boolean,
    blockingTouches: Boolean,
    widgetAddFlowActive: Boolean,
    side: PanelSide?,
    anchorRawY: Float?,
    hostContext: android.content.Context,
    deps: OverlayDependencies,
    onDismissOutside: () -> Unit,
    onDismiss: () -> Unit,
    onSavePages: (List<WidgetPanelPage>) -> Unit,
) {
    SlideIndexTheme(
        seedColor = Color(settings.themeColorArgb),
        dynamicColor = settings.dynamicColorEnabled,
    ) {
        val density = LocalDensity.current
        val windowInfo = LocalWindowInfo.current
        val densityValue = density.density
        val screenWidthPx = windowInfo.containerSize.width

        var pages by remember {
            mutableStateOf(
                WidgetPanelDefaults.effectivePages(settings.widgetPanelPages)
                    .map { WidgetPanelGridLogic.fitPageToGrid(it) },
            )
        }
        val latestPages by rememberUpdatedState(pages)
        var editMode by remember { mutableStateOf(false) }
        var gridInteractionActive by remember { mutableStateOf(false) }
        val pagerState = rememberPagerState(pageCount = { pages.size })

        LaunchedEffect(visible) {
            if (!visible) {
                editMode = false
                gridInteractionActive = false
                return@LaunchedEffect
            }
            val stored = deps.settingsRepository.settings.first().widgetPanelPages
            pages = WidgetPanelDefaults.effectivePages(stored)
                .map { WidgetPanelGridLogic.fitPageToGrid(it) }
        }

        LaunchedEffect(editMode) {
            if (!editMode) {
                gridInteractionActive = false
            }
        }

        fun persist(updated: List<WidgetPanelPage>) {
            pages = updated
            onSavePages(updated)
        }

        fun launchWidgetPicker(pageIndex: Int) {
            WidgetPickerTrampoline.launch(
                context = hostContext,
                onAdded = { appWidgetId ->
                    val updated = WidgetPanelMutator.addWidgetToPage(
                        hostContext,
                        latestPages,
                        pageIndex,
                        appWidgetId,
                    )
                    if (updated != null) {
                        persist(updated)
                    }
                },
            )
        }

        val progress by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) OverlayPanelEnterAnimation.enterSpec else OverlayPanelEnterAnimation.exitSpec,
            label = "widgetPopupProgress",
        )

        val page = pages.getOrElse(pagerState.currentPage) { pages.first() }
        val panelAlpha = page.overlayAlpha.coerceIn(0.2f, 0.95f)
        val panelSurfaceColor = Color(0xFF1C1C1E).copy(
            alpha = if (editMode) {
                (panelAlpha + 0.2f).coerceAtMost(0.88f)
            } else if (settings.widgetPanelBlurEnabled) {
                (panelAlpha * 0.55f + 0.18f).coerceAtMost(0.72f)
            } else {
                (panelAlpha + 0.1f).coerceAtMost(0.82f)
            },
        )
        val layoutMetrics = WidgetPanelLayoutMetrics.compute(
            screenWidthPx = screenWidthPx,
            page = page,
            density = density.density,
            panelPaddingDp = PANEL_PADDING_DP,
            panelInnerPaddingDp = PANEL_INNER_PADDING_DP,
        )
        val panelWidthDp = with(density) { layoutMetrics.panelWidthPx.toDp() }
        val viewportHeightDp = with(density) { layoutMetrics.viewportHeightPx.toDp() }
        val marginTopDp = page.marginTopDp.dp

        Box(Modifier.fillMaxSize()) {
            if (widgetAddFlowActive) {
                return@Box
            }
            WidgetPopupTouchHandler(
                blockingTouches = blockingTouches,
                visible = visible,
                editMode = editMode,
                progress = progress,
                onDismissOutside = onDismissOutside,
                onExitEditMode = { editMode = false },
            )

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = marginTopDp)
                    .width(panelWidthDp)
                    .graphicsLayer {
                        alpha = OverlayPanelEnterAnimation.alpha(progress)
                        scaleX = 0.92f + 0.08f * progress
                        scaleY = 0.92f + 0.08f * progress
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(panelSurfaceColor),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PANEL_PADDING_DP.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (editMode) {
                            Text(
                                text = stringResource(R.string.widget_panel_edit_mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        } else if (page.items.isEmpty()) {
                            Text(
                                text = stringResource(R.string.widget_panel_edit_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(viewportHeightDp)
                                .clip(RoundedCornerShape(12.dp))
                                .verticalScroll(rememberScrollState(), enabled = !gridInteractionActive),
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                userScrollEnabled = !editMode,
                            ) { pageIndex ->
                                val currentPage = pages[pageIndex]
                                AndroidView(
                                    factory = { ctx ->
                                        WidgetCanvasLayout(ctx).apply {
                                            val pad = (PANEL_INNER_PADDING_DP * densityValue).roundToInt()
                                            setPadding(pad, pad, pad, pad)
                                            onLongPressBlank = { editMode = true }
                                            onTapBlank = { if (!editMode) onDismissOutside() }
                                            onPageCommitted = { committedPage ->
                                                persist(WidgetPanelMutator.replacePage(latestPages, pageIndex, committedPage))
                                            }
                                            onItemRemoved = { widgetId ->
                                                persist(WidgetPanelMutator.removeWidgetFromPage(hostContext, latestPages, pageIndex, widgetId))
                                            }
                                            onConfigureWidget = { widgetId ->
                                                val intent = WidgetConfigureTrampolineActivity.createIntent(hostContext, widgetId)
                                                runCatching { hostContext.startActivity(intent) }
                                                onDismiss()
                                            }
                                            onAddWidgetRequested = { launchWidgetPicker(pageIndex) }
                                            onInteractionActiveChange = { gridInteractionActive = it }
                                            bindIfNeeded(currentPage, hostContext)
                                            this.editMode = editMode
                                        }
                                    },
                                    update = { grid ->
                                        grid.onLongPressBlank = { editMode = true }
                                        grid.onTapBlank = { if (!editMode) onDismissOutside() }
                                        grid.onPageCommitted = { committedPage ->
                                            persist(WidgetPanelMutator.replacePage(latestPages, pageIndex, committedPage))
                                        }
                                        grid.onItemRemoved = { widgetId ->
                                            persist(WidgetPanelMutator.removeWidgetFromPage(hostContext, latestPages, pageIndex, widgetId))
                                        }
                                        grid.onConfigureWidget = { widgetId ->
                                            val intent = WidgetConfigureTrampolineActivity.createIntent(hostContext, widgetId)
                                            runCatching { hostContext.startActivity(intent) }
                                            onDismiss()
                                        }
                                        grid.onAddWidgetRequested = { launchWidgetPicker(pageIndex) }
                                        grid.onInteractionActiveChange = { gridInteractionActive = it }
                                        grid.editMode = editMode
                                        grid.bindIfNeeded(currentPage, hostContext)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .nestedScroll(rememberNestedScrollInteropConnection()),
                                    onRelease = { /* host views cleaned on rebind */ },
                                )
                            }
                        }

                        if (pages.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                pages.forEachIndexed { index, _ ->
                                    Box(
                                        Modifier
                                            .padding(horizontal = 3.dp)
                                            .height(6.dp)
                                            .width(if (pagerState.currentPage == index) 12.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (pagerState.currentPage == index) {
                                                    Color.White.copy(alpha = 0.7f)
                                                } else {
                                                    Color.White.copy(alpha = 0.25f)
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                if (visible && editMode) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val fabSize = 48.dp
                        FloatingActionButton(
                            onClick = { launchWidgetPicker(pagerState.currentPage) },
                            modifier = Modifier.size(fabSize),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.widget_panel_add_widget))
                        }
                        FloatingActionButton(
                            onClick = { editMode = false },
                            modifier = Modifier.size(fabSize),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.widget_panel_exit_edit))
                        }
                    }
                }
            }
        }
    }
}

private const val PANEL_PADDING_DP = 12f
private const val PANEL_INNER_PADDING_DP = 4f
