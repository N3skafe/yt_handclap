package com.example.youtubegesturecontrol.gesture

/**
 * 한 프레임의 분류 결과를 바로 명령으로 바꾸지 않고, 같은 손 모양이 유지되는지만 판단합니다.
 * 카메라나 MediaPipe에 의존하지 않아서 단위 테스트로 확인할 수 있습니다.
 */
class GestureStabilityTracker(
    private val minConfidence: Float = GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE,
    private val stableDurationMs: Long = GestureThresholds.STABLE_DURATION_MS,
    private val maxAcceptedFrameGapMs: Long = GestureThresholds.MAX_ACCEPTED_FRAME_GAP_MS,
    private val noHandResetMs: Long = GestureThresholds.NO_HAND_RESET_MS,
) {
    private var candidate: HandGesture? = null
    private var stableSinceElapsedMs: Long = 0L
    private var lastAcceptedElapsedMs: Long = 0L
    private var confirmed: HandGesture? = null

    fun reset() {
        candidate = null
        stableSinceElapsedMs = 0L
        lastAcceptedElapsedMs = 0L
        confirmed = null
    }

    /**
     * 확정된 제스처를 한 번만 돌려줍니다. 아직 불안하거나, 이미 확정했다면 null입니다.
     */
    fun onSample(sample: GestureSample): HandGesture? {
        if (confirmed != null) {
            return null
        }
        if (candidate != null && sample.elapsedMs < lastAcceptedElapsedMs) {
            // 늦게 도착한 옛 프레임이 이미 흐른 안정 시간을 늘리면 안 됩니다.
            return null
        }

        val accepted = acceptedGesture(sample)
        if (accepted == null) {
            handleRejectedSample(sample)
            return null
        }

        val current = candidate
        val gapMs = sample.elapsedMs - lastAcceptedElapsedMs
        val startsNewWindow = current == null || current != accepted || gapMs > maxAcceptedFrameGapMs
        if (startsNewWindow) {
            candidate = accepted
            stableSinceElapsedMs = sample.elapsedMs
            lastAcceptedElapsedMs = sample.elapsedMs
            return null
        }

        lastAcceptedElapsedMs = sample.elapsedMs
        if (sample.elapsedMs - stableSinceElapsedMs >= stableDurationMs) {
            confirmed = accepted
            return accepted
        }
        return null
    }

    private fun acceptedGesture(sample: GestureSample): HandGesture? {
        val gesture = HandGesture.fromModelName(sample.modelCategoryName) ?: return null
        if (sample.confidence < minimumConfidence(gesture)) {
            return null
        }
        return gesture
    }

    private fun minimumConfidence(gesture: HandGesture): Float {
        return when (gesture) {
            HandGesture.OPEN_PALM -> GestureThresholds.OPEN_PALM_MIN_CONFIDENCE
            HandGesture.THUMB_UP -> minConfidence
        }
    }

    private fun handleRejectedSample(sample: GestureSample) {
        val current = candidate ?: return
        val named = HandGesture.fromModelName(sample.modelCategoryName)
        if (named != null && named != current) {
            candidate = null
            return
        }
        if (isDifferentPose(sample.modelCategoryName)) {
            candidate = null
            return
        }
        // 같은 손의 낮은 점수, None, 손이 안 보인 프레임은 잠시 건너뜁니다.
        // 손바닥은 프레임마다 점수가 흔들려서, 그때마다 창을 지우면 확정이 거의 안 됩니다.
        if (sample.elapsedMs - lastAcceptedElapsedMs >= noHandResetMs) {
            candidate = null
        }
    }

    private fun isDifferentPose(modelCategoryName: String?): Boolean {
        if (modelCategoryName == null || modelCategoryName == "None") {
            return false
        }
        return HandGesture.fromModelName(modelCategoryName) == null
    }
}
