package com.example.youtubegesturecontrol.service

import com.example.youtubegesturecontrol.UserMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 서비스와 화면이 함께 보는 현재 상태입니다.
 *
 * StateFlow는 항상 현재 값을 하나 가지고, 새 구독자에게 그 값을 바로 전달합니다.
 * Dart의 BehaviorSubject나 ValueNotifier와 비슷합니다. 일반 Stream은 과거 값을
 * 다시 주지 않을 수 있습니다.
 *
 * 이 객체는 Application이 소유합니다. Activity 인스턴스를 저장하지 않습니다.
 */
class ControlStatusRepository {
    private val mutableSnapshot = MutableStateFlow(ControlSnapshot())

    val snapshot: StateFlow<ControlSnapshot> = mutableSnapshot.asStateFlow()

    fun update(step: MachineStep) {
        val current = mutableSnapshot.value
        mutableSnapshot.value = current.copy(
            state = step.state,
            recentResult = step.recentResult ?: current.recentResult,
        )
    }

    fun updateDetail(detail: String) {
        val current = mutableSnapshot.value
        if (current.state !is ControlState.WaitingForWakeWord) {
            return
        }
        mutableSnapshot.value = current.copy(recentResult = detail)
    }
}

/**
 * data class의 copy는 바뀐 필드만 교체한 새 스냅샷을 만듭니다.
 * 화면은 이 객체를 읽고, 서비스만 새 값으로 바꿉니다.
 */
data class ControlSnapshot(
    val state: ControlState = ControlState.Stopped,
    val recentResult: String = UserMessages.NO_RESULT_YET,
)
