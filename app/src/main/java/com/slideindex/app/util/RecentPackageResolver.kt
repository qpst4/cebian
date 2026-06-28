package com.slideindex.app.util

/**
 * Flyme / AOSP recents dumps may report task owners as "pkg/Activity" or "pkg.Activity".
 * This helper turns those identifiers into something we can match or resolve to an installed package.
 */
object RecentPackageResolver {

    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.meizu.settings",
        "com.meizu.flyme.settings",
    )

    private val CAMERA_PACKAGES = setOf(
        "com.meizu.media.camera",
        "com.android.camera",
        "com.meizu.camera",
    )

    private val QUICK_SHARE_PACKAGES = setOf(
        "com.google.android.gms",
        "com.google.android.apps.nbu.p2p",
    )

    fun isQuickShareIdentifier(identifier: String): Boolean {
        val lower = identifier.lowercase()
        return lower.contains("nearby") ||
            lower.contains("sharing") ||
            lower.contains("quickshare") ||
            lower.contains("sendkit") ||
            lower.contains("android.beam")
    }

    fun isSettingsAppInfoIdentifier(identifier: String): Boolean {
        val lower = identifier.lowercase()
        return lower.contains("settings") &&
            (lower.contains("applications") ||
                lower.contains("appinfo") ||
                lower.contains("installedapp") ||
                lower.contains("manageapplications"))
    }

    fun normalizeIdentifier(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.contains('/')) {
            return trimmed.substringBefore('/').trim()
        }
        val segments = trimmed.split('.')
        if (segments.size >= 2) {
            val last = segments.last()
            if (last.isNotEmpty() && last[0].isUpperCase() && looksLikeJavaClassName(last)) {
                return segments.dropLast(1).joinToString(".")
            }
        }
        return trimmed
    }

    /** Flyme recents class suffixes only — do not strip package segments like AlipayGphone. */
    private fun looksLikeJavaClassName(name: String): Boolean =
        name.endsWith("Activity") ||
            name.endsWith("Service") ||
            name.endsWith("Fragment") ||
            name.endsWith("Receiver") ||
            name.endsWith("Provider") ||
            name.endsWith("Application") ||
            name.endsWith("Home") ||
            name.endsWith("Launcher") ||
            name.endsWith("Dialog")

    fun matches(candidate: String, targetPackage: String): Boolean {
        if (candidate.isBlank() || targetPackage.isBlank()) return false
        val normalizedCandidate = normalizeIdentifier(candidate)
        val normalizedTarget = normalizeIdentifier(targetPackage)
        if (normalizedCandidate == normalizedTarget) return true
        if (normalizedCandidate.startsWith("$normalizedTarget.") ||
            normalizedTarget.startsWith("$normalizedCandidate.")
        ) {
            return true
        }
        if (normalizedCandidate in SETTINGS_PACKAGES && normalizedTarget in SETTINGS_PACKAGES) return true
        if (normalizedCandidate in CAMERA_PACKAGES && normalizedTarget in CAMERA_PACKAGES) return true
        if (sharesQuickShareOwner(normalizedCandidate, normalizedTarget)) return true
        return false
    }

    private fun sharesQuickShareOwner(candidate: String, target: String): Boolean {
        if (candidate in QUICK_SHARE_PACKAGES && target in QUICK_SHARE_PACKAGES) return true
        val quickShareHints = listOf("sharing", "quickshare", "nearby", "gms")
        val candidateHint = quickShareHints.any { candidate.contains(it, ignoreCase = true) }
        val targetHint = quickShareHints.any { target.contains(it, ignoreCase = true) }
        return candidateHint && (target in QUICK_SHARE_PACKAGES || targetHint)
    }
}
