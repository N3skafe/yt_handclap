package com.example.youtubegesturecontrol.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.youtubegesturecontrol.ControlTimeouts
import com.example.youtubegesturecontrol.UserMessages
import com.example.youtubegesturecontrol.YouTubeGestureApplication
import com.example.youtubegesturecontrol.gesture.GestureAnalyzer
import com.example.youtubegesturecontrol.gesture.GestureSample
import com.example.youtubegesturecontrol.gesture.GestureStabilityTracker
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.PlaybackRequestResult
import com.example.youtubegesturecontrol.media.YoutubeController
import com.example.youtubegesturecontrol.media.toKorean
import com.example.youtubegesturecontrol.precondition.PrerequisiteChecker
import com.example.youtubegesturecontrol.timing.CoroutineDelayedTaskRunner
import com.example.youtubegesturecontrol.wakeword.SherpaWakeWordDetector
import com.example.youtubegesturecontrol.wakeword.WakeWordDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 호출어 감지부터 카메라 분석, 유튜브 명령 전달까지의 상태 전환을 관리합니다.
 * Activity가 닫혀도 동작해야 하므로 화면이 아닌 서비스가 작업을 소유합니다.
 *
 * 프로세스가 죽으면 자동으로 다시 시작하지 않습니다. 사용자가 화면에서 다시 시작해야 합니다.
 * 포그라운드 서비스도 제조사 절전 정책에서 항상 살아남는다는 보장은 없습니다.
 */
