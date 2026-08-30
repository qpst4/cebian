package com.slideindex.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log

/**
 * Dispatches Voice Search and Voice Assistant commands with graceful multi-level fallbacks.
 */
object VoiceActionHelper {

    private const val TAG = "VoiceActionHelper"
    private const val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    private val mainHandler = Handler(Looper.getMainLooper())

    fun launchVoiceSearch(context: Context): Boolean {
        val appContext = context.applicationContext

        // 1. Hands-free voice search
        val handsFreeIntent = Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
            flags = LAUNCH_FLAGS
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        if (startSafely(appContext, handsFreeIntent, "ACTION_VOICE_SEARCH_HANDS_FREE")) return true

        // 2. Web search with voice input fallback
        val webSearchIntent = Intent(RecognizerIntent.ACTION_WEB_SEARCH).apply {
            flags = LAUNCH_FLAGS
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        }
        if (startSafely(appContext, webSearchIntent, "ACTION_WEB_SEARCH")) return true

        // 3. General speech recognition fallback
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            flags = LAUNCH_FLAGS
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        if (startSafely(appContext, speechIntent, "ACTION_RECOGNIZE_SPEECH")) return true

        // 4. Shizuku fallback if available
        if (TaskManagerUtil.hasPermission()) {
            val ok = TaskManagerUtil.runShellCommand(
                "am", "start", "-a", RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE
            ) || TaskManagerUtil.runShellCommand(
                "am", "start", "-a", RecognizerIntent.ACTION_WEB_SEARCH
            )
            if (ok) {
                Log.i(TAG, "voice search started via Shizuku shell")
                return true
            }
        }

        Log.w(TAG, "all voice search launch attempts failed")
        return false
    }

    fun launchVoiceCommand(context: Context): Boolean {
        val appContext = context.applicationContext

        // 1. Standard Voice Command Intent (e.g. headset voice button)
        val voiceCommandIntent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            flags = LAUNCH_FLAGS
        }
        if (startSafely(appContext, voiceCommandIntent, "ACTION_VOICE_COMMAND")) return true

        // 2. Shizuku voice interaction show
        if (TaskManagerUtil.hasPermission()) {
            if (TaskManagerUtil.showVoiceAssistant()) {
                Log.i(TAG, "voice command started via Shizuku showVoiceAssistant")
                return true
            }
            if (TaskManagerUtil.runShellCommand("am", "start", "-a", Intent.ACTION_VOICE_COMMAND)) {
                Log.i(TAG, "voice command started via Shizuku shell ACTION_VOICE_COMMAND")
                return true
            }
        }

        // 3. Fallback to hands-free voice search
        val fallbackSearchIntent = Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
            flags = LAUNCH_FLAGS
        }
        if (startSafely(appContext, fallbackSearchIntent, "ACTION_VOICE_SEARCH_HANDS_FREE fallback")) return true

        // 4. Fallback to default assistant
        AssistantLauncher.launchDefault(appContext)
        return true
    }

    private fun startSafely(context: Context, intent: Intent, label: String): Boolean =
        runCatching {
            context.startActivity(intent)
            Log.i(TAG, "voice action succeeded via $label")
            true
        }.getOrElse { error ->
            if (error !is ActivityNotFoundException) {
                Log.w(TAG, "$label startActivity failed", error)
            } else {
                Log.w(TAG, "$label: no activity found for action=${intent.action}")
            }
            false
        }
}
