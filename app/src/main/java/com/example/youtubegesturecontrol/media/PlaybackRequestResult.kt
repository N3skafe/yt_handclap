package com.example.youtubegesturecontrol.media

import com.example.youtubegesturecontrol.UserMessages

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
}

fun PlaybackRequestResult.toKorean(): String {
    return when (this) {
        is PlaybackRequestResult.Confirmed -> confirmedMessage(command, alreadyInRequestedState)
        is PlaybackRequestResult.ConfirmationTimedOut -> timeoutMessage(command)
        is PlaybackRequestResult.SessionUnavailable -> UserMessages.sessionUnavailable(otherPackages)
        PlaybackRequestResult.ListenerAccessMissing ->
            "알림 접근이 꺼져 있어 미디어 세션을 조회할 수 없습니다."
        is PlaybackRequestResult.ActionUnsupported -> unsupportedMessage(command)
        is PlaybackRequestResult.Failed -> "유튜브 명령을 완료하지 못했습니다. $detail"
    }
}

private fun confirmedMessage(command: PlaybackCommand, alreadyInRequestedState: Boolean): String {
    return when (command) {
        PlaybackCommand.PAUSE -> if (alreadyInRequestedState) {
            "일시정지 요청을 보냈습니다. 요청 전에도 이미 일시정지 상태였습니다."
        } else {
            "일시정지 요청을 보냈고, 유튜브 상태가 일시정지로 확인되었습니다."
        }
        PlaybackCommand.PLAY -> if (alreadyInRequestedState) {
            "재생 요청을 보냈습니다. 요청 전에도 이미 재생 중이었습니다."
        } else {
            "재생 요청을 보냈고, 유튜브 상태가 재생 중으로 확인되었습니다."
        }
    }
}

private fun timeoutMessage(command: PlaybackCommand): String {
    return when (command) {
        PlaybackCommand.PAUSE ->
            "일시정지 요청은 보냈지만, 제한 시간 안에 일시정지 상태를 확인하지 못했습니다."
        PlaybackCommand.PLAY ->
            "재생 요청은 보냈지만, 제한 시간 안에 재생 상태를 확인하지 못했습니다."
    }
}

private fun unsupportedMessage(command: PlaybackCommand): String {
    val actionName = when (command) {
        PlaybackCommand.PAUSE -> "일시정지"
        PlaybackCommand.PLAY -> "재생"
    }
    return "유튜브 세션이 $actionName 동작을 지원하지 않는다고 알려 왔습니다. " +
        "재생/일시정지 토글로는 대체하지 않았습니다."
}
