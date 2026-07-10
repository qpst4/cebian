package com.slideindex.app.message

import com.slideindex.app.message.MessageAction.Dnd5Min
import com.slideindex.app.message.MessageAction.Ignore
import com.slideindex.app.message.MessageAction.Read
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageSwipeGestureTest {

    @Test
    fun resolveSwipe_prefersVerticalUp() {
        val settings = MessageSettings(swipeUpAction = Read, swipeRightAction = Dnd5Min)
        assertEquals(Read, resolveMessageSwipeAction(30f, -100f, settings))
    }

    @Test
    fun resolveSwipe_prefersVerticalDown() {
        val settings = MessageSettings(swipeDownAction = Dnd5Min)
        assertEquals(Dnd5Min, resolveMessageSwipeAction(20f, 120f, settings))
    }

    @Test
    fun resolveSwipe_horizontalWhenDominant() {
        val settings = MessageSettings(swipeLeftAction = Read, swipeUpAction = Dnd5Min)
        assertEquals(Read, resolveMessageSwipeAction(-120f, 10f, settings))
        assertEquals(Dnd5Min, resolveMessageSwipeAction(10f, -120f, settings))
    }

    @Test
    fun resolveSwipe_belowThreshold_returnsNull() {
        val settings = MessageSettings(swipeRightAction = Read)
        assertNull(resolveMessageSwipeAction(40f, 0f, settings, thresholdPx = 80f))
    }

    @Test
    fun resolveSwipe_ignoreActionStillReturned() {
        val settings = MessageSettings(swipeRightAction = Ignore)
        assertEquals(Ignore, resolveMessageSwipeAction(200f, 0f, settings))
    }
}
