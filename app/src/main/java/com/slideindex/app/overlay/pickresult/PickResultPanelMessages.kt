package com.slideindex.app.overlay.pickresult

import android.content.Context
import com.slideindex.app.R

internal fun translateErrorMessage(context: Context, code: String): String = when (code) {
    "mlkit_model_not_installed" -> context.getString(R.string.float_ball_translate_error_model_missing)
    "translate_engine_not_installed" -> context.getString(R.string.float_ball_translate_error_engine_missing)
    "wifi_required" -> context.getString(R.string.float_ball_translate_error_wifi_required)
    "unsupported_language" -> context.getString(R.string.float_ball_translate_error_unsupported_language)
    "translate_unavailable" -> context.getString(R.string.float_ball_translate_error_unavailable)
    "network_error", "http_403", "http_429", "http_500" ->
        context.getString(R.string.float_ball_translate_error_network)
    else -> code
}
