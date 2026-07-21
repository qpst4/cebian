package com.slideindex.app.notification

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.slideindex.app.util.BundleParcelCompat

internal object NotificationHistoryIntentExtraction {
  private val NOTIFICATION_EXTRAS_INTENT_KEYS = listOf(
      "android.intent.extra.INTENT",
      "android.intent.extra.shortcut.INTENT",
      "android.app.intent",
      "intent",
      "launch_intent",
      "target_intent",
      "click_intent",
      "open_intent",
      "deeplink_intent",
    )

    private val NOTIFICATION_EXTRAS_PENDING_INTENT_KEYS = listOf(
      "android.pendingIntent",
      "pending_intent",
      "content_intent",
      "click_pending_intent",
    )

    private val NOTIFICATION_EXTRAS_LINK_KEYS = listOf(
      "link",
      "url",
      "deeplink",
      "deep_link",
      "gcm.n.link",
      "google.c.a.c_l",
      "target_url",
      "open_url",
      "jump_url",
      "schema",
      "scheme",
      "click_url",
      "open_link",
      "landing_url",
      "router_url",
      "route_url",
    )

    private const val LAUNCH_FLAGS_MASK =
      Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_CLEAR_TASK

  fun isLaunchable(intent: Intent?): Boolean = intent?.isLaunchableForCapture() == true

  fun mergeReplayIntent(intent: Intent, other: Intent) {
      intent.apply {
          if (component == null) {
              other.component?.let { component = it }
          }
          if (action.isNullOrBlank() && !other.action.isNullOrBlank()) {
              action = other.action
          }
          if (data == null && other.data != null) {
              data = other.data
          }
          if (`package`.isNullOrBlank() && !other.`package`.isNullOrBlank()) {
              `package` = other.`package`
          }
          other.extras?.let { putExtras(it) }
          addFlags(other.flags and LAUNCH_FLAGS_MASK)
      }
  }

  fun collectPendingIntentSources(
      notification: android.app.Notification,
      extras: Bundle?,
  ): List<Pair<PendingIntent, String>> {
      val seen = LinkedHashSet<PendingIntent>()
      val sources = mutableListOf<Pair<PendingIntent, String>>()
      fun add(pendingIntent: PendingIntent?, source: String) {
          if (pendingIntent != null && seen.add(pendingIntent)) {
              sources += pendingIntent to source
          }
      }

      add(notification.contentIntent, "contentIntent")
      add(notification.fullScreenIntent, "fullScreenIntent")
      notification.publicVersion?.contentIntent?.let { add(it, "publicVersion.contentIntent") }
      notification.actions?.forEachIndexed { index, action ->
          add(action.actionIntent, "action[$index]")
      }

      if (extras != null) {
          for (key in NOTIFICATION_EXTRAS_PENDING_INTENT_KEYS) {
              add(
                  NotificationHistoryIntentSerialization.getParcelableCompat(extras, key, PendingIntent::class.java),
                  "extras.$key",
              )
          }
          scanExtrasForPendingIntents(extras).forEach { (pendingIntent, key) ->
              add(pendingIntent, "extras.scan.$key")
          }
      }
      return sources
  }

