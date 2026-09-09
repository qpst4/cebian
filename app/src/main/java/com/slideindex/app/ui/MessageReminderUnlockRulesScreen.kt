package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.util.PinyinHelper
import top.yukonga.miuix.kmp.basic.SmallTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageReminderUnlockRulesScreen(
    settings: MessageSettings,
    onBack: () -> Unit,
    onAlwaysAllowChange: (String, Boolean) -> Unit,
    onUnlockConfirmationAutoDismissSecondsChange: (Int) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val controlsEnabled = settings.enabled && settings.openLastMessageOnUnlock
    val neverDismissLabel = stringResource(R.string.message_reminder_unlock_auto_dismiss_never)
    val unlockRulesDesc = stringResource(R.string.message_reminder_open_last_rules_desc_page)
    val unlockAutoDismissDesc = stringResource(R.string.message_reminder_unlock_auto_dismiss_desc)
    val autoDismissTitle = stringResource(R.string.message_reminder_unlock_auto_dismiss)
    val alwaysAllowSectionTitle = stringResource(R.string.message_reminder_open_last_rules_always)
    val emptyAppsText = stringResource(R.string.message_reminder_open_last_rules_empty)
    val autoDismissSeconds = settings.unlockConfirmationAutoDismissSeconds

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
    }

    val apps = remember(allApps, settings.enabledPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { it.packageName in settings.enabledPackages }
            .filter {
                query.isEmpty() ||
                    it.label.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query) ||
                    it.pinyinKey.contains(query)
            }
            .sortedBy { it.pinyinKey }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.message_reminder_open_last_rules_title),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
    ) {
        settingsLazyHint(
            key = "unlock-rules-desc",
            text = unlockRulesDesc,
        )
        settingsLazyHint(
            key = "unlock-auto-dismiss-desc",
            text = unlockAutoDismissDesc,
        )
        groupedCardItems(
            keyPrefix = "unlock-confirm-dismiss",
            items = listOf(
                settingsCardScopeItem("auto-dismiss") {
                    SettingsSliderRow(
                        title = autoDismissTitle,
                        value = autoDismissSeconds.toFloat(),
                        valueRange = 0f..30f,
                        steps = 29,
                        enabled = controlsEnabled,
                        label = if (autoDismissSeconds == 0) {
                            neverDismissLabel
                        } else {
                            "${autoDismissSeconds}s"
                        },
                        formatLabel = { seconds ->
                            if (seconds == 0f) {
                                neverDismissLabel
                            } else {
                                "${seconds.toInt()}s"
                            }
                        },
                        onValueChange = { onUnlockConfirmationAutoDismissSecondsChange(it.toInt()) },
                    )
                },
            ),
        )
        item(key = "section-apps") {
            SmallTitle(
                text = alwaysAllowSectionTitle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (apps.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = emptyAppsText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                )
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                val entry = AppPackageEntry.Installed(app)
                Md3PickerListRow(
                    segmentIndex = apps.indexOf(app),
                    segmentCount = apps.size,
                    title = app.label,
                    subtitle = app.packageName,
                    selected = app.packageName in settings.openLastMessageAlwaysPackages,
                    onClick = {
                        onAlwaysAllowChange(
                            app.packageName,
                            app.packageName !in settings.openLastMessageAlwaysPackages,
                        )
                    },
                    leadingContent = {
                        Md3PickerAppEntryLeading(entry = entry, missingIcon = Icons.Default.Block)
                    },
                    trailingMode = PickerTrailingMode.Toggle,
                    onTrailingClick = {
                        onAlwaysAllowChange(
                            app.packageName,
                            app.packageName !in settings.openLastMessageAlwaysPackages,
                        )
                    },
                )
            }
        }
    }
}
