package com.slideindex.app.freezer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.di.AppDependencies
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FreezerAppLaunchTrampolineActivity : ComponentActivity() {
    @Inject lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val settings = deps.settingsRepository.readSnapshot()
            val app = withContext(Dispatchers.IO) {
                deps.appRepository.resolveFreezerMembers(setOf(packageName)).firstOrNull()
                    ?: deps.appRepository.loadApps(force = false)
                        .firstOrNull { it.packageName == packageName }
            }
            if (app != null) {
                FreezerOperations.launchAndUnfreeze(
                    this@FreezerAppLaunchTrampolineActivity,
                    deps.appRepository,
                    settings,
                    app,
                )
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_freezer_launch_package"
    }
}
