package com.slideindex.app.timeddnd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.slideindex.app.R
import com.slideindex.app.remind.RemindAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

class TempDndNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            runCatching {
                when (intent.action) {
                    TempDndManager.ACTION_REDEFINE -> handleRedefine(context, intent)
                    TempDndManager.ACTION_STOP -> TempDndManager.restoreDnd(context.applicationContext)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to handle notification action", error)
            }
            pendingResult.finish()
        }
    }

    private suspend fun handleRedefine(context: Context, intent: Intent) {
        val bundle = RemoteInput.getResultsFromIntent(intent)
        val raw = bundle?.getCharSequence(TempDndManager.KEY_INPUT_MINUTES)?.toString()?.trim()
        val minutes = raw?.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.temp_dnd_minutes_invalid),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        TempDndManager.enableTempDnd(
            context.applicationContext,
            RemindAlarmScheduler.clampMinutes(minutes),
        )
    }

    companion object {
        private const val TAG = "TempDndReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
