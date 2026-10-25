package com.example.youtubegesturecontrol.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.youtubegesturecontrol.UserMessages
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * sherpa-onnx로 영어 호출어 "SENA" 하나만 듣습니다.
 *
 * 이 라이브러리는 마이크를 직접 열지 않으므로, 16kHz 모노 오디오를 그대로 넣습니다.
 * 음량을 억지로 키우거나 48kHz를 다시 표본화하면 이 모델의 정확도가 떨어집니다.
 * 콜백은 마이크 스레드에서 오므로 메인 스레드로 올린 뒤에만 상태 기계를 호출합니다.
 * 손을 보는 동안에는 녹음을 멈추지만, 불러 둔 모델은 서비스가 끝날 때까지 유지합니다.
 * 네트워크로 음성을 보내지 않습니다. 문장 전체를 받아 적는 음성 인식은 하지 않습니다.
 */
class SherpaWakeWordDetector(
    context: Context,
) : WakeWordDetector {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineLock = Any()

    private var onDetected: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onDetail: ((String) -> Unit)? = null
    private var lastDetail: String? = null
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var inputSampleRate = SAMPLE_RATE

    @Volatile
    private var listening = false

    @Volatile
    private var acceptDetections = false

    override fun prepare(
        onDetected: () -> Unit,
        onError: (String) -> Unit,
        onDetail: (String) -> Unit,
    ) {
        this.onDetected = onDetected
        this.onError = onError
        this.onDetail = onDetail
        synchronized(engineLock) {
            if (spotter != null) {
                return
            }
            spotter = KeywordSpotter(
                appContext.assets,
                keywordSpotterConfig(),
            )
            stream = spotter?.createStream()
        }
    }

    override fun startListening() {
        if (listening) {
            return
        }
        if (spotter == null || stream == null) {
            throw IllegalStateException("호출어 엔진이 준비되지 않았습니다.")
        }
        acceptDetections = true
        listening = true
        captureThread = Thread(::captureLoop, "wake-word").also { thread ->
            thread.start()
        }
    }

    override fun suspendListening() {
        acceptDetections = false
        stopCapture()
    }

    override fun release() {
        acceptDetections = false
        val thread = stopCapture()
        if (thread != null && thread.isAlive) {
            Log.e(TAG, "호출어 스레드가 모델 계산 중이라 엔진 해제를 건너뜁니다.")
            return
        }
        synchronized(engineLock) {
            stream?.release()
            stream = null
            spotter?.release()
            spotter = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        Log.i(TAG, "호출어 녹음 스레드를 시작했습니다.")
        val opened = openRecorder()
        if (opened == null) {
            listening = false
            postDetail(UserMessages.MICROPHONE_UNAVAILABLE)
            postError(UserMessages.MICROPHONE_UNAVAILABLE)
            return
        }
        audioRecord = opened.recorder
        inputSampleRate = opened.recorder.sampleRate
        Log.i(
            TAG,
            "마이크를 열었습니다. source=${opened.sourceName} 요청=${opened.requestedRate} 실제=$inputSampleRate",
        )
        postDetail(UserMessages.MICROPHONE_OPENED)
        val buffer = ShortArray(frameSamples(inputSampleRate))
        val samples = FloatArray(buffer.size)
        var windowPeak = 0
        var lastLevelLogMs = SystemClock.elapsedRealtime()
        var quietWindows = 0
        var heardSpeech = false
        try {
            while (listening) {
                val read = opened.recorder.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    Log.w(TAG, "마이크 읽기 결과=$read")
                    continue
                }
                var framePeak = 0
                for (index in 0 until read) {
                    samples[index] = buffer[index] / PCM_SCALE
                    val magnitude = if (buffer[index] < 0) -buffer[index].toInt() else buffer[index].toInt()
                    if (magnitude > framePeak) {
                        framePeak = magnitude
                    }
                }
                if (framePeak > windowPeak) {
                    windowPeak = framePeak
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastLevelLogMs >= LEVEL_LOG_INTERVAL_MS) {
                    if (windowPeak >= SPEECH_PEAK) {
                        heardSpeech = true
                        quietWindows = 0
                        postDetail(UserMessages.MICROPHONE_HEARING)
                    } else if (!heardSpeech) {
                        quietWindows += 1
                        if (quietWindows >= QUIET_WINDOWS) {
                            postDetail(UserMessages.MICROPHONE_QUIET)
                        }
                    }
                    windowPeak = 0
                    lastLevelLogMs = now
                }
                val detected = try {
                    synchronized(engineLock) {
                        if (!listening) {
                            false
                        } else {
                            decode(samples, read)
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "호출어 추론 실패", error)
                    false
                }
                if (detected && acceptDetections) {
                    acceptDetections = false
                    val callback = onDetected
                    mainHandler.post {
                        if (callback != null) {
                            callback()
                        }
                    }
                }
            }
        } finally {
            releaseRecorder()
        }
    }

    private fun decode(samples: FloatArray, read: Int): Boolean {
        val currentStream = stream ?: return false
        val currentSpotter = spotter ?: return false
        currentStream.acceptWaveform(samples.copyOf(read), inputSampleRate)
        while (currentSpotter.isReady(currentStream)) {
            currentSpotter.decode(currentStream)
        }
        val keyword = currentSpotter.getResult(currentStream).keyword
        if (keyword.isBlank()) {
            return false
        }
        Log.i(TAG, "호출어를 들었습니다: $keyword")
        currentSpotter.reset(currentStream)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): OpenedRecorder? {
        var fallback: OpenedRecorder? = null
        for (candidate in CANDIDATES) {
            if (!listening) {
                fallback?.recorder?.let(::abandonRecorder)
                return null
            }
            val opened = startCandidate(candidate) ?: continue
            if (opened.recorder.sampleRate == SAMPLE_RATE) {
                fallback?.recorder?.let(::abandonRecorder)
                Log.i(
                    TAG,
                    "마이크를 골랐습니다. source=${candidate.sourceName} 실제=${opened.recorder.sampleRate}",
                )
                return opened
            }
            Log.w(
                TAG,
                "16kHz가 아니라서 예비로 둡니다. source=${candidate.sourceName} 실제=${opened.recorder.sampleRate}",
            )
            if (fallback == null) {
                fallback = opened
            } else {
                abandonRecorder(opened.recorder)
            }
        }
        if (fallback != null) {
            Log.i(
                TAG,
                "16kHz 마이크가 없어 ${fallback.recorder.sampleRate}Hz를 사용합니다. source=${fallback.sourceName}",
            )
        }
        return fallback
    }

    @SuppressLint("MissingPermission")
    private fun startCandidate(candidate: RecorderCandidate): OpenedRecorder? {
        val minimumBuffer = AudioRecord.getMinBufferSize(
            candidate.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) {
            Log.w(TAG, "지원하지 않는 녹음 설정 source=${candidate.sourceName} rate=${candidate.sampleRate}")
            return null
        }
        val recorder = AudioRecord(
            candidate.source,
            candidate.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimumBuffer, candidate.sampleRate * BYTES_PER_SAMPLE),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        try {
            recorder.startRecording()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "마이크 시작 실패 source=${candidate.sourceName}", error)
            recorder.release()
            return null
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            return null
        }
        return OpenedRecorder(recorder, candidate.sampleRate, candidate.sourceName)
    }

    private fun abandonRecorder(recorder: AudioRecord) {
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (error: IllegalStateException) {
            Log.w(TAG, "막힌 마이크를 멈추지 못했습니다.", error)
        }
        try {
            recorder.release()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "막힌 마이크를 닫지 못했습니다.", error)
        }
    }

    /**
     * 녹음을 연 스레드가 아닌 곳에서 AudioRecord를 닫으면 일부 폰에서 프로세스가 죽는다.
     * 여기서는 루프만 끝내고, 닫기는 녹음 스레드의 finally에 맡긴다.
     */
    private fun stopCapture(): Thread? {
        listening = false
        val thread = captureThread
        captureThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            thread.join(THREAD_JOIN_MS)
        }
        return thread
    }

    private fun releaseRecorder() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder == null) {
            return
        }
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (error: IllegalStateException) {
            Log.w(TAG, "마이크 중지 실패", error)
        }
        recorder.release()
    }

    private fun postDetail(detail: String) {
        if (detail == lastDetail) {
            return
        }
        lastDetail = detail
        val callback = onDetail ?: return
        mainHandler.post { callback(detail) }
    }

    private fun postError(message: String) {
        val callback = onError ?: return
        mainHandler.post { callback(message) }
    }

    private fun keywordSpotterConfig(): KeywordSpotterConfig {
        return KeywordSpotterConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = FEATURE_DIM,
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = WakeWordModels.ENCODER,
                    decoder = WakeWordModels.DECODER,
                    joiner = WakeWordModels.JOINER,
                ),
                tokens = WakeWordModels.TOKENS,
                numThreads = 2,
                provider = "cpu",
                modelType = "zipformer2",
            ),
            keywordsFile = WakeWordModels.KEYWORDS,
            maxActivePaths = 8,
            keywordsScore = KEYWORD_SCORE,
            keywordsThreshold = KEYWORD_THRESHOLD,
            // 0이면 단어가 끝나는 공백을 더 기다리지 않고 바로 확정합니다.
            numTrailingBlanks = 0,
        )
    }

    private data class RecorderCandidate(
        val source: Int,
        val sampleRate: Int,
        val sourceName: String,
    )

    private data class OpenedRecorder(
        val recorder: AudioRecord,
        val requestedRate: Int,
        val sourceName: String,
    )

    companion object {
        private const val TAG = "WakeWord"
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val PCM_SCALE = 32768.0f
        private const val BYTES_PER_SAMPLE = 2
        private const val THREAD_JOIN_MS = 3000L
        private const val LEVEL_LOG_INTERVAL_MS = 2000L
        private const val SPEECH_PEAK = 2000
        private const val QUIET_WINDOWS = 3
        private const val KEYWORD_SCORE = 2.5f
        private const val KEYWORD_THRESHOLD = 0.12f

        private val CANDIDATES = listOf(
            RecorderCandidate(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, "VOICE_RECOGNITION"),
            RecorderCandidate(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, "MIC"),
        )

        private fun frameSamples(sampleRate: Int): Int = sampleRate / 20
    }
}
