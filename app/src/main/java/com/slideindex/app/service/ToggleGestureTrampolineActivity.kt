package com.slideindex.app.service

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ToggleGestureTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            val currentState = deps.settingsRepository.settings.first().serviceEnabled
            val newState = !currentState
            deps.settingsRepository.setServiceEnabled(newState)
            OverlayServiceLifecycle.syncFromSettings(this@ToggleGestureTrampolineActivity, deps.settingsRepository)

            val msg = getString(
                if (newState) R.string.toggle_gesture_enabled else R.string.toggle_gesture_disabled
            )
            Toast.makeText(this@ToggleGestureTrampolineActivity, msg, Toast.LENGTH_SHORT).show()
            
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
