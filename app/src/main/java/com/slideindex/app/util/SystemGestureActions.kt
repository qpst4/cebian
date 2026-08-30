package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SystemGestureActions {
    fun isMuted(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    fun toggleMute(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val nextMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_SILENT
        }
        return runCatching {
            audioManager.ringerMode = nextMode
            true
        }.getOrDefault(false)
    }

    /** Sets ringer to silent without affecting media volume. */
    fun silenceRinger(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return true
        return runCatching {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        }.getOrDefault(false)
    }

    /** Silences ringer/notification alerts and sets media volume to 0. Alarm volume is unchanged. */
    fun muteAllVolumes(context: Context): Boolean {
        if (!PermissionHelper.hasNotificationPolicyAccess(context)) {
            PermissionHelper.requestNotificationPolicyAccess(context)
            return false
        }
        silenceRinger(context)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.MEDIA, 0f)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.RING, 0f)
        VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.NOTIFICATION, 0f)
        return true
    }

    fun dispatchMediaKey(context: Context, keyCode: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        }.getOrDefault(false)
    }

    fun openNotificationPolicySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /** 打开原生网络连接面板（Android 10+ Panel，低版本跳转设置）。 */
    fun openNativeInternetPanel(context: Context): Boolean {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val action = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.Panel.ACTION_WIFI
                }
                val intent = Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            true
        }.getOrDefault(false)
    }

    /** 调出系统原生声音调节面板（Settings.Panel.ACTION_VOLUME）。 */
    fun openNativeVolumePanel(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_VOLUME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                context.startActivity(panelIntent)
                true
            }.getOrDefault(false)
            if (started) return true
        }

        // Fallback: 调出音量条或声音设置
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val adjusted = runCatching {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                true
            }.getOrDefault(false)
            if (adjusted) return true
        }

        return runCatching {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 打开当前前台应用系统详情页。 */
    fun openCurrentAppInfo(context: Context): Boolean {
        val foregroundPkg = com.slideindex.app.service.SlideIndexAccessibilityService.currentForegroundPackageName()
        if (foregroundPkg.isNullOrBlank()) {
            android.widget.Toast.makeText(context, com.slideindex.app.R.string.current_app_info_not_found, android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
        val safePkg: String = foregroundPkg
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", safePkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 模拟按键事件（支持长按）。支持 Root 或 Shizuku。 */
    fun simulateKeyEvent(context: Context, keyCode: Int, isLongPress: Boolean = false): Boolean {
        val cmd = if (isLongPress) {
            "input keyevent --longpress $keyCode"
        } else {
            "input keyevent $keyCode"
        }
        CoroutineScope(Dispatchers.IO).launch {
            val useRoot = ShellCommandExecutor.probeRootAvailable()
            val result = TaskManagerUtil.runShellCommandLine(cmd, useRoot = useRoot)
            if (!result.success) {
                val errorMsg = result.output.ifBlank { "ExitCode ${result.exitCode}" }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(com.slideindex.app.R.string.key_event_failed_hint, errorMsg),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        return true
    }
}
