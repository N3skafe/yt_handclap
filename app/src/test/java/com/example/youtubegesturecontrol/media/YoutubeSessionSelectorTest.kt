package com.example.youtubegesturecontrol.media

import com.example.youtubegesturecontrol.UserNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSessionSelectorTest {
    @Test
    fun ignoresMusicAndOtherPlayers() {
        val index = YoutubeSessionSelector.selectIndex(
            listOf(
                session("com.spotify.music", SessionPlayback.PLAYING),
                session(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME, SessionPlayback.PLAYING),
                session(YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME, SessionPlayback.PAUSED),
            ),
        )
        assertEquals(2, index)
    }

    @Test
    fun prefersPlayingYoutubeSession() {
        val index = YoutubeSessionSelector.selectIndex(
            listOf(
                session(YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME, SessionPlayback.PAUSED),
                session(YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME, SessionPlayback.PLAYING),
            ),
        )
        assertEquals(1, index)
    }

    @Test
    fun returnsNullWhenYoutubeIsAbsent() {
        assertNull(
            YoutubeSessionSelector.selectIndex(
                listOf(session(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME, SessionPlayback.PLAYING)),
            ),
        )
        assertNull(YoutubeSessionSelector.selectIndex(emptyList()))
    }

    private fun session(packageName: String, playback: SessionPlayback): MediaSessionSnapshot {
        return MediaSessionSnapshot(packageName = packageName, playback = playback)
    }
}

class PlaybackMessageTest {
    @Test
    fun confirmedPauseAndPlayUseDifferentCommands() {
        val pause = PlaybackRequestResult.Confirmed(
            PlaybackCommand.PAUSE,
            alreadyInRequestedState = false,
        ).toNotice()
        val play = PlaybackRequestResult.Confirmed(
            PlaybackCommand.PLAY,
            alreadyInRequestedState = false,
        ).toNotice()
        assertEquals(
            UserNotice.PlaybackConfirmed(PlaybackCommand.PAUSE, alreadyInRequestedState = false),
            pause,
        )
        assertEquals(
            UserNotice.PlaybackConfirmed(PlaybackCommand.PLAY, alreadyInRequestedState = false),
            play,
        )
    }

    @Test
    fun unsupportedMessageKeepsTheCommand() {
        val notice = PlaybackRequestResult.ActionUnsupported(PlaybackCommand.PAUSE).toNotice()
        assertEquals(UserNotice.ActionUnsupported(PlaybackCommand.PAUSE), notice)
    }

    @Test
    fun unavailableMessageKeepsOtherPackages() {
        val notice = PlaybackRequestResult.SessionUnavailable(
            listOf(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME),
        ).toNotice()
        assertEquals(
            UserNotice.SessionUnavailable(listOf(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME)),
            notice,
        )
    }
}
