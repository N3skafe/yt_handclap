package com.example.youtubegesturecontrol

import com.example.youtubegesturecontrol.gesture.HandGesture
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.YoutubeSessionSelector
import com.example.youtubegesturecontrol.service.ControlState

/**
 * 화면에 보이는 한국어 문장입니다. 상태 기계와 알림이 같은 문장을 쓰게 한곳에 모았습니다.
 */
object UserMessages {
    const val NO_RESULT_YET = "아직 결과가 없습니다."
    const val STARTING = "제어 서비스를 시작하는 중입니다."
    const val WAITING_FOR_WAKE_WORD =
        "영어로 SENA 라고 말해 주세요. 이 동안에는 카메라를 열지 않습니다."
    const val MICROPHONE_OPENED =
        "마이크가 열렸습니다. 영어로 SENA 라고 말한 뒤 잠깐 멈추세요."
    const val MICROPHONE_HEARING =
        "소리가 들어오고 있습니다. 영어로 SENA 라고 말한 뒤 잠깐 멈추세요."
    const val MICROPHONE_QUIET =
        "마이크는 열렸지만 소리가 거의 없습니다. 폰에 더 가까이에서 SENA 라고 말해 주세요."
    const val MICROPHONE_UNAVAILABLE =
        "마이크에서 소리를 읽지 못했습니다. 다른 앱이 마이크를 쓰고 있는지 확인해 주세요."
    const val WAKE_WORD_HEARD = "SENA를 들었습니다. 전면 카메라를 엽니다."
    const val RECOGNITION_TIMEOUT =
        "10초 안에 손바닥 또는 엄지 제스처가 확인되지 않았습니다. 명령을 보내지 않았습니다."
    const val STOPPED = "제어를 중지했습니다."

    fun stateLabel(state: ControlState): String {
        return when (state) {
            is ControlState.Stopped -> "중지됨"
            is ControlState.Starting -> "시작 중"
            is ControlState.WaitingForWakeWord -> "호출어 대기"
            is ControlState.OpeningCamera -> "카메라 여는 중"
            is ControlState.RecognizingGesture -> "제스처 확인 중"
            is ControlState.DispatchingCommand -> when (state.command) {
                PlaybackCommand.PAUSE -> "일시정지 요청 중"
                PlaybackCommand.PLAY -> "재생 요청 중"
            }
            is ControlState.CleaningUp -> "카메라 정리 중"
            is ControlState.Error -> "오류"
        }
    }

    fun gestureConfirmed(gesture: HandGesture): String {
        return when (gesture) {
            HandGesture.OPEN_PALM ->
                "손바닥을 확인했습니다. 카메라를 닫고 유튜브에 일시정지를 요청합니다."
            HandGesture.THUMB_UP ->
                "엄지를 확인했습니다. 카메라를 닫고 유튜브에 재생을 요청합니다."
        }
    }

    fun cameraStartFailed(reason: String): String {
        return "카메라를 열지 못했습니다. 실패 직후 자동으로 다시 열지 않습니다. 원인: $reason"
    }

    fun recognizerFailed(reason: String): String {
        return "제스처 인식기를 사용할 수 없습니다. 원인: $reason"
    }

    fun sessionUnavailable(otherPackages: List<String>): String {
        if (otherPackages.isEmpty()) {
            return "활성 미디어 세션이 없습니다. 유튜브에서 일반 동영상을 재생한 뒤 다시 시도하세요."
        }
        val others = otherPackages.joinToString(", ")
        return "유튜브(${YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME}) 세션이 없습니다. " +
            "다른 앱의 세션은 제어하지 않습니다: $others"
    }
}
