package com.example.youtubegesturecontrol.gesture

/**
 * 카메라 한 프레임에서 읽은 최상위 제스처입니다.
 *
 * data class는 필드를 기준으로 equals, hashCode, toString, copy를 만들어 줍니다.
 * Dart에서 필드를 직접 비교하거나 copyWith를 만들던 클래스에 가깝습니다.
 * [modelCategoryName]이 null이면 그 프레임에서 손을 찾지 못한 것입니다.
 * 시간은 벽시계가 아니라 단조 증가 시계의 밀리초입니다.
 */
data class GestureSample(
    val elapsedMs: Long,
    val modelCategoryName: String?,
    val confidence: Float,
)
