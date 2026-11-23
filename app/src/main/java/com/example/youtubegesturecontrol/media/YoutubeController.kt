package com.example.youtubegesturecontrol.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.example.youtubegesturecontrol.ControlTimeouts
import com.example.youtubegesturecontrol.accessibility.ShortsAccessibilityFallback
import com.example.youtubegesturecontrol.accessibility.ShortsAccessibilityPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * 설치된 유튜브 앱의 미디어 세션에 일시정지, 재생, 다음, 이전을 요청합니다.
 * 쇼츠 껍데기(mediaId·title 없음, SEEK_TO 없음)는 전송 컨트롤을 호출하지 않고
 * 바로 접근성 탭·스와이프를 보냅니다. 일반 영상은 미디어 세션만 확인합니다.
 *
 * 콜백은 컨트롤러를 만든 스레드의 Looper로 오므로 이 함수는 메인 스레드에서 호출합니다.
 * 성공적인 메서드 호출은 요청을 보냈다는 뜻일 뿐, 재생 상태가 바뀌었다는 뜻은 아닙니다.
 */
class YoutubeController(
    private val context: Context,
) {
    suspend fun request(command: PlaybackCommand): PlaybackRequestResult {
        val fingerprint = DispatchFingerprint()
        var requestSent = false
        return try {
            val located = findSession()
            val shellResult = accessibilityForShell(located, command)
            shellResult ?: withTimeout(ControlTimeouts.PLAYBACK_CONFIRMATION_TIMEOUT_MS) {
                sendAndAwait(located, command, fingerprint) { sent -> requestSent = sent }
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            if (requestSent) {
                val result = PlaybackRequestResult.ConfirmationTimedOut(
                    command = command,
                    mediaId = fingerprint.mediaId,
                    title = fingerprint.title,
                    actions = fingerprint.actions,
                )
                logResult(command, result)
                result
            } else {
                PlaybackRequestResult.ResponseTimedOut
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val result = PlaybackRequestResult.Failed(command, error.message ?: error.javaClass.simpleName)
            logResult(command, result)
            result
        }
    }

    /**
     * 쇼츠 껍데기면 미디어 확인 시간 밖에서 접근성으로 보냅니다.
     * 껍데기가 아니거나 재생 상태를 못 읽으면 null이고, 그때는 미디어 세션만 사용합니다.
     */
    private suspend fun accessibilityForShell(
        located: FindResult,
        command: PlaybackCommand,
    ): PlaybackRequestResult? {
        val found = located as? FindResult.Found ?: return null
        val before = found.controller.playbackState ?: return null
        val identity = describedMedia(found.controller)
        if (!ShortsAccessibilityPolicy.looksLikeShortsShell(identity.mediaId, identity.title, before.actions)) {
            return null
        }
        logDispatchStart(command, before, identity, shell = true)
        return ShortsAccessibilityFallback.performForShell(
            command,
            identity.mediaId,
            identity.title,
            before.actions,
        )
    }

    private suspend fun sendAndAwait(
        located: FindResult,
        command: PlaybackCommand,
        fingerprint: DispatchFingerprint,
        markSent: (Boolean) -> Unit,
    ): PlaybackRequestResult {
        return when (located) {
            FindResult.ListenerMissing -> PlaybackRequestResult.ListenerAccessMissing
            is FindResult.Unavailable -> PlaybackRequestResult.SessionUnavailable(located.otherPackages)
            is FindResult.Found -> dispatch(located.controller, command, fingerprint, markSent)
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
        controllers.forEachIndexed { index, controller ->
            logSession(index, controller)
        }
        val listed = controllers.map { controller ->
            ListedSession(controller, snapshotOf(controller))
        }
        val index = YoutubeSessionSelector.selectIndex(listed.map { it.snapshot })
        if (index == null) {
            val packages = listed.map { it.snapshot.packageName }
            Log.i(TAG, "selectedIndex=null packages=$packages")
            return FindResult.Unavailable(packages)
        }
        Log.i(TAG, "selectedIndex=$index pkg=${listed[index].snapshot.packageName}")
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
        val identity = describedMedia(controller)
        return MediaSessionSnapshot(
            packageName = controller.packageName ?: "",
            playback = playback,
            mediaId = identity.mediaId,
            title = identity.title,
            actions = controller.playbackState?.actions,
        )
    }

    private suspend fun dispatch(
        controller: MediaController,
        command: PlaybackCommand,
        fingerprint: DispatchFingerprint,
        markSent: (Boolean) -> Unit,
    ): PlaybackRequestResult {
        val before = controller.playbackState
        val identity = describedMedia(controller)
        fingerprint.mediaId = identity.mediaId
        fingerprint.title = identity.title
        fingerprint.actions = before?.actions
        val shell = before != null && ShortsAccessibilityPolicy.looksLikeShortsShell(
            identity.mediaId,
            identity.title,
            before.actions,
        )
        logDispatchStart(command, before, identity, shell)
        if (before == null) {
            Log.i(TAG, "dispatch PlaybackStateUnreadable command=$command")
            return PlaybackRequestResult.PlaybackStateUnreadable
        }
        // 쇼츠 껍데기는 pause/play 비트가 있어도 전송하지 않습니다.
        // 그 호출은 상태가 바뀌지 않아 확인 시간만 지나고, 탭이 나가지 않습니다.
        if (shell) {
            return PlaybackRequestResult.ActionUnsupported(
                command = command,
                mediaId = identity.mediaId,
                title = identity.title,
                actions = before.actions,
            )
        }
        val required = requiredAction(command)
        if (!PlaybackActionSupport.allows(before.actions, required)) {
            Log.i(
                TAG,
                "dispatch ActionUnsupported command=$command requiredAction=$required actions=${before.actions}",
            )
            return PlaybackRequestResult.ActionUnsupported(
                command = command,
                mediaId = identity.mediaId,
                title = identity.title,
                actions = before.actions,
            )
        }
        val tracksMedia = command == PlaybackCommand.NEXT || command == PlaybackCommand.PREVIOUS
        val beforeMatches = if (tracksMedia) false else matchesPlayback(command, before)
        val beforeIdentity = currentIdentity(controller)
        traceBeforeIdentity = beforeIdentity
        traceIdentityReader = { currentIdentity(controller) }
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
                        logResult(command, result, beforeIdentity, currentIdentity(controller))
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

    private fun logSession(index: Int, controller: MediaController) {
        val state = controller.playbackState
        val identity = describedMedia(controller)
        val actions = state?.actions
        val shell = actions != null && ShortsAccessibilityPolicy.looksLikeShortsShell(
            identity.mediaId,
            identity.title,
            actions,
        )
        Log.i(
            TAG,
            "session[$index] pkg=${controller.packageName} playback=${state?.state} " +
                "actions=$actions shell=$shell hasMediaId=${!identity.mediaId.isNullOrEmpty()} " +
                "hasTitle=${!identity.title.isNullOrEmpty()}",
        )
    }

    private fun logDispatchStart(
        command: PlaybackCommand,
        state: PlaybackState?,
        identity: DescribedMedia,
        shell: Boolean,
    ) {
        Log.i(
            TAG,
            "dispatch shell=$shell skipMedia=$shell command=$command playback=${state?.state} " +
                "actions=${state?.actions} hasMediaId=${!identity.mediaId.isNullOrEmpty()} " +
                "hasTitle=${!identity.title.isNullOrEmpty()}",
        )
    }

    private fun describedMedia(controller: MediaController): DescribedMedia {
        val metadata = controller.metadata
        return DescribedMedia(
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            title = metadata?.description?.title?.toString(),
        )
    }

    private fun logResult(
        command: PlaybackCommand,
        result: PlaybackRequestResult,
        beforeIdentity: String? = traceBeforeIdentity,
        afterIdentity: String? = traceIdentityReader?.invoke(),
    ) {
        if (
            result !is PlaybackRequestResult.Confirmed &&
            result !is PlaybackRequestResult.ConfirmationTimedOut &&
            result !is PlaybackRequestResult.Failed
        ) {
            return
        }
        val identity = if (command == PlaybackCommand.NEXT || command == PlaybackCommand.PREVIOUS) {
            " hasBeforeIdentity=${beforeIdentity != null} hasAfterIdentity=${afterIdentity != null} " +
                "identityChanged=${MediaIdentity.changed(beforeIdentity, afterIdentity)}"
        } else {
            ""
        }
        Log.i(TAG, "result ${result::class.simpleName} command=$command$identity")
    }

    private fun currentIdentity(controller: MediaController): String? {
        val metadata = controller.metadata
        return MediaIdentity.of(
            metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            metadata?.description?.title?.toString(),
        )
    }

    private data class DescribedMedia(
        val mediaId: String?,
        val title: String?,
    )

    private class DispatchFingerprint {
        var mediaId: String? = null
        var title: String? = null
        var actions: Long? = null
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

    private var traceBeforeIdentity: String? = null
    private var traceIdentityReader: (() -> String?)? = null

    companion object {
        private const val TAG = "YoutubeController"
    }
}
