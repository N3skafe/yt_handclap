package com.example.youtubegesturecontrol.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPalmShapeTest {
    @Test
    fun fourExtendedFingersCountAsOpenPalm() {
        assertTrue(OpenPalmShape.looksLikeOpenPalm(hand(extendedFingers = true)))
    }

    @Test
    fun curledFingersDoNotCountAsOpenPalm() {
        assertFalse(OpenPalmShape.looksLikeOpenPalm(hand(extendedFingers = false)))
    }

    @Test
    fun twoExtendedFingersDoNotCountAsOpenPalm() {
        val points = hand(extendedFingers = false).toMutableList()
        extend(points, tip = 8, pip = 6)
        extend(points, tip = 12, pip = 10)
        assertFalse(OpenPalmShape.looksLikeOpenPalm(points))
    }

    private fun hand(extendedFingers: Boolean): List<HandPoint> {
        val points = MutableList(21) { HandPoint(0f, 0.2f) }
        points[0] = HandPoint(0f, 0f)
        val fingers = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)
        fingers.forEach { (tip, pip) ->
            if (extendedFingers) {
                extend(points, tip, pip)
            } else {
                points[pip] = HandPoint(0f, 1f)
                points[tip] = HandPoint(0f, 0.4f)
            }
        }
        return points
    }

    private fun extend(points: MutableList<HandPoint>, tip: Int, pip: Int) {
        points[pip] = HandPoint(0f, 1f)
        points[tip] = HandPoint(0f, 2f)
    }
}
