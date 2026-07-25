package com.slideindex.app.otp

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.settings.AppSettings
import java.text.DateFormat
import java.util.Date

object OtpAutoFillUiLabels {
    fun formatRuntimeStatus(
        context: Context,
        settings: AppSettings,
        accessibilityGranted: Boolean,
    ): String = when {
        !settings.otpAutoInputEnabled ->
            context.getString(R.string.otp_runtime_status_disabled)
        !accessibilityGranted ->
            context.getString(R.string.otp_runtime_status_need_accessibility)
        settings.otpLsposedSystemInjectEnabled ->
            context.getString(R.string.otp_runtime_status_inject_pipeline)
        else ->
            context.getString(R.string.otp_runtime_status_a11y_only)
    }

    fun formatRuntimeSmsHint(context: Context, settings: AppSettings): String? =
        if (settings.otpLsposedSmsCaptureEnabled) {
            context.getString(R.string.otp_runtime_status_sms_lsposed)
        } else {
            null
        }

    fun formatFillPipelineHint(
        context: Context,
        settings: AppSettings,
        accessibilityGranted: Boolean,
    ): String? {
        if (!settings.otpAutoInputEnabled || !accessibilityGranted) return null
        return if (settings.otpLsposedSystemInjectEnabled) {
            context.getString(R.string.otp_fill_method_pipeline_inject)
        } else {
            context.getString(R.string.otp_fill_method_pipeline_a11y_only)
        }
    }

    fun formatStatsEntrySubtitle(context: Context, stats: OtpAutoFillStats): String =
        if (stats.totalAttempts <= 0) {
            context.getString(R.string.otp_autofill_stats_entry_empty)
        } else {
            context.resources.getQuantityString(
                R.plurals.otp_autofill_stats_summary,
                stats.totalAttempts,
                stats.totalAttempts,
                stats.successRatePercent,
            )
        }

    fun formatLastAttemptTime(context: Context, epochMs: Long?): String? {
        epochMs ?: return null
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return formatter.format(Date(epochMs))
    }

    fun formatStrategy(context: Context, strategy: String?): String {
        val key = strategy.orEmpty()
        val resId = when (key) {
            "system_inject" -> R.string.otp_autofill_strategy_system_inject
            "focused_node" -> R.string.otp_autofill_strategy_focused_node
            "best_editable_node" -> R.string.otp_autofill_strategy_best_editable_node
            "group_nodes" -> R.string.otp_autofill_strategy_group_nodes
            "set_text" -> R.string.otp_autofill_strategy_set_text
            "none" -> R.string.otp_autofill_strategy_none
            "probe" -> R.string.otp_autofill_strategy_probe
            else -> return context.getString(R.string.otp_autofill_strategy_unknown, key.ifBlank { "—" })
        }
        return context.getString(resId)
    }

    fun formatRecordFillStatus(context: Context, status: OtpRecordFillStatus): String {
        val resId = when (status) {
            OtpRecordFillStatus.NONE -> R.string.otp_record_fill_none
            OtpRecordFillStatus.PENDING -> R.string.otp_record_fill_pending
            OtpRecordFillStatus.LSPOSED -> R.string.otp_record_fill_lsposed
            OtpRecordFillStatus.ACCESSIBILITY -> R.string.otp_record_fill_accessibility
            OtpRecordFillStatus.FAILED -> R.string.otp_record_fill_failed
        }
        return context.getString(resId)
    }

    fun formatReason(context: Context, reason: String?): String {
        val key = reason.orEmpty()
        if (key.isBlank()) return context.getString(R.string.otp_autofill_reason_empty)
        val resId = when (key) {
            "ok" -> R.string.otp_autofill_reason_ok
            "timeout" -> R.string.otp_autofill_reason_timeout
            "no_active_window" -> R.string.otp_autofill_reason_no_active_window
            "no_editable_node" -> R.string.otp_autofill_reason_no_editable_node
            "own_package" -> R.string.otp_autofill_reason_own_package
            "action_set_text_failed" -> R.string.otp_autofill_reason_action_set_text_failed
            "set_text_exception" -> R.string.otp_autofill_reason_set_text_exception
            "group_strategy_not_applicable" -> R.string.otp_autofill_reason_group_strategy_not_applicable
            "not_enough_editable_nodes" -> R.string.otp_autofill_reason_not_enough_editable_nodes
            "group_set_text_failed" -> R.string.otp_autofill_reason_group_set_text_failed
            "manager_unresolved" -> R.string.otp_autofill_reason_manager_unresolved
            "inject_method_unresolved" -> R.string.otp_autofill_reason_inject_method_unresolved
            "no_key_events" -> R.string.otp_autofill_reason_no_key_events
            "inject_exception" -> R.string.otp_autofill_reason_inject_exception
            "probe" -> R.string.otp_autofill_reason_probe
            OtpAutoInputBroadcastContract.SystemInjectReason.UID_REJECTED ->
                R.string.otp_autofill_reason_uid_rejected
            OtpAutoInputBroadcastContract.SystemInjectReason.INJECT_DISABLED ->
                R.string.otp_autofill_reason_inject_disabled
            OtpAutoInputBroadcastContract.SystemInjectReason.RESULT_CODE_BLOCKED ->
                R.string.otp_autofill_reason_result_code_blocked
            OtpAutoInputBroadcastContract.SystemInjectReason.INVALID_REQUEST ->
                R.string.otp_autofill_reason_invalid_request
            "receiver_not_ready" -> R.string.otp_autofill_reason_receiver_not_ready
            else -> return context.getString(R.string.otp_autofill_reason_unknown, key)
        }
        return context.getString(resId)
    }

    fun formatRecordFillDetail(
        context: Context,
        status: OtpRecordFillStatus,
        reason: String?,
    ): String? {
        if (status != OtpRecordFillStatus.FAILED || reason.isNullOrBlank()) return null
        return formatReason(context, reason)
    }
}
