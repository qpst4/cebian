@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
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
                        val uri = Uri.parse(projectUrl + "/releases")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
                PrivacyPolicyEntryCard(onClick = onOpenPrivacyPolicy)
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Code, contentDescription = label) },
                    title = stringResource(R.string.about_project_url_title),
                    subtitle = stringResource(R.string.about_project_url_desc),
                    onClick = {
                        val uri = Uri.parse(projectUrl)
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                )
            }
        }
    }
}
