package com.slideindex.app.clipboardfloat

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归：大窗必须始终带 [WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH]，
 * 否则窗口不再收到 ACTION_OUTSIDE，「点窗外关闭」失效（只能用返回键/关闭按钮）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ClipboardFloatWindowFlagsTest {

    private val outsideTouch = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    private val notTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    private val notFocusable = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

    @Test
    fun `expanded window watches outside touch`() {
        val flags = ClipboardFloatWindowFlags.forMode(expanded = true, keyboardFocus = false)
        assertTrue(flags and outsideTouch != 0)
        assertTrue(flags and notFocusable != 0)
    }

    @Test
    fun `chip window does not watch outside touch`() {
        val flags = ClipboardFloatWindowFlags.forMode(expanded = false, keyboardFocus = false)
        assertFalse(flags and outsideTouch != 0)
    }

    @Test
    fun `search state keeps ime focus and outside touch`() {
        val flags = ClipboardFloatWindowFlags.forMode(expanded = true, keyboardFocus = true)
        assertFalse(flags and notFocusable != 0)
        assertTrue(flags and outsideTouch != 0)
    }

    /** 起拖隐藏 → 拖放结束恢复，必须回到拖前的 flags。 */
    @Test
    fun `entry drag restore keeps outside touch`() {
        val before = ClipboardFloatWindowFlags.forMode(expanded = true, keyboardFocus = false)
        val dragging = ClipboardFloatWindowFlags.withNotTouchable(before, notTouchable = true)
        assertTrue(dragging and notTouchable != 0)

        val restored = ClipboardFloatWindowFlags.withNotTouchable(dragging, notTouchable = false)

        assertEquals(before, restored)
        assertTrue(restored and outsideTouch != 0)
    }

    /** 拖拽期间刷新几何只应增删窗外点击位，不能把窗口变回可点。 */
    @Test
    fun `geometry refresh keeps not touchable during drag`() {
        val dragging = ClipboardFloatWindowFlags.withNotTouchable(
            ClipboardFloatWindowFlags.forMode(expanded = true, keyboardFocus = false),
            notTouchable = true
        )

        val expanded = ClipboardFloatWindowFlags.withOutsideTouch(dragging, enabled = true)
        assertTrue(expanded and notTouchable != 0)

        val chip = ClipboardFloatWindowFlags.withOutsideTouch(dragging, enabled = false)
        assertTrue(chip and notTouchable != 0)
        assertFalse(chip and outsideTouch != 0)
    }

    /** 退出搜索（updateWindowFocusForSearch(false)）同样不能丢掉窗外点击。 */
    @Test
    fun `search exit restores outside touch`() {
        val searching = ClipboardFloatWindowFlags.forMode(expanded = true, keyboardFocus = true)
        val afterSearch = ClipboardFloatWindowFlags.forMode(expanded = true, keyboardFocus = false)

        assertEquals(searching and outsideTouch, afterSearch and outsideTouch)
        assertTrue(afterSearch and outsideTouch != 0)
    }
}
