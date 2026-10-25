package com.example.youtubegesturecontrol.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.example.youtubegesturecontrol.AssetFiles
import com.example.youtubegesturecontrol.service.ServiceCameraLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 서비스가 소유하는 전면 카메라 분석 세션입니다.
 * 화면이 유튜브로 바뀌어도 카메라 수명 주기가 Activity를 따라가지 않습니다.
 *
 * CPU delegate를 쓰는 이유는 GPU delegate가 만든 스레드에서만 호출해야 하기 때문입니다.
 * 분석 콜백은 별도 스레드이므로 CPU가 단순합니다. CPU가 항상 덜 전력을 쓴다는 뜻은 아닙니다.
 */
class GestureAnalyzer(
    context: Context,
    private val lifecycleOwner: ServiceCameraLifecycleOwner,
    private val listener: Listener,
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val engineLock = Any()

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var recognizer: GestureRecognizer? = null
    private var openGeneration = 0
    private var announcedReady = false
    private var loggedModelSize = false

    /**
     * 분석 콜백 스레드는 메인 스레드와 메모리를 공유합니다.
     * Dart isolate와 달리 필드가 자동으로 복사되지 않으므로, 세션 번호는 @Volatile로 최신 값을 보게 합니다.
     */
    @Volatile
    private var acceptingSessionId: Long? = null

    @Volatile
    private var framesAccepted = false

    private val inferenceInFlight = AtomicBoolean(false)
    private var inFlight: InFlightFrame? = null
    private var lastSubmittedElapsedMs = 0L
    private var lastMediaPipeTimestampMs = 0L

    private var providerFuture: ListenableFuture<ProcessCameraProvider>? = null

    /**
     * 호출어를 기다리는 동안 제스처 모델과 카메라 제공자만 미리 불러 둡니다.
     * 카메라는 여기서 열지 않습니다. 열기는 [open]이 수명 주기를 RESUMED로 올릴 때입니다.
     */
    fun prepare() {
        if (providerFuture == null) {
            providerFuture = ProcessCameraProvider.getInstance(appContext)
        }
        executor().execute {
            try {
                ensureRecognizer()
            } catch (error: Exception) {
                Log.w(TAG, "제스처 모델을 미리 불러오지 못했습니다.", error)
            }
        }
    }

    fun open(sessionId: Long) {
        val generation = ++openGeneration
        acceptingSessionId = sessionId
        framesAccepted = true
        announcedReady = false
        loggedModelSize = false
        lastSubmittedElapsedMs = 0L
        try {
            ensureRecognizer()
        } catch (error: Exception) {
            framesAccepted = false
            acceptingSessionId = null
            listener.onRecognizerFailed(sessionId, error, initializationFailed = true)
            return
        }
        val future = providerFuture ?: ProcessCameraProvider.getInstance(appContext).also {
            providerFuture = it
        }
        future.addListener(
            {
                if (generation != openGeneration) {
                    return@addListener
                }
                try {
                    bindCamera(future.get(), generation)
                } catch (error: Exception) {
                    Log.e(TAG, "전면 카메라 바인드 실패", error)
                    releaseBinding()
                    listener.onCameraFailed(sessionId, error)
                }
            },
            mainExecutor,
        )
    }

    fun closeCamera() {
        openGeneration += 1
        framesAccepted = false
        acceptingSessionId = null
        releaseBinding()
    }

    fun releaseEngine() {
        closeCamera()
        val leftover = synchronized(engineLock) {
            val frame = inFlight
            inFlight = null
            inferenceInFlight.set(false)
            val engine = recognizer
            recognizer = null
            frame to engine
        }
        leftover.first?.release()
        try {
            leftover.second?.close()
        } catch (error: Exception) {
            Log.w(TAG, "제스처 인식기 해제 실패", error)
        }
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    private fun bindCamera(
        provider: ProcessCameraProvider,
        generation: Int,
    ) {
        if (generation != openGeneration) {
            return
        }
        cameraProvider = provider
        lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        if (generation != openGeneration) {
            releaseBinding()
            return
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(currentDisplayRotation())
            .build()
        analysis.setAnalyzer(executor(), ::analyze)
        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            analysis,
        )
        Log.i(TAG, "전면 카메라 바인드를 요청했습니다. 목표 ${GestureThresholds.TARGET_ANALYSIS_WIDTH}x${GestureThresholds.TARGET_ANALYSIS_HEIGHT}")
        if (generation != openGeneration) {
            releaseBinding()
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            handleFrame(image)
        } catch (error: Exception) {
            Log.e(TAG, "프레임 변환 실패", error)
        } finally {
            image.close()
        }
    }

    private fun handleFrame(image: ImageProxy) {
        val sessionId = acceptingSessionId
        if (sessionId == null || !framesAccepted) {
            return
        }
        if (inferenceInFlight.get()) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastSubmittedElapsedMs < GestureThresholds.ANALYSIS_MIN_INTERVAL_MS) {
            return
        }
        val rotationDegrees = image.imageInfo.rotationDegrees
        if (!announcedReady) {
            announcedReady = true
            Log.i(
                TAG,
                "CameraX가 선택한 분석 해상도 ${image.width}x${image.height}, rotationDegrees=$rotationDegrees",
            )
            listener.onCameraReady(sessionId, image.width, image.height, rotationDegrees)
        }
        val bitmap = scaleForModel(
            rotateAndMirrorFrontCamera(copyRgbaToBitmap(image), rotationDegrees),
        )
        if (!loggedModelSize) {
            loggedModelSize = true
            Log.i(TAG, "모델에 넣는 이미지 크기 ${bitmap.width}x${bitmap.height}")
        }
        submit(sessionId, bitmap, now)
    }

    private fun submit(sessionId: Long, bitmap: Bitmap, elapsedMs: Long) {
        if (!inferenceInFlight.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }
        val timestampMs = nextMediaPipeTimestamp()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val frame = InFlightFrame(sessionId, elapsedMs, mpImage, bitmap)
        val engine = synchronized(engineLock) {
            inFlight = frame
            recognizer
        }
        if (engine == null || !framesAccepted) {
            finishInference(frame)
            return
        }
        try {
            lastSubmittedElapsedMs = elapsedMs
            engine.recognizeAsync(mpImage, timestampMs)
        } catch (error: Exception) {
            finishInference(frame)
            listener.onRecognizerFailed(sessionId, error, initializationFailed = false)
        }
    }

    private fun ensureRecognizer() {
        synchronized(engineLock) {
            if (recognizer != null) {
                return
            }
        }
        if (!AssetFiles.exists(appContext, MODEL_ASSET_PATH)) {
            throw IllegalStateException("assets/$MODEL_ASSET_PATH 파일이 없습니다.")
        }
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setDelegate(Delegate.CPU)
            .build()
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, input -> onRecognizerResult(result, input) }
            .setErrorListener { error -> onRecognizerError(error) }
            .build()
        val created = GestureRecognizer.createFromOptions(appContext, options)
        // 분석 스레드가 인식기를 읽으므로, 공개 시점은 같은 lock 안에서 맞춰 둡니다.
        synchronized(engineLock) {
            if (recognizer == null) {
                recognizer = created
            } else {
                created.close()
            }
        }
    }

    private fun onRecognizerResult(result: GestureRecognizerResult, input: MPImage) {
        val frame = synchronized(engineLock) { inFlight }
        val sample = if (frame == null) {
            input.closeQuietly()
            null
        } else {
            val category = recognizedGesture(result)
            finishInference(frame)
            GestureSample(
                elapsedMs = frame.elapsedMs,
                modelCategoryName = category.first,
                confidence = category.second,
            )
        }
        val sessionId = frame?.sessionId
        if (sample == null || sessionId == null || sessionId != acceptingSessionId || !framesAccepted) {
            return
        }
        listener.onGestureSample(sessionId, sample)
    }

    private fun onRecognizerError(error: RuntimeException) {
        val frame = synchronized(engineLock) { inFlight }
        finishInference(frame)
        val sessionId = frame?.sessionId ?: acceptingSessionId
        if (sessionId != null && framesAccepted) {
            listener.onRecognizerFailed(sessionId, error, initializationFailed = false)
        } else {
            Log.w(TAG, "세션이 끝난 뒤 인식 오류가 도착했습니다.", error)
        }
    }

    private fun finishInference(frame: InFlightFrame?) {
        synchronized(engineLock) {
            if (frame == null || inFlight === frame) {
                inFlight = null
                inferenceInFlight.set(false)
            }
        }
        frame?.release()
    }

    private fun recognizedGesture(result: GestureRecognizerResult): Pair<String?, Float> {
        val classified = topCategory(result)
        val thumbIsConfident = classified.first == "Thumb_Up" &&
            classified.second >= GestureThresholds.MIN_CLASSIFICATION_CONFIDENCE
        if (thumbIsConfident) {
            return classified
        }
        if (landmarksLookLikeThumbUp(result)) {
            val thumbScore = if (classified.first == "Thumb_Up") {
                maxOf(classified.second, LANDMARK_THUMB_CONFIDENCE)
            } else {
                LANDMARK_THUMB_CONFIDENCE
            }
            return "Thumb_Up" to thumbScore
        }
        if (landmarksLookLikeOpenPalm(result)) {
            val palmScore = if (classified.first == "Open_Palm") {
                maxOf(classified.second, LANDMARK_PALM_CONFIDENCE)
            } else {
                LANDMARK_PALM_CONFIDENCE
            }
            return "Open_Palm" to palmScore
        }
        return classified
    }

    private fun landmarksLookLikeThumbUp(result: GestureRecognizerResult): Boolean {
        val points = handPoints(result) ?: return false
        return ThumbUpShape.looksLikeThumbUp(points)
    }

    private fun landmarksLookLikeOpenPalm(result: GestureRecognizerResult): Boolean {
        val points = handPoints(result) ?: return false
        return OpenPalmShape.looksLikeOpenPalm(points)
    }

    private fun handPoints(result: GestureRecognizerResult): List<HandPoint>? {
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            return null
        }
        return hands[0].map { landmark ->
            HandPoint(landmark.x(), landmark.y(), landmark.z())
        }
    }

    private fun topCategory(result: GestureRecognizerResult): Pair<String?, Float> {
        val hands = result.gestures()
        if (hands.isEmpty() || hands[0].isEmpty()) {
            return null to 0f
        }
        val top = hands[0][0]
        return top.categoryName() to top.score()
    }

    private fun nextMediaPipeTimestamp(): Long {
        val now = SystemClock.elapsedRealtime()
        val timestamp = if (now > lastMediaPipeTimestampMs) now else lastMediaPipeTimestampMs + 1L
        lastMediaPipeTimestampMs = timestamp
        return timestamp
    }

    private fun releaseBinding() {
        try {
            cameraProvider?.unbindAll()
        } catch (error: Exception) {
            Log.w(TAG, "카메라 unbind 실패", error)
        }
        val state = lifecycleOwner.lifecycle.currentState
        if (state != Lifecycle.State.DESTROYED && state.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        }
    }

    private fun executor(): ExecutorService {
        val existing = analysisExecutor
        if (existing != null && !existing.isShutdown) {
            return existing
        }
        val created = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "gesture-analysis")
        }
        analysisExecutor = created
        return created
    }

    private fun resolutionSelector(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(
                        GestureThresholds.TARGET_ANALYSIS_WIDTH,
                        GestureThresholds.TARGET_ANALYSIS_HEIGHT,
                    ),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
    }

    private fun currentDisplayRotation(): Int {
        val displayManager = appContext.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return display?.rotation ?: Surface.ROTATION_0
    }

    /**
     * RGBA 평면을 복사한 뒤 ImageProxy는 호출자가 닫습니다.
     * 추론은 복사본만 사용하므로, 비동기 인식이 끝나기 전에 카메라 버퍼를 닫아도 됩니다.
     */
    private fun copyRgbaToBitmap(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride == 4 && rowStride == width * 4) {
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowStart = y * rowStride
            for (x in 0 until width) {
                val offset = rowStart + x * pixelStride
                val red = buffer.get(offset).toInt() and 0xFF
                val green = buffer.get(offset + 1).toInt() and 0xFF
                val blue = buffer.get(offset + 2).toInt() and 0xFF
                val alpha = buffer.get(offset + 3).toInt() and 0xFF
                pixels[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * 모델 입력의 긴 변을 줄입니다. 큰 프레임을 CPU에서 그대로 돌리면
     * 추론이 길어지고, 그 공백 때문에 손 모양 판정이 계속 처음부터 다시 시작합니다.
     */
    private fun scaleForModel(source: Bitmap): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        val maxEdge = GestureThresholds.MODEL_MAX_EDGE_PX
        if (longEdge <= maxEdge) {
            return source
        }
        val scale = maxEdge.toFloat() / longEdge.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        if (scaled !== source) {
            source.recycle()
        }
        return scaled
    }

    /**
     * 전면 카메라 이미지를 사용자가 보는 방향으로 돌리고 좌우를 뒤집습니다.
     * 폰에서 손바닥은 일시정지, 엄지는 재생으로 이어졌으므로 이 방향을 유지합니다.
     */
    private fun rotateAndMirrorFrontCamera(source: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        matrix.postScale(-1f, 1f, source.width.toFloat(), source.height.toFloat())
        val transformed = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        )
        if (transformed !== source) {
            source.recycle()
        }
        return transformed
    }

    private fun MPImage.closeQuietly() {
        try {
            close()
        } catch (error: Exception) {
            Log.w(TAG, "MPImage close 실패", error)
        }
    }

    interface Listener {
        fun onCameraReady(sessionId: Long, width: Int, height: Int, rotationDegrees: Int)

        fun onGestureSample(sessionId: Long, sample: GestureSample)

        fun onCameraFailed(sessionId: Long, error: Exception)

        fun onRecognizerFailed(sessionId: Long, error: Exception, initializationFailed: Boolean)
    }

    private class InFlightFrame(
        val sessionId: Long,
        val elapsedMs: Long,
        private val image: MPImage,
        private val bitmap: Bitmap,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (!released.compareAndSet(false, true)) {
                return
            }
            try {
                image.close()
            } catch (error: Exception) {
                Log.w(TAG, "추론 이미지 close 실패", error)
            }
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    companion object {
        const val MODEL_ASSET_PATH = "gesture_recognizer.task"
        private const val TAG = "GestureAnalyzer"
        private const val LANDMARK_PALM_CONFIDENCE = 0.85f
        private const val LANDMARK_THUMB_CONFIDENCE = 0.85f
    }
}
