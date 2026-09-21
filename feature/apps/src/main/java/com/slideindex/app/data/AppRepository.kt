package com.slideindex.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.slideindex.app.util.RecentPackageResolver
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.settings.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLaunchPort: AppLaunchPort,
    private val launchIconCache: AppLaunchIconCache,
    private val applicationScope: CoroutineScope,
) {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _appsForSearch = MutableStateFlow<List<AppInfo>>(emptyList())
    val appsForSearch: StateFlow<List<AppInfo>> = _appsForSearch.asStateFlow()

    private val _appsForSearchRevision = MutableStateFlow(0L)
    val appsForSearchRevision: StateFlow<Long> = _appsForSearchRevision.asStateFlow()

    private val _appsRevision = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val appsRevision: SharedFlow<Unit> = _appsRevision.asSharedFlow()

    @Volatile
    private var cachedFreezerApps: List<AppInfo> = emptyList()

    @Volatile
    private var cachedActivityTargetApps: List<AppInfo> = emptyList()

    @Volatile
    private var appsByPackage: Map<String, AppInfo> = emptyMap()

    private val refreshMutex = Mutex()
    private var debouncedRefreshJob: Job? = null

    suspend fun loadApps(force: Boolean = false): List<AppInfo> {
        if (!force && _apps.value.isNotEmpty()) return _apps.value
        return refreshApps()
    }

    /** Launcher 列表 + 已禁用（冻结）且不在 Launcher 中的应用，供搜索面板使用。 */
    suspend fun loadAppsForSearch(force: Boolean = false): List<AppInfo> {
        val launchable = if (force || _apps.value.isEmpty()) refreshApps() else _apps.value
        return publishAppsForSearch(launchable)
    }

    suspend fun refreshApps(): List<AppInfo> = refreshMutex.withLock {
        val queried = withContext(Dispatchers.IO) { queryLaunchableApps() }
        publishApps(queried)
        queried
    }

    fun requestRefresh(reason: String) {
        debouncedRefreshJob?.cancel()
        debouncedRefreshJob = applicationScope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            runCatching { refreshApps() }
        }
    }

    suspend fun loadFreezerApps(force: Boolean = false): List<AppInfo> {
        if (!force && cachedFreezerApps.isNotEmpty()) return cachedFreezerApps
        val apps = withContext(Dispatchers.IO) { queryInstalledFreezerApps() }
        cachedFreezerApps = apps
        return apps
    }

    /**
     * 「带 Activity 的已安装包」——启动器列表之外的系统应用候选集，供应用内直达 / 按应用禁用 /
     * 黑名单这类「先选应用、再选 Activity」的页面使用。
     *
     * 与 [loadFreezerApps] 的区别：逐包查 `GET_ACTIVITIES` 丢掉没有任何 Activity 的包
     * （provider / service-only 的系统包上百个，点进去只会得到空列表），且刻意**不预热图标**，
     * 几百个系统图标常驻内存的代价远大于收益，图标交给 picker 懒加载。
     */
    suspend fun loadActivityTargetApps(force: Boolean = false): List<AppInfo> {
        if (!force && cachedActivityTargetApps.isNotEmpty()) return cachedActivityTargetApps
        // 排除集必须是完整的启动器列表，否则会重复列出普通应用
        val launchable = if (_apps.value.isEmpty()) loadApps() else _apps.value
        val apps = withContext(Dispatchers.IO) {
            queryActivityTargetApps(launchable.mapTo(HashSet()) { it.packageName })
        }
        cachedActivityTargetApps = apps
        return apps
    }

    suspend fun resolveFreezerMembers(packages: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        packages.mapNotNull { packageName ->
            resolveAppInfo(packageName) ?: lookupApp(packageName)
        }.sortedBy { it.pinyinKey }
    }

    fun getCachedApps(): List<AppInfo> = _apps.value

    fun getCachedAppsForSearch(): List<AppInfo> = _appsForSearch.value

    fun hasCachedApps(): Boolean = _apps.value.isNotEmpty()

    fun lookupApp(packageName: String): AppInfo? {
        appsByPackage[packageName]?.let { return it }
        return queryAppInfo(packageName)?.also { info ->
            appsByPackage = appsByPackage + (packageName to info)
        }
    }

    fun getCachedAppInfo(packageName: String): AppInfo? {
        if (packageName.isBlank()) return null
        return appsByPackage[packageName]
    }

    suspend fun resolveAppInfo(packageName: String): AppInfo? {
        getCachedAppInfo(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            queryAppInfo(packageName)?.also { info ->
                appsByPackage = appsByPackage + (packageName to info)
            }
        }
    }

    /** Resolve icon/label from PackageManager even when the app is not in the launcher cache. */
    fun ensureAppInfo(packageName: String): AppInfo? {
        if (packageName.isBlank()) return null
        return lookupApp(packageName)
    }

    /** Map a recents dump identifier to an installed package (handles Flyme class-style names). */
    fun resolveInstalledPackage(identifier: String): String? {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) return null
        resolveByPackageManager(trimmed)?.let { return it }
        val normalized = RecentPackageResolver.normalizeIdentifier(trimmed)
        if (normalized != trimmed) {
            resolveByPackageManager(normalized)?.let { return it }
        }
        return guessKnownPackage(normalized)
    }

    private fun resolveByPackageManager(candidate: String): String? {
        var current = candidate
        while (current.contains('.')) {
            if (queryAppInfo(current) != null) return current
            val parent = current.substringBeforeLast('.', missingDelimiterValue = "")
            if (parent == current) break
            current = parent
        }
        return null
    }

    private fun guessKnownPackage(normalized: String): String? {
        val hints = when {
            normalized.contains("settings") -> listOf(
                "com.android.settings",
                "com.meizu.settings",
                "com.meizu.flyme.settings",
            )
            normalized.contains("camera") -> listOf(
                "com.meizu.media.camera",
                "com.android.camera",
                "com.meizu.camera",
            )
            normalized.contains("sharing") ||
                normalized.contains("quickshare") ||
                normalized.contains("nearby") ||
                normalized.contains("gms") -> listOf(
                "com.google.android.gms",
                "com.google.android.apps.nbu.p2p",
            )
            else -> emptyList()
        }
        return hints.firstOrNull { queryAppInfo(it) != null }
    }

    fun invalidate() {
        cachedFreezerApps = emptyList()
        cachedActivityTargetApps = emptyList()
        appsByPackage = emptyMap()
        launchIconCache.clear()
        _appsForSearch.value = emptyList()
    }

    fun launchIconBitmap(packageName: String, sizePx: Int): android.graphics.Bitmap =
        launchIconCache.bitmapFor(packageName, sizePx)

    fun peekLaunchIconDrawable(packageName: String): android.graphics.drawable.Drawable? =
        launchIconCache.peekDrawable(packageName)

    fun peekLaunchIconBitmap(packageName: String, sizePx: Int): android.graphics.Bitmap? =
        launchIconCache.peekBitmap(packageName, sizePx)

    fun launchIconDrawable(packageName: String): android.graphics.drawable.Drawable? =
        launchIconCache.drawableFor(packageName)

    fun warmLaunchIconBitmapsAsync(packageNames: Collection<String>, sizePx: Int) {
        launchIconCache.warmBitmapsAsync(packageNames, sizePx)
    }

    private fun publishApps(apps: List<AppInfo>) {
        appsByPackage = apps.associateBy { it.packageName }
        launchIconCache.retainPackages(apps.map { it.packageName })
        _apps.value = apps
        _appsRevision.tryEmit(Unit)
        scheduleAppsForSearchUpdate(apps)
    }

    private fun scheduleAppsForSearchUpdate(launchable: List<AppInfo>) {
        applicationScope.launch {
            runCatching { publishAppsForSearch(launchable) }
        }
    }

    private suspend fun publishAppsForSearch(launchable: List<AppInfo>): List<AppInfo> {
        val merged = withContext(Dispatchers.IO) { mergeAppsForSearch(launchable) }
        _appsForSearch.value = merged
        _appsForSearchRevision.value = _appsForSearchRevision.value + 1
        return merged
    }

    private fun mergeAppsForSearch(launchable: List<AppInfo>): List<AppInfo> {
        val launchablePackages = launchable.map { it.packageName }.toSet()
        val byPackage = launchable.associateBy { it.packageName }.toMutableMap()
        queryDisabledAppsNotInLaunchable(launchablePackages).forEach { app ->
            byPackage[app.packageName] = app
        }
        return byPackage.values.sortedWith(appLetterOrder)
    }

    private fun queryDisabledAppsNotInLaunchable(launchablePackages: Set<String>): List<AppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val appInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        return appInfos
            .asSequence()
            .filter { it.packageName != selfPackage }
            .filter { !it.enabled }
            .filter { it.packageName !in launchablePackages }
            .mapNotNull { appInfo ->
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(appInfo.packageName)
                launchIconCache.loadDrawable(appInfo)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                buildAppInfo(appInfo.packageName, label, isSystem)
            }
            .toList()
    }

    private fun queryActivityTargetApps(excludePackages: Set<String>): List<AppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val appInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        return appInfos
            .asSequence()
            .filter { it.packageName != selfPackage }
            .filter { it.packageName !in excludePackages }
            .filter { hasAnyActivity(pm, it.packageName) }
            .map { appInfo ->
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(appInfo.packageName)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                buildAppInfo(appInfo.packageName, label, isSystem)
            }
            .sortedWith(appLetterOrder)
            .toList()
    }

    /**
     * flags 必须与 [com.slideindex.app.util.PackageActivityResolver] 保持一致（含
     * `MATCH_DISABLED_COMPONENTS`），否则这里判定「有 Activity」的包，点进去可能列出空列表。
     */
    private fun hasAnyActivity(pm: PackageManager, packageName: String): Boolean = runCatching {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
        !info.activities.isNullOrEmpty()
    }.getOrDefault(false)

    /** 合并「启动器应用 + 补充候选包」，按包名去重（picker 的 LazyColumn 用包名做 key，重复会崩）。 */
    fun mergeActivityTargets(launchable: List<AppInfo>, extras: List<AppInfo>): List<AppInfo> =
        (launchable + extras).distinctBy { it.packageName }

    /** 统一的「首字母 → 拼音」排序口径，与分组列表 / 搜索面板保持一致。 */
    fun sortedByLetter(apps: List<AppInfo>): List<AppInfo> = apps.sortedWith(appLetterOrder)

    fun groupedItems(apps: List<AppInfo>): List<AppListItem> {
        val sorted = apps.sortedWith(appLetterOrder)
        val items = mutableListOf<AppListItem>()
        var currentLetter: Char? = null
        sorted.forEach { app ->
            if (app.letter != currentLetter) {
                currentLetter = app.letter
                items += AppListItem.Header(app.letter)
            }
            items += AppListItem.App(app)
        }
        return items
    }

    fun searchApps(
        apps: List<AppInfo>,
        query: String,
        limit: Int = Int.MAX_VALUE,
    ): List<AppInfo> = AppSearchMatcher.search(apps, query, limit)

    fun availableLetters(items: List<AppListItem>): List<Char> =
        items.filterIsInstance<AppListItem.Header>().map { it.letter }

    fun launchApp(appInfo: AppInfo, settings: AppSettings, fullscreen: Boolean): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
            ?: return false
        appLaunchPort.launch(intent, settings, fullscreen)
        return true
    }

    private fun queryLaunchableApps(): List<AppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val seen = mutableSetOf<String>()
        val apps = mutableListOf<AppInfo>()
        resolveInfos.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (!seen.add(pkg)) return@forEach
            val appInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@forEach
            }
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                pm.getLaunchIntentForPackage(pkg) == null
            ) {
                return@forEach
            }
            val label = pm.getApplicationLabel(appInfo).toString()
            launchIconCache.loadDrawable(appInfo)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            apps += buildAppInfo(pkg, label, isSystem)
        }
        return apps
    }

    private fun queryInstalledFreezerApps(): List<AppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val appInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        return appInfos
            .asSequence()
            .filter { it.packageName != selfPackage }
            .map { appInfo ->
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(appInfo.packageName)
                launchIconCache.loadDrawable(appInfo)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                buildAppInfo(appInfo.packageName, label, isSystem)
            }
            .sortedWith(compareBy<AppInfo> { !it.isSystem }.thenBy { it.pinyinKey })
            .toList()
    }

    private fun queryAppInfo(packageName: String): AppInfo? {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            launchIconCache.loadDrawable(appInfo)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            buildAppInfo(packageName, label, isSystem)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun buildAppInfo(packageName: String, label: String, isSystem: Boolean = false): AppInfo =
        AppInfo(
            packageName = packageName,
            label = label,
            letter = PinyinHelper.firstLetter(label),
            pinyinKey = PinyinHelper.sortKey(label),
            isSystem = isSystem,
        )

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 400L
    }
}

private val appLetterOrder = compareBy<AppInfo> { it.letter }.thenBy { it.pinyinKey }
