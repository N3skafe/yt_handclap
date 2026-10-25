package com.example.youtubegesturecontrol.gesture

import kotlin.math.hypot

/**
 * 분류 모델이 손바닥을 None으로 두는 경우가 많아서, 손가락이 펴졌는지만 따로 봅니다.
 * 좌표 21개는 MediaPipe 손 랜드마크 순서입니다. 카메라 없이 테스트할 수 있습니다.
 */
object OpenPalmShape {
    fun looksLikeOpenPalm(points: List<HandPoint>): Boolean {
        if (points.size < LANDMARK_COUNT) {
            return false
        }
        return EXTENDED_FINGERS.all { (tip, pip) ->
            isExtended(points, tip, pip)
        }
    }

    private fun isExtended(points: List<HandPoint>, tipIndex: Int, pipIndex: Int): Boolean {
        val wrist = points[WRIST]
        val tipDistance = distance(points[tipIndex], wrist)
        val pipDistance = distance(points[pipIndex], wrist)
        return tipDistance > pipDistance * EXTENSION_RATIO
    }

    private fun distance(first: HandPoint, second: HandPoint): Float {
        return hypot(first.x - second.x, first.y - second.y)
    }

    private const val WRIST = 0
    private const val LANDMARK_COUNT = 21
    private const val EXTENSION_RATIO = 1.15f
    private val EXTENDED_FINGERS = listOf(
        8 to 6,
        12 to 10,
        16 to 14,
        20 to 18,
    )
}

data class HandPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)
