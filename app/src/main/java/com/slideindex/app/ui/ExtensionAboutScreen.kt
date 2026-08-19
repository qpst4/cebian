@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
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

    val appInfoTitle = stringResource(R.string.about_section_app_info)
    val communityTitle = stringResource(R.string.about_section_community)
    val openSourceTitle = stringResource(R.string.about_section_open_source)
    val advancedTitle = stringResource(R.string.about_advanced_section_title)

    SettingsScreenScaffold(
        title = stringResource(R.string.about_section_title),
        onBack = onBack,
    ) {
        LazySettingsItem(key = "about-header") {
            AboutAppHeader()
        }

        settingsLazySmallTitle(key = "app_info_section", title = appInfoTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "about_app_info",
            items = buildList {
                add(
                    settingsCardScopeItem("check-update") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.SystemUpdate, contentDescription = label) },
                            title = stringResource(R.string.about_check_update_title),
                            subtitle = stringResource(R.string.about_check_update_subtitle),
                            onClick = onCheckUpdate,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("release-notes") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.NewReleases, contentDescription = label) },
                            title = stringResource(R.string.about_release_notes_title),
                            subtitle = stringResource(R.string.about_release_notes_subtitle),
                            onClick = {
                                val uri = (projectUrl + "/releases").toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "community_section", title = communityTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "about_community",
            items = buildList {
                add(
                    settingsCardScopeItem("project-url") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Code, contentDescription = label) },
                            title = stringResource(R.string.about_project_url_title),
                            subtitle = stringResource(R.string.about_project_url_desc),
                            onClick = {
                                val uri = projectUrl.toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("qq-group") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Groups, contentDescription = label) },
                            title = stringResource(R.string.about_qq_group_title),
                            subtitle = stringResource(R.string.about_qq_group_desc),
                            onClick = {
                                val uri = qqGroupUrl.toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "open_source_section", title = openSourceTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "about_open_source",
            items = buildList {
                add(
                    settingsCardScopeItem("privacy-policy") {
                        PrivacyPolicyEntryCard(onClick = onOpenPrivacyPolicy)
                    },
                )
                add(
                    settingsCardScopeItem("open-source-license") {
                        OpenSourceLicenseEntryCard(
                            onClick = {
                                val uri = (projectUrl + "/blob/HEAD/LICENSE").toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("third-party-notices") {
                        ThirdPartyNoticesEntryCard(onClick = onOpenThirdPartyNotices)
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "advanced_section", title = advancedTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "about_advanced",
            items = buildList {
                add(
                    settingsCardScopeItem("auto-check-update") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Outlined.SystemUpdate, contentDescription = label) },
                            title = stringResource(R.string.auto_check_update_title),
                            subtitle = stringResource(R.string.auto_check_update_hint),
                            checked = autoCheckUpdate,
                            onCheckedChange = onAutoCheckUpdateChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("native-engine-packs") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Memory, contentDescription = label) },
                            title = stringResource(R.string.extension_native_engine_packs_entry_title),
                            subtitle = stringResource(R.string.about_native_engine_packs_entry_desc),
                            onClick = onOpenNativeEnginePacks,
                        )
                    },
                )
            },
        )

        LazySettingsItem(key = "about-footer") {
            AboutCopyrightFooter()
        }
    }
}

@Composable
private fun AboutAppHeader(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.primaryContainer, colorScheme.secondaryContainer),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.about_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
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
