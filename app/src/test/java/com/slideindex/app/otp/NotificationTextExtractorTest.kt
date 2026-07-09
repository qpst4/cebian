package com.slideindex.app.otp

import android.app.Notification
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationTextExtractorTest {

    @Test
    fun extract_readsTitleAndTextExtras() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "银行通知")
            putCharSequence(Notification.EXTRA_TEXT, "验证码 123456")
        }

        val content = NotificationTextExtractor.extract(extras)

        assertEquals("银行通知", content.title)
        assertEquals("验证码 123456", content.text)
    }

    @Test
    fun extract_fallsBackToBigText() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "标题")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "展开后的正文")
        }

        val content = NotificationTextExtractor.extract(extras)

        assertEquals("展开后的正文", content.text)
    }

    @Test
    fun extract_readsMessagingStyleMessages() {
        val message = Bundle().apply {
            putCharSequence("text", "消息验证码 778899")
        }
        val extras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(message))
        }

        val content = NotificationTextExtractor.extract(extras)

        assertEquals("消息验证码 778899", content.text)
    }
}
