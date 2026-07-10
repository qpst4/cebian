package com.slideindex.app.notification

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log

object NotificationHistoryIntentCapture {
    private const val TAG = "NotifHistoryCapture"

    private val URI_FLAGS = Intent.URI_INTENT_SCHEME or Intent.URI_ALLOW_UNSAFE

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

    data class CapturedIntent(
        val intentUri: String?,
        val intentParcelBase64: String?,
        val intentExtrasBase64: String?,
        val pendingIntentBase64: String?,
        val extrasBase64: String?,
    )

    fun capture(sbn: StatusBarNotification, context: Context): CapturedIntent {
        val notification = sbn.notification ?: return emptyCapture()
        val notificationExtras = notification.extras
        val extrasBase64 = serializeNotificationExtras(notificationExtras)

        val contentPi = notification.contentIntent
        var pendingIntentBase64: String? = null
        if (contentPi != null) {
            pendingIntentBase64 = serializePendingIntent(contentPi, "contentIntent")
        }

        val pendingIntentSources = collectPendingIntentSources(notification, notificationExtras)
        if (pendingIntentBase64.isNullOrBlank()) {
            for ((pendingIntent, source) in pendingIntentSources) {
                if (source == "contentIntent") continue
                pendingIntentBase64 = serializePendingIntent(pendingIntent, source)
                if (!pendingIntentBase64.isNullOrBlank()) break
            }
        }

        Log.i(
            TAG,
            "Captured ${sbn.packageName} pi=${contentPi != null} " +
                "piBytes=${pendingIntentBase64?.length} contentIntent=${notification.contentIntent}",
        )

        if (pendingIntentBase64.isNullOrBlank()) {
            Log.w(
                TAG,
                "No PendingIntent captured for ${sbn.packageName} key=${sbn.key} " +
                    "flags=0x${Integer.toHexString(notification.flags)} " +
                    "category=${notification.category} " +
                    "contentIntent=${notification.contentIntent} " +
                    "fullScreenIntent=${notification.fullScreenIntent} " +
                    "publicVersionPi=${notification.publicVersion?.contentIntent} " +
                    "actions=${notification.actions?.size ?: 0} " +
                    "extrasKeys=${notificationExtras?.keySet()?.joinToString(",")}",
            )
        }

        val extrasIntent = extractIntentFromNotificationExtras(notificationExtras, context)
        var resolvedIntent: Intent? = extrasIntent?.takeIf { it.isLaunchable() } ?: extrasIntent
        for ((pendingIntent, source) in pendingIntentSources) {
            val piIntent = extractIntent(context, pendingIntent)
            if (piIntent == null) {
                Log.d(TAG, "Could not extract Intent from PendingIntent source=$source for ${sbn.packageName}")
                continue
            }
            resolvedIntent = when {
                piIntent.isLaunchable() -> piIntent
                resolvedIntent == null -> piIntent
                else -> resolvedIntent
            }
            if (piIntent.isLaunchable()) break
        }
        if (resolvedIntent == null || !resolvedIntent.isLaunchable()) {
            extractMessagingStyleIntent(notificationExtras)?.let { messagingIntent ->
                resolvedIntent = messagingIntent
            }
        }

        return if (resolvedIntent != null) {
            CapturedIntent(
                intentUri = serializeIntentUri(resolvedIntent, "resolved"),
                intentParcelBase64 = serializeIntentParcel(resolvedIntent, "resolved"),
                intentExtrasBase64 = serializeExtras(resolvedIntent, "resolved"),
                pendingIntentBase64 = pendingIntentBase64,
                extrasBase64 = extrasBase64,
            )
        } else {
            CapturedIntent(null, null, null, pendingIntentBase64, extrasBase64)
        }
    }

    fun captureIntentUri(sbn: StatusBarNotification, context: Context): String? =
        capture(sbn, context).intentUri

    fun deserializeIntent(
        intentUri: String?,
        intentParcelBase64: String?,
        intentExtrasBase64: String? = null,
    ): Intent? {
        val fromParcel = deserializeIntentParcel(intentParcelBase64)?.let { Intent(it) }
        val parsed = if (fromParcel != null) {
            fromParcel
        } else if (!intentUri.isNullOrBlank()) {
            parseIntentUri(intentUri)
        } else {
            null
        } ?: return null

        mergeExtras(parsed, intentExtrasBase64)
        return parsed
    }

