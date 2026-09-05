package com.slideindex.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt



object BrightnessControlHelper {

    const val UI_NIGHT_MODE_KEY = "ui_night_mode"



    private const val UI_NIGHT_MODE_AUTO = 0

    private const val UI_NIGHT_MODE_NO = 1

    private const val UI_NIGHT_MODE_YES = 2



    fun hasAccess(context: Context): Boolean =
        PermissionHelper.canWriteSettings(context) ||
        TaskManagerUtil.hasPermission() ||
        SecureSettingsHelper.hasWriteSecureSettings(context)

    fun hasDarkModeAccess(context: Context): Boolean =
        TaskManagerUtil.hasPermission() ||
        PermissionHelper.canWriteSettings(context) ||
        SecureSettingsHelper.hasWriteSecureSettings(context)



    fun readAutoBrightnessEnabled(context: Context): Boolean {
        return Settings.System.getInt(
            context.applicationContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }

    /** 自动亮度开启时，滑条位置 0–1 与系统 adj（-1–1）互转。 */
    fun adjToFraction(adj: Float): Float = ((adj + 1f) / 2f).coerceIn(0f, 1f)

    fun fractionToAdj(fraction: Float): Float = (fraction.coerceIn(0f, 1f) * 2f - 1f)

    fun readAutoBrightnessAdj(context: Context): Float {
        val appContext = context.applicationContext
        val adj = runCatching {
            Settings.System.getFloat(appContext.contentResolver, SCREEN_AUTO_BRIGHTNESS_ADJ)
        }.getOrNull()
        return if (adj != null && adj in -1f..1f) adj else 0f
    }

    /** 当前亮度比例（0–1），仅读 Settings（主线程安全，勿走 shell）。 */
    fun readBrightnessFraction(context: Context): Float =
        readManualBrightnessFraction(context.applicationContext)

    /**
     * 尽量读到系统当前亮度；有 Shizuku 时走 shell。仅限后台线程调用（勿在主线程阻塞）。
     */
    fun readBrightnessFractionAccurate(context: Context): Float {
        val appContext = context.applicationContext
        if (TaskManagerUtil.hasPermission()) {
            val intResult = TaskManagerUtil.runShellCommandOutput(
                "settings",
                "get",
                "system",
                "screen_brightness",
            )
            if (intResult.success) {
                intResult.output.trim().toIntOrNull()?.let { level ->
                    return levelToFraction(appContext, level)
                }
            }
            val floatResult = TaskManagerUtil.runShellCommandOutput(
                "settings",
                "get",
                "system",
                "screen_brightness_float",
            )
            if (floatResult.success) {
                floatResult.output.trim().toFloatOrNull()?.let { fraction ->
                    if (fraction in 0f..1f) return fraction
                }
            }
        }
        return readManualBrightnessFraction(appContext)
    }

    private fun readManualBrightnessFraction(appContext: Context): Float {
        val level = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255,
        )
        val intFraction = levelToFraction(appContext, level)
        val floatVal = runCatching {
            Settings.System.getFloat(appContext.contentResolver, "screen_brightness_float")
        }.getOrNull()
        if (floatVal != null && floatVal in 0f..1f) {
            if (abs(floatVal - intFraction) > FLOAT_INT_MISMATCH_EPSILON) {
                return intFraction
            }
            return floatVal
        }
        return intFraction
    }

