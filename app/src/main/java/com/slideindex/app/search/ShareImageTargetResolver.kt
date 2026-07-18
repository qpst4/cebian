package com.slideindex.app.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.slideindex.app.util.queryIntentActivitiesCompat

data class ShareImageTarget(
    val packageName: String,
    val activityClassName: String,
    /** Activity 在分享选择器里显示的名称，如「拍立淘」。 */
    val label: String,
    /** 所属应用的名称，如「淘宝」。 */
    val appLabel: String,
    val icon: Drawable?,
)

object ShareImageTargetResolver {
    fun listTargets(context: Context): List<ShareImageTarget> {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
        }
        val pm = context.packageManager
        return pm.queryIntentActivitiesCompat(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolveInfo -> toTarget(pm, resolveInfo) }
            .distinctBy { "${it.packageName}/${it.activityClassName}" }
            .sortedWith(
                compareBy<ShareImageTarget> { it.appLabel.lowercase() }
                    .thenBy { it.label.lowercase() },
            )
    }

    fun searchTargets(targets: List<ShareImageTarget>, query: String): List<ShareImageTarget> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return targets
        val lower = trimmed.lowercase()
        return targets.filter { target ->
            target.appLabel.lowercase().contains(lower) ||
                target.label.lowercase().contains(lower) ||
                target.packageName.lowercase().contains(lower) ||
                target.activityClassName.lowercase().contains(lower)
        }
    }

    fun displaySubtitle(target: ShareImageTarget): String {
        return if (target.label.equals(target.appLabel, ignoreCase = true)) {
            target.packageName
        } else {
            "${target.appLabel} · ${target.packageName}"
        }
    }

    private fun toTarget(pm: PackageManager, resolveInfo: ResolveInfo): ShareImageTarget? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val appLabel = runCatching {
            val appInfo = pm.getApplicationInfo(activityInfo.packageName, 0)
            pm.getApplicationLabel(appInfo).toString().trim()
        }.getOrDefault(activityInfo.packageName)
        val label = resolveInfo.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: activityInfo.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: appLabel
        return ShareImageTarget(
            packageName = activityInfo.packageName,
            activityClassName = activityInfo.name,
            label = label,
            appLabel = appLabel,
            icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull(),
        )
    }
}
