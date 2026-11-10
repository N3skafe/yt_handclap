package com.example.youtubegesturecontrol.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdentityTest {
    @Test
    fun prefersMediaIdOverTitle() {
        assertEquals("video-2", MediaIdentity.of("video-2", "title"))
    }

    @Test
    fun usesTitleWhenMediaIdIsMissing() {
        assertEquals("title", MediaIdentity.of(null, "title"))
        assertEquals("title", MediaIdentity.of("", "title"))
    }

    @Test
    fun emptyIdAndTitleAreAbsent() {
        assertNull(MediaIdentity.of(null, null))
        assertNull(MediaIdentity.of("", ""))
    }

    @Test
    fun changedWhenTheIdentityDiffers() {
        assertTrue(MediaIdentity.changed("video-1", "video-2"))
        assertTrue(MediaIdentity.changed(null, "video-2"))
    }

    @Test
    fun unchangedWhenTheIdentityStaysOrDisappears() {
        assertFalse(MediaIdentity.changed("video-1", "video-1"))
        assertFalse(MediaIdentity.changed("video-1", null))
        assertFalse(MediaIdentity.changed(null, null))
    }
}

class PlaybackActionSupportTest {
    @Test
    fun emptyActionListStillAllowsTheRequest() {
        assertTrue(PlaybackActionSupport.allows(actions = 0L, requiredAction = NEXT))
    }

    @Test
    fun advertisedSkipBitAllowsThatCommand() {
        assertTrue(PlaybackActionSupport.allows(actions = NEXT, requiredAction = NEXT))
        assertTrue(PlaybackActionSupport.allows(actions = PREVIOUS, requiredAction = PREVIOUS))
    }

    @Test
    fun playBitDoesNotStandInForNext() {
        assertFalse(PlaybackActionSupport.allows(actions = PLAY, requiredAction = NEXT))
        assertFalse(PlaybackActionSupport.allows(actions = PLAY or PAUSE, requiredAction = PREVIOUS))
    }

    companion object {
        private const val PAUSE = 1L shl 1
        private const val PLAY = 1L shl 2
        private const val PREVIOUS = 1L shl 4
        private const val NEXT = 1L shl 5
    }
}
