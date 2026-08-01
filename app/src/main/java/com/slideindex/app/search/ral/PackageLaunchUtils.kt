package com.slideindex.app.search.ral

import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager

internal val Context.isTouchWiz: Boolean
    get() = packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile")

internal fun PackageManager.getAllIntentFiltersCompat(packageName: String?): List<IntentFilter> {
    if (packageName.isNullOrBlank()) return emptyList()
    return HiddenFrameworkAccess.getAllIntentFilters(this, packageName)
}
