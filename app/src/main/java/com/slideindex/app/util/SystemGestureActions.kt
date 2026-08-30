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

    /** 切换单手模式（支持无障碍原生与 Shizuku 双通道）。 */
    fun toggleOneHandedMode(context: Context): Boolean {
        // 1. 尝试使用 SecureSettingsHelper (如果应用有 WRITE_SECURE_SETTINGS 权限)
        if (SecureSettingsHelper.hasWriteSecureSettings(context)) {
            val currentActivated = android.provider.Settings.Secure.getInt(
                context.contentResolver,
                "one_handed_mode_activated",
                0,
            )
            val nextActivated = if (currentActivated == 1) 0 else 1
            android.provider.Settings.Secure.putInt(
                context.contentResolver,
                "one_handed_mode_enabled",
                1,
            )
            android.provider.Settings.Secure.putInt(
                context.contentResolver,
                "one_handed_mode_activated",
                nextActivated,
            )
        }

        // 2. 通过 Shizuku / Shell 异步切换设置
        CoroutineScope(Dispatchers.IO).launch {
            val shellCmd = "settings put secure one_handed_mode_enabled 1; " +
                "curr=\$(settings get secure one_handed_mode_activated); " +
                "if [ \"\$curr\" = \"1\" ]; then settings put secure one_handed_mode_activated 0; else settings put secure one_handed_mode_activated 1; fi"
            val useRoot = ShellCommandExecutor.probeRootAvailable()
            TaskManagerUtil.runShellCommandLine(shellCmd, useRoot = useRoot)
        }

        // 3. 无障碍 GlobalAction 19 配合触发
        val service = com.slideindex.app.service.SlideIndexAccessibilityService.accessibilityInstance()
        if (service != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            service.performGlobalAction(19)
        }

        return true
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
