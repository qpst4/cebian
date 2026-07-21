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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLaunchPort: AppLaunchPort,
) {
    @Volatile
    private var cachedApps: List<AppInfo> = emptyList()

    @Volatile
    private var appsByPackage: Map<String, AppInfo> = emptyMap()

    suspend fun loadApps(force: Boolean = false): List<AppInfo> {
        if (!force && cachedApps.isNotEmpty()) return cachedApps
        val apps = withContext(Dispatchers.IO) { queryLaunchableApps() }
        cacheApps(apps)
        return apps
    }

    fun getCachedApps(): List<AppInfo> = cachedApps

    fun hasCachedApps(): Boolean = cachedApps.isNotEmpty()

    fun lookupApp(packageName: String): AppInfo? {
        if (packageName == context.packageName) return null
        appsByPackage[packageName]?.let { return it }
        return queryAppInfo(packageName)?.also { info ->
            appsByPackage = appsByPackage + (packageName to info)
        }
    }

    fun getCachedAppInfo(packageName: String): AppInfo? {
        if (packageName.isBlank() || packageName == context.packageName) return null
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
        if (packageName.isBlank() || packageName == context.packageName) return null
        return lookupApp(packageName)
    }

    /** Map a recents dump identifier to an installed package (handles Flyme class-style names). */
    fun resolveInstalledPackage(identifier: String): String? {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) return null
        // Try the raw identifier first — package names like com.eg.android.AlipayGphone must not
        // be normalized before lookup (normalize strips uppercase last segments).
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
        cachedApps = emptyList()
        appsByPackage = emptyMap()
    }

    private fun cacheApps(apps: List<AppInfo>) {
        cachedApps = apps
        appsByPackage = apps.associateBy { it.packageName }
    }

    fun groupedItems(apps: List<AppInfo>): List<AppListItem> {
        val sorted = apps.sortedWith(
            compareBy<AppInfo> { it.letter }.thenBy { it.pinyinKey },
        )
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

    fun searchApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.filter { app ->
            app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.pinyinKey.contains(q)
        }.sortedBy { it.pinyinKey }
    }

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
            if (pkg == context.packageName) return@forEach
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
            apps += buildAppInfo(pkg, label)
        }
        return apps
    }

    private fun queryAppInfo(packageName: String): AppInfo? {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            buildAppInfo(packageName, label)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun buildAppInfo(packageName: String, label: String): AppInfo =
        AppInfo(
            packageName = packageName,
            label = label,
            letter = PinyinHelper.firstLetter(label),
            pinyinKey = PinyinHelper.sortKey(label),
        )
}
