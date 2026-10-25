package com.example.youtubegesturecontrol.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureStabilityTrackerTest {
    private val tracker = GestureStabilityTracker()
    private val stepMs = 200L

    @Test
    fun singleFrameDoesNotConfirm() {
        val result = tracker.onSample(sample(0L, "Open_Palm", 0.95f))
        assertNull(result)
    }

    @Test
    fun stableOpenPalmConfirmsAfterDuration() {
        var confirmed: HandGesture? = null
        var time = 0L
        tracker.onSample(sample(time, "Open_Palm", 0.9f))
        while (time < GestureThresholds.STABLE_DURATION_MS) {
            time += stepMs
            confirmed = tracker.onSample(sample(time, "Open_Palm", 0.9f))
        }
        assertEquals(HandGesture.OPEN_PALM, confirmed)
    }

    @Test
    fun candidateChangeRestartsWindow() {
        tracker.onSample(sample(0L, "Open_Palm", 0.9f))
        tracker.onSample(sample(200L, "Thumb_Up", 0.9f))
        assertNull(tracker.onSample(sample(400L, "Thumb_Up", 0.9f)))
        val confirmed = tracker.onSample(
            sample(200L + GestureThresholds.STABLE_DURATION_MS, "Thumb_Up", 0.9f),
        )
        assertEquals(HandGesture.THUMB_UP, confirmed)
    }

    @Test
    fun lowConfidenceSameGestureDoesNotWipeTheWindow() {
        tracker.onSample(sample(0L, "Open_Palm", 0.9f))
        tracker.onSample(sample(80L, "Open_Palm", 0.4f))
        assertNull(tracker.onSample(sample(160L, "Open_Palm", 0.9f)))
        val confirmed = tracker.onSample(
            sample(GestureThresholds.STABLE_DURATION_MS, "Open_Palm", 0.9f),
        )
        assertEquals(HandGesture.OPEN_PALM, confirmed)
    }

    @Test
    fun noneLabelDoesNotImmediatelyResetPalm() {
        tracker.onSample(sample(0L, "Open_Palm", 0.9f))
        tracker.onSample(sample(80L, "None", 0.8f))
        assertNull(tracker.onSample(sample(160L, "Open_Palm", 0.9f)))
        val confirmed = tracker.onSample(
            sample(GestureThresholds.STABLE_DURATION_MS, "Open_Palm", 0.9f),
        )
        assertEquals(HandGesture.OPEN_PALM, confirmed)
    }

    @Test
    fun confidenceAtThresholdCanConfirm() {
        var time = 0L
        tracker.onSample(sample(time, "Open_Palm", GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE))
        var confirmed: HandGesture? = null
        while (confirmed == null && time <= GestureThresholds.STABLE_DURATION_MS + stepMs) {
            time += stepMs
            confirmed = tracker.onSample(
                sample(time, "Open_Palm", GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE),
            )
        }
        assertEquals(HandGesture.OPEN_PALM, confirmed)
    }

    @Test
    fun longGapDoesNotCountAsStableEvidence() {
        tracker.onSample(sample(0L, "Open_Palm", 0.95f))
        val tooLate = tracker.onSample(
            sample(GestureThresholds.MAX_ACCEPTED_FRAME_GAP_MS + 1L, "Open_Palm", 0.95f),
        )
        assertNull(tooLate)
    }

    @Test
    fun shortNoHandGapKeepsCandidate() {
        tracker.onSample(sample(0L, "Open_Palm", 0.95f))
        tracker.onSample(sample(80L, null, 0f))
        assertNull(tracker.onSample(sample(160L, "Open_Palm", 0.95f)))
        val confirmed = tracker.onSample(
            sample(GestureThresholds.STABLE_DURATION_MS, "Open_Palm", 0.95f),
        )
        assertEquals(HandGesture.OPEN_PALM, confirmed)
    }

    @Test
    fun prolongedNoHandResetsCandidate() {
        tracker.onSample(sample(0L, "Open_Palm", 0.95f))
        tracker.onSample(sample(GestureThresholds.NO_HAND_RESET_MS, null, 0f))
        assertNull(tracker.onSample(sample(GestureThresholds.NO_HAND_RESET_MS + 100L, "Open_Palm", 0.95f)))
    }

    @Test
    fun otherGestureResetsImmediately() {
        tracker.onSample(sample(0L, "Open_Palm", 0.95f))
        tracker.onSample(sample(200L, "Closed_Fist", 0.99f))
        assertNull(tracker.onSample(sample(400L, "Open_Palm", 0.95f)))
        assertNull(tracker.onSample(sample(600L, "Open_Palm", 0.95f)))
    }

    @Test
    fun secondConfirmationIsIgnoredUntilReset() {
        confirmPalm()
        val second = tracker.onSample(sample(2_000L, "Thumb_Up", 0.99f))
        assertNull(second)
    }

    @Test
    fun outOfOrderSampleDoesNotExtendEvidence() {
        tracker.onSample(sample(500L, "Open_Palm", 0.95f))
        assertNull(tracker.onSample(sample(100L, "Open_Palm", 0.95f)))
        assertNull(
            tracker.onSample(
                sample(500L + GestureThresholds.MAX_ACCEPTED_FRAME_GAP_MS + 50L, "Open_Palm", 0.95f),
            ),
        )
    }

    @Test
    fun lowercaseModelNameIsNotAccepted() {
        assertNull(tracker.onSample(sample(0L, "open_palm", 0.99f)))
        assertNull(tracker.onSample(sample(600L, "open_palm", 0.99f)))
    }

    private fun confirmPalm() {
        var time = 0L
        tracker.onSample(sample(time, "Open_Palm", 0.95f))
        var confirmed: HandGesture? = null
        while (confirmed == null && time < 2_000L) {
            time += stepMs
            confirmed = tracker.onSample(sample(time, "Open_Palm", 0.95f))
        }
        assertEquals(HandGesture.OPEN_PALM, confirmed)
    }

    private fun sample(elapsedMs: Long, name: String?, confidence: Float): GestureSample {
        return GestureSample(elapsedMs = elapsedMs, modelCategoryName = name, confidence = confidence)
    }
}