class ControlService : Service() {
    /**
     * 코루틴은 함수를 멈췄다가 이어서 실행하는 작업입니다. Dart의 async/await와 비슷합니다.
     * 이 scope를 취소하면 그 안의 대기도 함께 취소됩니다.
     * Main.immediate는 이미 메인 스레드이면 다음 루프까지 미루지 않습니다.
     * 상태 기계는 이 스레드에서만 호출해 전환 순서가 섞이지 않게 합니다.
     */
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(
        serviceJob + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "처리되지 않은 서비스 오류", error)
        },
    )

    private val repository: ControlStatusRepository
        get() = (application as YouTubeGestureApplication).statusRepository

    private val machine = ControlStateMachine()
    private val timers by lazy { CoroutineDelayedTaskRunner(scope) }
    private val lifecycleOwner = ServiceCameraLifecycleOwner()
    private val analyzer by lazy {
        GestureAnalyzer(applicationContext, lifecycleOwner, analyzerListener)
    }
    private val youtubeController by lazy { YoutubeController(applicationContext) }
    private val checker by lazy { PrerequisiteChecker(applicationContext) }
    private val notifier by lazy { ControlNotifier(this) }
    private val tracker = GestureStabilityTracker()

    private var wakeWord: WakeWordDetector? = null
    private var wakeWordPrepared = false
    private var commandJob: Job? = null
    private var foregroundStarted = false
    private var stopIssued = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY: 시스템이 프로세스를 죽인 뒤 서비스만 다시 만들지 않습니다.
        // 마이크와 카메라를 백그라운드에서 자동으로 재개하지 않기 위해서입니다.
        if (intent == null) {
            Log.w(TAG, "intent 없이 서비스가 시작되었습니다. 자동 재시작으로 보지 않고 종료합니다.")
            beginForegroundOrLog()
            stopServiceSafely()
            return START_NOT_STICKY
        }
        if (!beginForegroundOrLog()) {
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> handleStop()
            else -> handleStart()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        commandJob?.cancel()
        timers.cancelAll()
        releaseWakeWordEngine()
        analyzer.releaseEngine()
        lifecycleOwner.destroy()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun beginForegroundOrLog(): Boolean {
        if (foregroundStarted) {
            return true
        }
        return try {
            notifier.startForeground(UserMessages.stateLabel(ControlState.Starting), UserMessages.STARTING)
            foregroundStarted = true
            true
        } catch (error: Exception) {
            val message = "포그라운드 서비스를 시작하지 못했습니다. ${describe(error)}"
            Log.e(TAG, message, error)
            repository.update(MachineStep(state = ControlState.Error(message), recentResult = message))
            stopSelf()
            false
        }
    }

    private fun handleStart() {
        if (machine.state.isServiceActive()) {
            Log.i(TAG, "이미 제어 중이라 중복 시작을 무시합니다.")
            return
        }
        stopIssued = false
        apply(machine.requestStart())
        val problem = checker.blockingProblem()
        if (problem != null) {
            apply(machine.onPrerequisitesFailed(problem))
            return
        }
        wakeWord = SherpaWakeWordDetector(applicationContext)
        analyzer.prepare()
        apply(machine.onServiceInitialized())
    }

    private fun handleStop() {
        commandJob?.cancel()
        val step = machine.requestStop()
        apply(step)
        if (step.effects.none { effect -> effect is ControlEffect.StopService }) {
            stopServiceSafely()
        }
    }

    private fun apply(step: MachineStep) {
        Log.i(TAG, "상태=${UserMessages.stateLabel(step.state)} 효과=${step.effects.size}")
        repository.update(step)
        if (foregroundStarted && !stopIssued) {
            val detail = step.recentResult ?: repository.snapshot.value.recentResult
            try {
                notifier.update(UserMessages.stateLabel(step.state), detail)
            } catch (error: Exception) {
                Log.w(TAG, "상태 알림 갱신 실패", error)
            }
            if (step.recentResult == UserMessages.WAKE_WORD_HEARD) {
                try {
                    notifier.showWakeWordHeard()
                } catch (error: Exception) {
                    Log.w(TAG, "호출어 확인 표시 실패", error)
                }
            }
        }
        for (effect in step.effects) {
            if (execute(effect)) {
                break
            }
        }
    }

    /**
     * sealed 효과를 when으로 모두 다루면, 효과를 추가하고 여기서 빼먹으면 컴파일이 실패합니다.
     * Dart 3 sealed class의 switch와 같은 역할입니다.
     * true를 돌려주면 같은 목록의 남은 효과는 실행하지 않습니다.
     */
    private fun execute(effect: ControlEffect): Boolean {
        return when (effect) {
            ControlEffect.SuspendWakeWord -> suspendWakeWord()
            ControlEffect.ResumeWakeWord -> resumeWakeWord()
            ControlEffect.ReleaseWakeWord -> {
                releaseWakeWordEngine()
                false
            }
            is ControlEffect.ArmCameraStartTimeout -> {
                armCameraTimeout(effect.sessionId)
                false
            }
            is ControlEffect.ArmRecognitionTimeout -> {
                tracker.reset()
                armRecognitionTimeout(effect.sessionId)
                false
            }
            is ControlEffect.CancelSessionTimers -> {
                timers.cancel(cameraToken(effect.sessionId))
                timers.cancel(recognitionToken(effect.sessionId))
                false
            }
            ControlEffect.CancelAllTimers -> {
                timers.cancelAll()
                false
            }
            is ControlEffect.OpenCamera -> {
                analyzer.open(effect.sessionId)
                false
            }
            is ControlEffect.CloseCamera -> {
                analyzer.closeCamera()
                val cleaning = machine.state as? ControlState.CleaningUp
                if (cleaning != null && cleaning.sessionId == effect.sessionId) {
                    apply(machine.onCameraClosed(effect.sessionId))
                }
                false
            }
            is ControlEffect.DispatchCommand -> {
                dispatch(effect.sessionId, effect.command)
                false
            }
            ControlEffect.StopService -> {
                stopServiceSafely()
                false
            }
        }
    }

    private fun resumeWakeWord(): Boolean {
        val detector = wakeWord ?: return false
        return try {
            if (!wakeWordPrepared) {
                detector.prepare(
                    onDetected = { apply(machine.onWakeWordDetected()) },
                    onError = { message ->
                        Log.e(TAG, message)
                        apply(machine.onUnrecoverable(message))
                    },
                    onDetail = { detail -> publishDetail(detail) },
                )
                wakeWordPrepared = true
            }
            detector.startListening()
            false
        } catch (error: Exception) {
            Log.e(TAG, "호출어 시작 실패", error)
            apply(machine.onUnrecoverable(describe(error)))
            true
        }
    }

    private fun publishDetail(detail: String) {
        if (machine.state !is ControlState.WaitingForWakeWord || stopIssued) {
            return
        }
        repository.updateDetail(detail)
        try {
            notifier.update(UserMessages.stateLabel(machine.state), detail)
        } catch (error: Exception) {
            Log.w(TAG, "상태 알림 갱신 실패", error)
        }
    }

    private fun suspendWakeWord(): Boolean {
        val detector = wakeWord ?: return false
        return try {
            detector.suspendListening()
            false
        } catch (error: Exception) {
            Log.e(TAG, "호출어 일시 중지 실패", error)
            apply(machine.onUnrecoverable(describe(error)))
            true
        }
    }

    private fun releaseWakeWordEngine() {
        wakeWordPrepared = false
        wakeWord?.release()
        wakeWord = null
    }

    private fun armCameraTimeout(sessionId: Long) {
        timers.schedule(cameraToken(sessionId), ControlTimeouts.CAMERA_START_TIMEOUT_MS) {
            val reason = "카메라 시작이 ${ControlTimeouts.CAMERA_START_TIMEOUT_MS}ms 안에 끝나지 않았습니다."
            apply(machine.onCameraStartFailed(sessionId, reason))
        }
    }

    private fun armRecognitionTimeout(sessionId: Long) {
        timers.schedule(recognitionToken(sessionId), ControlTimeouts.RECOGNITION_TIMEOUT_MS) {
            apply(machine.onRecognitionTimedOut(sessionId))
        }
    }

    private fun dispatch(sessionId: Long, command: PlaybackCommand) {
        commandJob?.cancel()
        commandJob = scope.launch {
            val result = try {
                youtubeController.request(command)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PlaybackRequestResult.Failed(command, describe(error))
            }
            apply(machine.onCommandCompleted(sessionId, result.toKorean()))
        }
    }

    private fun stopServiceSafely() {
        if (stopIssued) {
            return
        }
        stopIssued = true
        commandJob?.cancel()
        commandJob = null
        timers.cancelAll()
        releaseWakeWordEngine()
        analyzer.releaseEngine()
        try {
            notifier.stop()
        } catch (error: Exception) {
            Log.w(TAG, "포그라운드 알림 제거 실패", error)
        }
        foregroundStarted = false
        stopSelf()
    }

    private val analyzerListener = object : GestureAnalyzer.Listener {
        override fun onCameraReady(sessionId: Long, width: Int, height: Int, rotationDegrees: Int) {
            scope.launch {
                Log.i(TAG, "사용 가능한 프레임 수신. ${width}x$height rotation=$rotationDegrees")
                apply(machine.onCameraReady(sessionId))
            }
        }

        override fun onGestureSample(sessionId: Long, sample: GestureSample) {
            scope.launch {
                val recognizing = machine.state as? ControlState.RecognizingGesture ?: return@launch
                if (recognizing.sessionId != sessionId) {
                    return@launch
                }
                val confirmed = tracker.onSample(sample)
                if (confirmed != null) {
                    Log.i(TAG, "제스처 확정: $confirmed")
                    apply(machine.onGestureConfirmed(sessionId, confirmed))
                }
            }
        }

        override fun onCameraFailed(sessionId: Long, error: Exception) {
            scope.launch {
                Log.e(TAG, "카메라 시작 실패", error)
                apply(machine.onCameraStartFailed(sessionId, describe(error)))
            }
        }

        override fun onRecognizerFailed(
            sessionId: Long,
            error: Exception,
            initializationFailed: Boolean,
        ) {
            scope.launch {
                Log.e(TAG, "제스처 인식 실패 initialization=$initializationFailed", error)
                apply(machine.onRecognizerFailed(sessionId, describe(error), initializationFailed))
            }
        }
    }

    private fun cameraToken(sessionId: Long): String = "camera-$sessionId"

    private fun recognitionToken(sessionId: Long): String = "recognition-$sessionId"

    private fun describe(error: Exception): String {
        val detail = error.message?.takeIf { it.isNotBlank() }
        return if (detail == null) error.javaClass.simpleName else "${error.javaClass.simpleName}: $detail"
    }

    /**
     * companion object는 인스턴스 없이 부르는 상수와 함수를 담습니다.
     * Dart의 static const, static method와 비슷하지만, 그 자체가 하나의 객체입니다.
     * Activity 참조는 저장하지 않고, 시작 요청만 서비스로 보냅니다.
     */
    companion object {
        private const val TAG = "ControlService"
        const val ACTION_START = "com.example.youtubegesturecontrol.action.START"
        const val ACTION_STOP = "com.example.youtubegesturecontrol.action.STOP"

        fun startWakeWordControl(context: Context) {
            start(context, ACTION_START)
        }

        fun stop(context: Context) {
            start(context, ACTION_STOP)
        }

        private fun start(context: Context, action: String) {
            val intent = Intent(context, ControlService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
