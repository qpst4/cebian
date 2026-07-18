@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.slideindex.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.ui.searchengine.SearchEngineActivityPickerDialog
import com.slideindex.app.ui.searchengine.SearchEngineAppPickerDialog
import com.slideindex.app.ui.searchengine.ShareImageTargetPickerDialog
import com.slideindex.app.search.SearchEngineFaviconFetcher
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.search.SearchEngineValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import java.util.UUID

enum class SearchEngineEditorCategory {
    TEXT,
    IMAGE_SHARE,
}

data class SearchEngineEditorResult(
    val engine: SearchEngineConfig,
    val iconUri: Uri?,
    val savedIconPath: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineEditorScreen(
    initialEngine: SearchEngineConfig?,
    editorCategory: SearchEngineEditorCategory = SearchEngineEditorCategory.TEXT,
    onBack: () -> Unit,
    onSave: (SearchEngineEditorResult) -> Unit,
) {
    val isNew = initialEngine == null
    var name by remember(initialEngine?.id) { mutableStateOf(initialEngine?.name.orEmpty()) }
    var engineType by remember(initialEngine?.id) {
        mutableStateOf(
            when {
                editorCategory == SearchEngineEditorCategory.IMAGE_SHARE ->
                    SearchEngineType.SHARE_IMAGE_TO_APP
                else -> initialEngine?.engineType ?: SearchEngineType.DIRECT_LINK
            },
        )
    }
    var searchLink by remember(initialEngine?.id) { mutableStateOf(initialEngine?.searchLink.orEmpty()) }
    var externJumpLink by remember(initialEngine?.id) { mutableStateOf(initialEngine?.externJumpLink.orEmpty()) }
    var externJumpPackage by remember(initialEngine?.id) { mutableStateOf(initialEngine?.externJumpPackage.orEmpty()) }
    var targetPackage by remember(initialEngine?.id) { mutableStateOf(initialEngine?.targetPackage.orEmpty()) }
    var targetActivity by remember(initialEngine?.id) { mutableStateOf(initialEngine?.targetActivity.orEmpty()) }
    var autoInputEnter by remember(initialEngine?.id) { mutableStateOf(initialEngine?.autoInputEnter ?: true) }
    var pendingIconUri by remember(initialEngine?.id) { mutableStateOf<Uri?>(null) }
    var pendingIconPath by remember(initialEngine?.id) { mutableStateOf<String?>(null) }
    var pendingTextIcon by remember(initialEngine?.id) {
        mutableStateOf(
            initialEngine?.textIcon?.takeIf { initialEngine.iconType == SearchIconType.TEXT },
        )
    }
    var isFetchingFavicon by remember(initialEngine?.id) { mutableStateOf(false) }
    var showAppIconPicker by remember(initialEngine?.id) { mutableStateOf(false) }
    var showTextIconDialog by remember(initialEngine?.id) { mutableStateOf(false) }
    var isSavingAppIcon by remember(initialEngine?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fetchFaviconFailedMessage = stringResource(R.string.search_engine_fetch_favicon_failed)
    val pickAppIconFailedMessage = stringResource(R.string.search_engine_pick_app_icon_failed)
    val isShareTextType = initialEngine?.engineType == SearchEngineType.SHARE_TO_APP
    val isShareImageType = editorCategory == SearchEngineEditorCategory.IMAGE_SHARE ||
        engineType == SearchEngineType.SHARE_IMAGE_TO_APP
    var showShareImageTargetPicker by remember(initialEngine?.id) { mutableStateOf(false) }
    val canFetchFavicon = !isShareTextType && !isShareImageType &&
        engineType == SearchEngineType.DIRECT_LINK &&
        searchLink.isNotBlank()
    val previewEngine = remember(
        initialEngine?.id,
        name,
        engineType,
        pendingIconUri,
        pendingIconPath,
        pendingTextIcon,
    ) {
        buildPreviewEngine(
            initialEngine = initialEngine,
            name = name,
            engineType = engineType,
            hasPendingIconUri = pendingIconUri != null,
            pendingIconPath = pendingIconPath,
            pendingTextIcon = pendingTextIcon,
        )
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        discardPendingIconPath(context, pendingIconPath, initialEngine?.iconPath)
        pendingIconPath = null
        pendingTextIcon = null
        pendingIconUri = uri
    }

    SettingsScreenScaffold(
        title = stringResource(
            if (isNew) R.string.search_engine_add_title else R.string.search_engine_edit_title,
        ),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchEngineIcon(engine = previewEngine, modifier = Modifier.size(56.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconSourceButton(
                            onClick = { iconPicker.launch("image/*") },
                            enabled = true,
                            isLoading = false,
                            icon = Icons.Default.Image,
                            label = stringResource(R.string.search_engine_pick_icon),
                            modifier = Modifier.weight(1f),
                        )
                        IconSourceButton(
                            onClick = { showAppIconPicker = true },
                            enabled = !isSavingAppIcon,
                            isLoading = isSavingAppIcon,
                            icon = Icons.Default.Apps,
                            label = stringResource(
                                if (isSavingAppIcon) {
                                    R.string.search_engine_pick_app_icon_loading
                                } else {
                                    R.string.search_engine_pick_app_icon
                                },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconSourceButton(
                            onClick = {
                                if (isFetchingFavicon) return@IconSourceButton
                                scope.launch {
                                    isFetchingFavicon = true
                                    val iconPath = SearchEngineFaviconFetcher.fetchAndSave(
                                        context = context,
                                        searchLink = searchLink.trim(),
                                    )
                                    isFetchingFavicon = false
                                    if (iconPath != null) {
                                        discardPendingIconPath(
                                            context,
                                            pendingIconPath,
                                            initialEngine?.iconPath,
                                        )
                                        pendingIconUri = null
                                        pendingTextIcon = null
                                        pendingIconPath = iconPath
                                    } else {
                                        Toast.makeText(
                                            context,
                                            fetchFaviconFailedMessage,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            enabled = canFetchFavicon && !isFetchingFavicon,
                            isLoading = isFetchingFavicon,
                            icon = Icons.Default.Language,
                            label = stringResource(
                                if (isFetchingFavicon) {
                                    R.string.search_engine_fetch_favicon_loading
                                } else {
                                    R.string.search_engine_fetch_favicon
                                },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconSourceButton(
                            onClick = { showTextIconDialog = true },
                            enabled = true,
                            isLoading = false,
                            icon = Icons.Default.Title,
                            label = stringResource(R.string.search_engine_text_icon),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (showTextIconDialog) {
                TextIconDialog(
                    initialText = pendingTextIcon ?: name.take(1),
                    onDismiss = { showTextIconDialog = false },
                    onConfirm = { text ->
                        discardPendingIconPath(context, pendingIconPath, initialEngine?.iconPath)
                        pendingIconUri = null
                        pendingIconPath = null
                        pendingTextIcon = text
                        showTextIconDialog = false
                    },
                )
            }

            if (showAppIconPicker) {
                SearchEngineAppPickerDialog(
                    titleResId = R.string.search_engine_pick_app_icon_title,
                    initialPackageName = "",
                    onDismiss = { showAppIconPicker = false },
                    onSelect = { app ->
                        showAppIconPicker = false
                        scope.launch {
                            isSavingAppIcon = true
                            val iconPath = withContext(Dispatchers.IO) {
                                SearchEngineIconStorage.saveIconFromPackage(context, app.packageName)
                            }
                            isSavingAppIcon = false
                            if (iconPath != null) {
                                discardPendingIconPath(context, pendingIconPath, initialEngine?.iconPath)
                                pendingIconUri = null
                                pendingTextIcon = null
                                pendingIconPath = iconPath
                            } else {
                                Toast.makeText(
                                    context,
                                    pickAppIconFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
            }

            if (showShareImageTargetPicker) {
                ShareImageTargetPickerDialog(
                    initialPackageName = targetPackage,
                    initialActivityClassName = targetActivity,
                    onDismiss = { showShareImageTargetPicker = false },
                    onSelect = { target ->
                        showShareImageTargetPicker = false
                        targetPackage = target.packageName
                        targetActivity = target.activityClassName
                        if (name.isBlank()) {
                            name = target.appLabel.ifBlank { target.label }
                        }
                        scope.launch {
                            val iconPath = withContext(Dispatchers.IO) {
                                SearchEngineIconStorage.saveIconFromPackage(context, target.packageName)
                            }
                            if (iconPath != null) {
                                discardPendingIconPath(context, pendingIconPath, initialEngine?.iconPath)
                                pendingIconUri = null
                                pendingTextIcon = null
                                pendingIconPath = iconPath
                            }
                        }
                    },
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.search_engine_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (!isShareTextType && editorCategory == SearchEngineEditorCategory.TEXT) {
                SettingsSectionTitle(stringResource(R.string.search_engine_type_section))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    textSearchEngineTypes().forEach { type ->
                        FilterChip(
                            selected = engineType == type,
                            onClick = { engineType = type },
                            label = { Text(searchEngineTypeLabel(type)) },
                        )
                    }
                }
            }

            if (isShareTextType) {
                SettingsHintText(stringResource(R.string.search_engine_share_type_readonly))
            } else if (isShareImageType) {
                SettingsSectionTitle(stringResource(R.string.search_engine_share_image_target_section))
                val targetSummary = when {
                    targetPackage.isBlank() -> stringResource(R.string.search_engine_share_image_target_not_set)
                    targetActivity.isBlank() -> targetPackage
                    else -> "$targetPackage / ${targetActivity.substringAfterLast('.')}"
                }
                SettingsHintText(targetSummary)
                OutlinedButton(
                    onClick = { showShareImageTargetPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.search_engine_pick_share_image_target))
                }
            } else {
                EditorTypeFields(
                    engineType = engineType,
                    onEngineTypeChange = { engineType = it },
                    searchLink = searchLink,
                    onSearchLinkChange = { searchLink = it },
                    externJumpLink = externJumpLink,
                    onExternJumpLinkChange = { externJumpLink = it },
                    externJumpPackage = externJumpPackage,
                    onExternJumpPackageChange = { externJumpPackage = it },
                    targetPackage = targetPackage,
                    onTargetPackageChange = { targetPackage = it },
                    targetActivity = targetActivity,
                    onTargetActivityChange = { targetActivity = it },
                    autoInputEnter = autoInputEnter,
                    onAutoInputEnterChange = { autoInputEnter = it },
                )
            }

            Button(
                onClick = {
                    val engine = if (isShareTextType && initialEngine != null) {
                        initialEngine.copy(name = name.trim())
                    } else {
                        buildEngine(
                            initialEngine = initialEngine,
                            name = name.trim(),
                            engineType = engineType,
                            searchLink = searchLink.trim(),
                            externJumpLink = externJumpLink.trim(),
                            externJumpPackage = externJumpPackage.trim(),
                            targetPackage = targetPackage.trim(),
                            targetActivity = targetActivity.trim(),
                            autoInputEnter = autoInputEnter,
                            hasPendingIcon = pendingIconUri != null || pendingIconPath != null,
                            pendingIconPath = pendingIconPath,
                            pendingTextIcon = pendingTextIcon,
                        )
                    }
                    if (!SearchEngineValidator.validate(engine)) return@Button
                    onSave(
                        SearchEngineEditorResult(
                            engine = engine,
                            iconUri = pendingIconUri,
                            savedIconPath = pendingIconPath,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.search_engine_save))
            }
        }
    }
}

private enum class PackagePickerTarget {
    TARGET,
    EXTERN,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorTypeFields(
    engineType: SearchEngineType,
    onEngineTypeChange: (SearchEngineType) -> Unit,
    searchLink: String,
    onSearchLinkChange: (String) -> Unit,
    externJumpLink: String,
    onExternJumpLinkChange: (String) -> Unit,
    externJumpPackage: String,
    onExternJumpPackageChange: (String) -> Unit,
    targetPackage: String,
    onTargetPackageChange: (String) -> Unit,
    targetActivity: String,
    onTargetActivityChange: (String) -> Unit,
    autoInputEnter: Boolean,
    onAutoInputEnterChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val pickActivityRequiresPackageMessage =
        stringResource(R.string.search_engine_pick_activity_requires_package)
    var packagePickerTarget by remember { mutableStateOf<PackagePickerTarget?>(null) }
    var showActivityPicker by remember { mutableStateOf(false) }

    when (engineType) {
        SearchEngineType.DIRECT_LINK -> {
            OutlinedTextField(
                value = searchLink,
                onValueChange = onSearchLinkChange,
                label = { Text(stringResource(R.string.search_engine_search_link_hint)) },
                supportingText = { Text(stringResource(R.string.search_engine_search_link_support)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            PackageNameField(
                value = targetPackage,
                onValueChange = onTargetPackageChange,
                label = { Text(stringResource(R.string.search_engine_target_package_hint)) },
                onPickApp = { packagePickerTarget = PackagePickerTarget.TARGET },
            )
        }
        SearchEngineType.EXTERN_JUMP_LINK -> {
            OutlinedTextField(
                value = externJumpLink,
                onValueChange = onExternJumpLinkChange,
                label = { Text(stringResource(R.string.search_engine_extern_link_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            PackageNameField(
                value = externJumpPackage,
                onValueChange = onExternJumpPackageChange,
                label = { Text(stringResource(R.string.search_engine_extern_package_hint)) },
                onPickApp = { packagePickerTarget = PackagePickerTarget.EXTERN },
            )
        }
        SearchEngineType.JUMP_TO_ACTIVITY -> {
            PackageNameField(
                value = targetPackage,
                onValueChange = onTargetPackageChange,
                label = { Text(stringResource(R.string.search_engine_target_package_hint)) },
                onPickApp = { packagePickerTarget = PackagePickerTarget.TARGET },
            )
            ActivityNameField(
                value = targetActivity,
                onValueChange = onTargetActivityChange,
                label = { Text(stringResource(R.string.search_engine_target_activity_hint)) },
                packageName = targetPackage,
                onPickActivity = {
                    if (targetPackage.isBlank()) {
                        Toast.makeText(
                            context,
                            pickActivityRequiresPackageMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        showActivityPicker = true
                    }
                },
            )
            OutlinedTextField(
                value = searchLink,
                onValueChange = onSearchLinkChange,
                label = { Text(stringResource(R.string.search_engine_optional_search_link_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SettingSwitchRow(
                title = stringResource(R.string.search_engine_auto_input_enter),
                subtitle = stringResource(R.string.search_engine_auto_input_enter_desc),
                checked = autoInputEnter,
                enabled = true,
                onCheckedChange = onAutoInputEnterChange,
            )
        }
        SearchEngineType.SHARE_TO_APP,
        SearchEngineType.SHARE_IMAGE_TO_APP,
        -> Unit
    }

    packagePickerTarget?.let { target ->
        val initialPackage = when (target) {
            PackagePickerTarget.TARGET -> targetPackage
            PackagePickerTarget.EXTERN -> externJumpPackage
        }
        SearchEngineAppPickerDialog(
            initialPackageName = initialPackage,
            onDismiss = { packagePickerTarget = null },
            onSelect = { app ->
                when (target) {
                    PackagePickerTarget.TARGET -> {
                        val previousPackage = targetPackage
                        onTargetPackageChange(app.packageName)
                        if (previousPackage != app.packageName) {
                            onTargetActivityChange("")
                        }
                    }
                    PackagePickerTarget.EXTERN -> onExternJumpPackageChange(app.packageName)
                }
                packagePickerTarget = null
            },
        )
    }

    if (showActivityPicker && targetPackage.isNotBlank()) {
        SearchEngineActivityPickerDialog(
            packageName = targetPackage,
            initialClassName = targetActivity,
            onDismiss = { showActivityPicker = false },
            onSelect = { activity ->
                onTargetActivityChange(activity.className)
                showActivityPicker = false
            },
        )
    }
}

@Composable
private fun PackageNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    onPickApp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedButton(onClick = onPickApp) {
            Text(stringResource(R.string.search_engine_pick_app))
        }
    }
}

@Composable
private fun ActivityNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    packageName: String,
    onPickActivity: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedButton(
            onClick = onPickActivity,
            enabled = packageName.isNotBlank(),
        ) {
            Text(stringResource(R.string.search_engine_pick_activity))
        }
    }
}

private fun textSearchEngineTypes(): List<SearchEngineType> = listOf(
    SearchEngineType.DIRECT_LINK,
    SearchEngineType.JUMP_TO_ACTIVITY,
    SearchEngineType.EXTERN_JUMP_LINK,
)

@Composable
private fun searchEngineTypeLabel(type: SearchEngineType): String = when (type) {
    SearchEngineType.DIRECT_LINK -> stringResource(R.string.search_engine_type_direct_link)
    SearchEngineType.JUMP_TO_ACTIVITY -> stringResource(R.string.search_engine_type_jump_activity)
    SearchEngineType.EXTERN_JUMP_LINK -> stringResource(R.string.search_engine_type_extern_jump)
    SearchEngineType.SHARE_TO_APP -> stringResource(R.string.search_engine_type_share)
    SearchEngineType.SHARE_IMAGE_TO_APP -> stringResource(R.string.search_engine_type_share_image)
}

private fun discardPendingIconPath(
    context: android.content.Context,
    pendingIconPath: String?,
    initialIconPath: String?,
) {
    pendingIconPath?.takeIf { it != initialIconPath }?.let { path ->
        SearchEngineIconStorage.deleteIconIfOwned(context, path)
    }
}

private fun buildPreviewEngine(
    initialEngine: SearchEngineConfig?,
    name: String,
    engineType: SearchEngineType,
    hasPendingIconUri: Boolean,
    pendingIconPath: String?,
    pendingTextIcon: String?,
): SearchEngineConfig {
    val base = initialEngine ?: SearchEngineConfig(name = name.ifBlank { "?" }, engineType = engineType)
    val hasPendingIcon = hasPendingIconUri || pendingIconPath != null
    val iconType = when {
        hasPendingIcon -> SearchIconType.URI
        pendingTextIcon != null -> SearchIconType.TEXT
        else -> base.iconType
    }
    return base.copy(
        name = name.ifBlank { base.name },
        engineType = engineType,
        iconType = iconType,
        iconPath = when {
            hasPendingIcon -> pendingIconPath
            pendingTextIcon != null -> null
            else -> base.iconPath.takeIf { !hasPendingIconUri }
        },
        textIcon = when {
            pendingTextIcon != null -> pendingTextIcon
            hasPendingIcon -> null
            else -> base.textIcon
        },
    )
}

private fun buildEngine(
    initialEngine: SearchEngineConfig?,
    name: String,
    engineType: SearchEngineType,
    searchLink: String,
    externJumpLink: String,
    externJumpPackage: String,
    targetPackage: String,
    targetActivity: String,
    autoInputEnter: Boolean,
    hasPendingIcon: Boolean,
    pendingIconPath: String?,
    pendingTextIcon: String?,
): SearchEngineConfig {
    val id = initialEngine?.id ?: UUID.randomUUID().toString()
    val sortOrder = initialEngine?.sortOrder ?: 0
    val iconType = when {
        hasPendingIcon -> SearchIconType.URI
        pendingTextIcon != null -> SearchIconType.TEXT
        initialEngine != null -> initialEngine.iconType
        targetPackage.isNotBlank() || externJumpPackage.isNotBlank() -> SearchIconType.OTHER
        else -> SearchIconType.OTHER
    }
    val iconPath = when {
        pendingIconPath != null -> pendingIconPath
        hasPendingIcon -> null
        iconType == SearchIconType.TEXT -> null
        else -> initialEngine?.iconPath
    }
    val textIcon = when {
        pendingTextIcon != null -> pendingTextIcon
        hasPendingIcon -> null
        iconType == SearchIconType.TEXT -> initialEngine?.textIcon
        else -> null
    }
    return SearchEngineConfig(
        id = id,
        name = name,
        engineType = engineType,
        iconType = iconType,
        iconPath = iconPath,
        textIcon = textIcon,
        searchLink = searchLink.takeIf { it.isNotBlank() },
        externJumpLink = externJumpLink.takeIf { it.isNotBlank() },
        externJumpPackage = externJumpPackage.takeIf { it.isNotBlank() },
        targetPackage = targetPackage.takeIf { it.isNotBlank() },
        targetActivity = targetActivity.takeIf { it.isNotBlank() },
        autoInputEnter = autoInputEnter,
        showInPickPanel = true,
        sortOrder = sortOrder,
    )
}

@Composable
private fun IconSourceButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextIconDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_engine_text_icon_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 8) input = it },
                label = { Text(stringResource(R.string.search_engine_text_icon_hint)) },
                supportingText = { Text(stringResource(R.string.search_engine_text_icon_support)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = input.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
