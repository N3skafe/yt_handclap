package com.example.youtubegesturecontrol.gesture

/**
 * 제스처 판정의 초기값입니다. 기기에서 맞춘 최종 성능 수치가 아닙니다.
 */
object GestureThresholds {
    const val MIN_CLASSIFICATION_CONFIDENCE = 0.7f

    /**
     * 손바닥은 폰에서 0.7을 안정적으로 넘기지 못했습니다.
     * 엄지보다 낮은 점수도 후보로 인정합니다.
     */
    const val OPEN_PALM_MIN_CONFIDENCE = 0.55f

    /** 같은 손 모양이 이 시간 동안 이어지면 명령으로 확정합니다. */
    const val STABLE_DURATION_MS = 250L

    /** 직전 인정 프레임과 이보다 멀리 떨어지면, 그 공백을 안정 시간으로 치지 않습니다. */
    const val MAX_ACCEPTED_FRAME_GAP_MS = 450L

    /** 손이 안 보인 상태가 이 시간 이상 계속되면 후보를 버립니다. */
    const val NO_HAND_RESET_MS = 350L

    /**
     * 카메라에 요청하는 분석 크기입니다.
     * 처음에는 640x480이었는데, 폰에서 CPU 추론이 한 장에 너무 오래 걸려
     * 같은 손 모양이 유지된 것으로 인정되기 전에 판정이 리셋됐습니다.
     */
    const val TARGET_ANALYSIS_WIDTH = 320
    const val TARGET_ANALYSIS_HEIGHT = 240

    /**
     * MediaPipe에 넣기 전 긴 변의 최대 픽셀입니다.
     * 카메라가 더 큰 해상도를 골라도 모델 입력은 이 크기로 줄입니다.
     */
    const val MODEL_MAX_EDGE_PX = 256

    /**
     * 모델 추론을 시도하는 최소 간격입니다.
     * 카메라 센서가 이 속도로 촬영한다는 뜻은 아닙니다.
     */
    const val ANALYSIS_MIN_INTERVAL_MS = 50L
}
