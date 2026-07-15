package com.slideindex.app.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AccessibilityTextExtractorTest {
    @Test
    fun pickBetterCandidate_prefersSmallerVisibleBounds() {
        val overlay = AccessibilityTextExtractor.TextCandidate(
            text = "暂停视频，按钮",
            area = 1080 * 2400,
            isPrimaryText = false,
        )
        val comment = AccessibilityTextExtractor.TextCandidate(
            text = "这也叫挤？真正的挤是你双脚离地全自动上车下车",
            area = 869 * 132,
            isPrimaryText = true,
        )
        val picked = AccessibilityTextExtractor.pickBetterCandidate(overlay, comment)
        assertEquals(comment, picked)
    }

    @Test
    fun pickBetterCandidate_prefersPrimaryTextOnEqualArea() {
        val description = AccessibilityTextExtractor.TextCandidate(
            text = "按钮",
            area = 100,
            isPrimaryText = false,
        )
        val text = AccessibilityTextExtractor.TextCandidate(
            text = "评论正文",
            area = 100,
            isPrimaryText = true,
        )
        val picked = AccessibilityTextExtractor.pickBetterCandidate(description, text)
        assertEquals(text, picked)
    }

    @Test
    fun joinSortedTexts_ordersTopToBottomAndDedupes() {
        val joined = AccessibilityTextExtractor.joinSortedTexts(
            listOf(
                AccessibilityTextExtractor.TextEntry("第二行", 40, 0, 100, 60),
                AccessibilityTextExtractor.TextEntry("第一行", 10, 0, 100, 30),
                AccessibilityTextExtractor.TextEntry("第一行", 12, 0, 100, 32),
            ),
        )
        assertEquals("第一行\n第二行", joined)
    }

    @Test
    fun pickBestPreviewMetadata_prefersPostMetadataOverFlairExact() {
        val flair = "🇨🇳 Mainland Chinese | 大陆人"
        val postMetadata = "发帖者：u/yuyu2333miao, 在 r/AskAChinese 中发帖, 17 小时前, 18 票, 229 条评论, 55 次分享, u/yuyu2333miao 带有用户标识 🇨🇳 Mainland Chinese | 大陆人"
        val picked = AccessibilityTextExtractor.pickBestPreviewMetadata(
            exactText = flair,
            exactMatchesPreview = false,
            exactArea = 120,
            previewArea = 12_000,
            centerMetadata = postMetadata,
            parentChainMetadata = postMetadata,
            intersectingLongest = flair,
        )
        assertEquals(postMetadata, picked)
    }

    @Test
    fun pickBestPreviewMetadata_exactMatchNeverUpgradesToListMetadata() {
        val child = "你已在电脑登录，可传文件到电脑"
        val listWide = "手心输入法核心内测群...\n我的电脑...\n豆包输入法..."
        val picked = AccessibilityTextExtractor.pickBestPreviewMetadata(
            exactText = child,
            exactMatchesPreview = true,
            exactArea = 8_000,
            previewArea = 10_000,
            centerMetadata = listWide,
            parentChainMetadata = listWide,
            intersectingLongest = listWide,
        )
        assertEquals(child, picked)
    }

    @Test
    fun pickBestPreviewMetadata_keepsQqChildWhenParentIsOnlySlightlyLonger() {
        val child = "你已在电脑登录，可传文件到电脑"
        val parent = "我的电脑,你已在电脑登录，可传文件到电脑,昨天 17:58,置顶聊天"
        val picked = AccessibilityTextExtractor.pickBestPreviewMetadata(
            exactText = child,
            exactMatchesPreview = true,
            centerMetadata = parent,
            parentChainMetadata = parent,
            intersectingLongest = parent,
        )
        assertEquals(child, picked)
    }

    @Test
    fun filterEntriesContainedInPreview_keepsOnlyRowsInsidePreview() {
        val rowInside = AccessibilityTextExtractor.TextEntry(
            text = "你已在电脑登录，可传文件到电脑",
            top = 100,
            left = 0,
            right = 1000,
            bottom = 180,
        )
        val rowOutside = AccessibilityTextExtractor.TextEntry(
            text = "淡色系\n那功能有啥用？",
            top = 0,
            left = 0,
            right = 1000,
            bottom = 80,
        )
        val preview = android.graphics.Rect(0, 90, 1000, 200)
        val filtered = AccessibilityTextExtractor.filterEntriesContainedInPreview(
            listOf(rowOutside, rowInside),
            preview,
        )
        assertEquals(listOf(rowInside), filtered)
    }

    @Test
    fun filterOutAncestorTextEntries_dropsParentRowSummary() {
        val parent = AccessibilityTextExtractor.TextEntry(
            text = "我的电脑,你已在电脑登录，可传文件到电脑,昨天 17:58,置顶聊天",
            top = 0,
            left = 0,
            right = 1000,
            bottom = 200,
        )
        val child = AccessibilityTextExtractor.TextEntry(
            text = "你已在电脑登录，可传文件到电脑",
            top = 50,
            left = 10,
            right = 500,
            bottom = 80,
        )
        val filtered = AccessibilityTextExtractor.filterOutAncestorTextEntries(listOf(parent, child))
        assertEquals(listOf(child), filtered)
    }

    @Test
    fun isWeakA11yPickResult_detectsMetadataOnly() {
        assertEquals(
            true,
            AccessibilityTextExtractor.isWeakA11yPickResult(
                "肥婆美妆家族...\n头像\n湖南\n1小时前",
            ),
        )
        assertEquals(
            false,
            AccessibilityTextExtractor.isWeakA11yPickResult(
                "我真的10个视频里面有八个是他的，你别给我推了行不行？",
            ),
        )
    }

    @Test
    fun preferLongerPickText_favorsOcrWhenA11yIsShort() {
        val a11y = "头像\n1小时前"
        val ocr = "我真的10个视频里面有八个是他的，你别给我推了行不行？"
        assertEquals(ocr, AccessibilityTextExtractor.preferLongerPickText(a11y, ocr))
    }

    @Test
    fun dedupeTextLines_removesRepeatedWeChatCommentLines() {
        val duplicated = "都不怎么登了，开了个季卡，不登又浪费\n都不怎么登了，开了个季卡，不登又浪费"
        assertEquals(
            "都不怎么登了，开了个季卡，不登又浪费",
            AccessibilityTextExtractor.dedupeTextLines(duplicated),
        )
    }
}
