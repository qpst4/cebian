@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionHubScreen(
    settings: AppSettings,
    gestureActive: Boolean,
    bottomContentPadding: Dp = 0.dp,
    onOpenLayoutSettings: () -> Unit,
    onOpenQuickLauncher: () -> Unit,
    onOpenShellCommands: () -> Unit,
    onOpenWidgetPanel: () -> Unit,
    onOpenFloatingPointer: () -> Unit,
    onOpenSettingsBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.main_nav_extension),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                    )
                },
                subtitle = {
                    Text(stringResource(R.string.extension_hub_subtitle))
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_section_features))
            SettingsCard {
                LayoutSettingsEntryCard(
                    settings = settings,
                    enabled = gestureActive,
                    onClick = onOpenLayoutSettings,
                )
                QuickLauncherEntryCard(
                    settings = settings,
                    enabled = gestureActive,
                    onClick = onOpenQuickLauncher,
                )
                ShellCommandEntryCard(
                    commandCount = settings.shellCommands.size,
                    onClick = onOpenShellCommands,
                )
                WidgetPanelEntryCard(
                    settings = settings,
                    enabled = gestureActive,
                    onClick = onOpenWidgetPanel,
                )
                FloatingPointerEntryCard(
                    settings = settings,
                    enabled = gestureActive,
                    onClick = onOpenFloatingPointer,
                )
                SettingsBackupEntryCard(onClick = onOpenSettingsBackup)
            }
            
            SettingsSectionTitle(stringResource(R.string.about_section_title))
            SettingsCard {
                AboutEntryCard(onClick = onOpenAbout)
            }

            Spacer(modifier = Modifier.height(8.dp + bottomContentPadding))
        }
    }
}

@Composable
fun AboutEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Info, contentDescription = label) },
        title = stringResource(R.string.about_section_title),
        subtitle = "版本、更新与隐私协议",
        onClick = onClick,
    )
}

@Composable
fun PrivacyPolicyEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Policy, contentDescription = label) },
        title = stringResource(R.string.privacy_policy_entry_title),
        subtitle = stringResource(R.string.privacy_policy_entry_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsBackupEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.Backup, contentDescription = label) },
        title = stringResource(R.string.settings_backup_entry_title),
        subtitle = stringResource(R.string.settings_backup_entry_desc),
        onClick = onClick,
    )
}
