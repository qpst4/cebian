@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutKind
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import com.slideindex.app.util.PickerAppIconBitmap
import com.slideindex.app.util.toSafeImageBitmap
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

internal val PickerListScaffoldEmbeddedHorizontalPadding = 0.dp
internal val PickerListOverlayHorizontalPadding = 12.dp
internal val PickerListHorizontalPadding = PickerListScaffoldEmbeddedHorizontalPadding
internal val PickerListGroupSpacing = 12.dp
internal val PickerListContentPadding = PaddingValues(
    horizontal = PickerListScaffoldEmbeddedHorizontalPadding,
    vertical = 8.dp,
)

enum class PickerTrailingMode {
    None,
    Radio,
    Toggle,
    Icon,
}

@Composable
internal fun settingsSegmentedColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
)

@Composable
internal fun pickerSegmentedColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
)

@Composable
internal fun pickerSegmentedShapes(index: Int, count: Int) =
    ListItemDefaults.segmentedShapes(index = index, count = count)

@Composable
internal fun pickerListSegmentedGap() = ListItemDefaults.SegmentedGap

private const val PickerSegmentGroupMax = 12

internal fun pickerSegmentIndex(index: Int, total: Int): Int =
    if (total <= PickerSegmentGroupMax) index else 0

internal fun pickerSegmentCount(total: Int): Int =
    if (total <= PickerSegmentGroupMax) total else 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerSearchListHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    @androidx.annotation.StringRes hintResId: Int = R.string.search_hint,
    horizontalPadding: Dp = PickerListScaffoldEmbeddedHorizontalPadding,
) {
    SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        hintResId = hintResId,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
    )
}

