package com.example.youtubegesturecontrol.media

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
        ).toKorean()
        val play = PlaybackRequestResult.Confirmed(
            PlaybackCommand.PLAY,
            alreadyInRequestedState = false,
        ).toKorean()
        assertTrue(pause.contains("일시정지 요청"))
        assertTrue(!pause.contains("재생 요청"))
        assertTrue(play.contains("재생 요청"))
        assertTrue(!play.contains("일시정지 요청"))
    }

    @Test
    fun unsupportedMessageSaysToggleWasNotUsed() {
        val message = PlaybackRequestResult.ActionUnsupported(PlaybackCommand.PAUSE).toKorean()
        assertTrue(message.contains("토글로는 대체하지 않았습니다"))
    }

    @Test
    fun unavailableMessageNamesOtherPackagesWithoutControllingThem() {
        val message = PlaybackRequestResult.SessionUnavailable(
            listOf(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME),
        ).toKorean()
        assertTrue(message.contains(YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME))
        assertTrue(message.contains(YoutubeSessionSelector.YOUTUBE_MUSIC_PACKAGE_NAME))
        assertTrue(message.contains("제어하지 않습니다"))
    }
}
