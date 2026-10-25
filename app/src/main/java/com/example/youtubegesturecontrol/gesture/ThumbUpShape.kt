package com.example.youtubegesturecontrol.gesture

import kotlin.math.hypot

/**
 * 분류 모델은 손등 쪽 엄지를 주로 맞춥니다.
 * 날이나 손바닥이 카메라를 봐도, 네 손가락이 접히고 엄지만 뻗었으면 같은 재생 제스처로 봅니다.
 * 화면의 위아래가 아니라 관절 사이 거리만 써서, 손이 돌아가 있어도 같은 판정이 됩니다.
 */
object ThumbUpShape {
    fun looksLikeThumbUp(points: List<HandPoint>): Boolean {
        if (points.size < LANDMARK_COUNT) {
            return false
        }
        if (!CURLED_FINGERS.all { (tip, pip) -> isCurled(points, tip, pip) }) {
            return false
        }
        if (!isThumbExtended(points)) {
            return false
        }
        return thumbStandsAwayFromFist(points)
    }

    private fun isCurled(points: List<HandPoint>, tipIndex: Int, pipIndex: Int): Boolean {
        val wrist = points[WRIST]
        val tipDistance = distance(points[tipIndex], wrist)
        val pipDistance = distance(points[pipIndex], wrist)
        return tipDistance <= pipDistance * CURL_RATIO
    }

    private fun isThumbExtended(points: List<HandPoint>): Boolean {
        val wrist = points[WRIST]
        val mcp = points[THUMB_MCP]
        val ip = points[THUMB_IP]
        val tip = points[THUMB_TIP]
        return distance(tip, wrist) > distance(ip, wrist) * THUMB_EXTENSION_RATIO &&
            distance(tip, mcp) > distance(ip, mcp) * THUMB_EXTENSION_RATIO
    }

    private fun thumbStandsAwayFromFist(points: List<HandPoint>): Boolean {
        val palm = palmCenter(points)
        val thumbDistance = distance(points[THUMB_TIP], palm)
        val fistDistance = CURLED_FINGERS.maxOf { (tip, _) -> distance(points[tip], palm) }
        return thumbDistance > fistDistance * PROTRUSION_RATIO
    }

    private fun palmCenter(points: List<HandPoint>): HandPoint {
        val wrist = points[WRIST]
        val indexKnuckle = points[INDEX_MCP]
        val pinkyKnuckle = points[PINKY_MCP]
        return HandPoint(
            x = (wrist.x + indexKnuckle.x + pinkyKnuckle.x) / 3f,
            y = (wrist.y + indexKnuckle.y + pinkyKnuckle.y) / 3f,
            z = (wrist.z + indexKnuckle.z + pinkyKnuckle.z) / 3f,
        )
    }

    private fun distance(first: HandPoint, second: HandPoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        val dz = first.z - second.z
        return hypot(hypot(dx, dy), dz)
    }

    private const val WRIST = 0
    private const val THUMB_MCP = 2
    private const val THUMB_IP = 3
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5
    private const val PINKY_MCP = 17
    private const val LANDMARK_COUNT = 21
    private const val CURL_RATIO = 1.05f
    private const val THUMB_EXTENSION_RATIO = 1.2f
    private const val PROTRUSION_RATIO = 1.2f
    private val CURLED_FINGERS = listOf(
        8 to 6,
        12 to 10,
        16 to 14,
        20 to 18,
    )
}
