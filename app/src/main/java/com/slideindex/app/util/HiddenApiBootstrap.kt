package com.slideindex.app.util

import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 一次性注册 Hidden API 豁免。
 *
 * [HiddenApiBypass.addHiddenApiExemptions] 会**替换**整表而非追加；若先用 [HiddenApiBypass.setHiddenApiExemptions]
 * 设 `"L"` 再 add 预测性返回签名，会把 `"L"` 清掉，导致悬浮窗 [View.getViewRootImpl] / BackgroundBlurDrawable 反射全部失效。
 */
object HiddenApiBootstrap {
    private const val PREDICTIVE_BACK =
        "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"

    fun install() {
        HiddenApiBypass.setHiddenApiExemptions("L", PREDICTIVE_BACK)
    }
}
