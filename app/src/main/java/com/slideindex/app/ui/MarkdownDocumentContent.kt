package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

private const val ASSET_LICENSE_PREFIX = "app/src/main/assets/licenses/"

@Composable
fun MarkdownDocumentContent(
    markdown: String,
    projectBaseUrl: String,
    onOpenAssetLicense: (fileName: String) -> Unit,
    modifier: Modifier = Modifier,
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
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Markdown(
            markdownState = markdownState,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
