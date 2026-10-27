package com.example.youtubegesturecontrol.service

import com.example.youtubegesturecontrol.ControlTimeouts
import com.example.youtubegesturecontrol.UserNotice
import com.example.youtubegesturecontrol.gesture.HandGesture
import com.example.youtubegesturecontrol.media.PlaybackCommand

/**
 * 호출어 감지부터 카메라 분석, 유튜브 명령 전달까지의 상태 전환을 결정합니다.
 * 실제 카메라와 마이크는 서비스가 소유하고, 이 클래스는 다음에 무엇을 할지만 돌려줍니다.
 */
class ControlStateMachine(
    private val maxConsecutiveCameraFailures: Int = ControlTimeouts.MAX_CONSECUTIVE_CAMERA_FAILURES,
    private val maxConsecutiveRecognizerFailures: Int = ControlTimeouts.MAX_CONSECUTIVE_RECOGNIZER_FAILURES,
) {
    var state: ControlState = ControlState.Stopped
        private set

    private var nextSessionId: Long = 0L
    private var consecutiveCameraFailures: Int = 0
    private var consecutiveRecognizerFailures: Int = 0

    fun requestStart(): MachineStep {
        if (state !is ControlState.Stopped && state !is ControlState.Error) {
            return unchanged()
        }
        consecutiveCameraFailures = 0
        consecutiveRecognizerFailures = 0
        state = ControlState.Starting
        return MachineStep(state = state, recentResult = UserNotice.Starting)
    }

    fun onPrerequisitesFailed(notice: UserNotice): MachineStep {
        if (state !is ControlState.Starting) {
            return unchanged()
        }
        state = ControlState.Error(notice)
        return MachineStep(
            state = state,
            effects = listOf(ControlEffect.ReleaseWakeWord, ControlEffect.StopService),
            recentResult = notice,
        )
    }

    fun onServiceInitialized(): MachineStep {
        if (state !is ControlState.Starting) {
            return unchanged()
        }
        state = ControlState.WaitingForWakeWord
        return MachineStep(
            state = state,
            effects = listOf(ControlEffect.ResumeWakeWord),
            recentResult = UserNotice.WaitingForWakeWord,
        )
    }

    fun onWakeWordDetected(): MachineStep {
        if (state !is ControlState.WaitingForWakeWord) {
            return unchanged()
        }
        val step = beginCameraSession(
            returnToWakeWord = true,
            suspendWakeWord = true,
            recentResult = UserNotice.WakeWordHeard,
        )
        return step.copy(effects = listOf(ControlEffect.ShowWakeWordHeard) + step.effects)
    }

    fun onCameraReady(sessionId: Long): MachineStep {
        // as? 는 타입이 아니면 null입니다. 실패 시 예외를 던지는 as 대신 사용합니다.
        val opening = state as? ControlState.OpeningCamera ?: return unchanged()
        if (opening.sessionId != sessionId) {
            return unchanged()
        }
        consecutiveCameraFailures = 0
        state = ControlState.RecognizingGesture(
            sessionId = sessionId,
            returnToWakeWord = opening.returnToWakeWord,
        )
        return MachineStep(
            state = state,
            effects = listOf(
                ControlEffect.CancelSessionTimers(sessionId),
                ControlEffect.ArmRecognitionTimeout(sessionId),
            ),
        )
    }

    fun onCameraStartFailed(sessionId: Long, notice: UserNotice): MachineStep {
        val opening = state as? ControlState.OpeningCamera ?: return unchanged()
        if (opening.sessionId != sessionId) {
            return unchanged()
        }
        consecutiveCameraFailures += 1
        val fatal = !opening.returnToWakeWord ||
            consecutiveCameraFailures >= maxConsecutiveCameraFailures
        return enterCleanup(
            sessionId = sessionId,
            returnToWakeWord = opening.returnToWakeWord,
            pendingCommand = null,
            fatal = fatal,
            notice = notice,
        )
    }

    fun onGestureConfirmed(sessionId: Long, gesture: HandGesture): MachineStep {
        val recognizing = state as? ControlState.RecognizingGesture ?: return unchanged()
        if (recognizing.sessionId != sessionId) {
            return unchanged()
        }
        consecutiveRecognizerFailures = 0
        return enterCleanup(
            sessionId = sessionId,
            returnToWakeWord = recognizing.returnToWakeWord,
            pendingCommand = playbackCommandFor(gesture),
            fatal = false,
            notice = UserNotice.GestureConfirmed(gesture),
        )
    }

    fun onRecognitionTimedOut(sessionId: Long): MachineStep {
        val recognizing = state as? ControlState.RecognizingGesture ?: return unchanged()
        if (recognizing.sessionId != sessionId) {
            return unchanged()
        }
        consecutiveRecognizerFailures = 0
        return enterCleanup(
            sessionId = sessionId,
            returnToWakeWord = recognizing.returnToWakeWord,
            pendingCommand = null,
            fatal = false,
            notice = UserNotice.RecognitionTimeout,
        )
    }

    fun onRecognizerFailed(
        sessionId: Long,
        reason: String,
        initializationFailed: Boolean,
    ): MachineStep {
        val returnToWakeWord = when (val current = state) {
            is ControlState.OpeningCamera -> {
                if (current.sessionId != sessionId) {
                    return unchanged()
                }
                current.returnToWakeWord
            }
            is ControlState.RecognizingGesture -> {
                if (current.sessionId != sessionId) {
                    return unchanged()
                }
                current.returnToWakeWord
            }
            else -> return unchanged()
        }
        consecutiveRecognizerFailures += 1
        val fatal = initializationFailed ||
            !returnToWakeWord ||
            consecutiveRecognizerFailures >= maxConsecutiveRecognizerFailures
        return enterCleanup(
            sessionId = sessionId,
            returnToWakeWord = returnToWakeWord,
            pendingCommand = null,
            fatal = fatal,
            notice = UserNotice.RecognizerFailed(reason),
        )
    }

    fun onCameraClosed(sessionId: Long): MachineStep {
        val cleaning = state as? ControlState.CleaningUp ?: return unchanged()
        if (cleaning.sessionId != sessionId) {
            return unchanged()
        }
        val pendingCommand = cleaning.pendingCommand
        if (pendingCommand != null) {
            state = ControlState.DispatchingCommand(
                sessionId = sessionId,
                command = pendingCommand,
                returnToWakeWord = cleaning.returnToWakeWord,
            )
            return MachineStep(
                state = state,
                effects = listOf(ControlEffect.DispatchCommand(sessionId, pendingCommand)),
            )
        }
        return finishSession(
            returnToWakeWord = cleaning.returnToWakeWord,
            fatal = cleaning.fatal,
            notice = cleaning.notice,
        )
    }

    fun onCommandCompleted(sessionId: Long, notice: UserNotice): MachineStep {
        val dispatching = state as? ControlState.DispatchingCommand ?: return unchanged()
        if (dispatching.sessionId != sessionId) {
            return unchanged()
        }
        return finishSession(
            returnToWakeWord = dispatching.returnToWakeWord,
            fatal = false,
            notice = notice,
        )
    }

    fun onUnrecoverable(notice: UserNotice): MachineStep {
        if (state is ControlState.Stopped || state is ControlState.Error) {
            state = ControlState.Error(notice)
            return MachineStep(state = state, recentResult = notice)
        }
        newSession()
        state = ControlState.Error(notice)
        return MachineStep(
            state = state,
            effects = listOf(
                ControlEffect.CancelAllTimers,
                ControlEffect.CloseCamera(sessionId = 0L),
                ControlEffect.ReleaseWakeWord,
                ControlEffect.StopService,
            ),
            recentResult = notice,
        )
    }

    fun requestStop(): MachineStep {
        if (state is ControlState.Stopped) {
            return unchanged()
        }
        newSession()
        consecutiveCameraFailures = 0
        consecutiveRecognizerFailures = 0
        state = ControlState.Stopped
        return MachineStep(
            state = state,
            effects = listOf(
                ControlEffect.CancelAllTimers,
                ControlEffect.CloseCamera(sessionId = 0L),
                ControlEffect.ReleaseWakeWord,
                ControlEffect.StopService,
            ),
            recentResult = UserNotice.Stopped,
        )
    }

    private fun beginCameraSession(
        returnToWakeWord: Boolean,
        suspendWakeWord: Boolean,
        recentResult: UserNotice,
    ): MachineStep {
        val sessionId = newSession()
        state = ControlState.OpeningCamera(
            sessionId = sessionId,
            returnToWakeWord = returnToWakeWord,
        )
        val effects = mutableListOf<ControlEffect>()
        if (suspendWakeWord) {
            effects.add(ControlEffect.SuspendWakeWord)
        }
        effects.add(ControlEffect.ArmCameraStartTimeout(sessionId))
        effects.add(ControlEffect.OpenCamera(sessionId))
        return MachineStep(state = state, effects = effects, recentResult = recentResult)
    }

    private fun enterCleanup(
        sessionId: Long,
        returnToWakeWord: Boolean,
        pendingCommand: PlaybackCommand?,
        fatal: Boolean,
        notice: UserNotice,
    ): MachineStep {
        state = ControlState.CleaningUp(
            sessionId = sessionId,
            returnToWakeWord = returnToWakeWord,
            pendingCommand = pendingCommand,
            fatal = fatal,
            notice = notice,
        )
        return MachineStep(
            state = state,
            effects = listOf(
                ControlEffect.CancelSessionTimers(sessionId),
                ControlEffect.CloseCamera(sessionId),
            ),
            recentResult = notice,
        )
    }

    private fun finishSession(
        returnToWakeWord: Boolean,
        fatal: Boolean,
        notice: UserNotice,
    ): MachineStep {
        if (fatal || !returnToWakeWord) {
            state = if (fatal) ControlState.Error(notice) else ControlState.Stopped
            return MachineStep(
                state = state,
                effects = listOf(ControlEffect.ReleaseWakeWord, ControlEffect.StopService),
                recentResult = notice,
            )
        }
        state = ControlState.WaitingForWakeWord
        return MachineStep(
            state = state,
            effects = listOf(ControlEffect.ResumeWakeWord),
            recentResult = notice,
        )
    }

    private fun newSession(): Long {
        nextSessionId += 1L
        return nextSessionId
    }

    private fun unchanged(): MachineStep = MachineStep(state = state)

    private fun playbackCommandFor(gesture: HandGesture): PlaybackCommand {
        return when (gesture) {
            HandGesture.OPEN_PALM -> PlaybackCommand.PAUSE
            HandGesture.THUMB_UP -> PlaybackCommand.PLAY
        }
    }
}
