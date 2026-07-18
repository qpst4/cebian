package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore

private val ImageSearchBarHeight = 48.dp

internal fun pickResultImageSectionReservedHeight(imageMaxHeight: Dp): Dp {
    val header = 36.dp
    val sectionSpacing = 12.dp
    return header + imageMaxHeight + sectionSpacing + ImageSearchBarHeight + 8.dp
}

@Composable
fun PickResultImageSearchBar(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shareEngines = SearchEngineStore.imageSharePanelEngines(engines)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shareEngines.forEachIndexed { index, engine ->
            if (index > 0) {
                Spacer(modifier = Modifier.size(16.dp))
            }
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
        if (shareEngines.isNotEmpty()) {
            Spacer(modifier = Modifier.size(24.dp))
        }
        PickResultToolbarIcon(Icons.Default.Share, enabled = true, onClick = onShare)
        Spacer(modifier = Modifier.size(24.dp))
        PickResultToolbarIcon(Icons.Default.ImageSearch, enabled = true, onClick = onImageSearch)
        Spacer(modifier = Modifier.size(24.dp))
        PickResultToolbarIcon(Icons.Default.Save, enabled = true, onClick = onSave)
    }
}
