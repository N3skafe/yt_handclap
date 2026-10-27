package com.example.youtubegesturecontrol

import android.content.Context
import com.example.youtubegesturecontrol.gesture.HandGesture
import com.example.youtubegesturecontrol.media.PlaybackCommand
import com.example.youtubegesturecontrol.media.YoutubeSessionSelector
import com.example.youtubegesturecontrol.service.ControlState

/**
 * 경로처럼 공백이 없는 긴 토큰이 화면 밖으로 나가지 않게, 슬래시와 밑줄과 하이픈 뒤에 폭이 0인 문자를 넣습니다.
 * 마침표와 콜론은 패키지 이름과 문장이 복사돼도 그대로이도록 끊지 않습니다.
 */
fun String.withBreakableTokens(): String {
    if (isEmpty()) {
        return this
    }
    return buildString(length + 8) {
        for (char in this@withBreakableTokens) {
            append(char)
            if (char == '/' || char == '_' || char == '-') {
                append('\u200B')
            }
        }
    }
}

fun Context.noticeText(notice: UserNotice): String {
    return renderNotice(notice).withBreakableTokens()
}

fun Context.controlStateLabel(state: ControlState): String {
    val resId = when (state) {
        is ControlState.Stopped -> R.string.state_stopped
        is ControlState.Starting -> R.string.state_starting
        is ControlState.WaitingForWakeWord -> R.string.state_waiting
        is ControlState.OpeningCamera -> R.string.state_opening_camera
        is ControlState.RecognizingGesture -> R.string.state_recognizing
        is ControlState.DispatchingCommand -> when (state.command) {
            PlaybackCommand.PAUSE -> R.string.state_request_pause
            PlaybackCommand.PLAY -> R.string.state_request_play
        }
        is ControlState.CleaningUp -> R.string.state_cleaning
        is ControlState.Error -> R.string.state_error
    }
    return getString(resId)
}

private fun Context.renderNotice(notice: UserNotice): String {
    return when (notice) {
        UserNotice.NoResultYet -> getString(R.string.notice_no_result)
        UserNotice.Starting -> getString(R.string.notice_starting)
        UserNotice.WaitingForWakeWord -> getString(R.string.notice_waiting)
        UserNotice.MicrophoneOpened -> getString(R.string.notice_mic_opened)
        UserNotice.MicrophoneHearing -> getString(R.string.notice_mic_hearing)
        UserNotice.MicrophoneQuiet -> getString(R.string.notice_mic_quiet)
        UserNotice.MicrophoneUnavailable -> getString(R.string.notice_mic_unavailable)
        UserNotice.WakeWordHeard -> getString(R.string.notice_wake_heard)
        UserNotice.RecognitionTimeout -> getString(R.string.notice_recognition_timeout)
        UserNotice.Stopped -> getString(R.string.notice_stopped)
        is UserNotice.GestureConfirmed -> when (notice.gesture) {
            HandGesture.OPEN_PALM -> getString(R.string.notice_palm)
            HandGesture.THUMB_UP -> getString(R.string.notice_thumb)
        }
        is UserNotice.CameraStartFailed -> getString(R.string.notice_camera_failed, notice.reason)
        is UserNotice.CameraStartTimedOut ->
            getString(R.string.notice_camera_timeout, notice.timeoutMs)
        is UserNotice.RecognizerFailed -> getString(R.string.notice_recognizer_failed, notice.reason)
        is UserNotice.SessionUnavailable -> sessionUnavailable(notice.otherPackages)
        is UserNotice.PlaybackConfirmed -> playbackConfirmed(notice.command, notice.alreadyInRequestedState)
        is UserNotice.PlaybackConfirmationTimedOut -> playbackTimedOut(notice.command)
        UserNotice.ListenerAccessMissing -> getString(R.string.notice_listener_missing)
        is UserNotice.ActionUnsupported ->
            getString(R.string.notice_unsupported, actionName(notice.command))
        is UserNotice.PlaybackFailed -> getString(R.string.notice_playback_failed, notice.detail)
        UserNotice.SessionResponseTimedOut -> getString(R.string.notice_session_response_timeout)
        is UserNotice.ForegroundStartFailed -> getString(R.string.notice_foreground_failed, notice.detail)
        is UserNotice.ModelMissing -> getString(R.string.block_model_missing, notice.assetPath)
        UserNotice.CameraPermissionMissing -> getString(R.string.block_camera)
        UserNotice.MicrophonePermissionMissing -> getString(R.string.block_microphone)
        UserNotice.NotificationListenerOff -> getString(R.string.block_listener)
        is UserNotice.WakeWordModelMissing -> getString(R.string.wake_word_missing, notice.files)
        UserNotice.PlaybackStateUnreadable -> getString(R.string.notice_playback_state_unreadable)
        is UserNotice.ControlInterrupted -> getString(R.string.notice_interrupted, notice.detail)
    }
}

private fun Context.sessionUnavailable(otherPackages: List<String>): String {
    if (otherPackages.isEmpty()) {
        return getString(R.string.notice_session_empty)
    }
    return getString(
        R.string.notice_session_other,
        YoutubeSessionSelector.YOUTUBE_PACKAGE_NAME,
        otherPackages.joinToString(", "),
    )
}

private fun Context.playbackConfirmed(command: PlaybackCommand, alreadyInRequestedState: Boolean): String {
    return when (command) {
        PlaybackCommand.PAUSE -> if (alreadyInRequestedState) {
            getString(R.string.notice_pause_already)
        } else {
            getString(R.string.notice_pause_confirmed)
        }
        PlaybackCommand.PLAY -> if (alreadyInRequestedState) {
            getString(R.string.notice_play_already)
        } else {
            getString(R.string.notice_play_confirmed)
        }
    }
}

private fun Context.playbackTimedOut(command: PlaybackCommand): String {
    return when (command) {
        PlaybackCommand.PAUSE -> getString(R.string.notice_pause_timeout)
        PlaybackCommand.PLAY -> getString(R.string.notice_play_timeout)
    }
}

private fun Context.actionName(command: PlaybackCommand): String {
    return when (command) {
        PlaybackCommand.PAUSE -> getString(R.string.action_pause)
        PlaybackCommand.PLAY -> getString(R.string.action_play)
    }
}