  fun extractIntentFromNotificationExtras(
      extras: Bundle?,
      context: Context,
  ): Intent? {
      if (extras == null || extras.isEmpty) return null

      for (key in NOTIFICATION_EXTRAS_INTENT_KEYS) {
          val intent = NotificationHistoryIntentSerialization.getParcelableCompat(extras, key, Intent::class.java)
          if (intent != null && intent.isLaunchableForCapture()) {
              Log.d(NotificationHistoryIntentSerialization.TAG, "Found launch intent in notification extras key=$key")
              return Intent(intent)
          }
      }

      for (key in extras.keySet()) {
          if (NOTIFICATION_EXTRAS_INTENT_KEYS.contains(key)) continue
          val lowerKey = key.lowercase()
          if (!lowerKey.contains("intent") && !lowerKey.contains("launch") && !lowerKey.contains("deeplink")) {
              continue
          }
          NotificationHistoryIntentSerialization.getParcelableCompat(extras, key, Intent::class.java)?.let { intent ->
              if (intent.isLaunchableForCapture()) {
                  Log.d(
                      NotificationHistoryIntentSerialization.TAG,
                      "Found launch intent in notification extras scan key=$key",
                  )
                  return Intent(intent)
              }
          }
      }

      for (key in NOTIFICATION_EXTRAS_PENDING_INTENT_KEYS) {
          val pendingIntent = NotificationHistoryIntentSerialization.getParcelableCompat(
              extras,
              key,
              PendingIntent::class.java,
          )
          if (pendingIntent != null) {
              extractIntent(context, pendingIntent)?.let { intent ->
                  if (intent.isLaunchableForCapture()) {
                      Log.d(
                          NotificationHistoryIntentSerialization.TAG,
                          "Found launch intent via notification extras PendingIntent key=$key",
                      )
                      return intent
                  }
              }
          }
      }

      for (key in extras.keySet()) {
          if (NOTIFICATION_EXTRAS_PENDING_INTENT_KEYS.contains(key)) continue
          val lowerKey = key.lowercase()
          if (!lowerKey.contains("intent") && !lowerKey.contains("pending") && !lowerKey.contains("click")) {
              continue
          }
          NotificationHistoryIntentSerialization.getParcelableCompat(extras, key, PendingIntent::class.java)
              ?.let { pendingIntent ->
                  extractIntent(context, pendingIntent)?.let { intent ->
                      if (intent.isLaunchableForCapture()) {
                          Log.d(
                              NotificationHistoryIntentSerialization.TAG,
                              "Found launch intent via notification extras scan PendingIntent key=$key",
                          )
                          return intent
                      }
                  }
              }
      }

      for (key in NOTIFICATION_EXTRAS_LINK_KEYS) {
          val link = extras.getString(key)
          if (!link.isNullOrBlank()) {
              val uri = runCatching { Uri.parse(link) }.getOrNull() ?: continue
              Log.d(NotificationHistoryIntentSerialization.TAG, "Found deep link in notification extras key=$key")
              return Intent(Intent.ACTION_VIEW, uri)
          }
      }

      for (key in extras.keySet()) {
          if (NOTIFICATION_EXTRAS_LINK_KEYS.contains(key)) continue
          val lowerKey = key.lowercase()
          if (!lowerKey.contains("link") && !lowerKey.contains("url") && !lowerKey.contains("deeplink") &&
              !lowerKey.contains("schema") && !lowerKey.contains("scheme")
          ) {
              continue
          }
          extras.getString(key)?.takeIf { it.isNotBlank() }?.let { link ->
              val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return@let
              Log.d(NotificationHistoryIntentSerialization.TAG, "Found deep link in notification extras scan key=$key")
              return Intent(Intent.ACTION_VIEW, uri)
          }
      }

      for (key in extras.keySet()) {
          when (val value = BundleParcelCompat.getValue(extras, key)) {
              is String -> parseIntentUriString(value)?.let { return it }
              is CharSequence -> parseIntentUriString(value.toString())?.let { return it }
          }
      }

      return null
  }

