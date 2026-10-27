package com.example.youtubegesturecontrol.service

import com.example.youtubegesturecontrol.UserNotice
import com.example.youtubegesturecontrol.gesture.HandGesture
import com.example.youtubegesturecontrol.media.PlaybackCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlStateMachineTest {
    @Test
    fun wakeWordWhileWaitingOpensCameraAndDoesNotDispatch() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        val step = machine.onWakeWordDetected()
        assertTrue(step.state is ControlState.OpeningCamera)
        assertTrue(step.effects.any { it is ControlEffect.ShowWakeWordHeard })
        assertTrue(step.effects.any { it is ControlEffect.OpenCamera })
        assertTrue(step.effects.none { it is ControlEffect.DispatchCommand })
    }

    @Test
    fun duplicateWakeWordIsIgnored() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        val first = machine.onWakeWordDetected()
        val second = machine.onWakeWordDetected()
        assertEquals(first.state, second.state)
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun openPalmRequestsPauseOnlyAfterCameraCloses() {
        val machine = ControlStateMachine()
        val sessionId = recognize(machine)
        val confirmed = machine.onGestureConfirmed(sessionId, HandGesture.OPEN_PALM)
        val cleaning = confirmed.state as ControlState.CleaningUp
        assertEquals(PlaybackCommand.PAUSE, cleaning.pendingCommand)
        assertTrue(confirmed.effects.none { it is ControlEffect.DispatchCommand })
        val dispatch = machine.onCameraClosed(sessionId)
        val effect = dispatch.effects.filterIsInstance<ControlEffect.DispatchCommand>().single()
        assertEquals(PlaybackCommand.PAUSE, effect.command)
    }

    @Test
    fun thumbUpRequestsPlay() {
        val machine = ControlStateMachine()
        val sessionId = recognize(machine)
        machine.onGestureConfirmed(sessionId, HandGesture.THUMB_UP)
        val dispatch = machine.onCameraClosed(sessionId)
        val effect = dispatch.effects.filterIsInstance<ControlEffect.DispatchCommand>().single()
        assertEquals(PlaybackCommand.PLAY, effect.command)
    }

    @Test
    fun secondGestureInSameSessionDoesNotDispatchAnotherCommand() {
        val machine = ControlStateMachine()
        val sessionId = recognize(machine)
        machine.onGestureConfirmed(sessionId, HandGesture.OPEN_PALM)
        val second = machine.onGestureConfirmed(sessionId, HandGesture.THUMB_UP)
        assertTrue(second.effects.isEmpty())
        val dispatch = machine.onCameraClosed(sessionId)
        assertEquals(1, dispatch.effects.count { it is ControlEffect.DispatchCommand })
    }

    @Test
    fun timeoutDoesNotDispatchAndReturnsToWaiting() {
        val machine = ControlStateMachine()
        val sessionId = recognize(machine)
        val timeout = machine.onRecognitionTimedOut(sessionId)
        assertEquals(UserNotice.RecognitionTimeout, timeout.recentResult)
        assertTrue(timeout.effects.none { it is ControlEffect.DispatchCommand })
        val closed = machine.onCameraClosed(sessionId)
        assertTrue(closed.state is ControlState.WaitingForWakeWord)
        assertTrue(closed.effects.any { it is ControlEffect.ResumeWakeWord })
        assertTrue(closed.effects.none { it is ControlEffect.OpenCamera })
    }

    @Test
    fun timeoutAfterConfirmIsIgnored() {
        val machine = ControlStateMachine()
        val sessionId = recognize(machine)
        machine.onGestureConfirmed(sessionId, HandGesture.OPEN_PALM)
        val timeout = machine.onRecognitionTimedOut(sessionId)
        assertTrue(timeout.effects.isEmpty())
        assertTrue(machine.state is ControlState.CleaningUp)
    }

    @Test
    fun staleCameraReadyAfterStopDoesNothing() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        val opening = machine.onWakeWordDetected().state as ControlState.OpeningCamera
        machine.requestStop()
        val late = machine.onCameraReady(opening.sessionId)
        assertTrue(late.effects.isEmpty())
        assertTrue(machine.state is ControlState.Stopped)
    }

    @Test
    fun stopWhileOpeningClosesCameraAndDoesNotDispatch() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        machine.onWakeWordDetected()
        val stopped = machine.requestStop()
        assertTrue(stopped.state is ControlState.Stopped)
        assertTrue(stopped.effects.any { it is ControlEffect.CloseCamera })
        assertTrue(stopped.effects.any { it is ControlEffect.StopService })
        assertTrue(stopped.effects.none { it is ControlEffect.DispatchCommand })
    }

    @Test
    fun stopWhileRecognizingDoesNotDispatch() {
        val machine = ControlStateMachine()
        recognize(machine)
        val stopped = machine.requestStop()
        assertTrue(stopped.effects.none { it is ControlEffect.DispatchCommand })
        assertTrue(stopped.state is ControlState.Stopped)
    }

    @Test
    fun cameraFailureDoesNotImmediatelyReopenAndCanReturnToWaiting() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        val sessionId = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        val failed = machine.onCameraStartFailed(sessionId, UserNotice.CameraStartFailed("in use"))
        assertTrue(failed.effects.none { it is ControlEffect.OpenCamera })
        val closed = machine.onCameraClosed(sessionId)
        assertTrue(closed.state is ControlState.WaitingForWakeWord)
        assertTrue(closed.effects.none { it is ControlEffect.OpenCamera })
    }

    @Test
    fun thirdConsecutiveCameraFailureStopsService() {
        val machine = ControlStateMachine(maxConsecutiveCameraFailures = 3)
        startWakeWord(machine)
        repeat(2) {
            val sessionId = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
            machine.onCameraStartFailed(sessionId, UserNotice.CameraStartFailed("busy"))
            val closed = machine.onCameraClosed(sessionId)
            assertTrue(closed.state is ControlState.WaitingForWakeWord)
        }
        val sessionId = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        machine.onCameraStartFailed(sessionId, UserNotice.CameraStartFailed("busy"))
        val closed = machine.onCameraClosed(sessionId)
        assertTrue(closed.state is ControlState.Error)
        assertTrue(closed.effects.any { it is ControlEffect.StopService })
        assertTrue(closed.effects.none { it is ControlEffect.OpenCamera })
    }

    @Test
    fun successfulCameraReadyResetsFailureStreak() {
        val machine = ControlStateMachine(maxConsecutiveCameraFailures = 2)
        startWakeWord(machine)
        val first = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        machine.onCameraStartFailed(first, UserNotice.CameraStartFailed("busy"))
        machine.onCameraClosed(first)
        val second = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        machine.onCameraReady(second)
        machine.onRecognitionTimedOut(second)
        machine.onCameraClosed(second)
        val third = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        machine.onCameraStartFailed(third, UserNotice.CameraStartFailed("busy"))
        val closed = machine.onCameraClosed(third)
        assertTrue(closed.state is ControlState.WaitingForWakeWord)
    }

    @Test
    fun wakeWordDuringDispatchIsIgnored() {
        val machine = ControlStateMachine()
        val dispatching = dispatchPause(machine)
        val ignored = machine.onWakeWordDetected()
        assertTrue(ignored.effects.isEmpty())
        assertEquals(dispatching.sessionId, (machine.state as ControlState.DispatchingCommand).sessionId)
    }

    @Test
    fun commandCompletionReturnsToWaiting() {
        val machine = ControlStateMachine()
        val dispatching = dispatchPause(machine)
        val notice = UserNotice.PlaybackConfirmed(
            PlaybackCommand.PAUSE,
            alreadyInRequestedState = false,
        )
        val done = machine.onCommandCompleted(dispatching.sessionId, notice)
        assertTrue(done.state is ControlState.WaitingForWakeWord)
        assertEquals(notice, done.recentResult)
        assertTrue(done.effects.any { it is ControlEffect.ResumeWakeWord })
    }

    @Test
    fun staleCommandCompletionDoesNotLeaveStoppedState() {
        val machine = ControlStateMachine()
        val dispatching = dispatchPause(machine)
        machine.requestStop()
        val late = machine.onCommandCompleted(dispatching.sessionId, UserNotice.Stopped)
        assertTrue(late.effects.isEmpty())
        assertTrue(machine.state is ControlState.Stopped)
        assertNull(late.recentResult)
    }

    @Test
    fun startIsIgnoredWhileActiveAndAllowedAfterError() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        val ignored = machine.requestStart()
        assertTrue(ignored.effects.isEmpty())
        assertTrue(machine.state is ControlState.WaitingForWakeWord)
        machine.onUnrecoverable(UserNotice.MicrophonePermissionMissing)
        val restarted = machine.requestStart()
        assertTrue(restarted.state is ControlState.Starting)
    }

    @Test
    fun recognizerInitializationFailureStopsImmediately() {
        val machine = ControlStateMachine()
        startWakeWord(machine)
        val sessionId = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        machine.onRecognizerFailed(sessionId, "model missing", initializationFailed = true)
        val closed = machine.onCameraClosed(sessionId)
        assertTrue(closed.state is ControlState.Error)
        assertTrue(closed.effects.any { it is ControlEffect.StopService })
    }

    private fun startWakeWord(machine: ControlStateMachine) {
        machine.requestStart()
        val started = machine.onServiceInitialized()
        assertTrue(started.state is ControlState.WaitingForWakeWord)
    }

    private fun recognize(machine: ControlStateMachine): Long {
        startWakeWord(machine)
        val sessionId = (machine.onWakeWordDetected().state as ControlState.OpeningCamera).sessionId
        val ready = machine.onCameraReady(sessionId)
        assertTrue(ready.state is ControlState.RecognizingGesture)
        assertTrue(ready.effects.any { it is ControlEffect.ArmRecognitionTimeout })
        return sessionId
    }

    private fun dispatchPause(machine: ControlStateMachine): ControlState.DispatchingCommand {
        val sessionId = recognize(machine)
        machine.onGestureConfirmed(sessionId, HandGesture.OPEN_PALM)
        val closed = machine.onCameraClosed(sessionId)
        return closed.state as ControlState.DispatchingCommand
    }
}
