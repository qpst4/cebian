package com.slideindex.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityClassValidatorTest {

    @Test
    fun isIgnoredNonActivityClass_returnsTrueForKnownViewsAndWidgets() {
        // Android 系统基础布局与视图
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.widget.FrameLayout"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.widget.LinearLayout"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.widget.RelativeLayout"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.widget.PopupWindow"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.widget.Toast"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.view.View"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.view.ViewGroup"))

        // Compose 视图
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("androidx.compose.ui.platform.ComposeView"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("androidx.compose.ui.platform.AndroidComposeView"))

        // Dialog 与输入法
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.app.Dialog"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.app.AlertDialog"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("com.sohu.inputmethod.sogou.InputMethodService"))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("android.inputmethodservice.InputMethodService"))

        // 空白字符串
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass(""))
        assertTrue(ActivityClassValidator.isIgnoredNonActivityClass("   "))
    }

    @Test
    fun isIgnoredNonActivityClass_returnsFalseForRealActivities() {
        // 真实业务 Activity
        assertFalse(ActivityClassValidator.isIgnoredNonActivityClass("com.tencent.mm.plugin.fav.ui.FavoriteIndexUI"))
        assertFalse(ActivityClassValidator.isIgnoredNonActivityClass("com.tencent.mm.ui.LauncherUI"))
        assertFalse(ActivityClassValidator.isIgnoredNonActivityClass("com.slideindex.app.MainActivity"))
        assertFalse(ActivityClassValidator.isIgnoredNonActivityClass("com.android.settings.Settings"))
    }
}
