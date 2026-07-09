package com.slideindex.app.notification

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.slideindex.app.message.NotificationTextExtractor
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHistoryRecorder @Inject constructor(
    private val filterRepository: NotificationFilterRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val otpOfficialRulesLoader: OtpOfficialRulesLoader,
    private val otpRecordsRepository: OtpRecordsRepository,
    private val shadeActions: NotificationShadeActions,
    private val otpSideEffects: NotificationOtpSideEffects,
) {
    fun onListenerConnected(
        listener: NotificationListenerService,
        notifications: Array<StatusBarNotification>,
    ) {
        NotificationSbnCache.refreshActive(notifications.toList())
    }

    fun onPosted(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
    ): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false
        val content = NotificationTextExtractor.extract(extras)
        val title = content.title
        val text = content.text
        if (title.isBlank() && text.isBlank()) return false

        NotificationSbnCache.cacheActive(sbn)

        val matchingRules = filterRepository.findMatchingRules(sbn)
        val shouldHide = shouldHideNotification(context, sbn)

        val capturedIntent = NotificationHistoryIntentCapture.capture(sbn, context)
        Log.i(
            TAG,
            "Record ${sbn.packageName} key=${sbn.key} " +
                "pi=${!capturedIntent.pendingIntentBase64.isNullOrBlank()} " +
                "uri=${!capturedIntent.intentUri.isNullOrBlank()} " +
                "parcel=${!capturedIntent.intentParcelBase64.isNullOrBlank()} " +
                "notificationExtras=${!capturedIntent.extrasBase64.isNullOrBlank()}",
        )

        val settings = settingsRepository.readSnapshot()
        val officialRules = otpOfficialRulesLoader.getRules()
        val extraction = VerificationCodeExtractor.extract(
            packageName = sbn.packageName,
            title = title,
            text = text,
            config = OtpExtractionConfig.build(
                keywordsRegex = settings.otpKeywordsRegex,
                officialRules = officialRules,
                userRules = settings.otpUserMatchRules,
                disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
            ),
        )
        val extractedCode = extraction.code
        val postedAtMs = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        if (!extractedCode.isNullOrBlank()) {
            Log.i(TAG, "Extracted OTP from ${sbn.packageName}")
            otpRecordsRepository.record(
                code = extractedCode,
                packageName = sbn.packageName,
                title = title,
                text = text,
                timestampMs = postedAtMs,
                ruleName = extraction.ruleName,
            )
            otpSideEffects.onVerificationCodeExtracted(
                context = context,
                code = extractedCode,
                packageName = sbn.packageName,
                title = title,
                text = text,
                postedAtMs = postedAtMs,
                ruleName = extraction.ruleName,
                copyToClipboard = settings.otpCopyToClipboard,
                autoInputEnabled = settings.otpAutoInputEnabled,
            )
        }

        val item = NotificationHistoryItem(
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAtMs = postedAtMs,
            intentUri = capturedIntent.intentUri,
            intentParcelBase64 = capturedIntent.intentParcelBase64,
            intentExtrasBase64 = capturedIntent.intentExtrasBase64,
            pendingIntentBase64 = capturedIntent.pendingIntentBase64,
            extrasBase64 = capturedIntent.extrasBase64,
            notificationKey = sbn.key,
            hidden = shouldHide,
            extractedCode = extractedCode,
            extractionAttempted = extraction.attempted,
        )
        historyRepository.record(item)

        if (matchingRules.isNotEmpty()) {
            shadeActions.executeRules(context, listener, sbn, matchingRules)
        }
        if (!shouldHide) return false
        if (matchingRules.any { it.hidesNotification() }) return true
        shadeActions.hideFromShadeOnMain(listener, sbn)
        return true
    }

    fun onRemoved(context: Context, sbn: StatusBarNotification, reason: Int) {
        NotificationSbnCache.onRemoved(sbn, reason)

        val captured = NotificationHistoryIntentCapture.capture(sbn, context)
        val hasCapture = !captured.pendingIntentBase64.isNullOrBlank() ||
            !captured.intentUri.isNullOrBlank() ||
            !captured.intentParcelBase64.isNullOrBlank() ||
            !captured.extrasBase64.isNullOrBlank()
        if (!hasCapture) return
        Log.i(
            TAG,
            "Refresh capture on remove ${sbn.packageName} key=${sbn.key} " +
                "pi=${!captured.pendingIntentBase64.isNullOrBlank()} " +
                "uri=${!captured.intentUri.isNullOrBlank()} " +
                "parcel=${!captured.intentParcelBase64.isNullOrBlank()}",
        )
        historyRepository.updateCapture(sbn.key, captured)
    }

    private fun shouldHideNotification(context: Context, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == context.packageName) return false
        return filterRepository.matches(sbn)
    }

    private companion object {
        const val TAG = "NotifFilterRecorder"
    }
}
