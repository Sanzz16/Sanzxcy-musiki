/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndedQueueResumeTest {
    @Test
    fun `missing or inactive sleep timer does not pause at song end`() {
        assertFalse(shouldPauseAtSongEnd(timerActive = false, pauseWhenSongEnd = true))
        assertFalse(shouldPauseAtSongEnd(timerActive = true, pauseWhenSongEnd = false))
        assertTrue(shouldPauseAtSongEnd(timerActive = true, pauseWhenSongEnd = true))
    }

    @Test
    fun `late queue append resumes playback only when playback was waiting for items`() {
        assertTrue(
            shouldResumeAfterQueueAppend(
                playbackEndedWaitingForItems = true,
                playWhenReady = true,
                hasNextMediaItem = true,
            ),
        )
        assertFalse(
            shouldResumeAfterQueueAppend(
                playbackEndedWaitingForItems = false,
                playWhenReady = true,
                hasNextMediaItem = true,
            ),
        )
    }

    @Test
    fun `late queue append does not override pause or empty queue`() {
        assertFalse(
            shouldResumeAfterQueueAppend(
                playbackEndedWaitingForItems = true,
                playWhenReady = false,
                hasNextMediaItem = true,
            ),
        )
        assertFalse(
            shouldResumeAfterQueueAppend(
                playbackEndedWaitingForItems = true,
                playWhenReady = true,
                hasNextMediaItem = false,
            ),
        )
    }
}
