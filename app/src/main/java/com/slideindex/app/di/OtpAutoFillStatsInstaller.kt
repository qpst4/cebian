package com.slideindex.app.di

import com.slideindex.app.otp.OtpAutoFillStatsRepository
import com.slideindex.app.otp.OtpAutoInputOrchestrator
import com.slideindex.app.otp.OtpRecordsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class OtpAutoFillStatsInstaller @Inject constructor(
    private val statsRepository: OtpAutoFillStatsRepository,
    private val otpRecordsRepository: OtpRecordsRepository,
    private val applicationScope: CoroutineScope,
) {
    fun install() {
        OtpAutoInputOrchestrator.setStatsRecorder { success, strategy, reason, recordId ->
            applicationScope.launch {
                statsRepository.recordAttempt(
                    success = success,
                    strategy = strategy,
                    reason = reason,
                )
                recordId?.let { id ->
                    otpRecordsRepository.updateAutoFillOutcome(
                        id = id,
                        success = success,
                        strategy = strategy,
                        reason = reason,
                    )
                }
            }
        }
    }
}
