package com.mpx.player.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackStateTest {

    @Test
    fun currentId_resolvesFromQueueAndIndex() {
        val s = PlaybackState(queueIds = listOf("a", "b", "c"), index = 1)
        assertEquals("b", s.currentId)
    }

    @Test
    fun currentId_nullWhenIndexOutOfRange() {
        assertNull(PlaybackState(queueIds = listOf("a"), index = -1).currentId)
        assertNull(PlaybackState(queueIds = emptyList(), index = 0).currentId)
    }
}
