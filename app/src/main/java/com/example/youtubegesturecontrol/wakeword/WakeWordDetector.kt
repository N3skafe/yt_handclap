package com.example.youtubegesturecontrol.wakeword

import com.example.youtubegesturecontrol.UserNotice

/**
 * 호출어 엔진을 갈아 끼우기 위한 작은 경계입니다.
 * 콜백은 메인 스레드에서 호출된다고 가정하지 않습니다. 구현이 메인 스레드로 옮깁니다.
 */
interface WakeWordDetector {
    fun prepare(
        onDetected: () -> Unit,
        onError: (UserNotice) -> Unit,
        onDetail: (UserNotice) -> Unit = {},
    )

    fun startListening()

    fun suspendListening()

    fun release()
}
