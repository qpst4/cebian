package com.slideindex.app.translate

object TranslateTargetLanguages {
    const val FOLLOW_APP = "app"

    fun isFollowApp(code: String): Boolean =
        code.trim().isEmpty() || code.equals(FOLLOW_APP, ignoreCase = true)
}
