package com.example.youtubegesturecontrol.media

/**
 * 활성 미디어 세션 중 일반 유튜브 앱만 고릅니다.
 * 유튜브 뮤직이나 다른 오디오 앱은 패키지 이름이 다르므로 선택하지 않습니다.
 *
 * object는 이 클래스의 인스턴스가 프로그램 안에 하나뿐이라는 뜻입니다.
 * Dart에서 생성자를 숨기고 static 함수만 두는 클래스와 비슷합니다.
 */
object YoutubeSessionSelector {
    const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
    const val YOUTUBE_MUSIC_PACKAGE_NAME = "com.google.android.apps.youtube.music"

    fun selectIndex(sessions: List<MediaSessionSnapshot>): Int? {
        val youtubeIndexes = sessions.mapIndexedNotNull { index, session ->
            if (session.packageName == YOUTUBE_PACKAGE_NAME) index else null
        }
        if (youtubeIndexes.isEmpty()) {
            return null
        }
        val playingIndex = youtubeIndexes.firstOrNull { index ->
            sessions[index].playback == SessionPlayback.PLAYING
        }
        return playingIndex ?: youtubeIndexes.first()
    }
}

enum class SessionPlayback {
    PLAYING,
    PAUSED,
    OTHER,
}

data class MediaSessionSnapshot(
    val packageName: String,
    val playback: SessionPlayback,
)
