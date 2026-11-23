package com.example.youtubegesturecontrol.accessibility

import android.media.session.PlaybackState
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.PlaybackRequestResult
import com.example.youtubegesturecontrol.media.YoutubeSessionSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsAccessibilityPolicyTest {
    @Test
    fun titlePresentIsNotAShortsShell() {
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell(null, "Sunday", actions = 0L))
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell("", "Sunday", actions = PAUSE_AND_PLAY))
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell(null, " ", actions = 0L))
    }

    @Test
    fun seekBitIsNotAShortsShell() {
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell(null, null, PlaybackState.ACTION_SEEK_TO))
        assertFalse(
            ShortsAccessibilityPolicy.looksLikeShortsShell(
                "",
                "",
                PlaybackState.ACTION_SEEK_TO or PAUSE_AND_PLAY,
            ),
        )
        // 기기에서 본 일반 영상 actions. SEEK_TO가 포함되어 있다.
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell(null, null, REGULAR_VIDEO_ACTIONS))
    }

    @Test
    fun mediaIdPresentIsNotAShortsShell() {
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell("video-1", null, actions = 0L))
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell("video-1", "", actions = PLAY_FROM_URI))
        assertFalse(ShortsAccessibilityPolicy.looksLikeShortsShell(" ", null, actions = 0L))
    }

    @Test
    fun emptyIdentityWithoutSeekIsAShortsShell() {
        assertTrue(ShortsAccessibilityPolicy.looksLikeShortsShell(null, null, actions = 0L))
        assertTrue(ShortsAccessibilityPolicy.looksLikeShortsShell("", "", actions = 0L))
        assertTrue(ShortsAccessibilityPolicy.looksLikeShortsShell(null, "", actions = PAUSE_AND_PLAY))
        assertTrue(ShortsAccessibilityPolicy.looksLikeShortsShell("", null, actions = PLAY_FROM_URI))
    }

    @Test
    fun shellAndUnsupportedIsAFallbackCandidate() {
        PlaybackCommand.entries.forEach { command ->
            assertTrue(
                ShortsAccessibilityPolicy.isFallbackCandidate(
                    PlaybackRequestResult.ActionUnsupported(command),
                    mediaId = null,
                    title = null,
                    actions = PAUSE_AND_PLAY,
                ),
            )
        }
        assertTrue(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ActionUnsupported(PlaybackCommand.NEXT),
                mediaId = "",
                title = "",
                actions = 0L,
            ),
        )
        assertTrue(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ActionUnsupported(PlaybackCommand.PREVIOUS),
                mediaId = null,
                title = null,
                actions = PLAY_FROM_URI,
            ),
        )
    }

    @Test
    fun shellAndConfirmationTimeoutIsAFallbackCandidate() {
        assertTrue(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ConfirmationTimedOut(PlaybackCommand.PAUSE),
                mediaId = null,
                title = null,
                actions = PAUSE_AND_PLAY,
            ),
        )
        assertTrue(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ConfirmationTimedOut(PlaybackCommand.PLAY),
                mediaId = "",
                title = null,
                actions = 0L,
            ),
        )
    }

    @Test
    fun nonShellUnsupportedIsNotAFallbackCandidate() {
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ActionUnsupported(PlaybackCommand.NEXT),
                mediaId = null,
                title = "Sunday",
                actions = 0L,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ActionUnsupported(PlaybackCommand.NEXT),
                mediaId = null,
                title = null,
                actions = PlaybackState.ACTION_SEEK_TO,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ActionUnsupported(PlaybackCommand.NEXT),
                mediaId = null,
                title = null,
                actions = REGULAR_VIDEO_ACTIONS,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ActionUnsupported(PlaybackCommand.PREVIOUS),
                mediaId = "video-1",
                title = null,
                actions = 0L,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ConfirmationTimedOut(PlaybackCommand.PAUSE),
                mediaId = "video-1",
                title = "Sunday",
                actions = REGULAR_VIDEO_ACTIONS,
            ),
        )
    }

    @Test
    fun confirmedIsNotAFallbackCandidate() {
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.Confirmed(PlaybackCommand.PAUSE, alreadyInRequestedState = false),
                mediaId = null,
                title = null,
                actions = 0L,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.Confirmed(PlaybackCommand.NEXT, alreadyInRequestedState = false),
                mediaId = null,
                title = "Sunday",
                actions = REGULAR_VIDEO_ACTIONS,
            ),
        )
    }

    @Test
    fun otherResultsAreNotFallbackCandidates() {
        val shellId: String? = null
        val shellTitle: String? = null
        val shellActions = 0L
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.Failed(PlaybackCommand.NEXT, "detail"),
                shellId,
                shellTitle,
                shellActions,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.SessionUnavailable(emptyList()),
                shellId,
                shellTitle,
                shellActions,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ListenerAccessMissing,
                shellId,
                shellTitle,
                shellActions,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.PlaybackStateUnreadable,
                shellId,
                shellTitle,
                shellActions,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.ResponseTimedOut,
                shellId,
                shellTitle,
                shellActions,
            ),
        )
        assertFalse(
            ShortsAccessibilityPolicy.isFallbackCandidate(
                PlaybackRequestResult.AccessibilityGestureCompleted(PlaybackCommand.PAUSE),
                shellId,
                shellTitle,
                shellActions,
            ),
        )
    }

    @Test
    fun onlyTheYouTubePackageMayReceiveGestures() {
        assertTrue(ShortsAccessibilityPolicy.isYouTubeForeground(YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME))
        assertFalse(ShortsAccessibilityPolicy.isYouTubeForeground(null))
        assertFalse(ShortsAccessibilityPolicy.isYouTubeForeground("com.android.launcher"))
        assertFalse(ShortsAccessibilityPolicy.isYouTubeForeground(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME))
        assertFalse(ShortsAccessibilityPolicy.isYouTubeForeground("com.google.android.youtube.tv"))
    }

    @Test
    fun lockedOrDarkScreenNeverReceivesGestures() {
        assertTrue(ShortsAccessibilityPolicy.isScreenUnlocked(interactive = true, keyguardLocked = false))
        assertFalse(ShortsAccessibilityPolicy.isScreenUnlocked(interactive = true, keyguardLocked = true))
        assertFalse(ShortsAccessibilityPolicy.isScreenUnlocked(interactive = false, keyguardLocked = false))
        assertFalse(ShortsAccessibilityPolicy.isScreenUnlocked(interactive = false, keyguardLocked = true))
    }

    @Test
    fun loggedActionMasksMatchPlatformBits() {
        assertEquals(6L, PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY)
        assertEquals(8192L, PlaybackState.ACTION_PLAY_FROM_URI)
        assertTrue((REGULAR_VIDEO_ACTIONS and PlaybackState.ACTION_SEEK_TO) != 0L)
    }

    @Test
    fun commandsMapToTapAndSwipes() {
        assertEquals(ShortsGestureMotion.TAP, ShortsAccessibilityPolicy.motionFor(PlaybackCommand.PAUSE))
        assertEquals(ShortsGestureMotion.TAP, ShortsAccessibilityPolicy.motionFor(PlaybackCommand.PLAY))
        assertEquals(ShortsGestureMotion.SWIPE_UP, ShortsAccessibilityPolicy.motionFor(PlaybackCommand.NEXT))
        assertEquals(ShortsGestureMotion.SWIPE_DOWN, ShortsAccessibilityPolicy.motionFor(PlaybackCommand.PREVIOUS))
    }

    private companion object {
        val PAUSE_AND_PLAY = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY
        val PLAY_FROM_URI = PlaybackState.ACTION_PLAY_FROM_URI
        const val REGULAR_VIDEO_ACTIONS = 8615L
    }
}