    fun deserializeIntentFromNotificationExtras(
        extrasBase64: String?,
        context: Context,
        packageName: String,
    ): Intent? {
        val extras = deserializeBundle(extrasBase64) ?: return null
        return extractIntentFromNotificationExtras(extras, context)
            ?.let { prepareIntentForReplay(it, packageName) }
    }

    fun deserializePendingIntent(pendingIntentBase64: String?): PendingIntent? {
        if (pendingIntentBase64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(pendingIntentBase64, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                PendingIntent.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to deserialize PendingIntent", error)
        }.getOrNull()
    }

    fun serializeIntentParcel(intent: Intent): String? = serializeIntentParcel(Intent(intent), "replay")

    fun deserializeIntentParcel(intentParcelBase64: String?): Intent? {
        if (intentParcelBase64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(intentParcelBase64, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                Intent.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to deserialize intent parcel", error)
        }.getOrNull()
    }

    fun serializeBundle(bundle: Bundle?, source: String): String? {
        if (bundle == null || bundle.isEmpty) return null
        return runCatching {
            val parcel = Parcel.obtain()
            try {
                bundle.writeToParcel(parcel, 0)
                Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
            } finally {
                parcel.recycle()
            }
        }.onSuccess {
            Log.d(TAG, "Captured bundle $source (${it.length} chars)")
        }.onFailure { error ->
            Log.w(TAG, "Failed to serialize bundle $source", error)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun deserializeBundle(bundleBase64: String?): Bundle? {
        if (bundleBase64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(bundleBase64, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                Bundle.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to deserialize bundle", error)
        }.getOrNull()
    }

    fun prepareIntentForReplay(intent: Intent, packageName: String): Intent {
        return Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (component == null && `package`.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
    }

    fun enrichIntentForReplay(
        intent: Intent,
        extrasBase64: String?,
        context: Context,
        packageName: String,
    ): Intent {
        val prepared = prepareIntentForReplay(intent, packageName)
        val extras = deserializeBundle(extrasBase64) ?: return prepared
        extractIntentFromNotificationExtras(extras, context)?.let { extraIntent ->
            prepared.mergeReplayIntent(extraIntent)
        }
        return prepared
    }

    fun isLaunchableIntent(intent: Intent?): Boolean = intent?.isLaunchable() == true

    private fun Intent.isLaunchable(): Boolean {
        if (component != null) return true
        if (!action.isNullOrBlank()) return true
        if (data != null) return true
        if (!`package`.isNullOrBlank()) return true
        val extras = extras
        return extras != null && !extras.isEmpty
    }

    private fun Intent.mergeReplayIntent(other: Intent) {
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

    private const val LAUNCH_FLAGS_MASK =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TASK


    private fun collectPendingIntentSources(
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
                add(getParcelableCompat(extras, key, PendingIntent::class.java), "extras.$key")
            }
            scanExtrasForPendingIntents(extras).forEach { (pendingIntent, key) ->
                add(pendingIntent, "extras.scan.$key")
            }
        }
        return sources
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
            getParcelableCompat(extras, key, PendingIntent::class.java)?.let { pendingIntent ->
                results += pendingIntent to key
            }
        }
        return results
    }

    private fun serializeNotificationExtras(extras: Bundle?): String? {
        serializeBundle(extras, "notification.extras")?.let { return it }
        val filtered = filterSerializableExtras(extras) ?: return null
        return serializeBundle(filtered, "notification.extras.filtered")
    }

    private fun filterSerializableExtras(extras: Bundle?): Bundle? {
        if (extras == null || extras.isEmpty) return null
        val filtered = Bundle()
        for (key in extras.keySet()) {
            when (val value = extras.get(key)) {
                null -> Unit
                is CharSequence -> filtered.putString(key, value.toString())
                is String -> filtered.putString(key, value)
                is Int -> filtered.putInt(key, value)
                is Long -> filtered.putLong(key, value)
                is Boolean -> filtered.putBoolean(key, value)
                is Bundle -> filtered.putBundle(key, value)
                is Intent -> filtered.putParcelable(key, Intent(value))
                is PendingIntent -> filtered.putParcelable(key, value)
            }
        }
        return filtered.takeIf { !it.isEmpty }
    }

    private fun parseIntentUri(intentUri: String): Intent? {
        return runCatching {
            Intent.parseUri(intentUri, URI_FLAGS)
        }.onFailure { error ->
            Log.w(TAG, "Failed to parse stored intent URI (intent scheme)", error)
        }.getOrNull() ?: runCatching {
            Intent.parseUri(
                intentUri,
                Intent.URI_ANDROID_APP_SCHEME or Intent.URI_ALLOW_UNSAFE,
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to parse stored intent URI (android-app scheme)", error)
        }.getOrNull()
    }

    private fun emptyCapture() = CapturedIntent(null, null, null, null, null)

    private fun serializeIntentUri(intent: Intent, source: String): String? {
        return runCatching {
            Intent(intent).toUri(URI_FLAGS)
        }.onSuccess { uri ->
            Log.d(TAG, "Captured URI $source: ${uri.take(120)}")
        }.onFailure { error ->
            Log.w(TAG, "Failed to serialize URI $source", error)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun serializeIntentParcel(intent: Intent, source: String): String? {
        return runCatching {
            val parcel = Parcel.obtain()
            try {
                Intent(intent).writeToParcel(parcel, 0)
                Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
            } finally {
                parcel.recycle()
            }
        }.onSuccess {
            Log.d(TAG, "Captured parcel $source (${it.length} chars)")
        }.onFailure { error ->
            Log.w(TAG, "Failed to serialize parcel $source", error)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun serializeExtras(intent: Intent, source: String): String? {
        val extras = intent.extras ?: return null
        if (extras.isEmpty) return null
        return serializeBundle(extras, "intent.$source")
    }

    private fun serializePendingIntent(pendingIntent: PendingIntent, source: String): String? {
        return runCatching {
            val parcel = Parcel.obtain()
            try {
                pendingIntent.writeToParcel(parcel, 0)
                Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
            } finally {
                parcel.recycle()
            }
        }.onSuccess {
            Log.d(TAG, "Captured PendingIntent $source (${it.length} chars)")
        }.onFailure { error ->
            Log.w(TAG, "Failed to serialize PendingIntent $source", error)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun mergeExtras(intent: Intent, intentExtrasBase64: String?) {
        val extras = deserializeBundle(intentExtrasBase64) ?: return
        val current = intent.extras
        if (current == null || current.isEmpty) {
            intent.replaceExtras(extras)
            return
        }
        intent.putExtras(extras)
    }

    private fun extractIntentFromNotificationExtras(
        extras: Bundle?,
        context: Context,
    ): Intent? {
        if (extras == null || extras.isEmpty) return null

        for (key in NOTIFICATION_EXTRAS_INTENT_KEYS) {
            val intent = getParcelableCompat(extras, key, Intent::class.java)
            if (intent != null && intent.isLaunchable()) {
                Log.d(TAG, "Found launch intent in notification extras key=$key")
                return Intent(intent)
            }
        }

        for (key in extras.keySet()) {
            if (NOTIFICATION_EXTRAS_INTENT_KEYS.contains(key)) continue
            val lowerKey = key.lowercase()
            if (!lowerKey.contains("intent") && !lowerKey.contains("launch") && !lowerKey.contains("deeplink")) {
                continue
            }
            getParcelableCompat(extras, key, Intent::class.java)?.let { intent ->
                if (intent.isLaunchable()) {
                    Log.d(TAG, "Found launch intent in notification extras scan key=$key")
                    return Intent(intent)
                }
            }
        }

        for (key in NOTIFICATION_EXTRAS_PENDING_INTENT_KEYS) {
            val pendingIntent = getParcelableCompat(extras, key, PendingIntent::class.java)
            if (pendingIntent != null) {
                extractIntent(context, pendingIntent)?.let { intent ->
                    if (intent.isLaunchable()) {
                        Log.d(TAG, "Found launch intent via notification extras PendingIntent key=$key")
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
            getParcelableCompat(extras, key, PendingIntent::class.java)?.let { pendingIntent ->
                extractIntent(context, pendingIntent)?.let { intent ->
                    if (intent.isLaunchable()) {
                        Log.d(TAG, "Found launch intent via notification extras scan PendingIntent key=$key")
                        return intent
                    }
                }
            }
        }

        for (key in NOTIFICATION_EXTRAS_LINK_KEYS) {
            val link = extras.getString(key)
            if (!link.isNullOrBlank()) {
                val uri = runCatching { Uri.parse(link) }.getOrNull() ?: continue
                Log.d(TAG, "Found deep link in notification extras key=$key")
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
                Log.d(TAG, "Found deep link in notification extras scan key=$key")
                return Intent(Intent.ACTION_VIEW, uri)
            }
        }

        for (key in extras.keySet()) {
            when (val value = extras.get(key)) {
                is String -> parseIntentUriString(value)?.let { return it }
                is CharSequence -> parseIntentUriString(value.toString())?.let { return it }
            }
        }

        return null
    }

    private fun extractMessagingStyleIntent(extras: Bundle?): Intent? {
        if (extras == null || extras.isEmpty) return null
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?: extras.getParcelableArray("android.messages")
            ?: return null
        for (message in messages) {
            val messageBundle = message as? Bundle ?: continue
            getParcelableCompat(messageBundle, "extras", Bundle::class.java)?.let { messageExtras ->
                for (key in messageExtras.keySet()) {
                    getParcelableCompat(messageExtras, key, Intent::class.java)?.let { intent ->
                        if (intent.isLaunchable()) {
                            Log.d(TAG, "Found MessagingStyle intent in message extras key=$key")
                            return Intent(intent)
                        }
                    }
                }
                messageExtras.getString("uri")?.takeIf { it.isNotBlank() }?.let { uri ->
                    Log.d(TAG, "Found MessagingStyle uri in message extras")
                    return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                }
            }
            messageBundle.getString("uri")?.takeIf { it.isNotBlank() }?.let { uri ->
                Log.d(TAG, "Found MessagingStyle uri in message bundle")
                return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            }
        }
        return null
    }

    private fun parseIntentUriString(raw: String): Intent? {
        val value = raw.trim()
        if (value.isBlank()) return null
        if (value.startsWith("#Intent;") || value.startsWith("intent:")) {
            return parseIntentUri(value)
        }
        if (value.startsWith("intent://")) {
            return runCatching {
                Intent.parseUri(value, Intent.URI_INTENT_SCHEME or Intent.URI_ALLOW_UNSAFE)
            }.getOrNull()
        }
        return null
    }

    private fun <T> getParcelableCompat(bundle: Bundle, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable(key) as? T
        }
    }

    private fun extractIntent(context: Context, pendingIntent: PendingIntent): Intent? {
        extractIntentViaReflection(pendingIntent)?.let { return it }
        extractIntentViaActivityManagerService(pendingIntent)?.let { return it }
        extractIntentViaActivityManager(context, pendingIntent)?.let { return it }
        return null
    }

    private fun extractIntentViaReflection(pendingIntent: PendingIntent): Intent? {
        runCatching {
            val method = PendingIntent::class.java.getDeclaredMethod("getIntent")
            method.isAccessible = true
            (method.invoke(pendingIntent) as? Intent)?.let { return Intent(it) }
        }.onFailure { error ->
            Log.d(TAG, "getIntent reflection failed", error)
        }

        runCatching {
            val field = PendingIntent::class.java.getDeclaredField("mIntent")
            field.isAccessible = true
            (field.get(pendingIntent) as? Intent)?.let { return Intent(it) }
        }.onFailure { error ->
            Log.d(TAG, "mIntent field reflection failed", error)
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
            Log.d(TAG, "mKey.requestIntent reflection failed", error)
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
            Log.d(TAG, "IActivityManager.getIntentForIntentSender failed", error)
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
            Log.d(TAG, "ActivityManager.getIntentForIntentSender failed", error)
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
