package com.slideindex.app.freezer

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezerListOperations {
    suspend fun removeFromList(
        context: Context,
        settingsRepository: SettingsRepository,
        packageName: String,
        appRepository: AppRepository? = null,
    ): Boolean {
        if (FreezerOperations.isFrozen(context, packageName)) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.freezer_remove_while_frozen, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        settingsRepository.removeFreezerApp(packageName)
        appRepository?.invalidate()
        return true
    }

    suspend fun unfreezeAndRemoveFromList(
        context: Context,
        settingsRepository: SettingsRepository,
        packageName: String,
        appRepository: AppRepository? = null,
    ): Boolean {
        if (FreezerOperations.isFrozen(context, packageName)) {
            if (!FreezerOperations.setFrozen(context, packageName, frozen = false)) {
                return false
            }
        }
        settingsRepository.removeFreezerApp(packageName)
        appRepository?.invalidate()
        return true
    }

    suspend fun importFrozenApps(
        context: Context,
        settingsRepository: SettingsRepository,
    ): Int {
        val scanned = FreezerBootstrap.scanDisabledLauncherPackages(context)
        if (scanned.isEmpty()) return 0
        val current = settingsRepository.readFreshSnapshot().freezerAppPackages
        val toAdd = scanned - current
        toAdd.forEach { settingsRepository.addFreezerApp(it) }
        return toAdd.size
    }
}
