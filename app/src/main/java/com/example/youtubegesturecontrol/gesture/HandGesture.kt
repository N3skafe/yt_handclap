package com.example.youtubegesturecontrol.gesture

/**
 * 이 앱이 구분하는 손 모양입니다.
 *
 * 손바닥은 일시정지, 엄지 올림은 재생, 엄지 내림은 다음, 주먹은 이전입니다.
 * MediaPipe 모델은 더 많은 손 모양을 분류합니다. 여기서 고르는 것은
 * 모델을 다시 학습했다는 뜻이 아닙니다.
 */
enum class HandGesture {
    OPEN_PALM,
    THUMB_UP,
    THUMB_DOWN,
    CLOSED_FIST,
    ;

    companion object {
        fun fromModelName(modelCategoryName: String?): HandGesture? {
            return when (modelCategoryName) {
                "Open_Palm" -> OPEN_PALM
                "Thumb_Up" -> THUMB_UP
                "Thumb_Down" -> THUMB_DOWN
                "Closed_Fist" -> CLOSED_FIST
                else -> null
            }
        }
    }
}
