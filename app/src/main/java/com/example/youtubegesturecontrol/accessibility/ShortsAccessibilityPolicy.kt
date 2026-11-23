package com.example.youtubegesturecontrol.accessibility

import android.media.session.PlaybackState
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.PlaybackRequestResult
import com.example.youtubegesturecontrol.media.YoutubeSessionSelector

/**
 * Shorts 접근성 폴백을 보낼지 결정합니다.
 * 패키지 이름이나 화면 노드가 아니라 세션 지문만 보고, 기기 없이 단위 테스트할 수 있습니다.
 */
enum class ShortsGestureMotion {
    TAP,
    SWIPE_UP,
    SWIPE_DOWN,
}

object ShortsAccessibilityPolicy {
    fun isYouTubeForeground(packageName: String?): Boolean {
        return packageName == YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME
    }

    /**
     * 화면이 꺼져 있거나 잠금 화면이 떠 있으면 보내지 않습니다.
     * 기기에 따라 잠금 화면 위에서도 전면 창이 유튜브로 보고될 수 있습니다.
     */
    fun isScreenUnlocked(interactive: Boolean, keyguardLocked: Boolean): Boolean {
        return interactive && !keyguardLocked
    }

    /**
     * mediaId와 title이 둘 다 비어 있고, 탐색(SEEK_TO) 비트가 없을 때만 쇼츠 껍데기입니다.
     */
    fun looksLikeShortsShell(mediaId: String?, title: String?, actions: Long): Boolean {
        return mediaId.isNullOrEmpty() &&
            title.isNullOrEmpty() &&
            (actions and PlaybackState.ACTION_SEEK_TO) == 0L
    }

    /**
     * 쇼츠 껍데기에서 미디어 명령이 거부되었거나 확인 시간이 지난 경우에만 폴백합니다.
     * 확인된 명령, 실패, 세션 없음, 응답 시간 초과는 접근성으로 넘기지 않습니다.
     */
    fun isFallbackCandidate(
        result: PlaybackRequestResult,
        mediaId: String?,
        title: String?,
        actions: Long,
    ): Boolean {
        if (!looksLikeShortsShell(mediaId, title, actions)) {
            return false
        }
        return result is PlaybackRequestResult.ActionUnsupported ||
            result is PlaybackRequestResult.ConfirmationTimedOut
    }

    fun motionFor(command: PlaybackCommand): ShortsGestureMotion {
        return when (command) {
            PlaybackCommand.PAUSE, PlaybackCommand.PLAY -> ShortsGestureMotion.TAP
            PlaybackCommand.NEXT -> ShortsGestureMotion.SWIPE_UP
            PlaybackCommand.PREVIOUS -> ShortsGestureMotion.SWIPE_DOWN
        }
    }
}
