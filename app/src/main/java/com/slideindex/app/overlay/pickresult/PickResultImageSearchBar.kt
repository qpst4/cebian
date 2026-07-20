package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore

private val ImageSearchBarHeight = 40.dp

private val ImageSectionItemSpacing = 6.dp
private val ImageSearchBarBottomPadding = 4.dp

internal fun pickResultImageSectionReservedHeight(imageMaxHeight: Dp): Dp {
    val header = 36.dp
    return header + imageMaxHeight + ImageSectionItemSpacing + ImageSearchBarHeight + ImageSearchBarBottomPadding
}

@Composable
fun PickResultImageSearchBar(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shareEngines = SearchEngineStore.imageSharePanelEngines(engines)
    if (shareEngines.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = ImageSearchBarBottomPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickResultImageSearchActions(
                onShare = onShare,
                onImageSearch = onImageSearch,
                onSave = onSave,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ImageSearchBarBottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shareEngines.forEach { engine ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onShareEngineClick(engine) },
                    contentAlignment = Alignment.Center,
                ) {
                    SearchEngineIcon(
                        engine = engine,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        PickResultImageSearchActions(
            onShare = onShare,
            onImageSearch = onImageSearch,
            onSave = onSave,
            onPinToScreen = onPinToScreen,
            onStash = onStash,
        )
    }
}

@Composable
private fun PickResultImageSearchActions(
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onPinToScreen?.let { PickResultToolbarIcon(Icons.Default.PushPin, enabled = true, onClick = it) }
        onStash?.let { PickResultToolbarIcon(Icons.Default.Inventory2, enabled = true, onClick = it) }
        PickResultToolbarIcon(Icons.Default.Share, enabled = true, onClick = onShare)
        PickResultToolbarIcon(Icons.Default.ImageSearch, enabled = true, onClick = onImageSearch)
        PickResultToolbarIcon(Icons.Default.Save, enabled = true, onClick = onSave)
    }
}
