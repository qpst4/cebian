package com.slideindex.app.message

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageActionTest {

    @Test
    fun fromId_decodesBatchActions() {
        assertTrue(MessageAction.fromId(6) == MessageAction.IgnoreAll)
        assertTrue(MessageAction.fromId(7) == MessageAction.IgnoreAndRemoveAll)
        assertTrue(MessageAction.fromId(8) == MessageAction.QuickReplyAndIgnore)
        assertTrue(MessageAction.fromId(9) == MessageAction.QuickReplyAndRemove)
        assertTrue(MessageAction.fromId(10) == MessageAction.IgnoreSameSource)
        assertTrue(MessageAction.fromId(11) == MessageAction.IgnoreSameSourceAndRemove)
    }

    @Test
    fun opensQuickReply_onlyQuickReplyVariants() {
        assertTrue(MessageAction.QuickReply.opensQuickReply)
        assertTrue(MessageAction.QuickReplyAndIgnore.opensQuickReply)
        assertTrue(MessageAction.QuickReplyAndRemove.opensQuickReply)
        assertFalse(MessageAction.Ignore.opensQuickReply)
        assertFalse(MessageAction.IgnoreAndRemove.opensQuickReply)
        assertFalse(MessageAction.Read.opensQuickReply)
    }

    @Test
    fun affectsAllDisplayed_onlyBatchActions() {
        assertTrue(MessageAction.IgnoreAll.affectsAllDisplayed)
        assertTrue(MessageAction.IgnoreAndRemoveAll.affectsAllDisplayed)
        assertFalse(MessageAction.Ignore.affectsAllDisplayed)
        assertFalse(MessageAction.IgnoreAndRemove.affectsAllDisplayed)
        assertFalse(MessageAction.IgnoreSameSource.affectsAllDisplayed)
        assertFalse(MessageAction.IgnoreSameSourceAndRemove.affectsAllDisplayed)
        assertFalse(MessageAction.Read.affectsAllDisplayed)
    }

    @Test
    fun affectsSameSource_onlySameSourceActions() {
        assertTrue(MessageAction.IgnoreSameSource.affectsSameSource)
        assertTrue(MessageAction.IgnoreSameSourceAndRemove.affectsSameSource)
        assertFalse(MessageAction.IgnoreAll.affectsSameSource)
        assertFalse(MessageAction.Ignore.affectsSameSource)
        assertFalse(MessageAction.Read.affectsSameSource)
    }
}
