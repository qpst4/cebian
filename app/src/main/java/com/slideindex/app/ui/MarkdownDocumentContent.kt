package com.slideindex.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

private const val ASSET_LICENSE_PREFIX = "app/src/main/assets/licenses/"

@Composable
private fun appMarkdownTypography(compact: Boolean): MarkdownTypography {
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    return markdownTypography(
        h1 = if (compact) typography.titleLarge else typography.headlineSmall,
        h2 = if (compact) typography.titleMedium else typography.titleLarge,
        h3 = typography.titleSmall,
        h4 = typography.titleSmall,
        h5 = typography.labelLarge,
        h6 = typography.labelLarge,
        text = typography.bodyMedium,
        paragraph = typography.bodyMedium,
        ordered = typography.bodyMedium,
        bullet = typography.bodyMedium,
        list = typography.bodyMedium,
        quote = typography.bodyMedium,
        code = typography.bodySmall,
        table = typography.bodyMedium,
        textLink = TextLinkStyles(
            style = typography.bodyMedium.copy(
                color = colors.primary,
                textDecoration = TextDecoration.None,
            ).toSpanStyle(),
        ),
    )
}

@Composable
fun MarkdownDocumentContent(
    markdown: String,
    projectBaseUrl: String,
    onOpenAssetLicense: (fileName: String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val defaultUriHandler = LocalUriHandler.current
    val uriHandler = remember(projectBaseUrl, defaultUriHandler, onOpenAssetLicense) {
        object : UriHandler {
            override fun openUri(uri: String) {
                when {
                    uri.startsWith("http://") || uri.startsWith("https://") -> {
                        defaultUriHandler.openUri(uri)
                    }
                    uri.startsWith(ASSET_LICENSE_PREFIX) && uri.endsWith(".txt") -> {
                        onOpenAssetLicense(uri.removePrefix(ASSET_LICENSE_PREFIX))
                    }
                    uri.endsWith(".txt") && !uri.contains("/") -> {
                        onOpenAssetLicense(uri)
                    }
                    else -> {
                        val path = uri.trimStart('/')
                        val githubUrl = "$projectBaseUrl/blob/HEAD/$path"
                        defaultUriHandler.openUri(githubUrl)
                    }
                }
            }
        }
    }
    val markdownState = rememberMarkdownState(markdown)
    val markdownTypography = appMarkdownTypography(compact)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Markdown(
            markdownState = markdownState,
            modifier = modifier,
            typography = markdownTypography,
        )
    }
}
