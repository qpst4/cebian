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

    @Test
    fun previewContainedNeedsMetadataExpansion_staysStrictForShortSideStat() {
        val stat = AccessibilityTextExtractor.TextEntry(
            text = "67.7万",
            top = 1693,
            left = 956,
            right = 1056,
            bottom = 1742,
        )
        assertEquals(
            false,
            AccessibilityTextExtractor.previewContainedNeedsMetadataExpansion(
                leafContained = listOf(stat),
                exactText = null,
                exactMatchesPreview = false,
                previewArea = 12_000,
            ),
        )
    }

    @Test
    fun previewContainedNeedsMetadataExpansion_expandsForRedditFlairInBox() {
        val flair = "🇨🇳 Mainland Chinese | 大陆人"
        val entry = AccessibilityTextExtractor.TextEntry(
            text = flair,
            top = 0,
            left = 0,
            right = 200,
            bottom = 40,
        )
        assertEquals(
            true,
            AccessibilityTextExtractor.previewContainedNeedsMetadataExpansion(
                leafContained = listOf(entry),
                exactText = flair,
                exactMatchesPreview = false,
                previewArea = 12_000,
            ),
        )
    }

    @Test
    fun previewContainedNeedsMetadataExpansion_expandsWhenPreviewEmpty() {
        assertEquals(
            true,
            AccessibilityTextExtractor.previewContainedNeedsMetadataExpansion(
                leafContained = emptyList(),
                exactText = null,
                exactMatchesPreview = false,
                previewArea = 1000,
            ),
        )
    }

    @Test
    fun previewContainedNeedsMetadataExpansion_expandsForMultiLineWeakMetadata() {
        val weak = listOf(
            AccessibilityTextExtractor.TextEntry("头像", 0, 0, 50, 20),
            AccessibilityTextExtractor.TextEntry("1小时前", 30, 0, 80, 50),
        )
        assertEquals(
            true,
            AccessibilityTextExtractor.previewContainedNeedsMetadataExpansion(
                leafContained = weak,
                exactText = null,
                exactMatchesPreview = false,
                previewArea = 2000,
            ),
        )
    }

    @Test
    fun filterPrimaryTextEntriesForPreview_keepsCenterOverlappingRow() {
        val preview = android.graphics.Rect(167, 1214, 815, 1266)
        val inside = AccessibilityTextExtractor.TextEntry(
            text = "展开2条回复",
            top = 1220,
            left = 200,
            right = 400,
            bottom = 1260,
        )
        val outside = AccessibilityTextExtractor.TextEntry(
            text = "整条评论",
            top = 1100,
            left = 200,
            right = 800,
            bottom = 1180,
        )
        val filtered = AccessibilityTextExtractor.filterPrimaryTextEntriesForPreview(
            listOf(inside, outside),
            preview,
        )
        assertEquals(listOf(inside), filtered)
    }

    @Test
    fun previewMetadataLikelyBeyondRect_flagsCommentListAggregate() {
        val aggregate = buildString {
            appendLine("用户甲: 评论一")
            appendLine("用户乙: 评论二")
            appendLine("用户丙: 评论三")
        }
        assertEquals(
            true,
            AccessibilityTextExtractor.previewMetadataLikelyBeyondRect(aggregate, previewArea = 30_000),
        )
        assertEquals(
            false,
            AccessibilityTextExtractor.previewMetadataLikelyBeyondRect("展开2条回复", previewArea = 30_000),
        )
    }

    @Test
    fun isPreviewNarrowBand_detectsHongguoExpandRow() {
        val preview = android.graphics.Rect(154, 1376, 1080, 1446)
        assertEquals(true, AccessibilityTextExtractor.isPreviewNarrowBand(preview))
    }

    @Test
    fun shouldAllowPreviewDescendantAggregate_blocksLargeListParent() {
        val preview = android.graphics.Rect(154, 1376, 1080, 1446)
        val previewArea = preview.width() * preview.height()
        val listParent = android.graphics.Rect(0, 400, 1080, 2000)
        assertEquals(
            false,
            AccessibilityTextExtractor.shouldAllowPreviewDescendantAggregate(
                listParent,
                preview,
                previewArea,
            ),
        )
    }

    @Test
    fun filterPrimaryTextEntriesForPreview_narrowBandOnlyFullyContained() {
        val preview = android.graphics.Rect(154, 1376, 1080, 1446)
        val expandRow = AccessibilityTextExtractor.TextEntry(
            text = "展开152条回复",
            top = 1380,
            left = 200,
            right = 500,
            bottom = 1440,
        )
        val tallComment = AccessibilityTextExtractor.TextEntry(
            text = "等了100年",
            top = 1200,
            left = 200,
            right = 900,
            bottom = 1450,
        )
        val filtered = AccessibilityTextExtractor.filterPrimaryTextEntriesForPreview(
            listOf(expandRow, tallComment),
            preview,
        )
        assertEquals(listOf(expandRow), filtered)
    }

    @Test
    fun entryOverlapsPreviewBand_excludesHistoryAboveTabStrip() {
        val preview = android.graphics.Rect(0, 1150, 1080, 1216)
        val history = AccessibilityTextExtractor.TextEntry(
            text = "吴建豪被陈伯开除了吗",
            top = 499,
            left = 39,
            right = 479,
            bottom = 595,
        )
        val tab = AccessibilityTextExtractor.TextEntry(
            text = "猜你想看",
            top = 1160,
            left = 40,
            right = 200,
            bottom = 1210,
        )
        assertEquals(false, AccessibilityTextExtractor.entryOverlapsPreviewBand(history, preview))
        assertEquals(true, AccessibilityTextExtractor.entryOverlapsPreviewBand(tab, preview))
    }
}
