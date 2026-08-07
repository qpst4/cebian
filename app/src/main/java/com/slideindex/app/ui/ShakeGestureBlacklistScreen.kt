package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeGestureBlacklistScreen(
    blacklistedPackages: Set<String>,
    onBack: () -> Unit,
    onOpenAddApp: () -> Unit,
    onRemoveBlacklistedApp: (String) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
        isLoading = false
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val blacklistedEntries = remember(blacklistedPackages, allApps) {
        blacklistedPackages.sorted().map { packageName ->
            appsByPackage[packageName]?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.shake_gestures_app_blacklist),
        onBack = onBack,
    ) {
        managedAppListDescription(key = "desc") {
            stringResource(R.string.shake_gestures_app_blacklist_desc)
        }
        managedAppListSectionTitle(
            key = "section-blacklisted",
            title = { stringResource(R.string.shake_gestures_blacklist_section_blocked) },
        )
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(message = stringResource(R.string.loading))
                }
            }
            blacklistedEntries.isEmpty() -> {
                managedAppListEmpty(key = "blacklisted-empty") {
                    stringResource(R.string.shake_gestures_blacklist_empty)
                }
            }
            else -> {
                managedAppPackageRows(
                    keyPrefix = "blacklisted",
                    entries = blacklistedEntries,
                    actionIcon = Icons.Default.Close,
                    actionDescription = { stringResource(R.string.shake_gestures_blacklist_remove) },
                    missingIcon = Icons.Default.Block,
                    onAction = { onRemoveBlacklistedApp(it.packageName) },
                )
            }
        }
        managedAppListAddRow(
            title = { stringResource(R.string.shake_gestures_blacklist_section_add) },
            onClick = onOpenAddApp,
        )
    }
}
