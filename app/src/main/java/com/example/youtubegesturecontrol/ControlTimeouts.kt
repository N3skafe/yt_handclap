package com.example.youtubegesturecontrol

/**
 * 시간 제한과 재시도 한도입니다.
 * 성능 목표가 아니라, 무한히 기다리지 않기 위한 상한입니다.
 */
object ControlTimeouts {
    const val CAMERA_START_TIMEOUT_MS = 8_000L
    const val RECOGNITION_TIMEOUT_MS = 10_000L
    const val PLAYBACK_CONFIRMATION_TIMEOUT_MS = 2_000L
    const val MAX_CONSECUTIVE_CAMERA_FAILURES = 3
    const val MAX_CONSECUTIVE_RECOGNIZER_FAILURES = 3
}
