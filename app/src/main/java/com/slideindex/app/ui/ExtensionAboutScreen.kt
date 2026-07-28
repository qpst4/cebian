@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.BuildConfig
import com.slideindex.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionAboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyNotices: () -> Unit,
    onOpenNativeEnginePacks: () -> Unit,
) {
    val context = LocalContext.current
    val projectUrl = stringResource(R.string.about_project_url_desc)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_section_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.NewReleases, contentDescription = label) },
                    title = stringResource(R.string.about_release_notes_title),
                    subtitle = "当前版本: ${BuildConfig.VERSION_NAME}",
                    onClick = {
                        val uri = (projectUrl + "/releases").toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
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
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Code, contentDescription = label) },
                    title = stringResource(R.string.about_project_url_title),
                    subtitle = stringResource(R.string.about_project_url_desc),
                    onClick = {
                        val uri = projectUrl.toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.about_advanced_section_title))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Memory, contentDescription = label) },
                    title = stringResource(R.string.extension_native_engine_packs_entry_title),
                    subtitle = stringResource(R.string.about_native_engine_packs_entry_desc),
                    onClick = onOpenNativeEnginePacks,
                )
            }
        }
    }
}