@Composable
fun Md3PickerSectionHeader(title: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = title,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun Md3PickerListRow(
    segmentIndex: Int,
    segmentCount: Int,
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingMode: PickerTrailingMode = PickerTrailingMode.None,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val cardModifier = modifier.miuixGroupedCardItem(segmentIndex, segmentCount)
    val summaryText = subtitle?.takeIf { supportingContent == null }
    val bottomAction: (@Composable () -> Unit)? = supportingContent?.let { extra ->
        { extra() }
    }

    when (trailingMode) {
        PickerTrailingMode.Radio -> {
            val click = onClick ?: return
            RadioButtonPreference(
                modifier = cardModifier,
                title = title,
                summary = summaryText,
                selected = selected,
                onClick = click,
                enabled = enabled,
                startAction = { leadingContent() },
                radioButtonLocation = RadioButtonLocation.End,
                bottomAction = bottomAction,
            )
        }
        PickerTrailingMode.Toggle -> {
            val click = onTrailingClick ?: onClick ?: return
            CheckboxPreference(
                modifier = cardModifier,
                title = title,
                summary = summaryText,
                checked = selected,
                onCheckedChange = { click() },
                enabled = enabled,
                startAction = { leadingContent() },
                checkboxLocation = CheckboxLocation.End,
                bottomAction = bottomAction,
            )
        }
        PickerTrailingMode.Icon -> {
            val icon = trailingIcon ?: return
            val trailingClick = onTrailingClick ?: onClick ?: return
            val rowClick = onClick ?: trailingClick
            BasicComponent(
                modifier = cardModifier,
                title = title,
                summary = summaryText,
                enabled = enabled,
                onClick = rowClick,
                startAction = { leadingContent() },
                endActions = {
                    IconButton(onClick = trailingClick) {
                        MiuixIcon(
                            imageVector = icon,
                            contentDescription = trailingIconDescription,
                        )
                    }
                },
                bottomAction = bottomAction,
            )
        }
        PickerTrailingMode.None -> {
            BasicComponent(
                modifier = cardModifier,
                title = title,
                summary = summaryText,
                enabled = enabled,
                onClick = onClick,
                startAction = { leadingContent() },
                bottomAction = bottomAction,
            )
        }
    }
}

@Composable
fun Md3PickerIconLeading(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String? = null,
) {
    val containerShape = if (selected) {
        MaterialShapes.Cookie9Sided.toShape()
    } else {
        MaterialTheme.shapes.small
    }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = containerShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
fun Md3PickerManagedShortcutLeading(
    shortcut: ActivityShortcut,
    selected: Boolean,
) {
    val context = LocalContext.current
    val fallbackIcon = when (shortcut.kind) {
        ActivityShortcutKind.COMPONENT -> Icons.AutoMirrored.Filled.Launch
        else -> Icons.AutoMirrored.Filled.Shortcut
    }
    val iconBitmap = remember(shortcut.id, shortcut.iconPath, shortcut.identityKey()) {
        ManagedShortcutIconResolver.drawableForManaged(context, shortcut)
            ?.toBitmap(96, 96)
    }
    if (iconBitmap != null) {
        val containerShape = if (selected) {
            MaterialShapes.Cookie9Sided.toShape()
        } else {
            MaterialTheme.shapes.small
        }
        Surface(
            modifier = Modifier.size(40.dp),
            shape = containerShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    } else {
        Md3PickerIconLeading(icon = fallbackIcon, selected = selected)
    }
}

@Composable
fun Md3PickerAppLeading(app: AppInfo) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(app.packageName) {
        mutableStateOf(PickerAppIconBitmap.peek(app.packageName))
    }
    var failed by remember(app.packageName) { mutableStateOf(false) }
    LaunchedEffect(app.packageName) {
        if (bitmap == null && !failed) {
            val loaded = PickerAppIconBitmap.load(context, app.packageName)
            if (loaded == null) {
                failed = true
            } else {
                bitmap = loaded
            }
        }
    }
    Md3PickerBitmapLeading(
        bitmap = bitmap,
        failed = failed,
        contentDescription = app.label,
    )
}

@Composable
fun Md3PickerActivityLeading(
    packageName: String,
    className: String,
    contentDescription: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cacheKey = "$packageName/$className"
    // 只认 Activity 缓存；不要用 App 图标 peek，否则会短路掉真正的 Activity 图标加载。
    var bitmap by remember(cacheKey) {
        mutableStateOf(PickerAppIconBitmap.peekActivity(packageName, className))
    }
    var failed by remember(cacheKey) { mutableStateOf(false) }
    LaunchedEffect(cacheKey) {
        val loaded = PickerAppIconBitmap.loadActivity(context, packageName, className)
        if (loaded == null) {
            failed = true
            bitmap = null
        } else {
            failed = false
            bitmap = loaded
        }
    }
    Md3PickerBitmapLeading(
        bitmap = bitmap,
        failed = failed,
        contentDescription = contentDescription,
    )
}

@Composable
private fun Md3PickerBitmapLeading(
    bitmap: ImageBitmap?,
    failed: Boolean,
    contentDescription: String,
) {
    when {
        bitmap != null -> {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        failed -> {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = contentDescription,
            )
        }
        else -> {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {}
        }
    }
}

@Composable
fun Md3PickerDrawableLeading(
    drawable: android.graphics.drawable.Drawable,
    contentDescription: String,
    cacheKey: String,
) {
    val bitmap = remember(cacheKey) { drawable.toSafeImageBitmap(96) }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun Md3PickerAppEntryLeading(
    entry: AppPackageEntry,
    missingIcon: ImageVector,
) {
    when (entry) {
        is AppPackageEntry.Installed -> Md3PickerAppLeading(entry.app)
        is AppPackageEntry.Missing -> Md3PickerIconLeading(
            icon = missingIcon,
            selected = false,
        )
    }
}

@Composable
fun Md3PickerSupportingHints(
    description: String?,
    permissionHint: String?,
) {
    if (description.isNullOrBlank() && permissionHint.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!permissionHint.isNullOrBlank()) {
            Text(
                text = permissionHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
