package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.search.ShareImageTarget
import com.slideindex.app.search.ShareImageTargetResolver
import com.slideindex.app.search.ShareTextTargetResolver
import com.slideindex.app.ui.Md3PickerDrawableLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareImageTargetPickScreen(
    selectedPackageName: String = "",
    selectedActivityClassName: String = "",
    onBack: () -> Unit,
    onSelectTarget: (ShareImageTarget) -> Unit,
) {
    ShareTargetPickScreen(
        forImage = true,
        selectedPackageName = selectedPackageName,
        selectedActivityClassName = selectedActivityClassName,
        onBack = onBack,
        onSelectTarget = onSelectTarget,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareTargetPickScreen(
    forImage: Boolean,
    selectedPackageName: String = "",
    selectedActivityClassName: String = "",
    onBack: () -> Unit,
    onSelectTarget: (ShareImageTarget) -> Unit,
) {
    val context = LocalContext.current
    var targets by remember { mutableStateOf<List<ShareImageTarget>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(forImage) {
        loading = true
        targets = withContext(Dispatchers.IO) {
            if (forImage) {
                ShareImageTargetResolver.listTargets(context)
            } else {
                ShareTextTargetResolver.listTargets(context)
            }
        }
        loading = false
    }

    val filtered = remember(targets, query, forImage) {
        if (forImage) {
            ShareImageTargetResolver.searchTargets(targets, query)
        } else {
            ShareTextTargetResolver.searchTargets(targets, query)
        }
    }

    val titleRes = if (forImage) {
        R.string.search_engine_pick_share_image_target_title
    } else {
        R.string.search_engine_pick_share_text_target_title
    }
    val hintRes = if (forImage) {
        R.string.search_engine_share_image_target_search_hint
    } else {
        R.string.search_engine_share_text_target_search_hint
    }
    val emptyRes = if (forImage) {
        R.string.search_engine_share_image_target_empty
    } else {
        R.string.search_engine_share_text_target_empty
    }
    val loadingKey = if (forImage) "share-image-target-loading" else "share-text-target-loading"
    val emptyKey = if (forImage) "share-image-target-empty" else "share-text-target-empty"

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(titleRes),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        hintResId = hintRes,
    ) {
        when {
            loading -> {
                item(key = loadingKey) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            filtered.isEmpty() -> {
                item(key = emptyKey) {
                    Text(
                        text = stringResource(emptyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            else -> {
                items(
                    items = filtered,
                    key = { "${it.packageName}/${it.activityClassName}" },
                ) { target ->
                    val index = filtered.indexOf(target)
                    val selected = target.packageName == selectedPackageName &&
                        target.activityClassName == selectedActivityClassName
                    val subtitle = if (forImage) {
                        ShareImageTargetResolver.displaySubtitle(target)
                    } else {
                        ShareTextTargetResolver.displaySubtitle(target)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = PickerListHorizontalPadding,
                                end = PickerListHorizontalPadding,
                                bottom = if (index == filtered.lastIndex) 8.dp else 0.dp,
                            ),
                    ) {
                        Md3PickerListRow(
                            segmentIndex = index,
                            segmentCount = filtered.size,
                            title = target.label,
                            subtitle = subtitle,
                            selected = selected,
                            onClick = { onSelectTarget(target) },
                            leadingContent = {
                                val pm = context.packageManager
                                val drawable = target.icon ?: runCatching {
                                    pm.getApplicationIcon(target.packageName)
                                }.getOrNull()
                                if (drawable != null) {
                                    Md3PickerDrawableLeading(
                                        drawable = drawable,
                                        contentDescription = target.label,
                                        cacheKey = target.activityClassName,
                                    )
                                }
                            },
                            trailingMode = PickerTrailingMode.Radio,
                        )
                    }
                }
            }
        }
    }
}