  fun extractMessagingStyleIntent(extras: Bundle?): Intent? {
      if (extras == null || extras.isEmpty) return null
      val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)
          ?: BundleParcelCompat.getParcelableArrayOfBundles(extras, "android.messages")
          ?: return null
      for (message in messages) {
          val messageBundle = message
          NotificationHistoryIntentSerialization.getParcelableCompat(messageBundle, "extras", Bundle::class.java)
              ?.let { messageExtras ->
                  for (key in messageExtras.keySet()) {
                      NotificationHistoryIntentSerialization.getParcelableCompat(
                          messageExtras,
                          key,
                          Intent::class.java,
                      )?.let { intent ->
                          if (intent.isLaunchableForCapture()) {
                              Log.d(
                                  NotificationHistoryIntentSerialization.TAG,
                                  "Found MessagingStyle intent in message extras key=$key",
                              )
                              return Intent(intent)
                          }
                      }
                  }
                  messageExtras.getString("uri")?.takeIf { it.isNotBlank() }?.let { uri ->
                      Log.d(NotificationHistoryIntentSerialization.TAG, "Found MessagingStyle uri in message extras")
                      return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                  }
              }
          messageBundle.getString("uri")?.takeIf { it.isNotBlank() }?.let { uri ->
              Log.d(NotificationHistoryIntentSerialization.TAG, "Found MessagingStyle uri in message bundle")
              return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
          }
      }
      return null
  }

  fun extractIntent(context: Context, pendingIntent: PendingIntent): Intent? {
      extractIntentViaReflection(pendingIntent)?.let { return it }
      extractIntentViaActivityManagerService(pendingIntent)?.let { return it }
      extractIntentViaActivityManager(context, pendingIntent)?.let { return it }
      return null
  }

  private fun Intent.isLaunchableForCapture(): Boolean {
      if (component != null) return true
      if (!action.isNullOrBlank()) return true
      if (data != null) return true
      if (!`package`.isNullOrBlank()) return true
      val extras = extras
      return extras != null && !extras.isEmpty
  }

  private fun scanExtrasForPendingIntents(extras: Bundle?): List<Pair<PendingIntent, String>> {
      if (extras == null || extras.isEmpty) return emptyList()
      val results = mutableListOf<Pair<PendingIntent, String>>()
      for (key in extras.keySet()) {
          if (NOTIFICATION_EXTRAS_PENDING_INTENT_KEYS.contains(key)) continue
          val lowerKey = key.lowercase()
          if (!lowerKey.contains("intent") && !lowerKey.contains("pending") && !lowerKey.contains("click")) {
              continue
          }
          NotificationHistoryIntentSerialization.getParcelableCompat(extras, key, PendingIntent::class.java)
              ?.let { pendingIntent ->
                  results += pendingIntent to key
              }
      }
      return results
  }

  private fun parseIntentUriString(raw: String): Intent? {
      val value = raw.trim()
      if (value.isBlank()) return null
      if (value.startsWith("#Intent;") || value.startsWith("intent:")) {
          return NotificationHistoryIntentSerialization.parseIntentUri(value)
      }
      if (value.startsWith("intent://")) {
          return runCatching {
              Intent.parseUri(value, Intent.URI_INTENT_SCHEME or Intent.URI_ALLOW_UNSAFE)
          }.getOrNull()
      }
      return null
  }

  private fun extractIntentViaReflection(pendingIntent: PendingIntent): Intent? {
      runCatching {
          val method = PendingIntent::class.java.getDeclaredMethod("getIntent")
          method.isAccessible = true
          (method.invoke(pendingIntent) as? Intent)?.let { return Intent(it) }
      }.onFailure { error ->
          Log.v(NotificationHistoryIntentSerialization.TAG, "getIntent reflection failed: ${error.message}")
      }

      runCatching {
          val field = PendingIntent::class.java.getDeclaredField("mIntent")
          field.isAccessible = true
          (field.get(pendingIntent) as? Intent)?.let { return Intent(it) }
      }.onFailure { error ->
          Log.v(NotificationHistoryIntentSerialization.TAG, "mIntent field reflection failed: ${error.message}")
      }

      runCatching {
          val keyField = PendingIntent::class.java.getDeclaredField("mKey")
          keyField.isAccessible = true
          val key = keyField.get(pendingIntent) ?: return@runCatching null
          val requestIntentMethod = key.javaClass.methods.firstOrNull { method ->
              method.name == "requestIntent" && method.parameterTypes.isEmpty()
          } ?: key.javaClass.getDeclaredMethod("requestIntent")
          requestIntentMethod.isAccessible = true
          (requestIntentMethod.invoke(key) as? Intent)?.let { return Intent(it) }
      }.onFailure { error ->
          Log.v(NotificationHistoryIntentSerialization.TAG, "mKey.requestIntent reflection failed: ${error.message}")
      }

      for (method in PendingIntent::class.java.declaredMethods) {
          if (method.returnType != Intent::class.java || method.parameterTypes.isNotEmpty()) continue
          runCatching {
              method.isAccessible = true
              (method.invoke(pendingIntent) as? Intent)?.let { return Intent(it) }
          }
      }

      return null
  }

  private fun extractIntentViaActivityManagerService(pendingIntent: PendingIntent): Intent? {
      return runCatching {
          val target = readPendingIntentTarget(pendingIntent) ?: return@runCatching null
          val amClass = Class.forName("android.app.ActivityManager")
          val service = amClass.getMethod("getService").invoke(null)
          val method = service.javaClass.getMethod(
              "getIntentForIntentSender",
              Class.forName("android.content.IIntentSender"),
          )
          (method.invoke(service, target) as? Intent)?.let { Intent(it) }
      }.onFailure { error ->
          Log.d(NotificationHistoryIntentSerialization.TAG, "IActivityManager.getIntentForIntentSender failed: ${error.message}")
      }.getOrNull()
  }

  private fun extractIntentViaActivityManager(context: Context, pendingIntent: PendingIntent): Intent? {
      return runCatching {
          val target = readPendingIntentTarget(pendingIntent) ?: return@runCatching null
          val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
          val method = ActivityManager::class.java.getMethod(
              "getIntentForIntentSender",
              Class.forName("android.content.IIntentSender"),
          )
          (method.invoke(activityManager, target) as? Intent)?.let { Intent(it) }
      }.onFailure { error ->
          Log.d(NotificationHistoryIntentSerialization.TAG, "ActivityManager.getIntentForIntentSender failed: ${error.message}")
      }.getOrNull()
  }

  @SuppressLint("SoonBlockedPrivateApi")
  private fun readPendingIntentTarget(pendingIntent: PendingIntent): Any? {
      return runCatching {
          val field = PendingIntent::class.java.getDeclaredField("mTarget")
          field.isAccessible = true
          field.get(pendingIntent)
      }.getOrNull()
  }
}