    private fun levelToFraction(context: Context, level: Int): Float {
        val max = brightnessMax(context).coerceAtLeast(level)
        val min = brightnessMin(context)
        if (max <= min) return 0f
        return ((level - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    }

    @SuppressLint("DiscouragedApi")
    private fun brightnessMax(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        val configured = if (id != 0) res.getInteger(id) else 0
        if (configured > 0) return configured
        val currentLevel = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            255,
        )
        return when {
            currentLevel > 4095 -> 65535
            currentLevel > 2047 -> 4095
            currentLevel > 255 -> 2047
            else -> 255
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun brightnessMin(context: Context): Int {
        val res = context.resources
        val id = res.getIdentifier("config_screenBrightnessSettingMinimum", "integer", "android")
        return if (id != 0) res.getInteger(id) else 0
    }

    /**
     * @param duringGesture 滑动过程中为 true：仅快速写 screen_brightness，不走 shell / adj。
     * @param commitAutoAdj 手势结束时为 true：在后台尽力同步 adj，不阻塞、不切换亮度模式。
     */
    fun writeBrightnessFraction(
        context: Context,
        fraction: Float,
        duringGesture: Boolean = false,
        commitAutoAdj: Boolean = false,
    ): Boolean {
        if (!hasAccess(context)) return false
        val appContext = context.applicationContext
        val clamped = fraction.coerceIn(0f, 1f)
        val synced = writeManualBrightnessFraction(
            context = appContext,
            clamped = clamped,
            logSuccess = !duringGesture,
            allowShell = !duringGesture,
        )
        if (commitAutoAdj && readAutoBrightnessEnabled(appContext)) {
            scheduleAutoBrightnessAdjCommit(appContext, clamped)
        }
        return synced
    }

    fun applyBrightnessFraction(context: Context, fraction: Float): Boolean =
        writeBrightnessFraction(context, fraction, commitAutoAdj = readAutoBrightnessEnabled(context))

    private fun writeManualBrightnessFraction(
        context: Context,
        clamped: Float,
        logSuccess: Boolean = true,
        allowShell: Boolean = true,
    ): Boolean {
        val appContext = context.applicationContext
        val max = brightnessMax(appContext)
        val min = brightnessMin(appContext)
        val targetIntLevel = if (max <= min) {
            min
        } else {
            (min + (max - min) * clamped).roundToInt().coerceIn(min, max)
        }

        var synced = false
        val canWriteSettings = PermissionHelper.canWriteSettings(appContext) ||
            SecureSettingsHelper.hasWriteSecureSettings(appContext)

        if (canWriteSettings) {
            synced = runCatching {
                Settings.System.putFloat(
                    appContext.contentResolver,
                    "screen_brightness_float",
                    clamped,
                )
            }.getOrDefault(false)
            synced = runCatching {
                Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    targetIntLevel,
                )
            }.getOrDefault(false) || synced
        }
        if (!synced && allowShell && TaskManagerUtil.hasPermission()) {
            synced = TaskManagerUtil.runShellCommand(
                "settings",
                "put",
                "system",
                "screen_brightness_float",
                clamped.toString(),
            ) || synced
            synced = TaskManagerUtil.runShellCommand(
                "settings",
                "put",
                "system",
                "screen_brightness",
                targetIntLevel.toString(),
            ) || synced
        }
        if (synced && logSuccess) {
            Log.d(TAG, "manual brightness set to $targetIntLevel (fraction=$clamped, max=$max)")
        }
        return synced
    }

    private fun scheduleAutoBrightnessAdjCommit(context: Context, fraction: Float) {
        val appContext = context.applicationContext
        val adj = fractionToAdj(fraction)
        brightnessAdjExecutor.execute {
            writeAutoBrightnessAdjBestEffort(appContext, adj)
        }
    }

    private fun writeAutoBrightnessAdjBestEffort(context: Context, adj: Float) {
        val clamped = adj.coerceIn(-1f, 1f)
        if (TaskManagerUtil.hasPermission() && writeAdjViaShell(clamped)) {
            Log.d(TAG, "auto brightness adj committed via shell: $clamped")
            return
        }
        val canWriteSettings = PermissionHelper.canWriteSettings(context) ||
            SecureSettingsHelper.hasWriteSecureSettings(context)
        if (canWriteSettings) {
            runCatching {
                if (Settings.System.putFloat(
                        context.contentResolver,
                        SCREEN_AUTO_BRIGHTNESS_ADJ,
                        clamped,
                    )
                ) {
                    Log.d(TAG, "auto brightness adj committed via Settings: $clamped")
                }
            }.onFailure { error ->
                Log.w(TAG, "auto brightness adj commit failed", error)
            }
        }
    }

    private fun writeAdjViaShell(adj: Float): Boolean =
        TaskManagerUtil.runShellCommand(
            "settings",
            "put",
            "system",
            SCREEN_AUTO_BRIGHTNESS_ADJ,
            adj.toString(),
        )



    fun toggleAutoBrightness(context: Context): Boolean? {

        if (!hasAccess(context)) return null

        val appContext = context.applicationContext

        val enable = !readAutoBrightnessEnabled(appContext)

        val mode = if (enable) {

            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        } else {

            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL

        }

        return if (setAutoBrightnessMode(appContext, mode)) enable else null

    }



    fun readDarkModeEnabled(context: Context): Boolean {

        val appContext = context.applicationContext

        return when (

            Settings.Secure.getInt(

                appContext.contentResolver,

                UI_NIGHT_MODE_KEY,

                UI_NIGHT_MODE_AUTO,

            )

        ) {

            UI_NIGHT_MODE_YES -> true

            UI_NIGHT_MODE_NO -> false

            else -> {

                val nightMask = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

                nightMask == Configuration.UI_MODE_NIGHT_YES

            }

        }

    }



    fun toggleDarkMode(context: Context): Boolean? {

        if (!hasDarkModeAccess(context)) return null

        val appContext = context.applicationContext

        val enable = !readDarkModeEnabled(appContext)

        return if (setUiNightMode(appContext, enable)) enable else null

    }



    private fun setAutoBrightnessMode(context: Context, mode: Int): Boolean {

        var synced = false

        if (TaskManagerUtil.hasPermission()) {

            synced = TaskManagerUtil.runShellCommand(

                "settings",

                "put",

                "system",

                "screen_brightness_mode",

                mode.toString(),

            )

        }

        if (PermissionHelper.canWriteSettings(context) || SecureSettingsHelper.hasWriteSecureSettings(context)) {
            runCatching {
                synced = Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    mode,
                ) || synced
            }.onFailure { error ->
                Log.w(TAG, "auto brightness write failed", error)
            }
        }

        return synced

    }



    private fun setUiNightMode(context: Context, enableDark: Boolean): Boolean {

        val mode = if (enableDark) UI_NIGHT_MODE_YES else UI_NIGHT_MODE_NO

        val nightArg = if (enableDark) "yes" else "no"



        if (TaskManagerUtil.hasPermission()) {

            val shellCommands = listOf(

                arrayOf("cmd", "uimode", "night", nightArg),

                arrayOf("settings", "put", "secure", UI_NIGHT_MODE_KEY, mode.toString()),

                arrayOf("settings", "put", "system", UI_NIGHT_MODE_KEY, mode.toString()),

            )

            for (command in shellCommands) {

                if (TaskManagerUtil.runShellCommand(*command)) {

                    Log.d(TAG, "dark mode set via shell: ${command.joinToString(" ")}")

                    return true

                }

            }

        }



        runCatching {

            if (Settings.Secure.putInt(context.contentResolver, UI_NIGHT_MODE_KEY, mode)) {

                Log.d(TAG, "dark mode set via Settings.Secure.putInt mode=$mode")

                return true

            }

        }.onFailure { error ->

            Log.w(TAG, "ui night mode write failed", error)

        }

        return false

    }



    private const val TAG = "BrightnessControlHelper"

    /** 与 AOSP BrightnessController 一致，自动亮度下用户偏移量。 */
    private const val SCREEN_AUTO_BRIGHTNESS_ADJ = "screen_auto_brightness_adj"

    /** 系统快捷设置常只改 int，float 可能仍为应用上次写入的值。 */
    private const val FLOAT_INT_MISMATCH_EPSILON = 0.02f

    private val brightnessAdjExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

}


