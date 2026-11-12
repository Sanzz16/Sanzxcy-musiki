package com.metrolist.music.listentogether

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMetadataPresenceTest {
    @Test
    fun `only a not found response marks room metadata missing`() {
        assertTrue(shouldExitForMissingRoomMetadata(404))
        assertFalse(shouldExitForMissingRoomMetadata(0))
        assertFalse(shouldExitForMissingRoomMetadata(408))
        assertFalse(shouldExitForMissingRoomMetadata(500))
    }
}
