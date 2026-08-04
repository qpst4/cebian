package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThirdPartyNoticesScreen(
    onBack: () -> Unit,
    onOpenLicenseText: (assetFileName: String) -> Unit,
) {
    val context = LocalContext.current
    val projectBaseUrl = stringResource(R.string.about_project_url_desc)
    val body = remember {
        runCatching {
            context.assets.open("licenses/third_party_notices.md")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "" }
    }
    val (introMarkdown, sections) = remember(body) { parseThirdPartyNoticeSections(body) }

    SettingsScreenScaffold(
        title = stringResource(R.string.about_third_party_notices_title),
        subtitle = stringResource(R.string.about_third_party_notices_subtitle),
        onBack = onBack,
    ) {
        if (introMarkdown.isNotBlank()) {
            MarkdownDocumentContent(
                markdown = introMarkdown,
                projectBaseUrl = projectBaseUrl,
                onOpenAssetLicense = onOpenLicenseText,
                compact = true,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        sections.forEach { section ->
            MiuixSmallTitle(section.title)
            SettingsCard {
                MarkdownDocumentContent(
                    markdown = section.bodyMarkdown,
                    projectBaseUrl = projectBaseUrl,
                    onOpenAssetLicense = onOpenLicenseText,
                    compact = true, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}
