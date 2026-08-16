package com.slideindex.app.overlay.searchpanel

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.content.ClipData
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.search.contacts.ContactSearchEntry
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.files.FileThumbnailCache
import com.slideindex.app.search.files.FileType
import com.slideindex.app.search.files.FileTypeUtils
import com.slideindex.app.search.settings.SystemSettingsSearchEntry
import com.slideindex.app.settings.SearchPanelAppDisplayStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(16.dp)
private const val INITIAL_VISIBLE_COUNT = 1
private const val APP_LIST_INITIAL_VISIBLE_COUNT = 3
private val ExpandedCardMaxHeight = 280.dp
private val LeadingSlotSize = 40.dp
private val ContactActionButtonSize = 36.dp
private val ContactActionIconSize = 20.dp
private val AppIconCandidateSize = 48.dp
private val AppIconCandidateItemWidth = 64.dp
private val AppIconCandidateCorner = RoundedCornerShape(12.dp)
private const val THUMBNAIL_LOAD_SIZE_PX = 256

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelCalculatorCard(
    expression: String,
    result: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun copyResult() {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText("calculator", result).toClipEntry())
        }
    }
    SearchPanelGroupedResultCard(
        modifier = modifier,
        scrollWhenExpanded = false,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = false,
        onExpandMore = {},
        showCollapse = false,
        onCollapse = {},
        expandLabel = "",
        collapseLabel = "",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = ::copyResult,
                    onLongClick = ::copyResult,
                )
                .padding(horizontal = 14.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "= $result",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expression,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun SearchPanelLinkResultCards(
    urls: List<String>,
    onOpenUrl: (url: String, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (urls.isEmpty()) return
    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = urls.size > 4,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = false,
        onExpandMore = {},
        showCollapse = false,
        onCollapse = {},
        expandLabel = "",
        collapseLabel = "",
    ) {
        urls.forEachIndexed { index, url ->
            val host = remember(url) { PickResultUrl.linkDisplayLabel(url) }
            SearchPanelResultCard(
                title = stringResource(R.string.search_panel_open_link_host, host),
                subtitle = url,
                leadingIcon = Icons.Outlined.Link,
                longPressEnabled = longPressEnabled,
                onClick = { onOpenUrl(url, false) },
                onLongClick = { onOpenUrl(url, true) },
            )
            if (index < urls.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelWebSuggestionsCard(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPanelQueryListCard(
        queries = suggestions,
        leadingIcon = Icons.Rounded.NorthWest,
        onQueryClick = onSuggestionClick,
        modifier = modifier,
    )
}

@Composable
fun SearchPanelSearchHistoryCard(
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPanelQueryListCard(
        queries = queries,
        leadingIcon = Icons.Outlined.History,
        onQueryClick = onQueryClick,
        modifier = modifier,
    )
}

@Composable
private fun SearchPanelQueryListCard(
    queries: List<String>,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onQueryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queries.isEmpty()) return
    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = queries.size > 4,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = false,
        onExpandMore = {},
        showCollapse = false,
        onCollapse = {},
        expandLabel = "",
        collapseLabel = "",
    ) {
        queries.forEachIndexed { index, query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQueryClick(query) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index < queries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelAppResultCards(
    apps: List<AppInfo>,
    style: SearchPanelAppDisplayStyle,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    longPressEnabled: Boolean = false,
) {
    if (apps.isEmpty()) return
    when (style) {
        SearchPanelAppDisplayStyle.ICONS -> SearchPanelAppIconStrip(
            apps = apps,
            onLaunchApp = onLaunchApp,
            modifier = modifier,
            longPressEnabled = longPressEnabled,
        )
        SearchPanelAppDisplayStyle.LIST -> SearchPanelAppListCards(
            apps = apps,
            onLaunchApp = onLaunchApp,
            modifier = modifier,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            longPressEnabled = longPressEnabled,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelAppIconStrip(
    apps: List<AppInfo>,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = false,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = false,
        onExpandMore = {},
        showCollapse = false,
        onCollapse = {},
        expandLabel = "",
        collapseLabel = "",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            apps.forEach { app ->
                Column(
                    modifier = Modifier
                        .width(AppIconCandidateItemWidth)
                        .then(
                            if (longPressEnabled) {
                                Modifier.combinedClickable(
                                    onClick = { onLaunchApp(app, false) },
                                    onLongClick = { onLaunchApp(app, true) },
                                )
                            } else {
                                Modifier.combinedClickable(onClick = { onLaunchApp(app, false) })
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SearchPanelAppIcon(
                        packageName = app.packageName,
                        contentDescription = app.label,
                        size = AppIconCandidateSize,
                        corner = AppIconCandidateCorner,
                    )
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanelAppListCards(
    apps: List<AppInfo>,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    longPressEnabled: Boolean = false,
) {
    val displayApps = if (expanded) apps else apps.take(APP_LIST_INITIAL_VISIBLE_COUNT)
    val showExpand = !expanded && apps.size > APP_LIST_INITIAL_VISIBLE_COUNT
    val showCollapse = expanded && apps.size > APP_LIST_INITIAL_VISIBLE_COUNT

    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = expanded && apps.size > APP_LIST_INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = showExpand,
        onExpandMore = { onExpandedChange(true) },
        showCollapse = showCollapse,
        onCollapse = { onExpandedChange(false) },
        expandLabel = stringResource(R.string.search_panel_expand_more_apps),
        collapseLabel = stringResource(R.string.search_panel_collapse),
    ) {
        displayApps.forEachIndexed { index, app ->
            SearchPanelResultCard(
                title = app.label,
                subtitle = null,
                leading = {
                    SearchPanelAppIcon(
                        packageName = app.packageName,
                        contentDescription = app.label,
                        size = LeadingSlotSize,
                        corner = RoundedCornerShape(8.dp),
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchApp(app, false) },
                onLongClick = { onLaunchApp(app, true) },
            )
            if (index < displayApps.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun SearchPanelAppIcon(
    packageName: String,
    contentDescription: String,
    size: Dp,
    corner: RoundedCornerShape,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconPx = with(density) { size.roundToPx().coerceAtLeast(1) }
    val iconBitmap = remember(packageName, iconPx) {
        val drawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
        drawable?.toBitmap(iconPx, iconPx)?.asImageBitmap()
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .clip(corner),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    corner,
                )
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun SearchPanelSettingsResultCards(
    entries: List<SystemSettingsSearchEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLaunchEntry: (SystemSettingsSearchEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (entries.isEmpty()) return
    val displayEntries = if (expanded) entries else entries.take(INITIAL_VISIBLE_COUNT)
    val showExpand = !expanded && entries.size > INITIAL_VISIBLE_COUNT
    val showCollapse = expanded && entries.size > INITIAL_VISIBLE_COUNT

    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = expanded && entries.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = showExpand,
        onExpandMore = { onExpandedChange(true) },
        showCollapse = showCollapse,
        onCollapse = { onExpandedChange(false) },
        expandLabel = stringResource(R.string.search_panel_expand_more_settings),
        collapseLabel = stringResource(R.string.search_panel_collapse),
    ) {
        displayEntries.forEachIndexed { index, entry ->
            SearchPanelResultCard(
                title = entry.title,
                subtitle = entry.subtitle,
                leadingIcon = Icons.Default.Settings,
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchEntry(entry, false) },
                onLongClick = { onLaunchEntry(entry, true) },
            )
            if (index < displayEntries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelContactResultCards(
    contacts: List<ContactSearchEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLaunchContact: (ContactSearchEntry, longPressTriggered: Boolean) -> Unit,
    onCallContact: (ContactSearchEntry) -> Unit,
    onSmsContact: (ContactSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (contacts.isEmpty()) return
    val displayContacts = if (expanded) contacts else contacts.take(INITIAL_VISIBLE_COUNT)
    val showExpand = !expanded && contacts.size > INITIAL_VISIBLE_COUNT
    val showCollapse = expanded && contacts.size > INITIAL_VISIBLE_COUNT

    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = expanded && contacts.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = showExpand,
        onExpandMore = { onExpandedChange(true) },
        showCollapse = showCollapse,
        onCollapse = { onExpandedChange(false) },
        expandLabel = stringResource(R.string.search_panel_expand_more_contacts),
        collapseLabel = stringResource(R.string.search_panel_collapse),
    ) {
        displayContacts.forEachIndexed { index, contact ->
            SearchPanelContactResultRow(
                contact = contact,
                longPressEnabled = longPressEnabled,
                onLaunchContact = onLaunchContact,
                onCallContact = onCallContact,
                onSmsContact = onSmsContact,
            )
            if (index < displayContacts.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelFileResultCards(
    files: List<DeviceFileEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenFile: (DeviceFileEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (files.isEmpty()) return
    val displayFiles = if (expanded) files else files.take(INITIAL_VISIBLE_COUNT)
    val showExpand = !expanded && files.size > INITIAL_VISIBLE_COUNT
    val showCollapse = expanded && files.size > INITIAL_VISIBLE_COUNT

    SearchPanelGroupedResultCard(
        modifier = modifier.padding(horizontal = 16.dp),
        scrollWhenExpanded = expanded && files.size > INITIAL_VISIBLE_COUNT,
        maxHeight = ExpandedCardMaxHeight,
        showExpandMore = showExpand,
        onExpandMore = { onExpandedChange(true) },
        showCollapse = showCollapse,
        onCollapse = { onExpandedChange(false) },
        expandLabel = stringResource(R.string.search_panel_expand_more_files),
        collapseLabel = stringResource(R.string.search_panel_collapse),
    ) {
        displayFiles.forEachIndexed { index, file ->
            SearchPanelResultCard(
                title = file.displayName,
                subtitle = file.relativePath?.trimEnd('/'),
                leading = {
                    SearchPanelFileLeadingIcon(file = file)
                },
                longPressEnabled = longPressEnabled,
                onClick = { onOpenFile(file, false) },
                onLongClick = { onOpenFile(file, true) },
            )
            if (index < displayFiles.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
fun SearchPanelPermissionResultCard(
    label: String,
    leadingIcon: ImageVector,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        SearchPanelResultCard(
            title = label,
            subtitle = null,
            leadingIcon = leadingIcon,
            longPressEnabled = false,
            onClick = onRequestPermission,
            onLongClick = {},
        )
    }
}

@Composable
private fun SearchPanelGroupedResultCard(
    modifier: Modifier = Modifier,
    scrollWhenExpanded: Boolean,
    maxHeight: Dp,
    showExpandMore: Boolean,
    onExpandMore: () -> Unit,
    showCollapse: Boolean,
    onCollapse: () -> Unit,
    expandLabel: String,
    collapseLabel: String,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrollWhenExpanded) {
                        Modifier
                            .heightIn(max = maxHeight)
                            .verticalScroll(scrollState)
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
            if (showExpandMore) {
                ExpandCollapseButton(
                    label = expandLabel,
                    expand = true,
                    onClick = onExpandMore,
                )
            }
            if (showCollapse) {
                ExpandCollapseButton(
                    label = collapseLabel,
                    expand = false,
                    onClick = onCollapse,
                )
            }
        }
    }
}

@Composable
private fun ExpandCollapseButton(
    label: String,
    expand: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expand) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelContactResultRow(
    contact: ContactSearchEntry,
    longPressEnabled: Boolean,
    onLaunchContact: (ContactSearchEntry, longPressTriggered: Boolean) -> Unit,
    onCallContact: (ContactSearchEntry) -> Unit,
    onSmsContact: (ContactSearchEntry) -> Unit,
) {
    val hasNumber = contact.phoneNumber.isNotBlank()
    val openWithLabel = stringResource(R.string.search_panel_contact_open_with)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = openWithLabel }
                .then(
                    if (longPressEnabled) {
                        Modifier.combinedClickable(
                            onClick = { onLaunchContact(contact, false) },
                            onLongClick = { onLaunchContact(contact, true) },
                        )
                    } else {
                        Modifier.clickable { onLaunchContact(contact, false) }
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchPanelContactAvatar(
                photoUri = contact.photoUri,
                displayName = contact.name,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contact.formattedPhone.isNotBlank()) {
                    Text(
                        text = contact.formattedPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        SearchPanelContactActionButton(
            icon = Icons.Rounded.Call,
            contentDescription = stringResource(R.string.search_panel_contact_action_call),
            enabled = hasNumber,
            onClick = { onCallContact(contact) },
        )
        SearchPanelContactActionButton(
            icon = Icons.Rounded.Sms,
            contentDescription = stringResource(R.string.search_panel_contact_action_sms),
            enabled = hasNumber,
            onClick = { onSmsContact(contact) },
        )
    }
}

@Composable
private fun SearchPanelContactActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        modifier = Modifier
            .size(ContactActionButtonSize)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ContactActionIconSize),
                tint = tint,
            )
        }
    }
}

@Composable
private fun SearchPanelContactAvatar(
    photoUri: String?,
    displayName: String,
) {
    val context = LocalContext.current
    var contactPhoto by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoUri) {
        contactPhoto = photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri.toUri())?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    val initials = remember(displayName) { contactInitials(displayName) }
    Surface(
        modifier = Modifier.size(LeadingSlotSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (contactPhoto != null) {
                Image(
                    bitmap = contactPhoto!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SearchPanelFileLeadingIcon(file: DeviceFileEntry) {
    val context = LocalContext.current
    val fileType = remember(file.uri) { FileTypeUtils.getFileType(file) }
    val showThumbnail = !file.isDirectory &&
        (fileType == FileType.PICTURES || fileType == FileType.VIDEOS)
    val uriString = file.uri.toString()
    var thumbnailBitmap by remember(uriString) { mutableStateOf(FileThumbnailCache.get(uriString)) }

    if (showThumbnail) {
        LaunchedEffect(uriString) {
            if (thumbnailBitmap != null) return@LaunchedEffect
            val imageBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .loadThumbnail(file.uri, Size(THUMBNAIL_LOAD_SIZE_PX, THUMBNAIL_LOAD_SIZE_PX), null)
                        .asImageBitmap()
                }.getOrNull()
            }
            if (imageBitmap != null) {
                FileThumbnailCache.put(uriString, imageBitmap)
                thumbnailBitmap = imageBitmap
            }
        }
    }

    val fallbackIcon = when {
        file.isDirectory -> Icons.Default.Folder
        fileType == FileType.PICTURES -> Icons.Default.Image
        fileType == FileType.VIDEOS -> Icons.Default.VideoLibrary
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    if (thumbnailBitmap != null) {
        val alpha = remember(uriString) { Animatable(0f) }
        LaunchedEffect(uriString) {
            alpha.animateTo(1f, animationSpec = tween(200))
        }
        Box(modifier = Modifier.size(LeadingSlotSize)) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(LeadingSlotSize)
                    .alpha(1f - alpha.value),
                tint = MaterialTheme.colorScheme.primary,
            )
            Image(
                bitmap = thumbnailBitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(LeadingSlotSize)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(alpha.value),
                contentScale = ContentScale.Crop,
            )
        }
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            modifier = Modifier
                .size(LeadingSlotSize)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                )
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelResultCard(
    title: String,
    subtitle: String?,
    leadingIcon: ImageVector,
    longPressEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleEmphasized: Boolean = false,
) {
    SearchPanelResultCard(
        title = title,
        subtitle = subtitle,
        leading = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(LeadingSlotSize)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        longPressEnabled = longPressEnabled,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        titleEmphasized = titleEmphasized,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelResultCard(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    longPressEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleEmphasized: Boolean = false,
) {
    val clickModifier = if (longPressEnabled) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (titleEmphasized) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                fontWeight = if (titleEmphasized) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (titleEmphasized) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun contactInitials(displayName: String): String {
    val parts = displayName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
