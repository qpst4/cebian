package com.slideindex.app.ui.appswitcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.miuix.MiuixExpandableSearchFieldStrip
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.quicklauncher.QUICK_LAUNCHER_SHEET_ENTER_MS
import com.slideindex.app.ui.quicklauncher.QUICK_LAUNCHER_SHEET_EXIT_MS
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddOverlaySheetBody
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddSubScreen
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitcherSlotConfigSheet(
    slotIndex: Int,
    currentItem: QuickLauncherItem?,
    apps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut> = emptyList(),
    shellCommands: List<ShellCommand> = emptyList(),
    onDismiss: () -> Unit,
    onSelectItem: (QuickLauncherItem?) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit = { _, _ -> },
) {
    var visible by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf<QuickLauncherAddSubScreen>(QuickLauncherAddSubScreen.Main) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val requestDismiss = remember { { visible = false } }

    val handleBack: () -> Unit = {
        when (subScreen) {
            QuickLauncherAddSubScreen.Main -> {
                if (
                    !consumeExpandableSearchBack(
                        expanded = searchExpanded,
                        query = searchQuery,
                        onExpandedChange = { searchExpanded = it },
                        onQueryChange = { searchQuery = it },
                    )
                ) {
                    requestDismiss()
                }
            }
            QuickLauncherAddSubScreen.PickApp -> subScreen = QuickLauncherAddSubScreen.Main
            is QuickLauncherAddSubScreen.PickActivity -> subScreen = QuickLauncherAddSubScreen.PickApp
            is QuickLauncherAddSubScreen.ShellCommandConfig -> subScreen = QuickLauncherAddSubScreen.Main
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(QUICK_LAUNCHER_SHEET_EXIT_MS.toLong())
            onDismiss()
        }
    }

    val searchHintResId = when (selectedTab) {
        0 -> R.string.search_actions_hint
        else -> R.string.search_hint
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = requestDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)),
            exit = fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 2 },
                animationSpec = tween(QUICK_LAUNCHER_SHEET_ENTER_MS),
            ) + fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight / 2 },
                animationSpec = tween(QUICK_LAUNCHER_SHEET_EXIT_MS),
            ) + fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (subScreen != QuickLauncherAddSubScreen.Main) {
                            IconButton(onClick = handleBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_navigate_back),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        ) {
                            Text(
                                text = "圆环启动器 · 槽位 ${slotIndex + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val statusText = if (currentItem != null && currentItem.payload.isNotBlank()) {
                                "已绑定: ${currentItem.label.ifBlank { currentItem.payload }}"
                            } else {
                                "未配置（自动填充最近任务）"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (currentItem != null && currentItem.payload.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    onSelectItem(null)
                                    requestDismiss()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "恢复默认")
                            }
                        }

                        if (subScreen == QuickLauncherAddSubScreen.Main) {
                            MiuixExpandableSearchIconAction(
                                expanded = searchExpanded,
                                query = searchQuery,
                                onExpandedChange = { searchExpanded = it },
                                onQueryChange = { searchQuery = it },
                            )
                        }

                        IconButton(onClick = requestDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                            )
                        }
                    }

                    if (subScreen == QuickLauncherAddSubScreen.Main) {
                        MiuixExpandableSearchFieldStrip(
                            expanded = searchExpanded,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            focusRequester = searchFocusRequester,
                            hintResId = searchHintResId,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        MiuixTabRowWithContour(
                            tabs = listOf(
                                stringResource(R.string.action_picker_tab_actions),
                                stringResource(R.string.action_picker_tab_apps),
                                stringResource(R.string.action_picker_tab_shortcuts),
                            ),
                            selectedTabIndex = selectedTab,
                            onTabSelected = { selectedTab = it },
                            contourHost = MiuixTabRowContourHost.SurfaceContainer,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        QuickLauncherAddOverlaySheetBody(
                            modifier = Modifier.fillMaxSize(),
                            padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            nestedScrollConnection = null,
                            searchQuery = searchQuery,
                            apps = apps,
                            addedAppPackages = emptySet(),
                            addedShortcutKeys = emptySet(),
                            addedActionKeys = emptySet(),
                            activityShortcuts = activityShortcuts,
                            shellCommands = shellCommands,
                            onToggle = { item, _ ->
                                onSelectItem(item)
                                requestDismiss()
                            },
                            launchCreateShortcut = launchCreateShortcut,
                            subScreen = subScreen,
                            onSubScreenChange = { subScreen = it },
                            selectedTab = selectedTab,
                        )
                    }
                }
            }
        }
    }
}
