package com.example.youtubegesturecontrol.gesture

/**
 * 모델 분류와 랜드마크 보정의 우선순위입니다.
 * MediaPipe 결과 객체를 받지 않아서, 카메라 없이 단위 테스트할 수 있습니다.
 *
 * 모델이 엄지 올림, 엄지 내림, 주먹을 0.7 이상으로 주면 그 분류를 유지합니다.
 * 그렇지 않을 때만 랜드마크로 엄지 올림과 손바닥을 살립니다.
 */
object GestureClassification {
    const val LANDMARK_THUMB_CONFIDENCE = 0.85f
    const val LANDMARK_PALM_CONFIDENCE = 0.85f

    private val trustedModelCategories = setOf("Thumb_Up", "Thumb_Down", "Closed_Fist")

    fun choose(
        modelCategoryName: String?,
        modelScore: Float,
        landmarksLookLikeThumb: Boolean,
        landmarksLookLikeOpenPalm: Boolean,
    ): Pair<String?, Float> {
        if (
            modelCategoryName in trustedModelCategories &&
            modelScore >= GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE
        ) {
            return modelCategoryName to modelScore
        }
        if (landmarksLookLikeThumb) {
            val thumbScore = if (modelCategoryName == "Thumb_Up") {
                maxOf(modelScore, LANDMARK_THUMB_CONFIDENCE)
            } else {
                LANDMARK_THUMB_CONFIDENCE
            }
            return "Thumb_Up" to thumbScore
        }
        if (landmarksLookLikeOpenPalm) {
            val palmScore = if (modelCategoryName == "Open_Palm") {
                maxOf(modelScore, LANDMARK_PALM_CONFIDENCE)
            } else {
                LANDMARK_PALM_CONFIDENCE
            }
            return "Open_Palm" to palmScore
        }
        return modelCategoryName to modelScore
    }
}
