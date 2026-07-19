package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.FloatBallTranslateEngine
import com.slideindex.app.translate.TranslateDependencyAccess
import com.slideindex.app.translate.TranslateEngine
import com.slideindex.app.translate.TranslateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FloatBallTranslateCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun translate(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?: return

        if (!settings.floatBallInstantTranslate) {
            FloatBallTextPick.translateText(context, trimmed)
            return
        }

        if (!FloatBallPickResultPanel.isShowing) return

        if (FloatBallPickResultPanel.isShowingTranslation()) {
            FloatBallPickResultPanel.restoreFromTranslation()
            return
        }

        val targetLang = settings.floatBallTranslateTargetLang.ifBlank { "zh-CN" }
        val engine = when (settings.floatBallTranslateEngine) {
            FloatBallTranslateEngine.GOOGLE -> TranslateEngine.GOOGLE
            FloatBallTranslateEngine.ML_KIT -> TranslateEngine.ML_KIT
        }

        FloatBallPickResultPanel.showTranslateLoading()
        scope.launch {
            val service = TranslateDependencyAccess.translateService(context)
            if (service == null) {
                FloatBallPickResultPanel.showTranslateError(context, "translate_unavailable")
                return@launch
            }
            when (val result = service.translate(trimmed, targetLang, engine)) {
                is TranslateResult.Success -> {
                    FloatBallPickResultPanel.showTranslateResult(result.translatedText)
                }
                is TranslateResult.Failure -> {
                    FloatBallPickResultPanel.showTranslateError(
                        context,
                        mapErrorMessage(result.message),
                    )
                }
            }
        }
    }

    private fun mapErrorMessage(code: String): String = when (code) {
        "target_model_not_installed", "source_model_not_installed", "model_download_required" ->
            "mlkit_model_not_installed"
        "wifi_required" -> "wifi_required"
        "unsupported_target_language" -> "unsupported_language"
        else -> code
    }
}
