package com.slideindex.app.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.settings.PresetSearchCategory
import com.slideindex.app.settings.PresetSearchEngine
import com.slideindex.app.settings.PresetSearchEngineCatalog
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSearchEnginePickerScreen(
    currentEngines: List<SearchEngineConfig>,
    onBack: () -> Unit,
    onAddEngines: (List<SearchEngineConfig>) -> Unit,
) {
    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val categories = remember {
        listOf(
            PresetSearchCategory.All to R.string.search_engine_preset_picker_cat_all,
            PresetSearchCategory.General to R.string.search_engine_preset_picker_cat_general,
            PresetSearchCategory.Social to R.string.search_engine_preset_picker_cat_social,
            PresetSearchCategory.VideoMedia to R.string.search_engine_preset_picker_cat_video,
            PresetSearchCategory.ShoppingLife to R.string.search_engine_preset_picker_cat_shopping,
            PresetSearchCategory.ToolsDev to R.string.search_engine_preset_picker_cat_tools,
        )
    }

    val allPresets = remember { PresetSearchEngineCatalog.allPresets() }

    fun isAlreadyAdded(preset: PresetSearchEngine): Boolean {
        return currentEngines.any { engine ->
            val presetPkg = preset.targetPackage?.takeIf { it.isNotBlank() }
            val enginePkg = engine.targetPackage?.takeIf { it.isNotBlank() }

            // 1. 若两者均指定了包名且不一致，说明属于不同应用（如微博官方 vs See vs Share，酷安官方 vs 泛酷客）
            if (presetPkg != null && enginePkg != null && presetPkg != enginePkg) {
                return@any false
            }

            // 2. 若为 Activity 组件跳转，必须包名和 Activity 均一致
            if (preset.engineType == SearchEngineType.JUMP_TO_ACTIVITY || engine.engineType == SearchEngineType.JUMP_TO_ACTIVITY) {
                if (presetPkg != null && preset.targetActivity != null) {
                    return@any enginePkg == presetPkg && engine.targetActivity == preset.targetActivity
                }
            }

            // 3. 若有 searchLink，匹配 searchLink
            if (!preset.searchLink.isNullOrBlank() && !engine.searchLink.isNullOrBlank()) {
                if (preset.searchLink == engine.searchLink) {
                    return@any (presetPkg == null || enginePkg == null || presetPkg == enginePkg)
                }
                return@any false
            }

            // 4. 若无 searchLink，根据名称及包名判定
            if (preset.name == engine.name) {
                return@any (presetPkg == null || enginePkg == null || presetPkg == enginePkg)
            }

            false
        }
    }

    val selectedPresetIds = remember { mutableStateListOf<String>() }

    val filteredPresets = remember(selectedCategoryIndex, searchQuery, currentEngines) {
        val cat = categories[selectedCategoryIndex].first
        val query = searchQuery.trim().lowercase()
        allPresets.filter { preset ->
            val matchCategory = (cat == PresetSearchCategory.All || preset.category == cat)
            val matchQuery = query.isEmpty() ||
                preset.name.lowercase().contains(query) ||
                preset.description.lowercase().contains(query) ||
                preset.targetPackage?.lowercase()?.contains(query) == true ||
                preset.searchLink?.lowercase()?.contains(query) == true
            matchCategory && matchQuery
        }
    }

    fun instantiatePreset(preset: PresetSearchEngine, sortOrder: Int): SearchEngineConfig {
        var iconPath: String? = null
        if (preset.iconAssetFileName != null) {
            iconPath = SearchEngineIconStorage.saveIconFromAsset(
                context,
                "preset_search_icons/${preset.iconAssetFileName}",
            )
        }
        val base = preset.toSearchEngineConfig(sortOrder)
        return if (iconPath != null) {
            base.copy(iconType = SearchIconType.URI, iconPath = iconPath)
        } else {
            base
        }
    }

    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it },
            )
        ) {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsLazyScreenScaffold(
            title = stringResource(R.string.search_engine_preset_picker_title),
            onBack = handleBack,
            modifier = Modifier.fillMaxSize(),
            actions = {
                MiuixExpandableSearchIconAction(
                    expanded = searchExpanded,
                    query = searchQuery,
                    onExpandedChange = { searchExpanded = it },
                    onQueryChange = { searchQuery = it },
                )
            },
            bottomContent = {
                MiuixScaffoldSearchTabBottomContent(
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    focusRequester = searchFocusRequester,
                    hintResId = R.string.search_engine_preset_picker_search_hint,
                    tabContent = {
                        MiuixTabRowWithContour(
                            tabs = categories.map { stringResource(it.second) },
                            selectedTabIndex = selectedCategoryIndex,
                            onTabSelected = { selectedCategoryIndex = it },
                        )
                    },
                )
            },
        ) {
            if (filteredPresets.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_engine_settings_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = filteredPresets,
                    key = { it.presetId },
                ) { preset ->
                    val alreadyAdded = isAlreadyAdded(preset)
                    val isSelected = preset.presetId in selectedPresetIds

                    PresetEngineItemRow(
                        preset = preset,
                        alreadyAdded = alreadyAdded,
                        isSelected = isSelected,
                        onToggleSelect = {
                            if (!alreadyAdded) {
                                if (isSelected) {
                                selectedPresetIds.remove(preset.presetId)
                            } else {
                                selectedPresetIds.add(preset.presetId)
                            }
                        }
                    },
                    onAddSingle = {
                        val config = instantiatePreset(preset, currentEngines.size)
                        onAddEngines(listOf(config))
                        Toast.makeText(
                            context,
                            context.getString(R.string.search_engine_preset_single_added_toast, preset.name),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // 底部批量添加悬浮栏
        if (selectedPresetIds.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MiuixTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        val toAdd = allPresets
                            .filter { it.presetId in selectedPresetIds && !isAlreadyAdded(it) }
                            .mapIndexed { index, preset ->
                                instantiatePreset(preset, currentEngines.size + index)
                            }
                        if (toAdd.isNotEmpty()) {
                            onAddEngines(toAdd)
                            Toast.makeText(
                                context,
                                context.getString(R.string.search_engine_preset_batch_added_toast, toAdd.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        selectedPresetIds.clear()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.search_engine_preset_batch_add, selectedPresetIds.size))
                }
            }
        }
    }
}

@Composable
private fun PresetEngineItemRow(
    preset: PresetSearchEngine,
    alreadyAdded: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onAddSingle: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(preset.iconAssetFileName, preset.targetPackage) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(preset.iconAssetFileName, preset.targetPackage) {
        val assetFile = preset.iconAssetFileName
        val targetPkg = preset.targetPackage
        iconBitmap = withContext(Dispatchers.IO) {
            if (assetFile != null) {
                runCatching {
                    context.assets.open("preset_search_icons/$assetFile").use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            } else if (targetPkg != null) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(targetPkg)
                    drawable.toBitmap(128, 128)
                }.getOrNull()
            } else {
                null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(enabled = !alreadyAdded) { onToggleSelect() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!alreadyAdded) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = iconBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = preset.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = if (alreadyAdded) 0.45f else 1f,
                    )
                } else {
                    Text(
                        text = preset.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (alreadyAdded) 0.45f else 1f,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 名称与描述
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (alreadyAdded) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (alreadyAdded) 0.45f else 0.8f,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 状态按钮
            if (alreadyAdded) {
                Text(
                    text = stringResource(R.string.search_engine_preset_added),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                OutlinedButton(
                    onClick = onAddSingle,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.search_engine_preset_add))
                }
            }
        }
    }
}
