package com.slideindex.app.autofill

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OtpAutoInputNodeHelperTest {
    private fun sealedNode(configure: AccessibilityNodeInfo.() -> Unit): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain().apply {
            configure()
            val sealedField = AccessibilityNodeInfo::class.java.getDeclaredField("mSealed")
            sealedField.isAccessible = true
            sealedField.setBoolean(this, true)
        }
    @Test
    fun performAutoInput_withoutRoot_returnsNoActiveWindow() {
        val result = OtpAutoInputNodeHelper.performAutoInput(
            root = null,
            code = "1234",
            autoEnter = false,
        )

        assertFalse(result.success)
        assertEquals("none", result.strategy)
        assertEquals("no_active_window", result.reason)
    }

    @Test
    fun performAutoInput_withoutEditableNodes_returnsNoEditableNode() {
        val root = sealedNode {
            packageName = "com.example.app"
            className = "android.widget.FrameLayout"
            isEnabled = true
            isVisibleToUser = true
        }

        val result = OtpAutoInputNodeHelper.performAutoInput(
            root = root,
            code = "1234",
            autoEnter = false,
        )

        assertFalse(result.success)
        assertEquals("none", result.strategy)
        assertEquals("no_editable_node", result.reason)
        assertEquals("com.example.app", result.windowPackage)
    }

    @Test
    fun performAutoInput_withShortCode_skipsGroupStrategy() {
        val root = editableRoot(focused = false)

        val result = OtpAutoInputNodeHelper.performAutoInput(
            root = root,
            code = "12",
            autoEnter = false,
        )

        assertEquals("com.test.app", result.windowPackage)
        assertTrue(result.success)
        assertEquals("best_editable_node", result.strategy)
    }

    private fun editableRoot(code: String = "", focused: Boolean = true): AccessibilityNodeInfo =
        sealedNode {
            packageName = "com.test.app"
            className = "android.widget.EditText"
            isEnabled = true
            isVisibleToUser = true
            isEditable = true
            isFocused = focused
            if (code.isNotEmpty()) {
                text = code
            }
        }
}
