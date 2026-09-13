package com.slideindex.app.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherAppsCallbackBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
) {
    private val launcherApps: LauncherApps? =
        context.getSystemService(LauncherApps::class.java)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            onPackageEvent("launcher_added:$packageName")
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            onPackageEvent("launcher_removed:$packageName")
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            onPackageEvent("launcher_changed:$packageName")
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) {
            onPackageEvent("launcher_available:${packageNames.joinToString()}")
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) {
            onPackageEvent("launcher_unavailable:${packageNames.joinToString()}")
        }

        private fun onPackageEvent(reason: String) {
            appRepository.invalidate()
            appRepository.requestRefresh(reason)
        }
    }

    fun register() {
        launcherApps?.registerCallback(callback)
    }

    fun unregister() {
        launcherApps?.unregisterCallback(callback)
    }
}
