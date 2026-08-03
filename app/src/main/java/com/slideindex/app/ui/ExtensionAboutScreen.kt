@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import java.util.Calendar

@Composable
fun ExtensionAboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyNotices: () -> Unit,
    onOpenNativeEnginePacks: () -> Unit,
    onCheckUpdate: () -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val projectUrl = stringResource(R.string.about_project_url_desc)
    val qqGroupUrl = stringResource(R.string.about_qq_group_url)
    SettingsScreenScaffold(
        title = stringResource(R.string.about_section_title),
        onBack = onBack,
    ) {
        AboutAppHeader()

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSectionTitle(stringResource(R.string.about_section_app_info))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.SystemUpdate, contentDescription = label) },
                    title = stringResource(R.string.about_check_update_title),
                    subtitle = stringResource(R.string.about_check_update_subtitle),
                    onClick = onCheckUpdate,
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.NewReleases, contentDescription = label) },
                    title = stringResource(R.string.about_release_notes_title),
                    subtitle = stringResource(R.string.about_release_notes_subtitle),
                    onClick = {
                        val uri = (projectUrl + "/releases").toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.about_section_community))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Code, contentDescription = label) },
                    title = stringResource(R.string.about_project_url_title),
                    subtitle = stringResource(R.string.about_project_url_desc),
                    onClick = {
                        val uri = projectUrl.toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Groups, contentDescription = label) },
                    title = stringResource(R.string.about_qq_group_title),
                    subtitle = stringResource(R.string.about_qq_group_desc),
                    onClick = {
                        val uri = qqGroupUrl.toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.about_section_open_source))
            SettingsCard {
                PrivacyPolicyEntryCard(onClick = onOpenPrivacyPolicy)
                OpenSourceLicenseEntryCard(
                    onClick = {
                        val uri = (projectUrl + "/blob/HEAD/LICENSE").toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
                ThirdPartyNoticesEntryCard(
                    onClick = onOpenThirdPartyNotices,
                )
            }

            SettingsSectionTitle(stringResource(R.string.about_advanced_section_title))
            SettingsCard {
                SettingToggleRow(
                    icon = { label -> Icon(Icons.Default.SystemUpdate, contentDescription = label) },
                    title = stringResource(R.string.auto_check_update_title),
                    subtitle = stringResource(R.string.auto_check_update_hint),
                    checked = autoCheckUpdate,
                    onCheckedChange = onAutoCheckUpdateChange,
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Memory, contentDescription = label) },
                    title = stringResource(R.string.extension_native_engine_packs_entry_title),
                    subtitle = stringResource(R.string.about_native_engine_packs_entry_desc),
                    onClick = onOpenNativeEnginePacks,
                )
            }

            AboutCopyrightFooter()
        }
    }
}

@Composable
private fun AboutAppHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCopyrightFooter(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            R.string.about_copyright_notice,
            Calendar.getInstance().get(Calendar.YEAR),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
