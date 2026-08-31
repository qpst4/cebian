@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Update
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.ui.icon.AppIconTheme
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixBlurredTopBar
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.effect.BgEffectBackground
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ExtensionAboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyNotices: () -> Unit,
    onOpenNativeEnginePacks: () -> Unit,
    onCheckUpdate: () -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    val scrollProgressState = remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "logoSpacer" }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }

    val heroCollapsed by remember { derivedStateOf { scrollProgressState.value == 1f } }
    val titleAlpha by remember {
        derivedStateOf { ((scrollProgressState.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }
    }

    val backdrop = rememberMiuixBlurBackdrop()
    val blurActive = backdrop != null && heroCollapsed
    val barColor = if (heroCollapsed && !blurActive) MiuixTheme.colorScheme.surface else Color.Transparent

    Scaffold(
        topBar = {
            MiuixBlurredTopBar(
                backdrop = backdrop,
                enabled = blurActive,
                scrollBehavior = scrollBehavior,
            ) {
                SmallTopAppBar(
                    title = stringResource(R.string.about_section_title),
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        MiuixBackNavigationIcon(onBack)
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            AboutContent(
                innerPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                lazyListState = lazyListState,
                scrollProgress = { scrollProgressState.value },
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                onOpenThirdPartyNotices = onOpenThirdPartyNotices,
                onOpenNativeEnginePacks = onOpenNativeEnginePacks,
                onCheckUpdate = onCheckUpdate,
                autoCheckUpdate = autoCheckUpdate,
                onAutoCheckUpdateChange = onAutoCheckUpdateChange,
            )
        }
    }
}

@Composable
private fun AboutContent(
    innerPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    lazyListState: LazyListState,
    scrollProgress: () -> Float,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyNotices: () -> Unit,
    onOpenNativeEnginePacks: () -> Unit,
    onCheckUpdate: () -> Unit,
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val backdrop = rememberLayerBackdrop()

    val isDark = LocalAppDarkTheme.current
    val blurEnabled = remember(isDark) { isRuntimeShaderSupported() }

    var selectedIconTheme by remember { mutableStateOf(AppIconTheme.getSelected(context)) }
    var showIconPicker by remember { mutableStateOf(false) }

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1.toInt()), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500.toInt()), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a.toInt()), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f.toInt()), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200.toInt()), BlurBlendMode.Lab),
            )
        }
    }

    var logoHeightDp by remember { mutableStateOf(280.dp) }

    val versionCodeProgress = { ((scrollProgress() - 0.05f) / 0.15f).coerceIn(0f, 1f) }
    val projectNameProgress = { ((scrollProgress() - 0.20f) / 0.15f).coerceIn(0f, 1f) }
    val iconProgress = { ((scrollProgress() - 0.35f) / 0.15f).coerceIn(0f, 1f) }

    val scrollPadding = PaddingValues(
        top = innerPadding.calculateTopPadding(),
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )
    val logoPadding = PaddingValues(
        top = innerPadding.calculateTopPadding() + 32.dp,
        start = innerPadding.calculateStartPadding(layoutDirection),
        end = innerPadding.calculateEndPadding(layoutDirection),
    )

    val projectUrl = stringResource(R.string.about_project_url_desc)
    val qqGroupUrl = stringResource(R.string.about_qq_group_url)
    val appInfoTitle = stringResource(R.string.about_section_app_info)
    val communityTitle = stringResource(R.string.about_section_community)
    val openSourceTitle = stringResource(R.string.about_section_open_source)
    val advancedTitle = stringResource(R.string.about_advanced_section_title)

    BgEffectBackground(
        dynamicBackground = blurEnabled,
        modifier = Modifier.fillMaxSize(),
        bgModifier = Modifier.layerBackdrop(backdrop),
        isFullSize = true,
        effectBackground = blurEnabled,
        alpha = { 1f - scrollProgress() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 44.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer {
                        val p = iconProgress()
                        alpha = 1 - p
                        scaleX = 1 - (p * 0.05f)
                        scaleY = 1 - (p * 0.05f)
                    }
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                Image(
                    painter = painterResource(selectedIconTheme.iconRes),
                    contentDescription = "icon",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .graphicsLayer {
                        val p = projectNameProgress()
                        alpha = 1 - p
                        scaleX = 1 - (p * 0.05f)
                        scaleY = 1 - (p * 0.05f)
                    }
                    .then(
                        if (blurEnabled) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = true,
                            )
                        } else {
                            Modifier
                        },
                    ),
                text = stringResource(R.string.app_name),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = versionCodeProgress()
                        alpha = 1 - p
                        scaleX = 1 - (p * 0.05f)
                        scaleY = 1 - (p * 0.05f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    text = stringResource(
                        R.string.about_version_format,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    fontSize = 14.sp,
                )
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = scrollPadding.calculateTopPadding(),
                start = scrollPadding.calculateStartPadding(layoutDirection),
                end = scrollPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            item(key = "logoSpacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 44.dp + logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding() + 96.dp,
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(
                                top = logoPadding.calculateTopPadding() + 44.dp - scrollPadding.calculateTopPadding(),
                            )
                            .size(90.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                showIconPicker = true
                            },
                    )
                }
            }

            settingsLazySmallTitle(key = "app_info_section", title = appInfoTitle, sectionTop = true)
            groupedCardItems(
                keyPrefix = "about_app_info",
                items = buildList {
                    add(
                        settingsCardScopeItem("check-update") {
                            SettingNavigationRow(
                                icon = { label -> Icon(MiuixIcons.Update, contentDescription = label) },
                                title = stringResource(R.string.about_check_update_title),
                                subtitle = stringResource(R.string.about_check_update_subtitle),
                                onClick = onCheckUpdate,
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("release-notes") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.NewReleases, contentDescription = label) },
                                title = stringResource(R.string.about_release_notes_title),
                                subtitle = stringResource(R.string.about_release_notes_subtitle),
                                onClick = {
                                    val uri = (projectUrl + "/releases").toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                            )
                        },
                    )
                },
            )

            settingsLazySmallTitle(key = "community_section", title = communityTitle, sectionTop = true)
            groupedCardItems(
                keyPrefix = "about_community",
                items = buildList {
                    add(
                        settingsCardScopeItem("project-url") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.Code, contentDescription = label) },
                                title = stringResource(R.string.about_project_url_title),
                                subtitle = stringResource(R.string.about_project_url_desc),
                                onClick = {
                                    val uri = projectUrl.toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("qq-group") {
                            SettingNavigationRow(
                                icon = { label -> Icon(MiuixIcons.Community, contentDescription = label) },
                                title = stringResource(R.string.about_qq_group_title),
                                subtitle = stringResource(R.string.about_qq_group_desc),
                                onClick = {
                                    val uri = qqGroupUrl.toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                            )
                        },
                    )
                },
            )

            settingsLazySmallTitle(key = "open_source_section", title = openSourceTitle, sectionTop = true)
            groupedCardItems(
                keyPrefix = "about_open_source",
                items = buildList {
                    add(
                        settingsCardScopeItem("privacy-policy") {
                            PrivacyPolicyEntryCard(onClick = onOpenPrivacyPolicy)
                        },
                    )
                    add(
                        settingsCardScopeItem("open-source-license") {
                            OpenSourceLicenseEntryCard(
                                onClick = {
                                    val uri = (projectUrl + "/blob/HEAD/LICENSE").toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("third-party-notices") {
                            ThirdPartyNoticesEntryCard(onClick = onOpenThirdPartyNotices)
                        },
                    )
                },
            )

            settingsLazySmallTitle(key = "advanced_section", title = advancedTitle, sectionTop = true)
            groupedCardItems(
                keyPrefix = "about_advanced",
                items = buildList {
                    add(
                        settingsCardScopeItem("auto-check-update") {
                            SettingToggleRow(
                                icon = { label -> Icon(MiuixIcons.Update, contentDescription = label) },
                                title = stringResource(R.string.auto_check_update_title),
                                subtitle = stringResource(R.string.auto_check_update_hint),
                                checked = autoCheckUpdate,
                                onCheckedChange = onAutoCheckUpdateChange,
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("native-engine-packs") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.Memory, contentDescription = label) },
                                title = stringResource(R.string.extension_native_engine_packs_entry_title),
                                subtitle = stringResource(R.string.about_native_engine_packs_entry_desc),
                                onClick = onOpenNativeEnginePacks,
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("diagnostic-report") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.BugReport, contentDescription = label) },
                                title = "复制系统诊断与错误日志",
                                subtitle = "一键导出系统状态与最近崩溃堆栈 (100% 本地隐私)",
                                onClick = {
                                    val report = com.slideindex.app.util.LocalCrashHandler.generateDiagnosticReport(context)
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Cebian Diagnostic Report", report))
                                    android.widget.Toast.makeText(context, "诊断与错误日志已复制至剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                                },
                            )
                        },
                    )
                },
            )

            item(key = "about-footer") {
                Text(
                    text = stringResource(
                        R.string.about_copyright_notice,
                        Calendar.getInstance().get(Calendar.YEAR),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            item(key = "bottom-inset") {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }

        // 图标挑选 BottomSheet
        AppIconPickerBottomSheet(
            show = showIconPicker,
            selected = selectedIconTheme,
            onDismissRequest = { showIconPicker = false },
            onSelect = { theme ->
                selectedIconTheme = theme
                AppIconTheme.applyIconTheme(context, theme)
                showIconPicker = false
            },
        )
    }
}

@Composable
private fun AppIconPickerBottomSheet(
    show: Boolean,
    selected: AppIconTheme,
    onDismissRequest: () -> Unit,
    onSelect: (AppIconTheme) -> Unit,
) {
    if (!show) return
    MiuixBottomSheet(
        show = true,
        title = stringResource(R.string.app_icon_theme_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIconTheme.entries.forEach { theme ->
                val isSelected = theme == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        )
                        .clickable { onSelect(theme) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(theme.iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(theme.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(theme.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
