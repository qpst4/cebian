package com.slideindex.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.otp.OtpAutoInputOrchestrator
import com.slideindex.app.otp.OtpCaptureDeduplicator
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.VerificationCodeExtractor
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OtpSmsBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OtpAutoInputBroadcastContract.ACTION_SMS_CAPTURED) return
        val body = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_SMS_BODY) ?: return
        val sender = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_SMS_SENDER).orEmpty()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handleSms(context, body, sender)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSms(context: Context, body: String, sender: String) {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppGraphEntryPoint::class.java,
        ).dependencies()
        val settings = deps.settingsRepository.settings.first()
        if (!settings.otpLsposedSmsCaptureEnabled) return
        val officialRules = deps.otpOfficialRulesLoader.getRules()
        val config = OtpExtractionConfig.build(
            keywordsRegex = settings.otpKeywordsRegex,
            officialRules = officialRules,
            userRules = settings.otpUserMatchRules,
            disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
        )
        val result = VerificationCodeExtractor.extract(
            packageName = sender,
            title = sender,
            text = body,
            config = config,
        )
        val code = result.code ?: return
        if (!OtpCaptureDeduplicator.tryConsumeExtractedCode(code)) {
            Log.d(TAG, "Skipping duplicate LSPosed SMS code")
            return
        }
        Log.i(TAG, "LSPosed SMS code extracted: ${code.length} chars")
        deps.otpRecordsRepository.record(
            code = code,
            packageName = sender,
            title = sender,
            text = body,
            ruleName = result.ruleName,
        )
        if (settings.otpAutoInputEnabled) {
            OtpAutoFillController.queueCode(code)
            OtpAutoInputOrchestrator.requestAutoFill(context.applicationContext, code, settings)
        }
        if (settings.otpCopyToClipboard) {
            runCatching { OtpClipboardHelper.copyCode(context.applicationContext, code) }
        }
    }

    companion object {
        private const val TAG = "OtpSmsBridge"
    }
}
