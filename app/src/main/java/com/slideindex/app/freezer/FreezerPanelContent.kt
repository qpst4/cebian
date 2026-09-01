package com.slideindex.app.freezer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchBottomContent
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreezerPanelContent(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onManageApps: (() -> Unit)? = null,
    onAppLaunched: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot(),
    )
    val appRepository = rememberAppRepository()
    var memberApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var freezeStateRevision by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val screenTitle = title ?: stringResource(R.string.extension_freezer_title)
    val unfreezeAllLabel = stringResource(R.string.freezer_batch_unfreeze_all)

    LaunchedEffect(settings.freezerAppPackages) {
        if (settings.freezerAppPackages.isEmpty()) {
            memberApps = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        if (memberApps.isEmpty()) {
            isLoading = true
        }
        memberApps = appRepository.resolveFreezerMembers(settings.freezerAppPackages)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val bootstrap = FreezerBootstrap.scanDisabledLauncherPackages(context)
            if (bootstrap.isNotEmpty()) {
                val current = settingsRepository.readSnapshot().freezerAppPackages
                val merged = current + bootstrap
                if (merged != current) {
                    settingsRepository.setFreezerAppPackages(merged)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                freezeStateRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val handleBack: () -> Unit = {
        if (
            onBack != null &&
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

    val overflowMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = unfreezeAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.unfreezeAll(context, settings.freezerAppPackages) > 0) {
                            freezeStateRevision++
                        }
                    }
                },
            ),
        ),
    )

    SettingsLazyScreenScaffold(
        modifier = modifier,
        title = screenTitle,
        onBack = onBack?.let { handleBack },
        userScrollEnabled = false,
        actions = {
            MiuixExpandableSearchIconAction(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it },
            )
            if (onManageApps != null) {
                IconButton(onClick = onManageApps) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.freezer_manage_apps),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            WindowIconDropdownMenu(entry = overflowMenuEntry) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.freezer_batch_menu),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        bottomContent = {
            MiuixExpandableSearchBottomContent(
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester,
                hintResId = R.string.freezer_grid_search_hint,
            )
        },
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = {
                    scope.launch {
                        if (FreezerOperations.freezeAll(context, settings.freezerAppPackages) > 0) {
                            freezeStateRevision++
                        }
                    }
                },
                icon = Icons.Default.AcUnit,
                contentDescription = stringResource(R.string.freezer_action_freeze),
            )
        },
    ) {
        if (isLoading) {
            item(key = "freezer-loading") {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                }
            }
        } else {
            LazySettingsItem(key = "freezer-grid", fillParentMaxSize = true) {
                FreezerGridUi(
                    settings = settings,
                    memberApps = memberApps,
                    appRepository = appRepository,
                    settingsRepository = settingsRepository,
                    searchQuery = searchQuery,
                    freezeStateRevision = freezeStateRevision,
                    onFreezeStateRevisionBump = { freezeStateRevision++ },
                    modifier = Modifier.fillMaxSize(),
                    onAppLaunched = onAppLaunched,
                    onManageApps = onManageApps,
                )
            }
        }
    }
}
