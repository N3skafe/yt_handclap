package com.example.youtubegesturecontrol.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassificationTest {
    @Test
    fun confidentThumbDownKeepsTheModelLabel() {
        val chosen = GestureClassification.choose(
            modelCategoryName = "Thumb_Down",
            modelScore = 0.9f,
            landmarksLookLikeThumb = true,
            landmarksLookLikeOpenPalm = false,
        )
        assertEquals("Thumb_Down" to 0.9f, chosen)
    }

    @Test
    fun confidentClosedFistKeepsTheModelLabel() {
        val chosen = GestureClassification.choose(
            modelCategoryName = "Closed_Fist",
            modelScore = 0.9f,
            landmarksLookLikeThumb = true,
            landmarksLookLikeOpenPalm = false,
        )
        assertEquals("Closed_Fist" to 0.9f, chosen)
    }

    @Test
    fun weakThumbDownUsesLandmarkThumbUp() {
        val chosen = GestureClassification.choose(
            modelCategoryName = "Thumb_Down",
            modelScore = 0.5f,
            landmarksLookLikeThumb = true,
            landmarksLookLikeOpenPalm = false,
        )
        assertEquals("Thumb_Up" to GestureClassification.LANDMARK_THUMB_CONFIDENCE, chosen)
    }

    @Test
    fun missingModelUsesLandmarkThumbUp() {
        val chosen = GestureClassification.choose(
            modelCategoryName = null,
            modelScore = 0f,
            landmarksLookLikeThumb = true,
            landmarksLookLikeOpenPalm = false,
        )
        assertEquals("Thumb_Up" to GestureClassification.LANDMARK_THUMB_CONFIDENCE, chosen)
    }

    @Test
    fun noneLabelUsesLandmarkThumbUp() {
        val chosen = GestureClassification.choose(
            modelCategoryName = "None",
            modelScore = 0.4f,
            landmarksLookLikeThumb = true,
            landmarksLookLikeOpenPalm = true,
        )
        assertEquals("Thumb_Up" to GestureClassification.LANDMARK_THUMB_CONFIDENCE, chosen)
    }

    @Test
    fun confidentThumbUpIgnoresLandmarks() {
        val chosen = GestureClassification.choose(
            modelCategoryName = "Thumb_Up",
            modelScore = GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE,
            landmarksLookLikeThumb = false,
            landmarksLookLikeOpenPalm = true,
        )
        assertEquals("Thumb_Up" to GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE, chosen)
    }

    @Test
    fun openPalmLandmarkCorrectionStillAppliesWhenThumbDoesNot() {
        val chosen = GestureClassification.choose(
            modelCategoryName = "None",
            modelScore = 0.2f,
            landmarksLookLikeThumb = false,
            landmarksLookLikeOpenPalm = true,
        )
        assertEquals("Open_Palm" to GestureClassification.LANDMARK_PALM_CONFIDENCE, chosen)
    }
}
