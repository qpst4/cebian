package com.slideindex.app.freezer

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PickerAppIconBitmap
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.popup.WindowDropdownDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun FreezerGridUi(
    settings: AppSettings,
    memberApps: List<AppInfo>,
    appRepository: AppRepository,
    settingsRepository: SettingsRepository,
    searchQuery: String,
    freezeStateRevision: Int,
    onFreezeStateRevisionBump: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onAppLaunched: (() -> Unit)? = null,
    onManageApps: (() -> Unit)? = null,
    overlayMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionTarget by remember { mutableStateOf<AppInfo?>(null) }

    val filteredApps = remember(memberApps, searchQuery) {
        val query = searchQuery.trim().lowercase()
        memberApps.filter { app ->
            query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true) ||
                app.pinyinKey.contains(query)
        }
    }

    val columnCount = if (
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        8
    } else {
        4
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            memberApps.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(R.string.freezer_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (onManageApps != null) {
                        TextButton(onClick = onManageApps, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.freezer_manage_apps))
                        }
                    }
                }
            }
            filteredApps.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = filteredApps,
                        key = { app -> "${app.packageName}_$freezeStateRevision" },
                    ) { app ->
                        val frozen = FreezerOperations.isFrozen(context, app.packageName)
                        FreezerGridItem(
                            app = app,
                            frozen = frozen,
                            onClick = {
                                scope.launch {
                                    if (!FreezerOperations.launchAndUnfreeze(
                                            context,
                                            appRepository,
                                            settings,
                                            app,
                                        )
                                    ) {
                                        Toast.makeText(
                                            context,
                                            R.string.freezer_launch_failed,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@launch
                                    }
                                    onFreezeStateRevisionBump()
                                    onAppLaunched?.invoke()
                                }
                            },
                            onLongClick = { actionTarget = app },
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { app ->
        val frozen = FreezerOperations.isFrozen(context, app.packageName)
        val dismissMenu = { actionTarget = null }
        val menuActions = FreezerAppActionCallbacks(
            onLaunchFreeWindow = {
                scope.launch {
                    if (!FreezerOperations.launchAndUnfreeze(
                            context,
                            appRepository,
                            settings,
                            app,
                            fullscreen = false,
                        )
                    ) {
                        Toast.makeText(
                            context,
                            R.string.freezer_launch_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@launch
                    }
                    onFreezeStateRevisionBump()
                    onAppLaunched?.invoke()
                    dismissMenu()
                }
            },
            onToggleFrozen = {
                scope.launch {
                    if (FreezerOperations.setFrozen(context, app.packageName, !frozen)) {
                        onFreezeStateRevisionBump()
                    }
                    dismissMenu()
                }
            },
            onAddToHome = {
                if (!FreezerAppShortcutHelper.requestPinAppShortcut(context, app)) {
                    FreezerAppShortcutHelper.showPinShortcutFailedToast(context)
                }
                dismissMenu()
            },
            onRemoveFromList = {
                scope.launch {
                    settingsRepository.removeFreezerApp(app.packageName)
                    dismissMenu()
                }
            },
        )
        if (overlayMode) {
            FreezerAppActionOverlayMenu(
                app = app,
                frozen = frozen,
                onDismiss = dismissMenu,
                callbacks = menuActions,
            )
        } else {
            FreezerAppActionWindowMenu(
                app = app,
                frozen = frozen,
                onDismiss = dismissMenu,
                callbacks = menuActions,
            )
        }
    }
}

private data class FreezerAppActionCallbacks(
    val onLaunchFreeWindow: () -> Unit,
    val onToggleFrozen: () -> Unit,
    val onAddToHome: () -> Unit,
    val onRemoveFromList: () -> Unit,
)

@Composable
private fun FreezerAppActionWindowMenu(
    app: AppInfo,
    frozen: Boolean,
    onDismiss: () -> Unit,
    callbacks: FreezerAppActionCallbacks,
) {
    val launchFreeWindowLabel = stringResource(R.string.task_switcher_menu_free_window)
    val freezeLabel = stringResource(
        if (frozen) R.string.freezer_action_unfreeze else R.string.freezer_action_freeze,
    )
    val addToHomeLabel = stringResource(R.string.freezer_action_add_to_home)
    val removeFromListLabel = stringResource(R.string.freezer_remove_from_list)
    val cancelLabel = stringResource(R.string.cancel)
    val menuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = launchFreeWindowLabel,
                onClick = callbacks.onLaunchFreeWindow,
            ),
            DropdownItem(
                text = freezeLabel,
                onClick = callbacks.onToggleFrozen,
            ),
            DropdownItem(
                text = addToHomeLabel,
                onClick = callbacks.onAddToHome,
            ),
            DropdownItem(
                text = removeFromListLabel,
                onClick = callbacks.onRemoveFromList,
            ),
        ),
    )
    WindowDropdownDialog(
        entry = menuEntry,
        title = app.label,
        dialogButtonString = cancelLabel,
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = {},
        dropdownColors = DropdownDefaults.dropdownColors(),
    )
}

@Composable
private fun FreezerAppActionOverlayMenu(
    app: AppInfo,
    frozen: Boolean,
    onDismiss: () -> Unit,
    callbacks: FreezerAppActionCallbacks,
) {
    val launchFreeWindowLabel = stringResource(R.string.task_switcher_menu_free_window)
    val freezeLabel = stringResource(
        if (frozen) R.string.freezer_action_unfreeze else R.string.freezer_action_freeze,
    )
    val addToHomeLabel = stringResource(R.string.freezer_action_add_to_home)
    val removeFromListLabel = stringResource(R.string.freezer_remove_from_list)
    val cancelLabel = stringResource(R.string.cancel)
    val menuItems = listOf(
        launchFreeWindowLabel to callbacks.onLaunchFreeWindow,
        freezeLabel to callbacks.onToggleFrozen,
        addToHomeLabel to callbacks.onAddToHome,
        removeFromListLabel to callbacks.onRemoveFromList,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MiuixText(
                    text = app.label,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                MiuixText(
                    text = app.packageName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                menuItems.forEach { (label, onClick) ->
                    MiuixTextButton(
                        text = label,
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MiuixTextButton(
                    text = cancelLabel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreezerGridItem(
    app: AppInfo,
    frozen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) {
        mutableStateOf(PickerAppIconBitmap.peek(app.packageName))
    }
    LaunchedEffect(app.packageName) {
        iconBitmap = PickerAppIconBitmap.load(context, app.packageName)
    }
    val grayscale = if (frozen) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = app.label,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
                colorFilter = grayscale,
            )
        } else {
            Box(modifier = Modifier.size(64.dp))
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (frozen) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
