package com.example.youtubegesturecontrol

import com.example.youtubegesturecontrol.gesture.HandGesture
import com.example.youtubegesturecontrol.media.PlaybackCommand

/**
 * 화면에 보일 내용의 종류입니다. 번역된 문장은 그리지 않고, 화면과 알림이 그때의 언어로 만듭니다.
 * 언어를 바꿔도 이미 저장된 문장이 남아 있지 않게 하려는 목적입니다.
 */
sealed class UserNotice {
    data object NoResultYet : UserNotice()

    data object Starting : UserNotice()

    data object WaitingForWakeWord : UserNotice()

    data object MicrophoneOpened : UserNotice()

    data object MicrophoneHearing : UserNotice()

    data object MicrophoneQuiet : UserNotice()

    data object MicrophoneUnavailable : UserNotice()

    data object WakeWordHeard : UserNotice()

    data object RecognitionTimeout : UserNotice()

    data object Stopped : UserNotice()

    data class GestureConfirmed(val gesture: HandGesture) : UserNotice()

    data class CameraStartFailed(val reason: String) : UserNotice()

    data class CameraStartTimedOut(val timeoutMs: Long) : UserNotice()

    data class RecognizerFailed(val reason: String) : UserNotice()

    data class SessionUnavailable(val otherPackages: List<String>) : UserNotice()

    data class PlaybackConfirmed(
        val command: PlaybackCommand,
        val alreadyInRequestedState: Boolean,
    ) : UserNotice()

    data class PlaybackConfirmationTimedOut(val command: PlaybackCommand) : UserNotice()

    data object ListenerAccessMissing : UserNotice()

    data class ActionUnsupported(val command: PlaybackCommand) : UserNotice()

    data class PlaybackFailed(
        val command: PlaybackCommand?,
        val detail: String,
    ) : UserNotice()

    data object SessionResponseTimedOut : UserNotice()

    data class ForegroundStartFailed(val detail: String) : UserNotice()

    data class ModelMissing(val assetPath: String) : UserNotice()

    data object CameraPermissionMissing : UserNotice()

    data object MicrophonePermissionMissing : UserNotice()

    data object NotificationListenerOff : UserNotice()

    data class WakeWordModelMissing(val files: String) : UserNotice()

    data object PlaybackStateUnreadable : UserNotice()

    data class ControlInterrupted(val detail: String) : UserNotice()
}
