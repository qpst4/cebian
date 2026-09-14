package com.slideindex.app.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLlmModelCatalogClientTest {

    private val client = CloudLlmModelCatalogClient()

    @Test
    fun modelsEndpoint_appendsV1Models() {
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/models",
            client.modelsEndpoint("https://dashscope.aliyuncs.com/compatible-mode/v1"),
        )
        assertEquals(
            "https://api.siliconflow.cn/v1/models",
            client.modelsEndpoint("https://api.siliconflow.cn/v1/"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/models",
            client.modelsEndpoint("https://open.bigmodel.cn/api/paas/v4/"),
        )
    }

    @Test
    fun parseOpenAiModelList_readsIds() {
        val json = """
            {"object":"list","data":[{"id":"qwen-plus"},{"id":"qwen-vl-max"}]}
        """.trimIndent()
        assertEquals(listOf("qwen-plus", "qwen-vl-max"), client.parseOpenAiModelList(json))
    }

    @Test
    fun textFilter_dropsVisionModels() {
        val input = listOf("qwen-plus", "qwen-vl-max", "qwen3.8-max", "text-embedding-v3")
        val out = CloudLlmTextModelFilter.filterForTextGeneration(input)
        assertTrue(out.contains("qwen-plus"))
        assertTrue(out.contains("qwen3.8-max"))
        assertFalse(out.any { it.contains("vl", ignoreCase = true) })
        assertFalse(out.any { it.contains("embed", ignoreCase = true) })
    }
}
