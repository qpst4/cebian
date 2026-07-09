package com.slideindex.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

@Singleton
class NotificationRuleExecutor @Inject constructor(
    private val shadeActions: Lazy<NotificationShadeActions>,
    private val uiStrings: NotificationRuleUiStrings,
) {
    private val ttsRef = AtomicReference<TextToSpeech?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recentWebhookKeys = mutableSetOf<String>()

    fun execute(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
        rules: List<NotificationFilterRule>,
    ) {
        if (rules.isEmpty()) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val contentText = text.ifBlank { bigText }

        rules.flatMap { it.normalized().actionEntries }.forEach { action ->
            executeAction(context, listener, sbn, action, title, contentText)
        }
    }

    private fun executeAction(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
        action: RuleActionEntry,
        title: String,
        text: String,
    ) {
        val run: () -> Unit = {
            when (action.type) {
                NotificationRuleActionType.HIDE -> hide(listener, sbn, action.includeOngoing)
                NotificationRuleActionType.MUTE -> mute(context)
                NotificationRuleActionType.LATER -> postpone(listener, sbn, action.laterTimesMs)
                NotificationRuleActionType.REPLACE -> replace(context, listener, sbn, action.replaceTitle, action.replaceMessage)
                NotificationRuleActionType.CHANGE_SOUND -> changeSound(context, action.soundUri)
                NotificationRuleActionType.CALL_NOTIFY -> callNotify(context, sbn, action, title, text)
                NotificationRuleActionType.TTS -> speak(context, sbn, action, title, text)
                NotificationRuleActionType.CLICK_BUTTON -> clickButtons(sbn, action)
                NotificationRuleActionType.OPEN -> openNotification(sbn)
                NotificationRuleActionType.WEBHOOK -> sendWebhook(context, sbn, action, title, text)
            }
        }
        if (action.delayTimeMs > 0) {
            mainHandler.postDelayed(run, action.delayTimeMs)
        } else {
            run()
        }
    }

    private fun hide(listener: NotificationListenerService, sbn: StatusBarNotification, includeOngoing: Boolean) {
        if (NotificationShadePolicy.isOngoing(sbn) && !includeOngoing) return
        shadeActions.get().hideFromShadeOnMain(listener, sbn)
    }

    private fun mute(context: Context) {
        val audioManager = context.getSystemService(android.media.AudioManager::class.java) ?: return
        val previous = audioManager.ringerMode
        runCatching { audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT }
        mainHandler.postDelayed({
            runCatching { audioManager.ringerMode = previous }
        }, 5_000L)
    }

    private fun postpone(listener: NotificationListenerService, sbn: StatusBarNotification, laterTimesMs: List<Int>) {
        if (laterTimesMs.isEmpty()) return
        val now = System.currentTimeMillis()
        val zoneOffset = TimeZone.getDefault().getOffset(now)
        val todayOffset = ((now + zoneOffset) % 86_400_000L).toInt()
        val target = laterTimesMs.minOfOrNull { time ->
            val delta = time - todayOffset
            if (delta > 0) delta.toLong() else (delta + 86_400_000L).toLong()
        } ?: return
        runCatching {
            listener.snoozeNotification(sbn.key, target.coerceAtLeast(1_000L))
        }.onFailure { error ->
            Log.w(TAG, "postpone failed for ${sbn.key}", error)
        }
    }

    private fun replace(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
        replaceTitle: String?,
        replaceMessage: String?,
    ) {
        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)
        val original = sbn.notification ?: return
        val extras = original.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val newTitle = expandTemplate(replaceTitle ?: "??", title, text, appLabel)
        val newText = expandTemplate(replaceMessage ?: "", title, text, appLabel)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager, sbn.packageName, "rule_replace")
        val builder = NotificationCompat.Builder(context, sbn.notification?.channelId ?: "rule_replace")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(newTitle)
            .setContentText(newText)
            .setContentIntent(original.contentIntent)
            .setAutoCancel(true)
        manager.notify(sbn.packageName, sbn.id + 90_000, builder.build())
        shadeActions.get().hideFromShadeOnMain(listener, sbn)
    }

    private fun changeSound(context: Context, soundUri: String?) {
        runCatching {
            val uri = soundUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }.onFailure { error ->
            Log.w(TAG, "changeSound failed", error)
        }
    }

    private fun callNotify(
        context: Context,
        sbn: StatusBarNotification,
        action: RuleActionEntry,
        title: String,
        text: String,
    ) {
        val screenOn = run {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            pm?.isInteractive == true
        }
        val mode = if (screenOn) action.notifyScreenOn else action.notifyScreenOff
        when (mode) {
            -1 -> return
            1 -> openNotification(sbn)
            else -> {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                ensureChannel(context, manager, CALL_NOTIFY_CHANNEL_ID, uiStrings.callNotifyChannelName)
                val builder = NotificationCompat.Builder(context, CALL_NOTIFY_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title.ifBlank { uiStrings.callNotifyFallbackTitle })
                    .setContentText(text)
                    .setContentIntent(sbn.notification?.contentIntent)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setFullScreenIntent(sbn.notification?.fullScreenIntent, true)
                    .setOngoing(true)
                manager.notify("call_notify_${sbn.key}".hashCode(), builder.build())
            }
        }
    }

    private fun speak(
        context: Context,
        sbn: StatusBarNotification,
        action: RuleActionEntry,
        title: String,
        text: String,
    ) {
        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)
        val template = action.ttsTemplate?.takeIf { it.isNotBlank() }
        val message = if (template != null) {
            expandTemplate(template, title, text, appLabel)
        } else {
            listOf(title, text).filter { it.isNotBlank() }.joinToString("?")
        }
        if (message.isBlank()) return
        val appContext = context.applicationContext
        var tts = ttsRef.get()
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed: $status")
                }
            }
            ttsRef.set(tts)
        }
        tts.language = Locale.getDefault()
        tts.speak(message, TextToSpeech.QUEUE_ADD, null, "notification_rule_${System.currentTimeMillis()}")
    }

    private fun clickButtons(sbn: StatusBarNotification, action: RuleActionEntry) {
        val notification = sbn.notification ?: return
        val actions = notification.actions ?: return
        actions.forEach { notificationAction ->
            val actionTitle = notificationAction.title?.toString().orEmpty()
            val matchesName = action.buttonNames.any { name ->
                name.isNotBlank() && actionTitle.contains(name, ignoreCase = true)
            }
            val matchesSemantic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && action.buttonSemantic != 0) {
                notificationAction.semanticAction == action.buttonSemantic
            } else {
                false
            }
            if (!matchesName && !matchesSemantic) return@forEach
            runCatching {
                notificationAction.actionIntent?.send()
            }.onFailure { error ->
                Log.w(TAG, "click button failed: $actionTitle", error)
            }
        }
    }

    private fun openNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val pendingIntent = notification.contentIntent ?: notification.fullScreenIntent ?: return
        runCatching {
            pendingIntent.send()
        }.onFailure { error ->
            Log.w(TAG, "Failed to open notification for ${sbn.packageName}", error)
        }
    }

    private fun sendWebhook(
        context: Context,
        sbn: StatusBarNotification,
        action: RuleActionEntry,
        title: String,
        text: String,
    ) {
        val urlText = action.webhookUrl?.trim().orEmpty()
        if (urlText.isBlank()) return
        val key = "$urlText|$title|$text"
        if (action.webhookDistinct) {
            synchronized(recentWebhookKeys) {
                if (key in recentWebhookKeys) return
                recentWebhookKeys += key
                mainHandler.postDelayed({
                    synchronized(recentWebhookKeys) { recentWebhookKeys.remove(key) }
                }, 5_000L)
            }
        }
        Thread {
            runCatching {
                val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
                    requestMethod = if (action.webhookMethod == 0) "POST" else "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    action.webhookHeaders?.lines()?.forEach { line ->
                        val index = line.indexOf(':')
                        if (index > 0) {
                            setRequestProperty(line.substring(0, index).trim(), line.substring(index + 1).trim())
                        }
                    }
                    if (action.webhookMethod == 0) {
                        doOutput = true
                        val body = action.webhookBody?.ifBlank {
                            """{"package":"${sbn.packageName}","title":${title.quoteJson()},"text":${text.quoteJson()}}"""
                        } ?: """{"package":"${sbn.packageName}","title":${title.quoteJson()},"text":${text.quoteJson()}}"""
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                connection.inputStream.use { it.readBytes() }
                connection.disconnect()
            }.onFailure { error ->
                Log.w(TAG, "webhook failed: $urlText", error)
            }
        }.start()
    }

    private fun expandTemplate(template: String, title: String, text: String, app: String): String =
        template
            .replace("{title}", title)
            .replace("{message}", text)
            .replace("{text}", text)
            .replace("{app}", app)

    private fun String.quoteJson(): String = buildString {
        append('"')
        for (ch in this@quoteJson) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun ensureChannel(context: Context, manager: NotificationManager, id: String, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private companion object {
        const val TAG = "NotificationRuleExecutor"
        const val CALL_NOTIFY_CHANNEL_ID = "notification_rule_call"
    }
}
