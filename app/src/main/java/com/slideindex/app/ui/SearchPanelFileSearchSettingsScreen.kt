@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.searchpanel.FilePermissionTrampolineActivity
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.search.files.FileType
import com.slideindex.app.search.files.FolderPathPatternMatcher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FolderFilterTarget { Whitelist, Blacklist }

@Composable
fun SearchPanelFileSearchSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetFileTypesEnabled: (Set<String>) -> Unit,
    onSetShowFolders: (Boolean) -> Unit,
    onSetShowSystemFiles: (Boolean) -> Unit,
    onSetFilePreviewsEnabled: (Boolean) -> Unit,
    onSetFolderWhitelist: (Set<String>) -> Unit,
    onSetFolderBlacklist: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasFilePermission by remember {
        mutableStateOf(FileSearchIndex.hasPermission(context))
    }
    var folderDialogTarget by remember { mutableStateOf<FolderFilterTarget?>(null) }
    val enabledTypes = remember(settings.searchPanelFileTypesEnabled) {
        FileType.fromNames(settings.searchPanelFileTypesEnabled)
    }

    val fileTypesSectionTitle = stringResource(R.string.search_panel_file_search_manage_title)
    val previewsSectionTitle = stringResource(R.string.search_panel_file_previews_section_title)
    val folderFiltersSectionTitle = stringResource(R.string.search_panel_file_folder_filters_title)
    val folderFiltersHint = stringResource(R.string.search_panel_file_folder_filters_desc)
    val whitelistTitle = stringResource(R.string.search_panel_file_whitelist_title)
    val whitelistDesc = stringResource(R.string.search_panel_file_whitelist_desc)
    val blacklistTitle = stringResource(R.string.search_panel_file_blacklist_title)
    val blacklistDesc = stringResource(R.string.search_panel_file_blacklist_desc)

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_file_search_manage_title),
        subtitle = stringResource(R.string.search_panel_file_search_manage_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "file-types", title = fileTypesSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "file-types",
            items = buildList {
                add(
                    settingsCardScopeItem("show-folders") {
                        SettingSwitchRow(
                            icon = { label -> Icon(Icons.Outlined.Folder, contentDescription = label) },
                            title = stringResource(R.string.search_panel_file_show_folders),
                            checked = settings.searchPanelFileShowFolders,
                            enabled = true,
                            onCheckedChange = onSetShowFolders,
                        )
                    },
                )
                orderedFileTypes().forEach { fileType ->
                    add(
                        settingsCardScopeItem("file-type-${fileType.name}") {
                            SettingSwitchRow(
                                icon = { label ->
                                    Icon(fileTypeIcon(fileType), contentDescription = label)
                                },
                                title = fileTypeLabel(fileType),
                                checked = fileType in enabledTypes,
                                enabled = true,
                                onCheckedChange = { enabled ->
                                    val next = if (enabled) {
                                        enabledTypes + fileType
                                    } else {
                                        enabledTypes - fileType
                                    }
                                    onSetFileTypesEnabled(next.map { it.name }.toSet())
                                },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("show-system-files") {
                        SettingSwitchRow(
                            icon = { label -> Icon(Icons.Outlined.Visibility, contentDescription = label) },
                            title = stringResource(R.string.search_panel_file_show_system),
                            checked = settings.searchPanelFileShowSystemFiles,
                            enabled = true,
                            onCheckedChange = onSetShowSystemFiles,
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(key = "file-previews", title = previewsSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "file-previews",
            items = buildList {
                add(
                    settingsCardScopeItem("file-previews-enabled") {
                        SettingSwitchRow(
                            icon = { label -> Icon(Icons.Outlined.Image, contentDescription = label) },
                            title = stringResource(R.string.search_panel_file_previews_title),
                            subtitle = stringResource(R.string.search_panel_file_previews_desc),
                            checked = settings.searchPanelFilePreviewsEnabled,
                            enabled = true,
                            onCheckedChange = onSetFilePreviewsEnabled,
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(key = "folder-filters", title = folderFiltersSectionTitle, sectionTop = true)
        settingsLazyHint(key = "folder-filters-hint", text = folderFiltersHint)
        settingsLazyHint(key = "folder-whitelist-desc", text = whitelistDesc)
        folderFilterGroupedItems(
            keyPrefix = "folder-whitelist",
            title = whitelistTitle,
            patterns = settings.searchPanelFileFolderWhitelist,
            onAdd = { folderDialogTarget = FolderFilterTarget.Whitelist },
            onRemove = { pattern ->
                onSetFolderWhitelist(settings.searchPanelFileFolderWhitelist - pattern)
            },
        )
        settingsLazyHint(key = "folder-blacklist-desc", text = blacklistDesc)
        folderFilterGroupedItems(
            keyPrefix = "folder-blacklist",
            title = blacklistTitle,
            patterns = settings.searchPanelFileFolderBlacklist,
            onAdd = { folderDialogTarget = FolderFilterTarget.Blacklist },
            onRemove = { pattern ->
                onSetFolderBlacklist(settings.searchPanelFileFolderBlacklist - pattern)
            },
        )
    }

    val dialogTarget = folderDialogTarget
    if (dialogTarget != null) {
        FolderSearchDialog(
            title = when (dialogTarget) {
                FolderFilterTarget.Whitelist ->
                    stringResource(R.string.search_panel_file_folder_add_title_whitelist)
                FolderFilterTarget.Blacklist ->
                    stringResource(R.string.search_panel_file_folder_add_title_blacklist)
            },
            hasPermission = hasFilePermission,
            onRequestPermission = {
                FilePermissionTrampolineActivity.launch(context) { granted ->
                    hasFilePermission = granted
                }
            },
            onDismiss = { folderDialogTarget = null },
            onPickFolder = { folder ->
                val path = FolderPathPatternMatcher.folderDisplayPath(folder)
                val pattern = FolderPathPatternMatcher.normalizePathFilterPattern(path)
                    ?: return@FolderSearchDialog
                when (dialogTarget) {
                    FolderFilterTarget.Whitelist ->
                        onSetFolderWhitelist(settings.searchPanelFileFolderWhitelist + pattern)
                    FolderFilterTarget.Blacklist ->
                        onSetFolderBlacklist(settings.searchPanelFileFolderBlacklist + pattern)
                }
                folderDialogTarget = null
            },
            searchFolders = { query ->
                FileSearchIndex.searchFolders(context, query, limit = 30)
            },
            scopeLaunch = { block -> scope.launch { block() } },
        )
    }
}

private fun LazyListScope.folderFilterGroupedItems(
    keyPrefix: String,
    title: String,
    patterns: Set<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = buildList {
            add(
                settingsCardScopeItem("$keyPrefix-nav") {
                    SettingNavigationRow(
                        icon = { label -> Icon(Icons.Outlined.Folder, contentDescription = label) },
                        title = title,
                        subtitle = if (patterns.isEmpty()) {
                            stringResource(R.string.search_panel_file_folder_empty)
                        } else {
                            pluralStringResource(
                                R.plurals.search_panel_file_folder_count,
                                patterns.size,
                                patterns.size,
                            )
                        },
                        onClick = onAdd,
                        trailingContent = {
                            IconButton(onClick = onAdd) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.search_panel_file_folder_add),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                },
            )
            patterns.sorted().forEach { pattern ->
                add(
                    settingsCardScopeItem("$keyPrefix-$pattern") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Folder, contentDescription = label) },
                            title = "/" + FolderPathPatternMatcher.patternDisplayPath(pattern),
                            subtitle = "",
                            onClick = { onRemove(pattern) },
                            trailingContent = {
                                IconButton(onClick = { onRemove(pattern) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(
                                            R.string.search_panel_file_folder_remove_hint,
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    },
                )
            }
        },
    )
}

@Composable
private fun FolderSearchDialog(
    title: String,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onPickFolder: (DeviceFileEntry) -> Unit,
    searchFolders: suspend (String) -> List<DeviceFileEntry>,
    scopeLaunch: (suspend () -> Unit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DeviceFileEntry>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query, hasPermission) {
        if (!hasPermission) {
            results = emptyList()
            return@LaunchedEffect
        }
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(200)
        results = searchFolders(trimmed)
        searching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (!hasPermission) {
                    TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.search_panel_file_permission_missing))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = hasPermission,
                    placeholder = {
                        Text(stringResource(R.string.search_panel_file_folder_search_hint))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        scopeLaunch {
                            if (hasPermission && query.trim().length >= 2) {
                                searching = true
                                results = searchFolders(query.trim())
                                searching = false
                            }
                        }
                    }),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(top = 8.dp),
                ) {
                    if (query.trim().length >= 2 && !searching && results.isEmpty() && hasPermission) {
                        item {
                            Text(
                                text = stringResource(R.string.search_panel_file_folder_no_results),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(results, key = { it.uri.toString() }) { folder ->
                        val path = FolderPathPatternMatcher.folderDisplayPath(folder)
                        TextButton(
                            onClick = { onPickFolder(folder) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "/$path",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun orderedFileTypes(): List<FileType> = listOf(
    FileType.DOCUMENTS,
    FileType.PICTURES,
    FileType.VIDEOS,
    FileType.AUDIO,
    FileType.APKS,
    FileType.OTHER,
)

@Composable
private fun fileTypeLabel(type: FileType): String = when (type) {
    FileType.DOCUMENTS -> stringResource(R.string.search_panel_file_type_documents)
    FileType.PICTURES -> stringResource(R.string.search_panel_file_type_pictures)
    FileType.VIDEOS -> stringResource(R.string.search_panel_file_type_videos)
    FileType.AUDIO -> stringResource(R.string.search_panel_file_type_audio)
    FileType.APKS -> stringResource(R.string.search_panel_file_type_apks)
    FileType.OTHER -> stringResource(R.string.search_panel_file_type_other)
}

private fun fileTypeIcon(type: FileType): ImageVector = when (type) {
    FileType.DOCUMENTS -> Icons.AutoMirrored.Outlined.InsertDriveFile
    FileType.PICTURES -> Icons.Outlined.Image
    FileType.VIDEOS -> Icons.Outlined.VideoLibrary
    FileType.AUDIO -> Icons.Outlined.AudioFile
    FileType.APKS -> Icons.Outlined.Android
    FileType.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
