package com.example.youtubegesturecontrol.service

import com.example.youtubegesturecontrol.UserNotice
import com.example.youtubegesturecontrol.media.PlaybackCommand

/**
 * 제어 서비스가 한 번에 하나만 가지는 상태입니다.
 *
 * sealed class는 허용된 하위 타입만 존재하는 상속입니다. Dart 3의 sealed class와 비슷합니다.
 * boolean 여러 개를 조합하면 "카메라 여는 중"이면서 "이미 중지됨" 같은 모순이 생길 수 있어서,
 * 지금 단계를 이 계층의 값 하나로만 표현합니다.
 *
 * data class인 단계는 그 단계에만 필요한 값을 함께 가집니다.
 * copy()는 일부 필드만 바꾼 새 객체를 만듭니다. Dart의 copyWith와 같은 역할입니다.
 */
sealed class ControlState {
    data object Stopped : ControlState()

    data object Starting : ControlState()

    data object WaitingForWakeWord : ControlState()

    data class OpeningCamera(
        val sessionId: Long,
        val returnToWakeWord: Boolean,
    ) : ControlState()

    data class RecognizingGesture(
        val sessionId: Long,
        val returnToWakeWord: Boolean,
    ) : ControlState()

    data class CleaningUp(
        val sessionId: Long,
        val returnToWakeWord: Boolean,
        val pendingCommand: PlaybackCommand?,
        val fatal: Boolean,
        val notice: UserNotice,
    ) : ControlState()

    data class DispatchingCommand(
        val sessionId: Long,
        val command: PlaybackCommand,
        val returnToWakeWord: Boolean,
    ) : ControlState()

    data class Error(val notice: UserNotice) : ControlState()
}

fun ControlState.isServiceActive(): Boolean {
    return when (this) {
        is ControlState.Stopped, is ControlState.Error -> false
        else -> true
    }
}

/**
 * 상태 기계가 서비스에게 시키는 부수 효과입니다.
 * 기계 자체는 카메라나 마이크를 직접 만지지 않습니다.
 */
sealed interface ControlEffect {
    data object SuspendWakeWord : ControlEffect

    data object ResumeWakeWord : ControlEffect

    data object ReleaseWakeWord : ControlEffect

    data class OpenCamera(val sessionId: Long) : ControlEffect

    data class CloseCamera(val sessionId: Long) : ControlEffect

    data class ArmCameraStartTimeout(val sessionId: Long) : ControlEffect

    data class ArmRecognitionTimeout(val sessionId: Long) : ControlEffect

    data class CancelSessionTimers(val sessionId: Long) : ControlEffect

    data object CancelAllTimers : ControlEffect

    data class DispatchCommand(
        val sessionId: Long,
        val command: PlaybackCommand,
    ) : ControlEffect

    data object StopService : ControlEffect

    data object ShowWakeWordHeard : ControlEffect
}

data class MachineStep(
    val state: ControlState,
    val effects: List<ControlEffect> = emptyList(),
    val recentResult: UserNotice? = null,
)
