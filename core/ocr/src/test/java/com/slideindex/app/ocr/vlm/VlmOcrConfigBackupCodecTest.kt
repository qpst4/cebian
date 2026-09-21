package com.slideindex.app.ocr.vlm

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class VlmOcrConfigBackupCodecTest {
    private lateinit var context: Context
    private lateinit var manager: VlmOcrConfigManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("vlm_ocr_config", Context.MODE_PRIVATE).edit().clear().apply()
        manager = VlmOcrConfigManager(context)
    }

    @Test
    fun exportImport_roundTrip_preservesApiKeyAndPrompt() {
        manager.setApiKey(VlmProvider.DASHSCOPE, "test-secret-key")
        manager.setBaseUrl(VlmProvider.DASHSCOPE, "https://example.com/v1")
        manager.commonPrompt = "custom prompt body"

        val exported = manager.exportRawJson()
        context.getSharedPreferences("vlm_ocr_config", Context.MODE_PRIVATE).edit().clear().apply()
        manager.importRawJson(exported, replaceExisting = true)

        assertEquals("test-secret-key", manager.getApiKey(VlmProvider.DASHSCOPE))
        assertEquals("https://example.com/v1", manager.getBaseUrl(VlmProvider.DASHSCOPE))
        assertEquals("custom prompt body", manager.commonPrompt)
    }
}
