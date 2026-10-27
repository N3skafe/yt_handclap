package com.example.youtubegesturecontrol.media

import com.example.youtubegesturecontrol.UserNotice

/**
 * 재생 명령을 보낸 뒤의 결과입니다.
 * 메서드 호출에 성공한 것과, 유튜브 상태가 실제로 바뀐 것을 구분합니다.
 */
sealed class PlaybackRequestResult {
    data class Confirmed(
        val command: PlaybackCommand,
        val alreadyInRequestedState: Boolean,
    ) : PlaybackRequestResult()

    data class ConfirmationTimedOut(
        val command: PlaybackCommand,
    ) : PlaybackRequestResult()

    data class SessionUnavailable(
        val otherPackages: List<String>,
    ) : PlaybackRequestResult()

    data object ListenerAccessMissing : PlaybackRequestResult()

    data class ActionUnsupported(
        val command: PlaybackCommand,
    ) : PlaybackRequestResult()

    data class Failed(
        val command: PlaybackCommand?,
        val detail: String,
    ) : PlaybackRequestResult()

    data object ResponseTimedOut : PlaybackRequestResult()

    data object PlaybackStateUnreadable : PlaybackRequestResult()
}

fun PlaybackRequestResult.toNotice(): UserNotice {
    return when (this) {
        is PlaybackRequestResult.Confirmed ->
            UserNotice.PlaybackConfirmed(command, alreadyInRequestedState)
        is PlaybackRequestResult.ConfirmationTimedOut ->
            UserNotice.PlaybackConfirmationTimedOut(command)
        is PlaybackRequestResult.SessionUnavailable ->
            UserNotice.SessionUnavailable(otherPackages)
        PlaybackRequestResult.ListenerAccessMissing -> UserNotice.ListenerAccessMissing
        is PlaybackRequestResult.ActionUnsupported -> UserNotice.ActionUnsupported(command)
        is PlaybackRequestResult.Failed -> UserNotice.PlaybackFailed(command, detail)
        PlaybackRequestResult.ResponseTimedOut -> UserNotice.SessionResponseTimedOut
        PlaybackRequestResult.PlaybackStateUnreadable -> UserNotice.PlaybackStateUnreadable
    }
}
