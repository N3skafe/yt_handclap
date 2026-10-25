package com.example.youtubegesturecontrol.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ThumbUpShapeTest {
    @Test
    fun extendedThumbWithCurledFingersCountsFromAnyRotation() {
        val thumbUp = thumbUpHand()
        assertTrue(ThumbUpShape.looksLikeThumbUp(thumbUp))
        assertTrue(ThumbUpShape.looksLikeThumbUp(rotate(thumbUp, 90.0)))
        assertTrue(ThumbUpShape.looksLikeThumbUp(rotate(thumbUp, 180.0)))
    }

    @Test
    fun thumbPointingTowardCameraStillCounts() {
        val points = thumbUpHand().toMutableList()
        points[4] = HandPoint(points[2].x, points[2].y, z = -0.45f)
        assertTrue(ThumbUpShape.looksLikeThumbUp(points))
    }

    @Test
    fun openPalmIsNotThumbUp() {
        assertFalse(ThumbUpShape.looksLikeThumbUp(openPalm()))
    }

    @Test
    fun curledThumbIsNotThumbUp() {
        val points = thumbUpHand().toMutableList()
        points[4] = HandPoint(0.1f, 0.2f)
        assertFalse(ThumbUpShape.looksLikeThumbUp(points))
    }

    private fun thumbUpHand(): List<HandPoint> {
        val points = MutableList(21) { HandPoint(0.2f, 0.2f) }
        points[0] = HandPoint(0f, 0f)
        points[2] = HandPoint(0.08f, 0.12f)
        points[3] = HandPoint(0.06f, 0.28f)
        points[4] = HandPoint(0.04f, 0.5f)
        curl(points, mcp = 5, pip = 6, tip = 8, x = 0.16f)
        curl(points, mcp = 9, pip = 10, tip = 12, x = 0.24f)
        curl(points, mcp = 13, pip = 14, tip = 16, x = 0.30f)
        curl(points, mcp = 17, pip = 18, tip = 20, x = 0.36f)
        return points
    }

    private fun curl(points: MutableList<HandPoint>, mcp: Int, pip: Int, tip: Int, x: Float) {
        points[mcp] = HandPoint(x, 0.25f)
        points[pip] = HandPoint(x, 0.42f)
        points[tip] = HandPoint(x, 0.27f)
    }

    private fun openPalm(): List<HandPoint> {
        val points = MutableList(21) { HandPoint(0f, 0.2f) }
        points[0] = HandPoint(0f, 0f)
        listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).forEach { (tip, pip) ->
            points[pip] = HandPoint(0f, 1f)
            points[tip] = HandPoint(0f, 2f)
        }
        return points
    }

    private fun rotate(points: List<HandPoint>, degrees: Double): List<HandPoint> {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        return points.map { point ->
            HandPoint(
                x = point.x * cosine - point.y * sine,
                y = point.x * sine + point.y * cosine,
                z = point.z,
            )
        }
    }
}
