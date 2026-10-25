package com.example.youtubegesturecontrol.gesture

/**
 * 이 앱이 명령으로 받아들이는 손 모양입니다.
 *
 * enum class는 값이 정해진 목록입니다. Dart의 enum과 같고, 각 값에 함수를 둘 수 있습니다.
 * MediaPipe 모델은 더 많은 손 모양을 분류합니다. 여기서 두 가지만 고르는 것은
 * 모델을 두 클래스 모델로 다시 학습했다는 뜻이 아닙니다.
 */
enum class HandGesture {
    OPEN_PALM,
    THUMB_UP,
    ;

    companion object {
        fun fromModelName(modelCategoryName: String?): HandGesture? {
            return when (modelCategoryName) {
                "Open_Palm" -> OPEN_PALM
                "Thumb_Up" -> THUMB_UP
                else -> null
            }
        }
    }
}
