package com.slideindex.app.activity

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType

fun ActivityShortcut.toLaunchShortcut(): GestureAction.LaunchShortcut = when (kind) {
    ActivityShortcutKind.COMPONENT ->
        GestureAction.LaunchShortcut.component(componentFlat(), label)
    ActivityShortcutKind.DYNAMIC ->
        GestureAction.LaunchShortcut.dynamic(packageName, shortcutId, label)
    ActivityShortcutKind.INTENT -> {
        val hostPackage = packageName.takeIf { it.isNotBlank() }
        if (intentUris.size <= 1) {
            GestureAction.LaunchShortcut.intent(
                intentUris.firstOrNull().orEmpty(),
                label,
                hostPackage,
            )
        } else {
            GestureAction.LaunchShortcut.intents(intentUris, label, hostPackage)
        }
    }
}

fun ActivityShortcut.toQuickLauncherItem(): QuickLauncherItem = when (kind) {
    ActivityShortcutKind.COMPONENT ->
        QuickLauncherItem.shortcut(componentFlat(), label)
    ActivityShortcutKind.DYNAMIC ->
        QuickLauncherItem.dynamicShortcut(packageName, shortcutId, label)
    ActivityShortcutKind.INTENT -> {
        if (intentUris.size <= 1) {
            QuickLauncherItem.intentShortcut(
                intentUri = intentUris.firstOrNull().orEmpty(),
                label = label,
                hostPackage = packageName,
            )
        } else {
            QuickLauncherItem.intentShortcuts(
                intentUris = intentUris,
                label = label,
                hostPackage = packageName,
            )
        }
    }
}

fun ActivityShortcut.subtitleDetail(): String = when (kind) {
    ActivityShortcutKind.COMPONENT -> activityClassName
    ActivityShortcutKind.DYNAMIC -> shortcutId
    ActivityShortcutKind.INTENT -> intentUris.firstOrNull().orEmpty()
}

fun List<ActivityShortcut>.findForLaunchShortcut(payloadKey: String): ActivityShortcut? {
    val decoded = GestureShortcutPayload.decode(payloadKey) ?: return null
    return when (decoded) {
        is GestureShortcutPayload.Decoded.Dynamic ->
            findByIdentityKey(
                QuickLauncherItemCodec.shortcutKey(decoded.packageName, decoded.shortcutId),
            )
        is GestureShortcutPayload.Decoded.Component ->
            findByIdentityKey(decoded.componentFlat)
        is GestureShortcutPayload.Decoded.IntentShortcut ->
            findByIdentityKey("intent:${decoded.intentUri}")
        is GestureShortcutPayload.Decoded.IntentsShortcut ->
            findByIdentityKey(
                if (decoded.intentUris.size == 1) {
                    "intent:${decoded.intentUris[0]}"
                } else {
                    "intents:${decoded.intentUris.joinToString(QuickLauncherItemCodec.INTENT_LIST_SEP)}"
                },
            )
    }
}

fun List<ActivityShortcut>.findForQuickLauncherItem(item: QuickLauncherItem): ActivityShortcut? {
    if (item.type != QuickLauncherItemType.SHORTCUT) return null
    val key = QuickLauncherItemCodec.shortcutItemKey(item) ?: return null
    return findByIdentityKey(key)
}

fun activityShortcutFromQuickLauncherItem(item: QuickLauncherItem): ActivityShortcut? {
    if (item.type != QuickLauncherItemType.SHORTCUT) return null
    QuickLauncherItemCodec.parseIntentListPayload(item.payload)?.let { uris ->
        val pkg = QuickLauncherItemCodec.resolveHostPackageName(item.payload).orEmpty()
        return ActivityShortcut.intent(label = item.label, packageName = pkg, intentUris = uris)
    }
    QuickLauncherItemCodec.parseIntentPayload(item.payload)?.let { uri ->
        val pkg = QuickLauncherItemCodec.resolveHostPackageName(item.payload).orEmpty()
        return ActivityShortcut.intent(label = item.label, packageName = pkg, intentUris = listOf(uri))
    }
    QuickLauncherItemCodec.parseShortcutPayload(item.payload)?.let { (pkg, id) ->
        return ActivityShortcut.dynamic(label = item.label, packageName = pkg, shortcutId = id)
    }
    val payload = item.payload
    if (payload.startsWith("c:")) {
        val flat = payload.removePrefix("c:").substringBefore('\u001D')
        val pkg = flat.substringBefore('/')
        val cls = flat.substringAfter('/', missingDelimiterValue = "")
        if (pkg.isBlank() || cls.isBlank()) return null
        return ActivityShortcut.component(label = item.label, packageName = pkg, activityClassName = cls)
    }
    if ('/' in payload) {
        val pkg = payload.substringBefore('/')
        val cls = payload.substringAfter('/')
        if (pkg.isBlank() || cls.isBlank()) return null
        return ActivityShortcut.component(
            label = item.label.ifBlank { cls },
            packageName = pkg,
            activityClassName = cls,
        )
    }
    return null
}
