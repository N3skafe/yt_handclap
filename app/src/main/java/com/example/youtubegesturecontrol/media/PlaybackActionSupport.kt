package com.example.youtubegesturecontrol.media

/**
 * 세션이 광고한 동작 비트로 명령을 보낼 수 있는지 판단합니다.
 * 비트가 0이면 목록을 안 채운 것이라 시도합니다. 다른 명령의 비트로 대체하지 않습니다.
 * 쇼츠 껍데기는 이 함수보다 먼저 걸러서, 여기서는 일반 영상만 봅니다.
 */
object PlaybackActionSupport {
    fun allows(actions: Long, requiredAction: Long): Boolean {
        if (actions == 0L) {
            return true
        }
        return actions and requiredAction != 0L
    }
}
