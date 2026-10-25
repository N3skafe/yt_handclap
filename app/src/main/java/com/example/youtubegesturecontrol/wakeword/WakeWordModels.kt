package com.example.youtubegesturecontrol.wakeword

/**
 * 호출어 모델이 앱 안에 들어 있는지 확인한 결과입니다.
 * 계정이나 API 키는 사용하지 않습니다.
 */
data class WakeWordStatus(
    val configured: Boolean,
    val phraseLabel: String,
    val detail: String,
)

/**
 * sherpa-onnx 영어 키워드 모델이 듣는 호출어 하나와 필요한 파일 목록입니다.
 * 이 파일은 안드로이드 API를 쓰지 않아서 단위 테스트할 수 있습니다.
 */
object WakeWordModels {
    const val PHRASE = "SENA"
    const val DIRECTORY = "kws"

    /**
     * 배포된 인코더는 16프레임 청크에 마지막 프레임을 한 번 더 붙인 뒤
     * 16프레임짜리 reshape로 들어가서, 제어를 시작하면 프로세스가 죽습니다.
     * 에셋에 넣은 파일은 그 여분 프레임 연결만 뺀 것입니다.
     */
    const val ENCODER = "$DIRECTORY/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val DECODER = "$DIRECTORY/decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
    const val JOINER = "$DIRECTORY/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val TOKENS = "$DIRECTORY/tokens.txt"
    const val KEYWORDS = "$DIRECTORY/keywords.txt"

    val requiredAssets: List<String> = listOf(
        ENCODER,
        DECODER,
        JOINER,
        TOKENS,
        KEYWORDS,
    )

    fun status(missingAssets: List<String>): WakeWordStatus {
        if (missingAssets.isNotEmpty()) {
            return WakeWordStatus(
                configured = false,
                phraseLabel = "설정되지 않음",
                detail = "호출어 모델 파일이 없습니다: ${missingAssets.joinToString(", ")}",
            )
        }
        return WakeWordStatus(
            configured = true,
            phraseLabel = PHRASE,
            detail = "영어 호출어입니다. \"$PHRASE\" 라고 말하면 카메라가 열립니다. " +
                "음성은 기기 안에서만 확인하고 올리지 않습니다.",
        )
    }
}
