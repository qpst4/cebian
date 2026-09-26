package com.slideindex.app.receiver

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.R
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.otp.OtpAutoInputOrchestrator
import com.slideindex.app.otp.OtpCaptureDeduplicator
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.OtpRecordFillStatus
import com.slideindex.app.otp.OtpRecordCategory
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.settings.AppSettings
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
        val slot = intent.getIntExtra(OtpAutoInputBroadcastContract.EXTRA_SMS_SLOT, -1)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handleSms(context, body, sender, slot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSms(context: Context, body: String, sender: String, slot: Int) {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppGraphEntryPoint::class.java
        ).dependencies()
        val settings = deps.settingsRepository.settings.first()
        if (!settings.otpLsposedSmsCaptureEnabled) return
        val officialRules = deps.otpOfficialRulesLoader.getRules()
        val config = OtpExtractionConfig.build(
            keywordsRegex = settings.otpKeywordsRegex,
            officialRules = officialRules,
            userRules = settings.otpUserMatchRules,
            disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds
        )
        val result = VerificationCodeExtractor.extract(
            packageName = sender,
            title = sender,
            text = body,
            config = config
        )
        val code = result.code
        if (code.isNullOrBlank()) {
            recordPlainSmsIfEnabled(deps, settings, sender, body, slot)
            return
        }
        if (!OtpCaptureDeduplicator.tryConsumeExtractedCode(code)) {
            Log.d(TAG, "Skipping duplicate LSPosed SMS code")
            return
        }
        Log.i(TAG, "LSPosed SMS code extracted: ${code.length} chars")
        val fillStatus = if (settings.otpAutoInputEnabled) {
            OtpRecordFillStatus.PENDING
        } else {
            OtpRecordFillStatus.NONE
        }
        val recordId = if (settings.otpRecordCodeEnabled) {
            deps.otpRecordsRepository.recordSuspend(
                code = code,
                packageName = sender,
                title = sender,
                text = body,
                ruleName = result.ruleName,
                autoFillStatus = fillStatus,
                category = OtpRecordCategory.CODE,
                simSlot = slot,
            ).getOrNull()
        } else {
            null
        }
        if (settings.otpAutoInputEnabled) {
            OtpAutoInputOrchestrator.requestAutoFill(context.applicationContext, code, settings, recordId)
        }
        if (settings.otpCopyToClipboard) {
            runCatching { OtpClipboardHelper.copyCode(context.applicationContext, code) }
        }
        deps.otpCodeAlertPresenter.present(
            code = code,
            sourceLabel = formatSourceLabel(context, sender, slot),
            settings = settings,
        )
    }

    /** 带上卡槽信息，便于用户分辨是哪张卡收到的验证码（单卡/未知槽位只显示发送方）。 */
    private fun formatSourceLabel(context: Context, sender: String, slot: Int): String =
        if (slot < 0) {
            sender
        } else {
            context.getString(R.string.otp_sim_slot_label, slot + 1) + " · " + sender
        }

    /** 未提取到验证码时按开关记录普通短信（对齐上游「记录普通短信」分类）。 */
    private suspend fun recordPlainSmsIfEnabled(
        deps: AppDependencies,
        settings: AppSettings,
        sender: String,
        body: String,
        slot: Int,
    ) {
        if (!settings.otpRecordPlainSmsEnabled) return
        deps.otpRecordsRepository.recordSuspend(
            code = "",
            packageName = sender,
            title = sender,
            text = body,
            category = OtpRecordCategory.PLAIN_SMS,
            simSlot = slot,
        )
    }

    companion object {
        private const val TAG = "OtpSmsBridge"
    }
}
