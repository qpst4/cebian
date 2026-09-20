package com.slideindex.app.clipboardoverlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.textclassifier.TextClassification
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLanguage
import android.view.textclassifier.TextLinks
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.settings.AppUiLanguage
import com.slideindex.app.util.AppLocaleApplier
import java.util.Locale

internal data class OverlaySmartActionSpec(
    val iconRes: Int,
    val label: String,
    val launch: OverlaySmartLaunch,
)

internal sealed interface OverlaySmartLaunch {
    data class Activity(val intent: Intent) : OverlaySmartLaunch
    data class Translate(val text: String) : OverlaySmartLaunch
    data class PickUrls(val urls: List<String>) : OverlaySmartLaunch
}

internal object ClipboardOverlaySmartActions {
    private const val ENTITY_CONFIDENCE = 0.5f
    private const val LANG_CONFIDENCE = 0.55f
    private val ENTITY_TYPES = setOf(
        TextClassifier.TYPE_URL,
        TextClassifier.TYPE_PHONE,
        TextClassifier.TYPE_EMAIL,
        TextClassifier.TYPE_ADDRESS,
    )

    fun resolve(context: Context, text: String): OverlaySmartActionSpec? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val classifier = context.getSystemService(TextClassificationManager::class.java)
            ?.textClassifier
            ?: return null
        val webUrls = PickResultUrl.extractOpenableUrls(trimmed).filter(::isWebUrl).distinct()
        if (webUrls.size >= 2) {
            return OverlaySmartActionSpec(
                iconRes = R.drawable.ic_clipboard_overlay_open,
                label = context.getString(R.string.clipboard_overlay_action_open_count, webUrls.size),
                launch = OverlaySmartLaunch.PickUrls(webUrls),
            )
        }
        entityAction(context, classifier, trimmed)?.let { return it }
        if (shouldOfferTranslate(classifier, trimmed)) {
            return OverlaySmartActionSpec(
                iconRes = R.drawable.ic_clipboard_overlay_translate,
                label = context.getString(R.string.clipboard_overlay_action_translate),
                launch = OverlaySmartLaunch.Translate(trimmed),
            )
        }
        return null
    }

    private fun entityAction(
        context: Context,
        classifier: TextClassifier,
        text: String,
    ): OverlaySmartActionSpec? {
        var bestType: String? = null
        var bestSpan = text
        var bestScore = 0f
        val links = runCatching {
            classifier.generateLinks(TextLinks.Request.Builder(text).build())
        }.getOrNull()
        links?.links?.forEach { link ->
            val typed = bestTypedEntity(link) ?: return@forEach
            if (typed.second > bestScore) {
                bestType = typed.first
                bestSpan = text.substring(link.start, link.end)
                bestScore = typed.second
            }
        }
        if (bestType == null) {
            val classification = runCatching {
                classifier.classifyText(text, 0, text.length, null)
            }.getOrNull()
            val typed = classification?.let(::bestTypedEntity) ?: return null
            bestType = typed.first
            bestSpan = text
            bestScore = typed.second
        }
        val type = bestType ?: return null
        if (bestScore < ENTITY_CONFIDENCE) return null
        return toAction(context, type, bestSpan)
    }

    private fun bestTypedEntity(link: TextLinks.TextLink): Pair<String, Float>? {
        var best: Pair<String, Float>? = null
        for (i in 0 until link.entityCount) {
            val type = link.getEntity(i)
            if (type !in ENTITY_TYPES) continue
            val score = link.getConfidenceScore(type)
            if (best == null || score > best.second) best = type to score
        }
        return best
    }

    private fun bestTypedEntity(classification: TextClassification): Pair<String, Float>? {
        var best: Pair<String, Float>? = null
        for (i in 0 until classification.entityCount) {
            val type = classification.getEntity(i)
            if (type !in ENTITY_TYPES) continue
            val score = classification.getConfidenceScore(type)
            if (best == null || score > best.second) best = type to score
        }
        return best
    }

    private fun toAction(context: Context, type: String, span: String): OverlaySmartActionSpec? {
        val value = span.trim()
        if (value.isEmpty()) return null
        return when (type) {
            TextClassifier.TYPE_URL -> OverlaySmartActionSpec(
                iconRes = R.drawable.ic_clipboard_overlay_open,
                label = context.getString(R.string.clipboard_overlay_action_open),
                launch = OverlaySmartLaunch.Activity(viewIntent(normalizeUrl(value))),
            )
            TextClassifier.TYPE_PHONE -> OverlaySmartActionSpec(
                iconRes = R.drawable.ic_clipboard_overlay_call,
                label = context.getString(R.string.clipboard_overlay_action_call),
                launch = OverlaySmartLaunch.Activity(
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            TextClassifier.TYPE_EMAIL -> OverlaySmartActionSpec(
                iconRes = R.drawable.ic_clipboard_overlay_email,
                label = context.getString(R.string.clipboard_overlay_action_email),
                launch = OverlaySmartLaunch.Activity(
                    Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", value, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            TextClassifier.TYPE_ADDRESS -> OverlaySmartActionSpec(
                iconRes = R.drawable.ic_clipboard_overlay_place,
                label = context.getString(R.string.clipboard_overlay_action_map),
                launch = OverlaySmartLaunch.Activity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(value)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ),
            )
            else -> null
        }
    }

    private fun viewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        return if (value.contains("://")) value else "https://$value"
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun shouldOfferTranslate(classifier: TextClassifier, text: String): Boolean {
        val language = runCatching {
            classifier.detectLanguage(TextLanguage.Request.Builder(text).build())
        }.getOrNull() ?: return false
        if (language.localeHypothesisCount <= 0) return false
        val top = language.getLocale(0)
        if (language.getConfidenceScore(top) < LANG_CONFIDENCE) return false
        val detected = top.language.orEmpty().lowercase(Locale.ROOT)
        if (detected.isBlank() || detected == "und") return false
        return detected != appLanguageCode()
    }

    private fun appLanguageCode(): String {
        val applied = AppLocaleApplier.currentLanguage()
        val locale = when (applied) {
            AppUiLanguage.SYSTEM -> Locale.getDefault()
            else -> Locale.forLanguageTag(applied.toLanguageTags().orEmpty())
        }
        return locale.language.lowercase(Locale.ROOT)
    }
}
