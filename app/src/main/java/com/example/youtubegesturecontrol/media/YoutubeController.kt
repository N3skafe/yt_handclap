package com.example.youtubegesturecontrol.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.example.youtubegesturecontrol.ControlTimeouts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * 설치된 유튜브 앱의 미디어 세션에 일시정지, 재생, 다음, 이전을 요청합니다.
 * 접근성 서비스, 화면 터치, 전역 재생 토글은 사용하지 않습니다.
 *
 * 콜백은 컨트롤러를 만든 스레드의 Looper로 오므로 이 함수는 메인 스레드에서 호출합니다.
 * 성공적인 메서드 호출은 요청을 보냈다는 뜻일 뿐, 재생 상태가 바뀌었다는 뜻은 아닙니다.
 */
class YoutubeController(
    private val context: Context,
) {
    suspend fun request(command: PlaybackCommand): PlaybackRequestResult {
        var requestSent = false
        return try {
            withTimeout(ControlTimeouts.PLAYBACK_CONFIRMATION_TIMEOUT_MS) {
                sendAndAwait(command) { sent -> requestSent = sent }
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (requestSent) {
                PlaybackRequestResult.ConfirmationTimedOut(command)
            } else {
                PlaybackRequestResult.ResponseTimedOut
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PlaybackRequestResult.Failed(command, error.message ?: error.javaClass.simpleName)
        }
    }

    private suspend fun sendAndAwait(
        command: PlaybackCommand,
        markSent: (Boolean) -> Unit,
    ): PlaybackRequestResult {
        when (val found = findSession()) {
            FindResult.ListenerMissing -> return PlaybackRequestResult.ListenerAccessMissing
            is FindResult.Unavailable -> return PlaybackRequestResult.SessionUnavailable(found.otherPackages)
            is FindResult.Found -> return dispatch(found.controller, command, markSent)
        }
    }

    private fun findSession(): FindResult {
        val manager = context.getSystemService(MediaSessionManager::class.java)
            ?: return FindResult.Unavailable(emptyList())
        val component = ComponentName(context, MediaNotificationListener::class.java)
        val controllers = try {
            manager.getActiveSessions(component)
        } catch (error: SecurityException) {
            Log.w(TAG, "알림 접근이 없어 세션을 조회하지 못했습니다.", error)
            return FindResult.ListenerMissing
        }
        val listed = controllers.map { controller ->
            ListedSession(controller, snapshotOf(controller))
        }
        val index = YoutubeSessionSelector.selectIndex(listed.map { it.snapshot })
        if (index == null) {
            return FindResult.Unavailable(listed.map { it.snapshot.packageName })
        }
        return FindResult.Found(listed[index].controller)
    }

    /**
     * `?.` 는 null이면 호출을 건너뜁니다. Dart의 `?.` 와 같습니다.
     * `?:` 는 null일 때 오른쪽 값을 씁니다. Dart의 `??` 와 같습니다.
     */
    private fun snapshotOf(controller: MediaController): MediaSessionSnapshot {
        val playback = when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> SessionPlayback.PLAYING
            PlaybackState.STATE_PAUSED -> SessionPlayback.PAUSED
            else -> SessionPlayback.OTHER
        }
        return MediaSessionSnapshot(
            packageName = controller.packageName ?: "",
            playback = playback,
        )
    }

    private suspend fun dispatch(
        controller: MediaController,
        command: PlaybackCommand,
        markSent: (Boolean) -> Unit,
    ): PlaybackRequestResult {
        val before = controller.playbackState
        if (before == null) {
            return PlaybackRequestResult.PlaybackStateUnreadable
        }
        if (!PlaybackActionSupport.allows(before.actions, requiredAction(command))) {
            return PlaybackRequestResult.ActionUnsupported(command)
        }
        val tracksMedia = command == PlaybackCommand.NEXT || command == PlaybackCommand.PREVIOUS
        val beforeMatches = if (tracksMedia) false else matchesPlayback(command, before)
        val beforeIdentity = currentIdentity(controller)
        var unregistered = false
        val callback = object : MediaController.Callback() {
            var onPlayback: ((PlaybackState?) -> Unit)? = null
            var onMetadata: (() -> Unit)? = null

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                onPlayback?.invoke(state)
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                onMetadata?.invoke()
            }

            override fun onSessionDestroyed() {
                onPlayback?.invoke(null)
            }
        }
        fun unregister() {
            if (unregistered) {
                return
            }
            unregistered = true
            controller.unregisterCallback(callback)
        }
        controller.registerCallback(callback)
        try {
            return suspendCancellableCoroutine { continuation ->
                fun resumeOnce(result: PlaybackRequestResult) {
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                fun confirmMediaChange() {
                    if (MediaIdentity.changed(beforeIdentity, currentIdentity(controller))) {
                        resumeOnce(
                            PlaybackRequestResult.Confirmed(
                                command = command,
                                alreadyInRequestedState = false,
                            ),
                        )
                    }
                }
                callback.onPlayback = { state ->
                    if (state == null) {
                        resumeOnce(PlaybackRequestResult.SessionUnavailable(emptyList()))
                    } else if (tracksMedia) {
                        confirmMediaChange()
                    } else if (matchesPlayback(command, state)) {
                        resumeOnce(
                            PlaybackRequestResult.Confirmed(
                                command = command,
                                alreadyInRequestedState = beforeMatches,
                            ),
                        )
                    }
                }
                callback.onMetadata = {
                    if (tracksMedia) {
                        confirmMediaChange()
                    }
                }
                continuation.invokeOnCancellation { unregister() }
                try {
                    markSent(true)
                    when (command) {
                        PlaybackCommand.PAUSE -> controller.transportControls.pause()
                        PlaybackCommand.PLAY -> controller.transportControls.play()
                        PlaybackCommand.NEXT -> controller.transportControls.skipToNext()
                        PlaybackCommand.PREVIOUS -> controller.transportControls.skipToPrevious()
                    }
                } catch (error: Exception) {
                    markSent(false)
                    resumeOnce(
                        PlaybackRequestResult.Failed(
                            command,
                            error.message ?: error.javaClass.simpleName,
                        ),
                    )
                    return@suspendCancellableCoroutine
                }
                if (tracksMedia) {
                    confirmMediaChange()
                } else {
                    val after = controller.playbackState
                    if (matchesPlayback(command, after)) {
                        resumeOnce(
                            PlaybackRequestResult.Confirmed(
                                command = command,
                                alreadyInRequestedState = beforeMatches,
                            ),
                        )
                    }
                }
            }
        } finally {
            // 확인이 끝난 뒤에도 콜백이 남아 있으면 다음 명령과 세션 이벤트가 섞입니다.
            unregister()
        }
    }

    private fun requiredAction(command: PlaybackCommand): Long {
        return when (command) {
            PlaybackCommand.PAUSE -> PlaybackState.ACTION_PAUSE
            PlaybackCommand.PLAY -> PlaybackState.ACTION_PLAY
            PlaybackCommand.NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
            PlaybackCommand.PREVIOUS -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
    }

    private fun matchesPlayback(command: PlaybackCommand, state: PlaybackState?): Boolean {
        return when (command) {
            PlaybackCommand.PAUSE -> state?.state == PlaybackState.STATE_PAUSED
            PlaybackCommand.PLAY -> state?.state == PlaybackState.STATE_PLAYING
            PlaybackCommand.NEXT, PlaybackCommand.PREVIOUS -> false
        }
    }

    private fun currentIdentity(controller: MediaController): String? {
        val metadata = controller.metadata
        return MediaIdentity.of(
            metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            metadata?.description?.title?.toString(),
        )
    }

    private sealed interface FindResult {
        data object ListenerMissing : FindResult

        data class Unavailable(val otherPackages: List<String>) : FindResult

        data class Found(val controller: MediaController) : FindResult
    }

    private data class ListedSession(
        val controller: MediaController,
        val snapshot: MediaSessionSnapshot,
    )

    companion object {
        private const val TAG = "YoutubeController"
    }
}
